package com.cg.readingcompanion.android

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var call: Call? = null
    private val sessionId: String = UUID.randomUUID().toString()

    fun cancel() {
        call?.cancel()
        call = null
        job?.cancel()
        job = null
    }

    fun stream(
        turns: List<ChatTurn>,
        context: ReadingContext,
        config: LlmConfig,
        onDelta: (String) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ) {
        cancel()
        if (config.apiKey.isBlank()) {
            onDone(Result.failure(IllegalStateException("请先在设置里填写 OpenCode Go API Key")))
            return
        }
        job = scope.launch {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt(context)))
                turns.takeLast(8).forEach {
                    messages.put(JSONObject().put("role", it.role).put("content", it.text))
                }
                val body = JSONObject()
                    .put("model", config.model)
                    .put("messages", messages)
                    .put("stream", true)
                    .put("temperature", 0.7)
                    .put("max_tokens", 600)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(config.baseUrl.trimEnd('/') + "/chat/completions")
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("x-opencode-session", sessionId)
                    .header("User-Agent", "ReadingCompanionAndroid/0.1")
                    .post(body)
                    .build()
                call = client.newCall(request)
                call!!.execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body?.string()?.take(800) ?: ""
                        throw IllegalStateException("API ${response.code}: $detail")
                    }
                    val source = response.body?.source()
                        ?: throw IllegalStateException("没有响应")
                    while (!source.exhausted() && isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        val content = runCatching {
                            JSONObject(payload)
                                .optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content")
                                .orEmpty()
                        }.getOrDefault("")
                        if (content.isNotEmpty()) withContext(Dispatchers.Main) { onDelta(content) }
                    }
                }
                withContext(Dispatchers.Main) { onDone(Result.success(Unit)) }
            } catch (t: Throwable) {
                if (t is CancellationException || !isActive) return@launch
                withContext(Dispatchers.Main) { onDone(Result.failure(t)) }
            }
        }
    }

    private fun systemPrompt(context: ReadingContext): String = """
        你是用户的读书陪读，中文口语化，回答简短、具体，像坐在旁边聊天。
        只基于下面“当前阅读上下文”回答；不要剧透用户还没读到的内容。
        如果用户问的问题超出上下文，先说明你只看到当前页，再给一般性解释。
        必要时用一个问题反问，帮用户想深一点。

        当前书：${context.book}
        当前章节：${context.chapter}
        当前页：${context.page}
        阅读进度：${String.format("%.1f", context.progress)}%

        当前阅读上下文：
        ${context.text.take(6000)}
    """.trimIndent()
}
