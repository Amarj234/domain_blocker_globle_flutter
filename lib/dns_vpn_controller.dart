import 'package:flutter/services.dart';

class DnsVpnController {
  static const MethodChannel _channel = MethodChannel('dns.blocker.vpn');
  final List<String> blockedDomains = ['google.com', 'youtube.com'];
  Future<void> startVpn() async {
    try {
      // await _channel.invokeMethod('requestBatteryWhitelist');

    await _channel.invokeMethod('startVpn', {'domains': blockedDomains});
    } catch (e) {
      print("Error starting VPN: $e");
    }
  }

  Future<void> stopVpn() async {
    try {
      await _channel.invokeMethod('stopVpn');
    } catch (e) {
      print("Error stopping VPN: $e");
    }
  }
}
