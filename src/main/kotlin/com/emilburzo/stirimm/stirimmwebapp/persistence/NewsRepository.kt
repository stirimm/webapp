package com.emilburzo.stirimm.stirimmwebapp.persistence

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface NewsRepository : CrudRepository<News, Long> {

    fun findTop300ByOrderByPublishDateDesc(): Iterable<News>

    @Query("SELECT MAX(n.id) FROM News n")
    fun findMaxId(): Long?

}
