package com.example.data.models

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class DeviceActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val error: String? = null,
    val data: Any? = null
)
