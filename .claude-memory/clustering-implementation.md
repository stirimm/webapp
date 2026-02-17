# News Clustering Implementation (2026-02-16)

## What Was Built
In-memory trigram-based clustering of the top 300 news articles. Duplicate articles (same event, different sources) are grouped — only the primary (earliest published) is shown, with other sources available via an expandable `<details>` element. Two views: `/` (chronological feed) and `/popular` (multi-source stories sorted by source count). Both views share the same cached data from `NewsService.findRecent()`; the controller filters/sorts for `/popular`.

## Architecture Decisions

### Clustering in Kotlin (not SQL)
- 200 articles = 19,900 pairwise comparisons — trivially fast in memory
- No dependency on `pg_trgm` or any external library
- Runs inside the existing Caffeine cache (1-min TTL), so clustering happens at most once per minute

### Algorithm: Character Trigram Jaccard + Word-Level Dice + Union-Find
- **Three scoring signals** (best one wins):
  1. **Title char-trigram Jaccard** — catches similar titles
  2. **Description char-trigram Jaccard × 0.9** — catches copy-pasted press releases
  3. **Description word-level Dice coefficient** — catches same-event articles with different prose but shared entities/numbers/key terms (stop words removed)
- **Scoring formula**: `max(titleSim, descSim * 0.9, descWordDice)`
- **Dual threshold**:
  - **Cross-source**: > 0.35 (same event covered by different outlets)
  - **Same-source**: > 0.8 (catches RSS republishes/corrections without over-clustering)
- Description comparison uses first 500 chars (enough for press release copies)
- **Clustering**: Union-Find (disjoint set) with path compression for transitive grouping
- **Primary selection**: Two-step process:
  1. Collapse same-source duplicates — keep latest per source (the corrected/updated version)
  2. Among remaining (one per source), pick earliest `publishDate` as primary (first to report)

### Key file: `NewsClusterService.kt`
- `trigrams()` — lowercase, normalize whitespace, extract character 3-grams
- `words()` — lowercase, split on non-alphanumeric, filter stop words, keep tokens ≥ 2 chars
- `jaccardSimilarity()` — intersection / union of sets
- `diceSimilarity()` — Sørensen–Dice coefficient: 2×intersection / (|A|+|B|)
- `cluster()` — pairwise compare, union-find, group, sort by primary date desc
- `STOP_WORDS` — ~50 common Romanian function words filtered from word-level comparison

## Bugs Encountered & Fixed

### Same-source duplicates not clustered (2026-02-16)
**Problem**: `if (articles[i].source == articles[j].source) continue` skipped all same-source comparisons. When a source republished an article with a minor title edit (e.g., "Bani europeni pentru persoane defavorizate" → "Bani pentru persoane defavorizate"), both appeared as separate items.
**Fix**: Dual-threshold approach — compare all pairs, use 0.8 threshold for same-source (vs 0.35 for cross-source). Also changed primary selection to collapse same-source duplicates first (keep latest per source = corrected version), then pick earliest across sources.

### Same source appearing in both primary and duplicates
**Problem**: Transitive clustering (A[source1] ~ B[source2] ~ C[source1]) puts two articles from the same source in one cluster. When source1 is primary, source1 also appears in duplicates.
**Fix**: Filter duplicates to exclude articles matching `primary.source`:
```kotlin
duplicates = sorted.drop(1).map { articles[it] }.filter { it.source != primary.source }
```

### Duplicate source names in "also reported by" list
**Problem**: Same source appearing twice in the expanded chips (e.g., directmm.ro x2) because multiple articles from the same source join the cluster via different transitive links.
**Fix**: `distinctBy { it.source }` when rendering in `IndexController.kt`

### Test articles accidentally clustering
**Problem**: Unit test articles with titles like "Old unique article about something" and "Recent unique article about other thing" shared enough trigrams to exceed threshold 0.35.
**Fix**: Use truly distinct titles in tests (e.g., Romanian-style realistic but unrelated headlines)

### H2 test data.sql interfering with Spring context
**Problem**: Creating `src/test/resources/data.sql` for H2 seed data caused Spring Boot context test to fail.
**Fix**: Removed the file and ran `mvnw clean test` to clear cached target/ artifacts

