package com.emilburzo.stirimm.stirimmwebapp.controller

import com.emilburzo.stirimm.stirimmwebapp.formatTimeAgo
import com.emilburzo.stirimm.stirimmwebapp.service.NewsCluster
import com.emilburzo.stirimm.stirimmwebapp.service.NewsService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Controller
class IndexController(
    private val service: NewsService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/")
    fun blog(model: Model): String {
        val rendered = service.findRecent().map { it.render() }
        model["news"] = rendered
        model["isRecent"] = true
        model["isPopular"] = false
        model["canonicalPath"] = "/"
        model["jsonLd"] = buildJsonLd("/", rendered)
        return "index"
    }

    @GetMapping("/popular")
    fun popularRedirect() = RedirectView("/populare").apply { setStatusCode(org.springframework.http.HttpStatus.MOVED_PERMANENTLY) }

    @GetMapping("/populare")
    fun popular(model: Model): String {
        val rendered = service.findRecent()
            .filter { it.duplicates.isNotEmpty() }
            .sortedByDescending { it.duplicates.distinctBy { d -> d.source }.size + 1 }
            .map { it.render() }
        model["news"] = rendered
        model["isRecent"] = false
        model["isPopular"] = true
        model["canonicalPath"] = "/populare"
        model["jsonLd"] = buildJsonLd("/populare", rendered)
        return "index"
    }

    private fun buildJsonLd(path: String, news: List<RenderedNews>): String {
        val items = news.mapIndexed { index, item ->
            mapOf(
                "@type" to "ListItem",
                "position" to (index + 1),
                "item" to mapOf(
                    "@type" to "NewsArticle",
                    "headline" to item.title,
                    "url" to item.url,
                    "datePublished" to item.publishedAtIso,
                    "description" to item.description,
                    "publisher" to mapOf(
                        "@type" to "Organization",
                        "name" to item.source
                    )
                )
            )
        }
        val jsonLd = mapOf(
            "@context" to "https://schema.org",
            "@type" to "CollectionPage",
            "name" to "Știri din Maramureș",
            "headline" to "Știri din Maramureș - Toată presa locală într-un singur loc",
            "description" to "Cele mai recente știri din județul Maramureș agregate din surse locale.",
            "url" to "https://stiri.maramures.io$path",
            "inLanguage" to "ro",
            "mainEntity" to mapOf(
                "@type" to "ItemList",
                "itemListElement" to items
            )
        )
        return objectMapper.writeValueAsString(jsonLd)
    }

}

fun NewsCluster.render(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): RenderedNews {
    val uniqueDuplicates = duplicates.distinctBy { it.source }
    val sourceCount = uniqueDuplicates.size + 1

    // All sources: primary first, then duplicates
    val allSourcesHtml = buildList {
        add("<a href=\"${escapeHtml(primary.url)}\" target=\"_blank\" rel=\"noopener noreferrer\">${escapeHtml(primary.source)}</a>")
        uniqueDuplicates.forEach { dup ->
            add("<a href=\"${escapeHtml(dup.url)}\" target=\"_blank\" rel=\"noopener noreferrer\">${escapeHtml(dup.source)}</a>")
        }
    }.joinToString("\n")

    return RenderedNews(
        id = primary.id,
        title = primary.title.ifBlank { "«Fără titlu»" },
        description = primary.description,
        url = primary.url,
        source = primary.source,
        addedAt = formatTimeAgo(primary.publishDate, now),
        publishedAtIso = primary.publishDate.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        sourcesHtml = allSourcesHtml,
        sourceCount = sourceCount,
        sourceCountLabel = if (sourceCount == 1) "1 sursă" else "$sourceCount surse",
        hasMultipleSources = sourceCount > 1
    )
}

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

data class RenderedNews(
    val id: Long,
    val title: String,
    val description: String,
    val url: String,
    val source: String,
    val addedAt: String,
    val publishedAtIso: String,
    val sourcesHtml: String = "",
    val sourceCount: Int = 1,
    val sourceCountLabel: String = "1 sursă",
    val hasMultipleSources: Boolean = false
)
