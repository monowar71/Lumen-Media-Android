package com.freeplex.android.core.network

import com.freeplex.android.core.model.ProblemDetails
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class ApiException(val messageText: String, val status: Int? = null) : Exception(messageText)

fun Throwable.toUserMessage(fallback: String = "Something went wrong"): String {
    return when (this) {
        is ApiException -> messageText
        is HttpException -> {
            val body = response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(ProblemDetails.serializer(), body)
                }.getOrNull()?.let { problem ->
                    problem.detail ?: problem.title ?: fallback
                } ?: fallback
            } else fallback
        }
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
