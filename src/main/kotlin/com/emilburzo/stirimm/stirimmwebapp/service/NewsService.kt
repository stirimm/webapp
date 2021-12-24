package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import org.springframework.stereotype.Service

@Service
class NewsService constructor(
    private val repository: NewsRepository
) {

    fun findRecent(): Iterable<News> {
        return repository.findTop200ByOrderByPublishDateDesc()
    }
}