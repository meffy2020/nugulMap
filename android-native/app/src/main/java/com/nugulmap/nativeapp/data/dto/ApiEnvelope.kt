package com.nugulmap.nativeapp.data.dto

data class ApiEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val data: T? = null,
)

data class ApiError(
    val success: Boolean = false,
    val timestamp: String? = null,
    val status: Int? = null,
    val code: String? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val trace: String? = null,
)
