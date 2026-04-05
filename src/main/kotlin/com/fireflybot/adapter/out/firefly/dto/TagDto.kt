package com.fireflybot.adapter.out.firefly.dto

import kotlinx.serialization.Serializable

@Serializable
data class TagListResponse(
    val data: List<TagRead>,
    val meta: MetaData? = null,
)

@Serializable
data class TagRead(
    val id: String,
    val attributes: TagAttributes,
)

@Serializable
data class TagAttributes(
    val tag: String,
)
