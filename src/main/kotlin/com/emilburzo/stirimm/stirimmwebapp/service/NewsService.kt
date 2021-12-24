package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import com.emilburzo.stirimm.stirimmwebapp.persistence.cache.CACHE_NAME_RECENT_NEWS
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class NewsService constructor(
    private val repository: NewsRepository
) {

    @Cacheable(CACHE_NAME_RECENT_NEWS)
    fun findRecent(): Iterable<News> {
        return repository.findTop200ByOrderByPublishDateDesc()
    }
}