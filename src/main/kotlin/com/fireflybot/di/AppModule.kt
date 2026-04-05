package com.fireflybot.di

import com.fireflybot.adapter.`in`.telegram.FireflyBot
import com.fireflybot.adapter.`in`.telegram.TelegramWizardPresenter
import com.fireflybot.adapter.out.firefly.FireflyAccountAdapter
import com.fireflybot.adapter.out.firefly.FireflyCategoryAdapter
import com.fireflybot.adapter.out.firefly.FireflyTagAdapter
import com.fireflybot.adapter.out.firefly.FireflyTransactionAdapter
import com.fireflybot.adapter.out.mock.InMemoryWizardSessionRepository
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.application.port.out.TagRepository
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.application.port.out.WizardSessionRepository
import com.fireflybot.application.wizard.WizardService
import com.fireflybot.config.AppConfig
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
                headers.append("Authorization", "Bearer ${config.fireflyToken}")
                headers.append("Accept", "application/vnd.api+json")
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

    // ── Outbound adapters ─────────────────────────────────────────────────────

    single<WizardSessionRepository> { InMemoryWizardSessionRepository() }
    single<AccountRepository> { FireflyAccountAdapter(get(), get()) }
    single<CategoryRepository> { FireflyCategoryAdapter(get(), get()) }
    single<TransactionRepository> { FireflyTransactionAdapter(get(), get()) }
    single<TagRepository> { FireflyTagAdapter(get(), get()) }

    // ── Application ───────────────────────────────────────────────────────────

    single {
        WizardService(
            sessionRepository = get(),
            accountRepository = get(),
            categoryRepository = get(),
            transactionRepository = get(),
            tagRepository = get(),
        )
    }

    // ── Inbound adapters ──────────────────────────────────────────────────────

    single { TelegramWizardPresenter() }
    single { FireflyBot(get(), get<WizardService>(), get()) }
}
