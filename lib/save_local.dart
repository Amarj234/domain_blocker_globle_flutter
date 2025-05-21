

import 'package:get_storage/get_storage.dart';


class SaveList {

  final box = GetStorage();

  saveList(String url){
    List<dynamic> stored =box.read('domain')??[];
    List<String> fruits = List<String>.from(stored);

 if(fruits.isEmpty){
   box.write('domain', [url]);
 }else{
   fruits.add(url);
   box.write('domain', fruits);
 }
  }

  saveRunning(bool isRunning){
    box.write('isRunning', isRunning);
  }
  bool? getRunning(){
    return box.read('isRunning')??false;
  }


  List<String>? getList(){


    List<dynamic> stored =box.read('domain')??['google.com', 'youtube.com'];
    List<String> fruits = List<String>.from(stored);
    return fruits;

  }

  deleteData(String url){
    List<dynamic> stored =box.read('domain')??[];
    List<String> fruits = List<String>.from(stored);
    fruits.remove(url);

    box.write('domain', fruits);

  }

}




