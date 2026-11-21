package com.cg.readingcompanion.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EdgeTtsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String, voice: String, output: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val token = SecMsGec.token()
            val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4" +
                "&Sec-MS-GEC=$token&Sec-MS-GEC-Version=2-143.0.3650.75&ConnectionId=$connectionId"
            val latch = CountDownLatch(1)
            val error = arrayOfNulls<Throwable>(1)
            var stream: FileOutputStream? = null

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val timestamp = timestamp()
                    val config = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(config)
                    val ssml = """<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='http://www.w3.org/2001/mstts' xml:lang='zh-CN'><voice name='${xml(voice)}'><prosody rate='+0%' pitch='+0Hz' volume='+0%'>${xml(text)}</prosody></voice></speak>"""
                    val ssmlMessage = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp()}Z\r\n" +
                        "Path:ssml\r\n\r\n" + ssml
                    webSocket.send(ssmlMessage)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size < 2) return
                    val headerSize = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
                    if (data.size < 2 + headerSize) return
                    val header = String(data, 2, headerSize, Charsets.UTF_8)
                    if (!header.contains("Path:audio")) return
                    val start = 2 + headerSize
                    if (stream == null) stream = FileOutputStream(output)
                    stream?.write(data, start, data.size - start)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, null)
                        latch.countDown()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("EdgeTts: failure ${t.message} http=${response?.code}")
                    error[0] = t
                    latch.countDown()
                }
            }
            val muid = UUID.randomUUID().toString().replace("-", "").take(32).uppercase()
            val request = Request.Builder()
                .url(url)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "muid=$muid;")
                .build()
            client.newWebSocket(request, listener)
            if (!latch.await(30, TimeUnit.SECONDS)) throw IllegalStateException("Edge TTS 超时")
            stream?.close()
            error[0]?.let { throw it }
            if (!output.exists() || output.length() == 0L) error("Edge TTS 没有返回音频")
            println("EdgeTts: synthesized ${output.length()} bytes")
            output
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;")
        .replace("\"", "&quot;")
}
