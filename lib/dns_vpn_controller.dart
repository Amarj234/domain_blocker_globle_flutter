import 'package:domain_block/save_local.dart';
import 'package:flutter/services.dart';

class DnsVpnController {
  static const MethodChannel _channel = MethodChannel('dns.blocker.vpn');

  List<String> blockedDomains=[];
  static const platform = MethodChannel('com.example.domain_block/foregroundService');
  Future<void> startVpn() async {
    try {
      // await _channel.invokeMethod('requestBatteryWhitelist');
      if(SaveList().getList()==null || SaveList().getList()==[]){

        blockedDomains = [ 'youtube.com'];
      }else{

        blockedDomains = SaveList().getList()??[ 'youtube.com'];
      }
      print(blockedDomains);
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

  Future<void> startForegroundService() async {
    try {
      // Call the native Android method to start the service
      await platform.invokeMethod('startForegroundService');
    } on PlatformException catch (e) {
      print("Failed to start foreground service: ${e.message}");
    }
  }
}