plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.6"
    application
    jacoco
}

group = "com.fireflybot"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val koinVersion = "3.5.6"

dependencies {
    // Koin DI
    implementation("io.insert-koin:koin-core:$koinVersion")

    // Telegram Bot
    implementation("org.telegram:telegrambots:6.9.7.1")

    // Ktor HTTP Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.8")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.fireflybot.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        // Three separate rules so each package is checked independently.
        // A single BUNDLE rule with multiple includes would only enforce 85% on
        // the combined aggregate, letting one package compensate for another.
        listOf(
            "com.fireflybot.domain.model.*",
            "com.fireflybot.application.wizard.*",
            "com.fireflybot.adapter.out.mock.*",
        ).forEach { pkg ->
            rule {
                includes = listOf(pkg)
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = "0.85".toBigDecimal()
                }
            }
        }
        rule {
            includes = listOf("com.fireflybot.adapter.out.persistence.*")
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

jacoco {
    toolVersion = "0.8.12"
}
