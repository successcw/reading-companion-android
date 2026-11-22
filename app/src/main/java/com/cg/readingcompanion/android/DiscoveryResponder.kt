package com.cg.readingcompanion.android

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import org.json.JSONObject

class DiscoveryResponder(
    private val serverPort: Int,
    private val discoveryPort: Int = 8791,
) {
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    fun start() {
        stop()
        val s = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(discoveryPort))
            }
        } catch (_: Throwable) {
            return
        }
        socket = s
        running = true
        worker = Thread({
            val buffer = ByteArray(512)
            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    val request = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (request.trim() != "READING_COMPANION_DISCOVER_V1") continue
                    val remote = packet.address?.hostAddress ?: continue
                    val url = "http://${localAddressFor(remote)}:$serverPort/context"
                    val reply = JSONObject()
                        .put("service", "reading-companion")
                        .put("url", url)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                    s.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                } catch (_: SocketException) {
                    if (!running) break
                } catch (_: Throwable) {
                    // A bad packet must never kill the discovery listener.
                }
            }
        }, "reading-companion-discovery").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
        worker?.join(300)
        worker = null
    }

    private fun localAddressFor(remote: String): String {
        val prefix = remote.split(".").take(3).joinToString(".")
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
        return addresses.firstOrNull { it.startsWith("$prefix.") }
            ?: addresses.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
            ?: "127.0.0.1"
    }
}
