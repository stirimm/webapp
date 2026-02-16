package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import com.emilburzo.stirimm.stirimmwebapp.persistence.cache.CACHE_NAME_RECENT_NEWS
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class NewsService constructor(
    private val repository: NewsRepository,
    private val clusterService: NewsClusterService
) {

    @Cacheable(CACHE_NAME_RECENT_NEWS)
    fun findRecent(): List<NewsCluster> {
        val articles = repository.findTop200ByOrderByPublishDateDesc().toList()
        return clusterService.cluster(articles)
    }
}
