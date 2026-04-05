package com.fireflybot.adapter.out.firefly

import com.fireflybot.adapter.out.firefly.dto.CategoryListResponse
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.config.AppConfig
import com.fireflybot.domain.model.Category
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class FireflyCategoryAdapter(
    private val config: AppConfig,
    private val httpClient: HttpClient,
    private val categoriesCache: FireflyCache<Unit, List<Category>> = FireflyCache(),
) : CategoryRepository {

    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')

    override fun getCategories(): List<Category> {
        categoriesCache.get(Unit)?.let {
            log.info { "Serving categories from cache" }
            return it
        }
        log.info { "Fetching categories" }
        return buildList {
            var page = 1
            do {
                val response = kotlinx.coroutines.runBlocking {
                    httpClient.get("$baseUrl/api/v1/categories") {
                        parameter("page", page)
                    }.body<CategoryListResponse>()
                }
                addAll(response.data.map { Category(id = it.id, name = it.attributes.name) })
                val totalPages = response.meta?.pagination?.totalPages ?: 1
                page++
            } while (page <= totalPages)
        }.also { categoriesCache.put(Unit, it) }
    }
}
