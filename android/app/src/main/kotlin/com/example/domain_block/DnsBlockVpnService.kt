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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var blockedDomains: List<String> = emptyList()
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            isRunning = false
            stopForeground(true)
            stopVpn()
            return START_NOT_STICKY
        }
        
        val domains = intent?.getStringArrayListExtra("domains")
        if (domains != null) {
            blockedDomains = domains
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
            .setContentText("VPN service is running, blocking " + blockedDomains.size + " domains")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("DomainBlockerVPN")
            .addAddress("10.0.0.2", 32)
            // Primary DNS
            .addDnsServer("8.8.8.8")
            .addRoute("8.8.8.8", 32)
            // Secondary DNS
            .addDnsServer("8.8.4.4")
            .addRoute("8.8.4.4", 32)
            // Block IPv6 to prevent DNS leak via IPv6
            .addRoute("2000::", 3) 

        vpnInterface?.close()
        vpnInterface = builder.establish()

        isRunning = true
        Thread { runDnsProxy() }.start()
    }

    private fun runDnsProxy() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val packet = ByteArray(32767)
        val dnsServerObj = InetAddress.getByName("8.8.8.8")

        while (isRunning) {
            try {
                val length = inputStream.read(packet)
                if (length > 0) {
                    val pktCopy = packet.copyOf(length)
                    handlePacket(pktCopy, length, outputStream, dnsServerObj)
                }
            } catch (e: Exception) {
                if (!isRunning) break
            }
        }
    }

    private fun handlePacket(
        packet: ByteArray, 
        length: Int, 
        outputStream: FileOutputStream, 
        dnsServerObj: InetAddress
    ) {
        if (length < 20) return // Invalid IP packet
        val versionAndIHL = packet[0].toInt()
        val ihl = (versionAndIHL and 0x0F) * 4
        val protocol = packet[9].toInt()

        if (protocol != 17) return // Not UDP

        val udpHeaderOffset = ihl
        if (length < udpHeaderOffset + 8) return

        // val srcPort = ((packet[udpHeaderOffset].toInt() and 0xFF) shl 8) or (packet[udpHeaderOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpHeaderOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpHeaderOffset + 3].toInt() and 0xFF)
        val udpPayloadOffset = udpHeaderOffset + 8
        val udpPayloadLength = length - udpPayloadOffset

        if (dstPort == 53) { // It's a DNS query
            val domain = parseDnsDomain(packet, udpPayloadOffset, length)
            
            if (domain != null) {
                android.util.Log.d("DnsBlockVpn", "DNS Query intercept: $domain")
                if (isBlocked(domain)) {
                    android.util.Log.d("DnsBlockVpn", "Domain BLOCKED: $domain")
                    // Return immediate DNS blocked response (Sinkhole 0.0.0.0)
                    sendSpoofedBlockedResponse(packet, ihl, udpHeaderOffset, outputStream, domain)
                } else {
                    // Spin up a thread to forward the request to the real DNS server without blocking TUN reads
                Thread {
                    try {
                        val socket = DatagramSocket()
                        protect(socket) // Ensure this socket bypasses the VPN
                        socket.soTimeout = 3000 // 3 sec timeout

                        val dnsPayload = packet.copyOfRange(udpPayloadOffset, udpPayloadOffset + udpPayloadLength)
                        val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, dnsServerObj, 53)
                        socket.send(outPacket)

                        val inBuf = ByteArray(4096)
                        val inPacket = DatagramPacket(inBuf, inBuf.size)
                        
                        socket.receive(inPacket)
                        
                        // Construct IP and UDP headers back to the TUN
                        forwardResponseToTun(packet, ihl, udpHeaderOffset, inPacket.data, inPacket.length, outputStream)
                        socket.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }
    }
}

    private fun isBlocked(domain: String): Boolean {
        val d = domain.trimEnd('.').lowercase()
        return blockedDomains.any { 
            val blocked = it.trim().lowercase()
            d == blocked || d.endsWith(".$blocked") 
        }
    }

    private fun forwardResponseToTun(
        requestPacket: ByteArray, 
        ipHdrLen: Int, 
        udpHeaderOffset: Int,
        dnsResponse: ByteArray, 
        dnsResponseLen: Int, 
        outputStream: FileOutputStream
    ) {
        val totalLength = ipHdrLen + 8 + dnsResponseLen
        val response = ByteArray(totalLength)

        // Copy IP Header
        System.arraycopy(requestPacket, 0, response, 0, ipHdrLen)
        
        // Swap Source & Destination IPs
        System.arraycopy(requestPacket, 12, response, 16, 4) // Src to Dst
        System.arraycopy(requestPacket, 16, response, 12, 4) // Dst to Src
        
        // Update total length in IP header
        response[2] = (totalLength shr 8).toByte()
        response[3] = totalLength.toByte()

        // Reset IP Checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = computeChecksum(response, 0, ipHdrLen)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()

        // Copy UDP Header
        System.arraycopy(requestPacket, udpHeaderOffset, response, ipHdrLen, 8)
        
        // Swap Source & Destination Ports
        System.arraycopy(requestPacket, udpHeaderOffset, response, ipHdrLen + 2, 2)
        System.arraycopy(requestPacket, udpHeaderOffset + 2, response, ipHdrLen, 2)
        
        // Update UDP Length
        val udpLen = 8 + dnsResponseLen
        response[ipHdrLen + 4] = (udpLen shr 8).toByte()
        response[ipHdrLen + 5] = udpLen.toByte()
        
        // Reset UDP Checksum to 0 (Optional in IPv4)
        response[ipHdrLen + 6] = 0
        response[ipHdrLen + 7] = 0

        // Copy DNS Payload
        System.arraycopy(dnsResponse, 0, response, ipHdrLen + 8, dnsResponseLen)
        
        try {
            outputStream.write(response)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSpoofedBlockedResponse(
        requestPacket: ByteArray,
        ipHdrLen: Int, 
        udpHeaderOffset: Int,
        outputStream: FileOutputStream,
        domain: String
    ) {
        val udpLen = ((requestPacket[udpHeaderOffset + 4].toInt() and 0xFF) shl 8) or (requestPacket[udpHeaderOffset + 5].toInt() and 0xFF)
        val dnsReqLen = udpLen - 8
        val dnsReqOffset = ipHdrLen + 8
        
        val answerLen = 16
        val dnsRespLen = dnsReqLen + answerLen
        
        // Build DNS response: copy request
        val dnsResp = ByteArray(dnsRespLen)
        System.arraycopy(requestPacket, dnsReqOffset, dnsResp, 0, dnsReqLen)
        
        // Modify DNS Header Flags (bytes 2 and 3)
        // Set QR (response) = 1, Opcode = 0, AA = 0, TC = 0, RD = 1
        // Set RA = 1, RCODE = 0 (NOERROR)
        dnsResp[2] = 0x81.toByte() 
        dnsResp[3] = 0x80.toByte() 
        
        // Set Answer Count (ANCOUNT) to 1 (bytes 6 and 7)
        dnsResp[6] = 0x00.toByte()
        dnsResp[7] = 0x01.toByte()
        
        // Append Answer Section at the end
        var offset = dnsReqLen
        dnsResp[offset++] = 0xC0.toByte() // Pointer
        dnsResp[offset++] = 0x0C.toByte() // to offset 12 (start of question)
        dnsResp[offset++] = 0x00.toByte() // Type
        dnsResp[offset++] = 0x01.toByte() // A
        dnsResp[offset++] = 0x00.toByte() // Class
        dnsResp[offset++] = 0x01.toByte() // IN
        dnsResp[offset++] = 0x00.toByte() // TTL
        dnsResp[offset++] = 0x00.toByte()
        dnsResp[offset++] = 0x00.toByte()
        dnsResp[offset++] = 0x3C.toByte() // 60 seconds
        dnsResp[offset++] = 0x00.toByte() // RDLEN
        dnsResp[offset++] = 0x04.toByte() // 4 bytes
        dnsResp[offset++] = 0x00.toByte() // IP 0.0.0.0
        dnsResp[offset++] = 0x00.toByte()
        dnsResp[offset++] = 0x00.toByte()
        dnsResp[offset++] = 0x00.toByte()

        forwardResponseToTun(requestPacket, ipHdrLen, udpHeaderOffset, dnsResp, dnsRespLen, outputStream)
    }

    private fun computeChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len - 1) {
            val word = ((buf[offset + i].toInt() and 0xFF) shl 8) + (buf[offset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (len % 2 != 0) {
            val word = (buf[offset + len - 1].toInt() and 0xFF) shl 8
            sum += word
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun parseDnsDomain(packet: ByteArray, dnsStart: Int, len: Int): String? {
        val parts = mutableListOf<String>()
        var pos = dnsStart + 12 // Skip DNS header (12 bytes)
        while (pos < len) {
            val l = packet[pos].toInt() and 0xFF
            if (l == 0) break // root label
            
            // Check for pointer (compression) - shouldn't happen in the question section usually, but safe
            if ((l and 0xC0) == 0xC0) {
                break
            }
            
            pos++
            if (pos + l > len) return null
            parts += String(packet, pos, l)
            pos += l
        }
        return parts.joinToString(".")
    }

    private fun stopVpn() {
        android.util.Log.d("MainActivity", "Really stopping VPN service")
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        isRunning = false
        stopVpn()
        stopSelf()
        android.util.Log.d("DnsBlockVpnService", "VPN stopped and service destroyed")
        super.onDestroy()
    }
}
