package com.example.domain_block

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class DnsBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            stopForeground(true)
            stopVpn()
            return START_NOT_STICKY
        }
        startMyForegroundService()
        setupVpn()
        return START_STICKY
    }

    private fun startMyForegroundService() {
        val channelId = "vpn_service_channel"
        val channelName = "VPN Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Domain Blocker VPN")
            .setContentText("VPN service is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("DomainBlockerVPN")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()
    }

    private fun stopVpn() {
        android.util.Log.d("MainActivity", "Really stopping VPN service")
        vpnInterface?.close()
        vpnInterface = null
    }


    override fun onDestroy() {
        stopVpn()
        stopSelf()
        android.util.Log.d("DnsBlockVpnService", "VPN stopped and service destroyed")
        super.onDestroy()
    }




}


