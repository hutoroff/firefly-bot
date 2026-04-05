package com.fireflybot.adapter.out.firefly.dto

import kotlinx.serialization.Serializable

@Serializable
data class CategoryListResponse(
    val data: List<CategoryRead>,
    val meta: MetaData? = null,
)

@Serializable
data class CategoryRead(
    val id: String,
    val attributes: CategoryAttributes,
)

@Serializable
data class CategoryAttributes(
    val name: String,
)
