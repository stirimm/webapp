package com.emilburzo.stirimm.stirimmwebapp.controller

import com.emilburzo.stirimm.stirimmwebapp.service.NewsCluster
import com.emilburzo.stirimm.stirimmwebapp.service.NewsService
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import java.time.ZoneOffset
import java.util.*

@Controller
class IndexController(
    private val service: NewsService
) {

    @GetMapping("/")
    fun blog(model: Model): String {
        model["news"] = service.findRecent().map { it.render() }
        return "index"
    }

}

fun NewsCluster.render(): RenderedNews {
    val prettyTime = PrettyTime(Locale("ro"))

    // Deduplicate sources (keep first article per unique source)
    val uniqueDuplicates = duplicates
        .distinctBy { it.source }

    val count = uniqueDuplicates.size
    val summary = when {
        count == 0 -> ""
        count == 1 -> "și altă 1 sursă"
        else -> "și alte $count surse"
    }
    val sourceChipsHtml = uniqueDuplicates.joinToString("\n") { dup ->
        "<a href=\"${escapeHtml(dup.url)}\" target=\"_blank\">${escapeHtml(dup.source)}</a>"
    }

    return RenderedNews(
        id = primary.id,
        title = primary.title.ifBlank { "«Fără titlu»" },
        description = primary.description,
        url = primary.url,
        source = primary.source,
        addedAt = prettyTime.format(Date.from(primary.publishDate.atZone(ZoneOffset.UTC).toInstant())),
        alsoReportedBySummary = summary,
        alsoReportedByHtml = sourceChipsHtml,
        hasAlsoReportedBy = count > 0
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
    val alsoReportedBySummary: String = "",
    val alsoReportedByHtml: String = "",
    val hasAlsoReportedBy: Boolean = false
)
