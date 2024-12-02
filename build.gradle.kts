
val ktor_version = "3.0.1"

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization").version("2.0.0")
}

group = "com.yellastrodev"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.auth)
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation ("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
//    implementation("io.ktor:ktor-servet-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.logback.classic)
    implementation("redis.clients:jedis:4.2.0") // Добавьте эту строку для Redis
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}