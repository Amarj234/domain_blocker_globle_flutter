package com.example.domain_block

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "dns.blocker.vpn"
    private val VPN_REQUEST_CODE = 1001
    private var domainList: ArrayList<String> = arrayListOf()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startVpn" -> {
                        val domains = call.argument<List<String>>("domains")
                        domainList.clear()
                        domainList.addAll(domains ?: listOf())

                        val intent = VpnService.prepare(this)
                        if (intent != null) {
                            startActivityForResult(intent, VPN_REQUEST_CODE)
                        } else {
                            startVpnService()
                        }
                        result.success(true)
                    }
                    "stopVpn" -> {
                        android.util.Log.d("MainActivity", "Stopping VPN service")
                        val intent = Intent(this, DnsBlockVpnService::class.java)
                        intent.action = "STOP_VPN"
                        startService(intent)

                    }

                    else -> {
                        result.notImplemented()  // <- Must have parentheses here
                    }
                }
            }
    }

    private fun startVpnService() {
        val intent = Intent(this, DnsBlockVpnService::class.java)
        intent.putStringArrayListExtra("domains", domainList)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}