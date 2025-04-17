package com.example.domain_block

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat  // Correct import for NotificationCompat
import java.io.FileInputStream



class DnsBlockVpnService : VpnService(), Runnable {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var thread: Thread? = null
    private val blockedDomains = mutableListOf<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        intent?.getStringArrayListExtra("domains")?.let {
            blockedDomains.clear()
            blockedDomains.addAll(it)
        }
        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("VPN Service Running")
            .setContentText("Blocking DNS requests for blocked domains.")
            .setSmallIcon(android.R.drawable.ic_notification_clear_all) //
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Start the service as a foreground service
     //   startForeground(1, notification)
        val builder = Builder()
        builder.setSession("DNS Blocker")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()


        thread = Thread(this)
        thread!!.start()


        return START_STICKY
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vpn_channel", // Unique channel ID
                "VPN Service", // Channel name
                NotificationManager.IMPORTANCE_LOW // Importance level
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        thread?.interrupt()
        vpnInterface?.close()
        super.onDestroy()
    }

    override fun run() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val packet = ByteArray(32767)

        while (!Thread.interrupted()) {
            val len = input.read(packet)
            if (len > 0) {
                val domain = parseDomainFromDnsPacket(packet)
                if (domain != null && blockedDomains.contains(domain)) {
                    continue // Block the DNS request
                }
                // Optionally, forward the DNS request to the actual DNS server
            }
        }
    }

    private fun parseDomainFromDnsPacket(packet: ByteArray): String? {
        try {
            val sb = StringBuilder()
            var i = 12
            while (packet[i].toInt() != 0) {
                val len = packet[i].toInt()
                for (j in 1..len) {
                    sb.append(packet[i + j].toInt().toChar())
                }
                sb.append('.')
                i += len + 1
            }
            return sb.toString().trim('.')
        } catch (e: Exception) {
            return null
        }
    }
}
