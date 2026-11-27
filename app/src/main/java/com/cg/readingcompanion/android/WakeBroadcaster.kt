package com.cg.readingcompanion.android

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeBroadcaster {
    private const val PORT = 8792
    private val message = "READING_COMPANION_WAKE_V1".toByteArray(Charsets.UTF_8)

    fun send() {
        Thread({
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(DatagramPacket(message, message.size,
                        InetAddress.getByName("255.255.255.255"), PORT))
                }
            }
        }, "reading-companion-wake").start()
    }
}
