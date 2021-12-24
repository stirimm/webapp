package com.emilburzo.stirimm.stirimmwebapp.persistence

import org.springframework.data.repository.CrudRepository

/**
 * Created by emil on 14.12.2019.
 */
interface NewsRepository : CrudRepository<News, Long> {

    fun findTop200ByOrderByPublishDateDesc(): Iterable<News>

}
