package com.cg.readingcompanion.android

import org.json.JSONObject

data class ReadingContext(
    val book: String = "",
    val chapter: String = "",
    val page: Int = 0,
    val progress: Double = 0.0,
    val text: String = "",
    val updatedAt: Long = 0L,
) {
    val pageLabel: String get() = if (page > 0) "第 $page 页" else "未连接"

    companion object {
        fun fromJson(body: String): ReadingContext {
            val json = JSONObject(body)
            return ReadingContext(
                book = json.optString("book"),
                chapter = json.optString("chapter"),
                page = json.optInt("page"),
                progress = json.optDouble("progress", 0.0),
                text = json.optString("text"),
                updatedAt = json.optLong("updatedAt"),
            )
        }
    }
}

data class ChatTurn(val role: String, val text: String)

data class LlmConfig(
    val baseUrl: String = "https://opencode.ai/zen/go/v1",
    val model: String = "deepseek-v4.1-flash",
    val apiKey: String = "",
)
