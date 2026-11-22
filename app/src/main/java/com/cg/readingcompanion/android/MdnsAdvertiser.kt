package com.cg.readingcompanion.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class MdnsAdvertiser(private val context: Context) {
    private var manager: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start(port: Int) {
        stop()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "橘猫陪读"
            serviceType = "_reading-companion._tcp"
            this.port = port
            setAttribute("v", "1")
            setAttribute("path", "/context")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        manager = nsd
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        manager?.let { m -> registration?.let { runCatching { m.unregisterService(it) } } }
        manager = null
        registration = null
    }
}
