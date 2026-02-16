# SEO Improvements (2026-02-16)

## Context
Site was crawled by Google but poorly indexed. Google Search Console showed:
- "User-declared canonical: None"
- "Sitemaps: Temporary processing error"
- Site treated as thin content / aggregator with no unique value

## What Was Implemented

### Phase 1 (done before this session, via Gemini advice)
- `<title>` with keywords: "Știri din Maramureș - Toată presa locală într-un singur loc"
- `<meta name="description">` with local source names
- `<meta name="keywords">`
- `<link rel="canonical">` (initially hardcoded to `/`)
- Open Graph tags (og:type, og:title, og:description, og:url, og:image)
- Schema.org JSON-LD CollectionPage (initially minimal/static)

### Phase 2 (this session)
1. **Dynamic canonical/OG URLs** — `canonicalPath` model attribute set per route (`/` or `/popular`). Both `<link rel="canonical">` and `<meta property="og:url">` use it. Previously `/popular` wrongly declared canonical as `/`.
2. **robots.txt** — static file in `src/main/resources/static/`, allows all crawlers, references sitemap.
3. **sitemap.xml** — static file listing `/` (priority 1.0) and `/popular` (priority 0.8), both hourly changefreq.
4. **Rich JSON-LD schema** — generated server-side by `IndexController.buildJsonLd()` using Jackson `ObjectMapper`. Lists actual news articles as `NewsArticle` items in an `ItemList`. Mustache renders it via triple-mustache `{{{jsonLd}}}`.
5. **`<time>` elements** — `publishedAtIso` field added to `RenderedNews`, formatted as ISO 8601 (`DateTimeFormatter.ISO_OFFSET_DATE_TIME`). Used in `<time datetime="...">` wrapping the PrettyTime relative date.
6. **Twitter Card meta tags** — `twitter:card` (summary), `twitter:title`, `twitter:description`, `twitter:image`.
7. **`site.webmanifest`** — filled `name` ("Știri din Maramureș") and `short_name` ("Știri MM"), matched theme/background colors to dark theme (#333333).
8. **`rel="noopener noreferrer"`** — added to all `target="_blank"` links (article titles in template + source chips in controller-generated HTML).
9. **`<main>` landmark** — news list container changed from `<div>` to `<main>`.
10. **Font stack cleanup** — removed `'Noto Sans'` (was never loaded), replaced with `system-ui, -apple-system, sans-serif`. User prefers no Google Fonts / no external dependencies.

## Technical Decisions

### JSON-LD: server-side generation vs Mustache template
Mustache has no way to insert commas between loop iterations, so inline JSON-LD in templates produces invalid JSON. Solution: generate the entire JSON string in the controller using Jackson `ObjectMapper.writeValueAsString()` and pass it to the template as a model attribute rendered via triple-mustache (`{{{jsonLd}}}`).

### No Google Fonts
User explicitly does not want Google dependencies. Use `system-ui` font stack instead.

### Static sitemap
Only 2 routes exist (`/` and `/popular`), so a static XML file suffices. If routes are added later, consider a dynamic sitemap endpoint.

## User Preferences
- No external service dependencies (Google Fonts, CDNs, etc.)
- Romanian language UI — "Agregator" sounds unnatural in Romanian; use "Toată presa locală într-un singur loc" instead
