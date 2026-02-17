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

    // --- Word extraction tests ---

    @Test
    fun `words extracts lowercase tokens and filters stop words`() {
        val result = NewsClusterService.words("CS Minaur a câștigat cu CSM Constanța")
        assertTrue("minaur" in result)
        assertTrue("csm" in result)
        assertTrue("constanta" in result, "diacritics should be normalized (ț→t)")
        assertTrue("castigat" in result, "diacritics should be normalized (â→a, ș→s)")
        // Stop words should be filtered
        assertFalse("cu" in result, "stop word 'cu' should be filtered")
    }

    @Test
    fun `words keeps numbers`() {
        val result = NewsClusterService.words("scor 26-24 (12-9), etapa 13")
        assertTrue("26" in result)
        assertTrue("24" in result)
        assertTrue("12" in result)
        assertTrue("13" in result)
    }

    @Test
    fun `dice similarity of identical sets`() {
        val s = setOf("abc", "bcd", "cde")
        assertEquals(1.0, NewsClusterService.diceSimilarity(s, s))
    }

    @Test
    fun `dice similarity of disjoint sets`() {
        assertEquals(0.0, NewsClusterService.diceSimilarity(setOf("abc"), setOf("xyz")))
    }

    @Test
    fun `dice similarity of empty sets`() {
        assertEquals(0.0, NewsClusterService.diceSimilarity(emptySet(), emptySet()))
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
    fun `same-source identical articles are clustered and latest version is kept`() {
        val older = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val newer = makeNews(2, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 5))
        val result = service.cluster(listOf(older, newer))
        assertEquals(1, result.size, "Identical same-source articles should be merged into one cluster")
        assertEquals(newer.id, result[0].primary.id, "Latest version should be kept as primary")
        assertTrue(result[0].duplicates.isEmpty(),
            "Same-source duplicate should not appear in the reported duplicates list")
    }

    @Test
    fun `same-source near-duplicate keeps corrected version`() {
        // Real-world case: RSS feed republishes with title correction
        val original = makeNews(1,
            "Încăpățânările politice ale unor consilieri locali din SĂCEL pot duce la pierderea a 1,76 milioane lei. Bani europeni pentru persoane defavorizate",
            "vasiledale.ro",
            description = "Situație pur și simplu haluncinantă în comuna maramureșeană Săcel.",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val corrected = makeNews(2,
            "Încăpățânările politice ale unor consilieri locali din SĂCEL pot duce la pierderea a 1,76 milioane lei. Bani pentru persoane defavorizate",
            "vasiledale.ro",
            description = "Situație pur și simplu haluncinantă în comuna maramureșeană Săcel.",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 5))
        val result = service.cluster(listOf(original, corrected))
        assertEquals(1, result.size, "Same-source near-duplicates should be merged")
        assertEquals(corrected.id, result[0].primary.id, "Corrected (latest) version should be shown")
    }

    @Test
    fun `same-source different articles are not clustered`() {
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1")
        val b = makeNews(2, "Meteo: prognoza pentru weekend în Maramureș", "source1")
        val result = service.cluster(listOf(a, b))
        assertEquals(2, result.size, "Different articles from same source should stay separate")
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
    fun `mixed cluster collapses same-source and keeps cross-source primary logic`() {
        // A(source1, 10:00), B(source2, 11:00), C(source1, 12:00)
        // Same-source dedup: source1 keeps C (latest), source2 keeps B
        // Among [B(11:00), C(12:00)], earliest = B → primary
        // Duplicates: [C] from source1 (different source than primary)
        val a = makeNews(1, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 10, 0))
        val b = makeNews(2, "Accident grav pe DN18 în Baia Mare", "source2",
            publishDate = LocalDateTime.of(2024, 1, 1, 11, 0))
        val c = makeNews(3, "Accident grav pe DN18 în Baia Mare", "source1",
            publishDate = LocalDateTime.of(2024, 1, 1, 12, 0))
        val result = service.cluster(listOf(a, b, c))
        assertEquals(1, result.size)
        assertEquals(b.id, result[0].primary.id, "source2 is earliest after same-source dedup")
        assertEquals(1, result[0].duplicates.size)
        assertEquals("source1", result[0].duplicates[0].source)
        assertEquals(c.id, result[0].duplicates[0].id, "Latest version from source1 should be kept")
    }

    @Test
    fun `handball match articles from different sources should be clustered`() {
        // Real-world case: three outlets covering the same handball match (Minaur vs CSM Constanța, 26-24)
        // with completely different titles but overlapping descriptions
        val a = makeNews(1,
            "LIGA ZIMBRILOR – CS Minaur Baia Mare, la a doua victorie consecutivă în campionat",
            "24news.ro",
            description = "CS Minaur Baia Mare a învins luni, 16 februarie, pe teren propriu, formația CSM Constanța, scor 26-24 (12-9), într-un joc contând pentru etapa a 13-a a Ligii Zimbrilor la handbal masculin. După o primă repriză cu multe greșeli, în care oaspeții au condus la un moment și cu trei goluri, Minaur a revenit și a controlat jocul.",
            publishDate = LocalDateTime.of(2026, 2, 16, 22, 0))
        val b = makeNews(2,
            "Handbal – Liga Zimbrilor | Victorie cu CSM Constanța și un loc mai sus în clasament",
            "eziarultau.ro",
            description = "În joc din etapa 13 de Liga Zimbrilor, CS Minaur a câștigat în această seară meciul disputat pe propriul teren cu CSM Constanța: 26-24 (12-9). Un succes care duce echipa din Baia Mare pe locul 6 în clasamentul momentului, cu 13 puncte. Cândva un derby care decidea configurația podiumului, întâlnirea dintre Baia Mare și Constanța a fost una echilibrată.",
            publishDate = LocalDateTime.of(2026, 2, 16, 22, 30))
        val c = makeNews(3,
            "AU CÂȘTIGAT – Minaur se impune la Baia Mare în meciul cu CSM Constanța și urcă pe locul 6 în clasamentul primei ligi",
            "2mnews.ro",
            description = """HANDBAL MASCULIN – LIGA I. Luni, 16 februarie 2026, în Sala Sporturilor „Lascăr Pană" din Baia Mare, în meci contând pentru etapa a 13-a Liga I la handbal masculin, CS Minaur Baia Mare a câștigat, cu 26-24 (12-9), partida cu CSM Constanța. Meciul, arbitrat de buzoianul Ionuț Velișca și gălățeanul Ciprian Popa, a opus echipele clasate pe locurile 6 și 7.""",
            publishDate = LocalDateTime.of(2026, 2, 16, 21, 0))

        val result = service.cluster(listOf(a, b, c))
        assertEquals(1, result.size, "All three articles about the same match should be clustered together")
        assertEquals(2, result[0].duplicates.size, "Should have 2 duplicates (one per additional source)")
    }

    @Test
    fun `different police incidents sharing location names should not be clustered`() {
        // Real-world false positive risk: two different police incidents in the same area
        // (theft suspect vs drunk driver, both in Baia Mare / Coltău)
        val theft = makeNews(1,
            "Lovitură în Baia Mare: suspect din Coltău, reținut. Prejudiciu de 25.000 lei recuperat 100%",
            "maramuresnonstop.ro",
            description = """Polițiștii din cadrul Poliției municipiului Baia Mare au reținut pentru 24 de ore un bărbat bănuit de comiterea infracțiunilor de furt calificat și tentativă la furt calificat, în urma unor sesizări și a cercetărilor desfășurate sub coordonarea procurorului de caz. Două fapte, în perioade diferite. Din verificările efectuate a reieșit că bănuitul ar fi sustras mai multe bunuri.""",
            publishDate = LocalDateTime.of(2026, 2, 15, 14, 0))
        val drunkDriving = makeNews(2,
            "Bărbat din Baia Mare, prins băut și fără permis la volan în Coltău",
            "24news.ro",
            description = """Astă-noapte, în jurul orei 00:30, polițiștii Secției 1 Poliție Rurală Groși au oprit pentru control un autoturism condus în localitatea Coltău. La volan a fost identificat un bărbat în vârstă de 38 de ani, din Baia Mare. În urma verificărilor efectuate s-a stabilit faptul că acesta nu deține permis de conducere, iar testarea cu aparatul etilotest a indicat o alcoolemie de 0,52 mg/l.""",
            publishDate = LocalDateTime.of(2026, 2, 15, 10, 0))
        val result = service.cluster(listOf(theft, drunkDriving))
        assertEquals(2, result.size, "Different police incidents should not be clustered even if they share location names")
    }

    @Test
    fun `different police reports sharing institutional vocabulary should not be clustered`() {
        // Real-world false positive risk: specific local operation vs national police summary
        val localOperation = makeNews(1,
            "Amenzi de peste 10.000 de lei și cinci permise reținute",
            "graiul.ro",
            description = """Polițiștii au aplicat sancțiuni de peste 10.000 de lei în cadrul unei acțiuni de tip BLITZ desfășurate pentru reducerea riscului rutier în zona trecerilor la nivel cu calea ferată. Joi, 12 februarie, polițiștii din cadrul Biroului Județean de Poliție Transporturi Maramureș, împreună cu cei ai Biroului Rutier Baia Mare, au organizat o acțiune la nivelul trecerilor la nivel cu calea ferată.""",
            publishDate = LocalDateTime.of(2026, 2, 13, 16, 0))
        val nationalSummary = makeNews(2,
            "Peste 2.300 de intervenții în 24 de ore și 520 de permise reținute",
            "jurnalmm.ro",
            description = """Polițiștii au intervenit, în ultimele 24 de ore, la peste 2.300 de solicitări ale cetățenilor, au aplicat peste 10.200 de sancțiuni contravenționale și au reținut 520 de permise de conducere, potrivit unui bilanț transmis de autorități. La data de 13 februarie, polițiștii au acționat la nivel național pentru prevenirea faptelor antisociale, organizând 540 de acțiuni.""",
            publishDate = LocalDateTime.of(2026, 2, 14, 8, 0))
        val result = service.cluster(listOf(localOperation, nationalSummary))
        assertEquals(2, result.size, "Different police reports should not be clustered even if they share institutional vocabulary")
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

    @Test
    fun `unicode mathematical bold characters are normalized for matching`() {
        // Real-world: 24news.ro uses Unicode mathematical bold (U+1D400+) for styled text
        val normal = makeNews(1,
            "TAROM suspendă zborurile Satu Mare-București",
            "source1",
            description = "TAROM a decis să suspende pe termen nedeterminat zborurile dintre București și Satu Mare",
            publishDate = LocalDateTime.of(2026, 2, 17, 10, 0))
        val unicodeBold = makeNews(2,
            "\uD835\uDC13\uD835\uDC00\uD835\uDC11\uD835\uDC0E\uD835\uDC0C suspendă zborurile Satu Mare-București",
            "source2",
            description = "TAROM suspendă pe termen nedeterminat zborurile Satu Mare-București",
            publishDate = LocalDateTime.of(2026, 2, 17, 11, 0))
        val result = service.cluster(listOf(normal, unicodeBold))
        assertEquals(1, result.size, "Unicode bold TAROM should match regular TAROM")
    }

    @Test
    fun `same-event articles with different angles cluster via title word overlap`() {
        // Real-world: TAROM cancels Satu Mare flights and increases Baia Mare flights
        // Different outlets cover different angles but share key entities in titles
        val satuMareSuspension = makeNews(1,
            "TAROM renunță la ruta București-Satu Mare și mută frecvența spre Baia Mare",
            "bunaziuamaramures.ro",
            description = "TAROM a decis să suspende pe termen nedeterminat zborurile regulate dintre București și Satu Mare",
            publishDate = LocalDateTime.of(2026, 2, 17, 10, 0))
        val baiaMaraIncrease = makeNews(2,
            "VEȘTI EXCELENTE LA AEROPORTUL INTERNAȚIONAL MARAMUREȘ | TAROM dublează miza la Baia Mare: 6 curse săptămânale spre București din martie",
            "actualmm.ro",
            description = "Începând cu finalul lunii martie, compania va opera șase curse pe săptămână pe ruta Baia Mare – București, după ce două frecvențe au fost relocate de la Satu Mare",
            publishDate = LocalDateTime.of(2026, 2, 17, 9, 0))
        val result = service.cluster(listOf(satuMareSuspension, baiaMaraIncrease))
        assertEquals(1, result.size,
            "Articles about same event (TAROM flight reallocation) should cluster via shared title keywords: TAROM, Baia Mare, București")
    }
}
