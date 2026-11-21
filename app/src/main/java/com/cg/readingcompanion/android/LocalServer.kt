package com.cg.readingcompanion.android

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class LocalServer(
    port: Int,
    private val tokenProvider: () -> String,
    private val onContext: (ReadingContext) -> Unit,
) : NanoHTTPD(port) {

    @Volatile var running = false
        private set

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            running = true
        } catch (_: Throwable) {
            running = false
        }
    }

    fun stopServer() {
        stop()
        running = false
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri
        val token = session.headers["x-companion-token"]
            ?: session.headers["X-Companion-Token"]
        if (token != tokenProvider()) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "invalid token"))
        }
        if (session.method == Method.GET && path == "/health") {
            return json(Response.Status.OK, JSONObject().put("ok", true))
        }
        if (session.method == Method.POST && path == "/context") {
            return try {
                val size = session.headers["content-length"]?.toIntOrNull() ?: 0
                val raw = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = session.inputStream.read(raw, read, size - read)
                    if (n <= 0) break
                    read += n
                }
                val data = if (read > 0) {
                    String(raw, 0, read, Charsets.UTF_8)
                } else {
                    val form = HashMap<String, String>()
                    session.parseBody(form)
                    form["postData"] ?: ""
                }
                onContext(ReadingContext.fromJson(data))
                json(Response.Status.OK, JSONObject().put("ok", true))
            } catch (t: Throwable) {
                json(Response.Status.BAD_REQUEST, JSONObject().put("error", "invalid json"))
            }
        }
        return json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
