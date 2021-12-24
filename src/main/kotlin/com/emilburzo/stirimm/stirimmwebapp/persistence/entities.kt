package com.emilburzo.stirimm.stirimmwebapp.persistence

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * Created by emil on 14.12.2019.
 */
@Entity
class News(
        @Id var id: Long,
        @Column(name = "publish_date") var publishDate: LocalDateTime,
        @Column(name = "ingest_date") var ingestDate: LocalDateTime,
        var title: String,
        var description: String,
        var url: String,
        var source: String

)