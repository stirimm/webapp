package com.emilburzo.stirimm.stirimmwebapp.service

import com.emilburzo.stirimm.stirimmwebapp.persistence.News
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NewsClusterServiceTest {

    private val service = NewsClusterService()

    private fun makeNews(
        id: Long,
        title: String,
        source: String,
        description: String = "",
        publishDate: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0).plusHours(id)
    ) = News(
        id = id,
        title = title,
        description = description,
        source = source,
        url = "https://$source.ro/article/$id",
        publishDate = publishDate,
        ingestDate = publishDate
    )

    // --- Trigram similarity tests ---

    @Test
    fun `trigrams of short text`() {
        assertEquals(emptySet<String>(), NewsClusterService.trigrams("ab"))
        assertEquals(setOf("abc"), NewsClusterService.trigrams("abc"))
        assertEquals(setOf("abc", "bcd"), NewsClusterService.trigrams("abcd"))
    }

    @Test
    fun `trigrams normalizes whitespace and case`() {
        val a = NewsClusterService.trigrams("Hello  World")
        val b = NewsClusterService.trigrams("hello world")
        assertEquals(a, b)
    }

    @Test
    fun `jaccard similarity of identical sets`() {
        val s = setOf("abc", "bcd", "cde")
        assertEquals(1.0, NewsClusterService.jaccardSimilarity(s, s))
    }

    @Test
    fun `jaccard similarity of disjoint sets`() {
        assertEquals(0.0, NewsClusterService.jaccardSimilarity(setOf("abc"), setOf("xyz")))
    }

    @Test
    fun `jaccard similarity of empty sets`() {
        assertEquals(0.0, NewsClusterService.jaccardSimilarity(emptySet(), emptySet()))
    }

    @Test
    fun `identical titles have similarity 1`() {
        val t = NewsClusterService.trigrams("Accident grav pe DN18 în Baia Mare")
        assertEquals(1.0, NewsClusterService.jaccardSimilarity(t, t))
    }

    @Test
    fun `similar titles have high similarity`() {
        val a = NewsClusterService.trigrams("Accident grav pe DN18 în Baia Mare")
        val b = NewsClusterService.trigrams("Accident grav pe DN 18 în Baia Mare")
        assertTrue(NewsClusterService.jaccardSimilarity(a, b) > 0.8)
    }

    @Test
    fun `different titles have low similarity`() {
        val a = NewsClusterService.trigrams("Accident grav pe DN18")
        val b = NewsClusterService.trigrams("Meteo: prognoza pentru weekend")
        assertTrue(NewsClusterService.jaccardSimilarity(a, b) < 0.1)
    }

    // --- Clustering tests ---

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList<NewsCluster>(), service.cluster(emptyList()))
    }

    @Test
    fun `single article returns single cluster with no duplicates`() {
        val article = makeNews(1, "Test article", "source1")
        val result = service.cluster(listOf(article))
        assertEquals(1, result.size)
        assertEquals(article, result[0].primary)
        assertTrue(result[0].duplicates.isEmpty())
    }

    @Test
    fun `articles from same source are never clustered`() {
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1")
        val b = makeNews(2, "Accident grav pe DN18 în Baia Mare", "source1")
        val result = service.cluster(listOf(a, b))
        assertEquals(2, result.size)
        assertTrue(result.all { it.duplicates.isEmpty() })
    }

    @Test
    fun `near-duplicate titles from different sources are clustered`() {
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1")
        val b = makeNews(2, "Accident grav pe DN 18 în Baia Mare", "source2")
        val result = service.cluster(listOf(a, b))
        assertEquals(1, result.size)
        assertEquals(1, result[0].duplicates.size)
    }

    @Test
    fun `completely different articles are not clustered`() {
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1")
        val b = makeNews(2, "Meteo: prognoza pentru weekend în Maramureș", "source2")
        val result = service.cluster(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `earliest article is primary in cluster`() {
        val early = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val late = makeNews(2, "Accident grav pe DN18 în Baia Mare", "source2",
            publishDate = LocalDateTime.of(2024, 1, 1, 14, 0))
        val result = service.cluster(listOf(late, early))
        assertEquals(1, result.size)
        assertEquals(early.id, result[0].primary.id)
        assertEquals(late.id, result[0].duplicates[0].id)
    }

    @Test
    fun `multi-source cluster groups all duplicates`() {
        val a = makeNews(1, "Viteză record de 135 km pe oră în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val b = makeNews(2, "Viteză record de 135 km pe oră în Baia Mare", "source2",
            publishDate = LocalDateTime.of(2024, 1, 1, 11, 0))
        val c = makeNews(3, "Viteză record de 135 km pe oră în Baia Mare", "source3",
            publishDate = LocalDateTime.of(2024, 1, 1, 12, 0))
        val result = service.cluster(listOf(a, b, c))
        assertEquals(1, result.size)
        assertEquals(a.id, result[0].primary.id)
        assertEquals(2, result[0].duplicates.size)
    }

    @Test
    fun `description similarity catches different titles about same event`() {
        val sharedDesc = "Polițiștii din Baia Mare au depistat un autoturism care circula cu viteza de 135 km/h pe DN1C"
        val a = makeNews(1, "Viteză de autostradă pe DN1C", "source1", description = sharedDesc,
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val b = makeNews(2, "Incredibil: prins cu 135 km/h", "source2", description = sharedDesc,
            publishDate = LocalDateTime.of(2024, 1, 1, 11, 0))
        val result = service.cluster(listOf(a, b))
        assertEquals(1, result.size)
    }

    @Test
    fun `transitive cluster excludes same-source duplicates from reported-by list`() {
        // A(source1) matches B(source2), B(source2) matches C(source1)
        // All three cluster together, primary = A (earliest, source1)
        // Duplicates should NOT include C because C.source == primary.source
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val b = makeNews(2, "Accident grav pe DN18 în Baia Mare", "source2",
            publishDate = LocalDateTime.of(2024, 1, 1, 11, 0))
        val c = makeNews(3, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 12, 0))
        val result = service.cluster(listOf(a, b, c))
        assertEquals(1, result.size)
        assertEquals(a.id, result[0].primary.id)
        // Only source2 should appear in duplicates, not source1 again
        assertTrue(result[0].duplicates.all { it.source != a.source },
            "Duplicates should not contain articles from the same source as primary")
        assertEquals(1, result[0].duplicates.size)
        assertEquals("source2", result[0].duplicates[0].source)
    }

    @Test
    fun `results are sorted by primary publish date descending`() {
        val old = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 8, 0))
        val recent = makeNews(2, "Meteo: prognoza pentru weekend în Maramureș", "source2",
            publishDate = LocalDateTime.of(2024, 1, 2, 12, 0))
        val result = service.cluster(listOf(old, recent))
        assertEquals(2, result.size)
        assertEquals(recent.id, result[0].primary.id)
        assertEquals(old.id, result[1].primary.id)
    }
}
