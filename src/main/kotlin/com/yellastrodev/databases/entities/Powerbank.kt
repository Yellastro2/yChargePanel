package com.yellastrodev.databases.entities

data class Powerbank(
    val id: String,
    var status: Status
) {
    enum class Status {
        AVAILABLE,
        BLOCKED
    }
}
