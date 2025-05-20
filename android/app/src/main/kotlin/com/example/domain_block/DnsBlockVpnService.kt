package com.example.domain_block

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress

class DnsBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val blockedDomains = setOf("google.com", "youtube.com")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        establishVpn()
        vpnInterface?.fileDescriptor?.let { fd ->
            Thread { vpnLoop(fd) }.start()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "vpn_service"
        val channelName = "VPN Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(chan)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Domain Blocker VPN")
            .setContentText("Blocking only google.com & youtube.com")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)
    }

    private fun establishVpn() {
        vpnInterface?.close()
        vpnInterface = Builder()
            .setSession("DomainBlockerVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .establish()
    }

    private fun vpnLoop(fd: java.io.FileDescriptor) {
        val `in` = FileInputStream(fd)
        val out = FileOutputStream(fd)
        val packet = ByteArray(32767)

        while (true) {
            val length = `in`.read(packet)
            if (length > 0 && isDnsQuery(packet, length)) {
                val domain = parseDnsDomain(packet, length)
                if (domain != null && isBlocked(domain)) {
                    // DROP the packet by not writing it back
                    continue
                }
            }
            // forward packet unchanged
            out.write(packet, 0, length)
        }
    }

    private fun isBlocked(domain: String): Boolean {
        val d = domain.trimEnd('.').lowercase()
        return blockedDomains.any { d == it || d.endsWith(".$it") }
    }

    private fun isDnsQuery(packet: ByteArray, len: Int): Boolean {
        if (len < 20) return false
        // IPv4?
        if ((packet[0].toInt() shr 4) != 4) return false
        // UDP?
        if (packet[9].toInt() != 17) return false
        val ipHdrLen = (packet[0].toInt() and 0x0F) * 4
        if (len < ipHdrLen + 8) return false
        // dest port == 53?
        val destPort = ((packet[ipHdrLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHdrLen + 3].toInt() and 0xFF)
        return destPort == 53
    }

    private fun parseDnsDomain(packet: ByteArray, len: Int): String? {
        // skip IP + UDP headers
        val ipHdrLen = (packet[0].toInt() and 0x0F) * 4
        val dnsStart = ipHdrLen + 8 + 12  // UDP header + DNS header
        if (dnsStart >= len) return null

        val parts = mutableListOf<String>()
        var pos = dnsStart
        while (pos < len) {
            val l = packet[pos].toInt() and 0xFF
            if (l == 0) break
            pos++
            if (pos + l > len) return null
            parts += String(packet, pos, l)
            pos += l
        }
        return parts.joinToString(".")
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
