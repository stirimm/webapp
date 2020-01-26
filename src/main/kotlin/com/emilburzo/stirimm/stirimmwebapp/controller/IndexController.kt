package com.emilburzo.stirimm.stirimmwebapp.controller

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import java.time.ZoneOffset
import java.util.*

/**
 * Created by emil on 14.12.2019.
 */
@Controller
class IndexController(
        private val repository: NewsRepository
) {

    @GetMapping("/")
    fun blog(model: Model): String {
        model["news"] = repository.findTop200ByOrderByPublishDateDesc().map { it.render() }
        return "index"
    }

}

fun News.render() = RenderedNews(
        id = id,
        title = title,
        description = description,
        url = url,
        source = source,
        addedAt = PrettyTime(Locale("ro")).format(Date.from(publishDate.atZone(ZoneOffset.UTC).toInstant()))
)

data class RenderedNews(
        val id: Long,
        val title: String,
        val description: String,
        val url: String,
        val source: String,
        val addedAt: String
)


