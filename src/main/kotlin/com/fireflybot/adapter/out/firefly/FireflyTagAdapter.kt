package com.fireflybot.adapter.out.firefly

import com.fireflybot.adapter.out.firefly.dto.TagListResponse
import com.fireflybot.application.port.out.TagRepository
import com.fireflybot.config.AppConfig
import com.fireflybot.domain.model.Tag
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class FireflyTagAdapter(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) : TagRepository {

    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')

    override fun getTags(): List<Tag> {
        log.info { "Fetching tags" }
        return buildList {
            var page = 1
            do {
                val response = kotlinx.coroutines.runBlocking {
                    httpClient.get("$baseUrl/api/v1/tags") {
                        parameter("page", page)
                    }.body<TagListResponse>()
                }
                addAll(response.data.map { Tag(id = it.id, name = it.attributes.tag) })
                val totalPages = response.meta?.pagination?.totalPages ?: 1
                page++
            } while (page <= totalPages)
        }
    }
}
