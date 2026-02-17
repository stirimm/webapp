package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import org.springframework.stereotype.Service

@Service
class NewsService(
    private val repository: NewsRepository,
    private val clusterService: NewsClusterService
) {

    @Volatile
    private var cachedClusters: List<NewsCluster>? = null

    fun findRecent(): List<NewsCluster> {
        // Lazy init: if cache hasn't been populated yet (startup race),
        // the first request triggers a synchronous load.
        cachedClusters?.let { return it }
        synchronized(this) {
            cachedClusters?.let { return it }
            refresh()
            return cachedClusters ?: emptyList()
        }
    }

    fun refresh() {
        val articles = repository.findTop300ByOrderByPublishDateDesc().toList()
        cachedClusters = clusterService.cluster(articles)
    }
}
