package com.fireflybot.di

import com.fireflybot.config.AppConfig
import com.fireflybot.firefly.FireflyClient
import com.fireflybot.telegram.FireflyBot
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

private val log = KotlinLogging.logger {}

val appModule = module {
    single { AppConfig.fromEnv() }

    single {
        val config: AppConfig = get()
        HttpClient(OkHttp) {
            defaultRequest {
                header("Authorization", "Bearer ${config.fireflyToken}")
                header("Accept", "application/vnd.api+json")
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }
    }

    single { FireflyClient(get(), get()) }
    single { FireflyBot(get(), get()) }
}
