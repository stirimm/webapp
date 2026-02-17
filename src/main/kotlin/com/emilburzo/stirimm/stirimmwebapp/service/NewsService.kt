package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.NewsRepository
import org.springframework.stereotype.Service

@Service
class NewsService(
    private val repository: NewsRepository,
    private val clusterService: NewsClusterService
) {

    @Volatile
    private var cachedClusters: List<NewsCluster> = emptyList()

    fun findRecent(): List<NewsCluster> = cachedClusters

    fun refresh() {
        val articles = repository.findTop300ByOrderByPublishDateDesc().toList()
        cachedClusters = clusterService.cluster(articles)
    }
}
