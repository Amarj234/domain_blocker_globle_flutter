import 'package:domain_block/save_local.dart';
import 'package:flutter/services.dart';

class DnsVpnController {
  static const platform = MethodChannel('vpn_service');

   List<String> blockedDomains=[];

  Future<void> startVpn() async {
    try {
      final result = await platform.invokeMethod('startVpn');
      if(SaveList().getList()==null || SaveList().getList()==[]){

    blockedDomains = [ 'youtube.com'];
      }else{

        blockedDomains = SaveList().getList()??[ 'youtube.com'];
      }
print(blockedDomains);
   // await _channel.invokeMethod('startVpn', {'domains': blockedDomains});
    } catch (e) {
      print("Error starting VPN: $e");
    }
  }

  Future<void> stopVpn() async {
    try {
    //  await _channel.invokeMethod('stopVpn');
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
