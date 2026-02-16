package com.emilburzo.stirimm.stirimmwebapp.controller

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
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
    val alsoHtml = if (duplicates.isNotEmpty()) {
        duplicates.joinToString(", ") { dup ->
            "<a href=\"${escapeHtml(dup.url)}\" target=\"_blank\">${escapeHtml(dup.source)}</a>"
        }
    } else ""

    return RenderedNews(
        id = primary.id,
        title = primary.title.ifBlank { "«Fără titlu»" },
        description = primary.description,
        url = primary.url,
        source = primary.source,
        addedAt = prettyTime.format(Date.from(primary.publishDate.atZone(ZoneOffset.UTC).toInstant())),
        alsoReportedByHtml = alsoHtml,
        hasAlsoReportedBy = alsoHtml.isNotEmpty()
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
    val alsoReportedByHtml: String = "",
    val hasAlsoReportedBy: Boolean = false
)