### Same-event articles with different prose not clustered (2026-02-17)
**Problem**: Three articles about the same handball match (CS Minaur vs CSM Constanța, 26-24) from 24news.ro, eziarultau.ro, and 2mnews.ro appeared as separate items. Each outlet wrote completely different titles and descriptions, so character trigram Jaccard was below 0.35 for all pairs (title: 0.16–0.25, desc: 0.25–0.31).
**Root cause**: Character trigrams dilute the signal from shared entities/numbers when surrounded by different prose. Shared content words (Minaur, CSM, Constanța, 26, 24, 12, 13, handbal, etapa) get buried in unique vocabulary.
**Approaches tested and rejected**:
- Lowering threshold below 0.35: research showed title sim > 0.25 causes many false positives
- Combined `(titleSim + descSim) * 0.8`: catches the handball case BUT also creates false positives — police/traffic articles from the same area share institutional vocabulary + location names (e.g., theft vs drunk driving both in Baia Mare/Coltău scored 0.488). Validated against 300 real articles from local DB.
- Word-level Jaccard (without stop words): scores 0.27–0.32, still below 0.35
**Fix**: Added word-level Sørensen–Dice coefficient on content words (Romanian stop words removed) as a third scoring signal. Dice is more generous than Jaccard (Dice = 2J/(1+J)) and content word filtering removes generic vocabulary that inflates overlap between unrelated articles.
- Handball match: descWordDice = 0.38–0.45 → above 0.35 ✓
- Police false positive: descWordDice ≈ 0.18 → below 0.35 ✓
**Tests added**: Handball match clustering (positive), two police false-positive rejection tests (negative), unit tests for `words()` and `diceSimilarity()`.

## UI/UX Design Process

### Design iterations (11+ mockups tested with Playwright screenshots)
1. **Round 1** (A-F): Variations on comma-separated source links — all felt cluttered with 7-8 sources
2. **Round 2** (G-K): Radical redesigns — story-centric, newspaper, clean byline, ultra-compact, cards
3. **Final choice**: Design H base (left border accent) with expandable `<details>/<summary>`

### What was rejected and why
- **Inline sources** ("publicat de X și încă N surse cu Y"): Too long on mobile, steals focus from description
- **Comma-separated links**: Visually noisy with many sources, hard to scan
- **Dot-separated meta line** ("source . time . N surse"): Hard to parse three items
- **Cards layout**: Too much whitespace, fewer articles visible

### Final design (v2 — two views)
- **Removed** blue left border for multi-source — all items now use uniform gray (#555) border on both views
- Two views: `/` (chronological, all clusters) and `/popular` (multi-source only, sorted by source count desc)
- Nav toggle: "cele mai recente · cele mai populare" — active link is bold, inactive is a link
- On `/popular` view, source count shown in meta line (e.g. "· **9 surse**")
- Meta line: "publicat de **source** cu **time**" (unchanged from original)
- Below meta: "▸ și alte N surse" / "▸ și altă 1 sursă" as `<details>` summary (on both views)
- Expanded: flex-wrap chips (gray background pills) with source links, right-aligned

### Mustache template notes
- Use `{{{tripleMustache}}}` for pre-built HTML (source chips, summary text)
- Build HTML strings in Kotlin controller — Mustache has no join/last-item helpers
- `escapeHtml()` utility in controller for user-supplied content in HTML strings
- Model-level attributes (`isRecent`, `isPopular`) are accessible inside `{{#news}}` list sections via jmustache's parent context fallback
- `RenderedNews` includes `sourceCount` (int) and `sourceCountLabel` (e.g. "9 surse") for the popular view

## Playwright Screenshot Testing
- Install: `npm install playwright` + `npx playwright install chromium` + `npx playwright install-deps chromium`
- Desktop viewport: `{ width: 800, height: 1200, deviceScaleFactor: 2 }`
- Mobile viewport: `{ width: 375, height: 812, deviceScaleFactor: 2 }`
- Use `waitForLoadState('networkidle')` before screenshots
- Click `<details>` summaries via `page.$$('details.source-expand summary')` for expanded state screenshots
- Script at `/workspace/screenshot.js`

## Live Results
- 200 articles fetched, 26 multi-source clusters found (~60% dedup rate matches investigation predictions)
- Largest clusters: 8-9 sources for major news stories
- Clustering is imperceptible in page load time (sub-millisecond for 200 articles)
