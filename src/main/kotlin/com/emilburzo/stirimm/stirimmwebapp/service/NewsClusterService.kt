package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import org.springframework.stereotype.Service

data class NewsCluster(
    val primary: News,
    val duplicates: List<News>
)

@Service
class NewsClusterService {

    fun cluster(articles: List<News>): List<NewsCluster> {
        val n = articles.size
        if (n == 0) return emptyList()

        // Union-Find
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (c != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        // Precompute trigram sets
        val titleTrigrams = articles.map { trigrams(it.title) }
        val descTrigrams = articles.map { trigrams(it.description.take(500)) }

        // Pairwise comparison — only link articles from different sources
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (articles[i].source == articles[j].source) continue
                val titleSim = jaccardSimilarity(titleTrigrams[i], titleTrigrams[j])
                val descSim = jaccardSimilarity(descTrigrams[i], descTrigrams[j])
                val score = maxOf(titleSim, descSim * 0.9)
                if (score > 0.35) {
                    union(i, j)
                }
            }
        }

        // Group by cluster root
        val groups = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        // For each cluster, pick earliest publishDate as primary
        // Filter duplicates to only show distinct sources (skip same-source as primary)
        return groups.values.map { indices ->
            val sorted = indices.sortedBy { articles[it].publishDate }
            val primary = articles[sorted.first()]
            NewsCluster(
                primary = primary,
                duplicates = sorted.drop(1)
                    .map { articles[it] }
                    .filter { it.source != primary.source }
            )
        }.sortedByDescending { it.primary.publishDate }
    }

    companion object {
        fun trigrams(text: String): Set<String> {
            val normalized = text.lowercase().replace(Regex("\\s+"), " ").trim()
            if (normalized.length < 3) return emptySet()
            return (0..normalized.length - 3).mapTo(mutableSetOf()) { normalized.substring(it, it + 3) }
        }

        fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 0.0
            val intersection = a.count { it in b }
            val union = a.size + b.size - intersection
            return if (union == 0) 0.0 else intersection.toDouble() / union
        }
    }
}
