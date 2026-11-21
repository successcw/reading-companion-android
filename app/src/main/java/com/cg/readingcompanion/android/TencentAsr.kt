package com.cg.readingcompanion.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class EmptySpeechException : Exception("没有识别到语音")

class TencentAsrClient {
    private val http = OkHttpClient()
    private val host = "asr.tencentcloudapi.com"
    private val service = "asr"
    private val action = "SentenceRecognition"
    private val version = "2019-06-14"

    suspend fun transcribe(
        wav: ByteArray,
        secretId: String,
        secretKey: String,
        region: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(secretId.isNotBlank() && secretKey.isNotBlank()) {
                "未配置腾讯云 SecretId / SecretKey"
            }
            val payload = JSONObject()
                .put("ProjectId", 0)
                .put("SubServiceType", 2)
                .put("EngSerViceType", "16k_zh")
                .put("SourceType", 1)
                .put("VoiceFormat", "wav")
                .put("Data", Base64.getEncoder().encodeToString(wav))
                .put("DataLen", wav.size)
                .toString()
            val timestamp = System.currentTimeMillis() / 1000
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(timestamp * 1000))
            val contentType = "application/json; charset=utf-8"
            val signedHeaders = "content-type;host"
            val canonicalHeaders = "content-type:$contentType\nhost:$host\n"
            val hashedPayload = sha256Hex(payload.toByteArray())
            val canonicalRequest = listOf(
                "POST", "/", "", canonicalHeaders, signedHeaders, hashedPayload
            ).joinToString("\n")
            val credentialScope = "$date/$service/tc3_request"
            val stringToSign = listOf(
                "TC3-HMAC-SHA256",
                timestamp.toString(),
                credentialScope,
                sha256Hex(canonicalRequest.toByteArray())
            ).joinToString("\n")
            val secretDate = hmac(("TC3$secretKey").toByteArray(), date.toByteArray())
            val secretService = hmac(secretDate, service.toByteArray())
            val secretSigning = hmac(secretService, "tc3_request".toByteArray())
            val signature = hmac(secretSigning, stringToSign.toByteArray()).toHex()
            val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

            val request = Request.Builder()
                .url("https://$host")
                .header("Content-Type", contentType)
                .header("Host", host)
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Timestamp", timestamp.toString())
                .header("X-TC-Region", region)
                .header("Authorization", authorization)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("腾讯云 HTTP ${response.code}: ${body.take(300)}")
                val root = JSONObject(body).optJSONObject("Response")
                    ?: error("腾讯云返回格式异常")
                root.optJSONObject("Error")?.let {
                    error("腾讯云识别失败（${it.optString("Code")}）：${it.optString("Message")}")
                }
                val text = root.optString("Result").trim()
                if (text.isEmpty()) throw EmptySpeechException()
                text
            }
        }
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(data)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

object WavWriter {
    fun pcm16ToWav(pcm: ByteArray, sampleRate: Int = 16_000): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val dataSize = pcm.size
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)
        return header.array() + pcm
    }
}
