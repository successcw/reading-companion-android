package com.cg.readingcompanion.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class LocalServerService : Service() {
    private var server: LocalServer? = null
    private var discovery: DiscoveryResponder? = null
    private val channelId = "reading_companion_server"

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        server?.stopServer()
        val store = SecureStore(this)
        server = LocalServer(store.port, { store.token }) { ctx ->
            ServerHub.context.value = ctx
        }.also {
            it.startServer()
            ServerHub.running.value = it.running
        }
        discovery?.stop()
        discovery = DiscoveryResponder(store.port).also { it.start() }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.cat_idle)
            .setContentTitle("橘猫陪读")
            .setContentText("Kindle 连接服务运行中")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Kindle 连接", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        server?.stopServer()
        ServerHub.running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, LocalServerService::class.java)
            )
        }

        fun reload(context: Context) {
            start(context)
        }
    }
}
