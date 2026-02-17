package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import org.springframework.stereotype.Service
import java.text.Normalizer

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

        // Precompute trigram sets and content word sets
        val titleTrigrams = articles.map { trigrams(it.title) }
        val descTrigrams = articles.map { trigrams(it.description.take(500)) }
        val titleWords = articles.map { words(it.title) }
        val descWords = articles.map { words(it.description.take(500)) }

        // Pairwise comparison
        // Four scoring signals (best one wins):
        // 1. Title char-trigram Jaccard — catches similar titles
        // 2. Description char-trigram Jaccard (×0.9) — catches copy-pasted press releases
        // 3. Description word-level Dice coefficient (stop words removed) — catches same-event
        //    articles with different prose but shared entities/numbers/key terms
        // 4. Title word-level Dice (×0.9) — catches same-event articles covered from different
        //    angles that share key entity names (proper nouns, locations) in their titles
        // Cross-source: threshold 0.35 (same event covered by different outlets)
        // Same-source: threshold 0.8 (catch true duplicates, e.g. RSS republish with minor edits)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val sameSource = articles[i].source == articles[j].source
                val titleSim = jaccardSimilarity(titleTrigrams[i], titleTrigrams[j])
                val descSim = jaccardSimilarity(descTrigrams[i], descTrigrams[j])
                val descWordDice = diceSimilarity(descWords[i], descWords[j])
                val titleWordDice = diceSimilarity(titleWords[i], titleWords[j])
                val score = maxOf(titleSim, descSim * 0.9, descWordDice, titleWordDice * 0.9)
                val threshold = if (sameSource) 0.8 else 0.35
                if (score > threshold) {
                    union(i, j)
                }
            }
        }

        // Group by cluster root
        val groups = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        // For each cluster:
        // 1. Collapse same-source duplicates — keep latest per source (corrected version)
        // 2. Among the remaining (one per source), pick earliest as primary (first to report)
        return groups.values.map { indices ->
            val latestPerSource = indices
                .groupBy { articles[it].source }
                .values
                .map { sourceIndices -> sourceIndices.maxBy { articles[it].publishDate } }

            val sorted = latestPerSource.sortedBy { articles[it].publishDate }
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
        // NFKD decomposition normalizes Unicode mathematical bold/italic/sans-serif characters
        // (e.g. 𝐓𝐀𝐑𝐎𝐌 → TAROM) which news sites use for styling. Also strips diacritics
        // (ă→a, ș→s) which improves matching across variant spellings.
        private val COMBINING_MARKS = Regex("\\p{M}")
        private fun normalizeUnicode(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "")
        }

        // Common Romanian stop words — filtered from word-level comparison to focus on
        // content words (entity names, numbers, domain-specific terms).
        // Stored in normalized form (no diacritics) to match NFKD-normalized input.
        private val STOP_WORDS = setOf(
            "al", "am", "ar", "au", "ca", "ce", "cu", "da", "de", "din", "ea", "ei",
            "el", "eu", "fi", "ia", "ii", "la", "le", "li", "mai", "ne", "ni", "nu",
            "pe", "sa", "se", "si", "un", "va", "in", "una", "sau", "cum",
            "dar", "ori", "prin", "sub", "tot", "sunt", "este", "fost", "care", "pentru",
            "dupa", "intre", "aceasta", "acest", "asupra", "fiind", "intr"
        )

        fun trigrams(text: String): Set<String> {
            val normalized = normalizeUnicode(text).lowercase().replace(Regex("\\s+"), " ").trim()
            if (normalized.length < 3) return emptySet()
            return (0..normalized.length - 3).mapTo(mutableSetOf()) { normalized.substring(it, it + 3) }
        }

        fun words(text: String): Set<String> {
            return normalizeUnicode(text).lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 2 && it !in STOP_WORDS }
                .toSet()
        }

        fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 0.0
            val intersection = a.count { it in b }
            val union = a.size + b.size - intersection
            return if (union == 0) 0.0 else intersection.toDouble() / union
        }

        fun diceSimilarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 0.0
            val intersection = a.count { it in b }
            val total = a.size + b.size
            return if (total == 0) 0.0 else 2.0 * intersection / total
        }
    }
}
