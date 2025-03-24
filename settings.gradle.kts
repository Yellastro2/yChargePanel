plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"

}
rootProject.name = "yChargePanel"

include("ylogger")
project(":ylogger").projectDir = file("libs/ylogger")

dependencyResolutionManagement {
    repositories {
        mavenCentral()  // Основной репозиторий для зависимостей
    }
}
