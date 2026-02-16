# News Deduplication Investigation (2026-02-16)

## Data Profile
- 252K articles, 16 sources, ~126 articles/day, 14 active sources on a typical day
- Top 200 displayed articles: ~60% have at least one duplicate (same event, different source)
- On a single day: ~34% of articles are in a duplicate cluster (title sim > 0.35)

## Duplicate Patterns Observed
1. **Near-identical titles** — trivial differences like "DN18" vs "DN 18" (sim 0.94)
2. **Same facts, different phrasing** — "prinsă cu 135 km/h" vs "Incredibil: 135 km/h" (sim 0.33)
3. **Different titles, same description** — "Viteză de autostradă" (title sim 0.19) but desc sim 0.50+ because sites copy-paste police/ISU press releases
4. **Recurring columns** — same source, same title, different days (weather, jobs) — NOT duplicates

## Key Insight: Description Similarity
Many local news sites copy-paste from the same official press releases. Description similarity (first 300-500 chars) is often MORE reliable than title similarity:
- Example: article 267748 vs 267722 — title sim 0.187, desc sim 0.500
- Three articles (267681, 267693, 267722) had desc sim of 1.000 despite title sim ranging 0.28-0.39

## Approach Benchmarks (PostgreSQL pg_trgm)
- **Per-article check** (1 article vs 48h window): **24ms** — very fast
- **Batch 1-week** (1231 articles, CTE + brute force): **~4 seconds**
- GIN trigram index on full table was SLOWER (114s) for windowed queries — the date-filtered CTE approach wins

## Thresholds Tested
- Title trigram > 0.7: Very high precision, catches ~40% of duplicates
- Title trigram > 0.5: Good precision, catches ~60%
- Title trigram > 0.35: Catches ~80%, ~15% false positive rate at lower end
- Title trigram > 0.25: Too noisy, many false positives
- Description trigram > 0.4: Excellent for press-release-based duplicates, catches cases title misses

## Shared Numbers as Signal
- Numbers (ages, speeds, amounts, road numbers) are strong discriminators
- "135" + "Baia Mare" within same day = almost certainly same event
- Extract via `regexp_matches(title, '(\d+)', 'g')`, filter out years and very common numbers

## PostgreSQL Extensions Available
- `pg_trgm` — trigram similarity, works well
- Romanian full-text search config already set as DB default
- `to_tsvector('romanian', ...)` does stemming and stop-word removal

## Recommended Approach for Top 200
For small result sets (200-500 articles), all work can happen in-memory in Kotlin after the DB fetch. No need for MinHash, TF-IDF, or complex DB-side operations. Simple pairwise comparison of 200 articles = 19,900 pairs — trivial.
