package com.fireflybot.adapter.out.firefly.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetaData(
    val pagination: Pagination? = null,
)

@Serializable
data class Pagination(
    val total: Int = 0,
    val count: Int = 0,
    @SerialName("per_page") val perPage: Int = 50,
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)
