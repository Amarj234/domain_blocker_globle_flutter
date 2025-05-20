import 'dart:io';

import 'package:domain_block/save_local.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get_storage/get_storage.dart';
import 'background_service.dart';
import 'dns_vpn_controller.dart';

void main() async{
  WidgetsFlutterBinding.ensureInitialized();
  await GetStorage.init();


  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});



  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {

  final TextEditingController urlController = TextEditingController();
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();

  bool isValidUrl(String url) {
    final Uri? uri = Uri.tryParse(url);
    return uri != null && uri.hasAbsolutePath;
  }

  @override
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Beautiful URL Validator'),
        backgroundColor: Colors.deepPurple,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Beautiful TextFormField with custom decoration
              TextFormField(
                controller: urlController,
                decoration: InputDecoration(
                  labelText: 'Enter URL',
                  labelStyle: TextStyle(
                    color: Colors.deepPurpleAccent,
                    fontWeight: FontWeight.bold,
                  ),
                  hintText: 'example.com',
                  hintStyle: TextStyle(color: Colors.grey[500]),
                  filled: true,
                  fillColor: Colors.deepPurple[50],
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(30.0),
                    borderSide: BorderSide.none,
                  ),
                  contentPadding: EdgeInsets.symmetric(
                      vertical: 20, horizontal: 15),
                  prefixIcon: Icon(Icons.link, color: Colors.deepPurple),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(30.0),
                    borderSide: BorderSide(
                        color: Colors.deepPurpleAccent, width: 2),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(30.0),
                    borderSide: BorderSide(
                        color: Colors.deepPurpleAccent, width: 1.5),
                  ),
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter a URL';
                  }
                  return null;
                },
              ),
              SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  // Background color
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(
                        30.0), // Button with rounded corners
                  ),
                  padding: EdgeInsets.symmetric(vertical: 15),
                ),
                onPressed: () {
                  if (_formKey.currentState?.validate() ?? false) {
                    final url = urlController.text;

                    SaveList().saveList(url);
                    urlController.text = '';
                    // Proceed with the URL
                    print('Valid URL: $url');
                    setState(() {

                    });
                  }
                },
                child: Text(
                  'Submit',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
              SizedBox(height: 20),
              if(SaveList().getList() != null ||
                  SaveList().getList() != []) ListView.builder(
                  physics: NeverScrollableScrollPhysics(),
                  shrinkWrap: true,
                  itemCount: SaveList()
                      .getList()
                      ?.length,

                  itemBuilder: (context, index) {
                    List<String>? myList = SaveList().getList();
                    return Container(
                      margin: EdgeInsets.symmetric(vertical: 5),
                      decoration: BoxDecoration(
                          color: Colors.grey[300],
                          borderRadius: BorderRadius.circular(10)
                      ),
                      child: ListTile(

                        title: Text(myList![index]),
                        trailing: IconButton(onPressed: () {
                          SaveList().deleteData(myList[index]);
                          setState(() {

                          });
                        }, icon: Icon(Icons.delete, color: Colors.pink,)),
                      ),
                    );
                  })
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(onPressed: () async {
        //  initializeService();
        incrementCounter();

        SystemNavigator.pop();
      }, child: Icon(Icons.block, color: Colors.red,),),
    );
  }

  DnsVpnController dnsVpnController = DnsVpnController();
  bool isVpnRunning = false;

  void incrementCounter() {
    if (!isVpnRunning) {
      dnsVpnController.startVpn();
      isVpnRunning = true; // Mark VPN as running
    }
  }
}