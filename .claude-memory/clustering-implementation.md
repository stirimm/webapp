# News Clustering Implementation (2026-02-16)

## What Was Built
In-memory trigram-based clustering of the top 200 news articles. Duplicate articles (same event, different sources) are grouped — only the primary (earliest published) is shown, with other sources available via an expandable `<details>` element.

## Architecture Decisions

### Clustering in Kotlin (not SQL)
- 200 articles = 19,900 pairwise comparisons — trivially fast in memory
- No dependency on `pg_trgm` or any external library
- Runs inside the existing Caffeine cache (1-min TTL), so clustering happens at most once per minute

### Algorithm: Character Trigram Jaccard + Union-Find
- **Similarity**: `max(titleSim, descSim * 0.9)` with threshold > 0.35
- Description comparison uses first 500 chars (enough for press release copies)
- **Clustering**: Union-Find (disjoint set) with path compression for transitive grouping
- **Primary selection**: Earliest `publishDate` in each cluster

### Key file: `NewsClusterService.kt`
- `trigrams()` — lowercase, normalize whitespace, extract character 3-grams
- `jaccardSimilarity()` — intersection / union of trigram sets
- `cluster()` — pairwise compare, union-find, group, sort by primary date desc

## Bugs Encountered & Fixed

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

### Final design
- Left border: gray (#555) for single-source, blue (#5a8fa8) for multi-source
- Meta line: "publicat de **source** cu **time**" (unchanged from original)
- Below meta: "▸ și alte N surse" / "▸ și altă 1 sursă" as `<details>` summary
- Expanded: flex-wrap chips (gray background pills) with source links, right-aligned

### Mustache template notes
- Use `{{{tripleMustache}}}` for pre-built HTML (source chips, summary text)
- Build HTML strings in Kotlin controller — Mustache has no join/last-item helpers
- `escapeHtml()` utility in controller for user-supplied content in HTML strings

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
