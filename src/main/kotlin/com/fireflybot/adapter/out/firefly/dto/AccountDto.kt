package com.fireflybot.adapter.out.firefly.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountListResponse(
    val data: List<AccountRead>,
    val meta: MetaData? = null,
)

@Serializable
data class AccountSingleResponse(
    val data: AccountRead,
)

@Serializable
data class AccountRead(
    val id: String,
    val attributes: AccountAttributes,
)

@Serializable
data class AccountAttributes(
    val name: String,
    @SerialName("currency_code") val currencyCode: String? = null,
    val type: String? = null,
)

@Serializable
data class StoreAccountRequest(
    val name: String,
    /** "asset", "expense", or "revenue" */
    val type: String,
    @SerialName("currency_code") val currencyCode: String? = null,
)
