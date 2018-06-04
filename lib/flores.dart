import 'dart:async';

import 'package:flutter/services.dart';
import 'package:meta/meta.dart' show visibleForTesting;

class Flores {
  factory Flores() {
    if (_instance == null) {
      final MethodChannel methodChannel =
          const MethodChannel('chimple.org/flores');
      final EventChannel eventChannel =
          const EventChannel('chimple.org/flores_event');
      _instance = new Flores.private(methodChannel, eventChannel);
    }
    return _instance;
  }

  @visibleForTesting
  Flores.private(this._methodChannel, this._eventChannel);

  static Flores _instance;

  final MethodChannel _methodChannel;
  final EventChannel _eventChannel;

//  Future<List<String>> get neighbors => _methodChannel
//      .invokeMethod('getNeighbors')
//      .then<List<String>>((dynamic result) => result);


  Future<bool> connectTo(String neighbor) => _methodChannel
      .invokeMethod('connectTo')
      .then<bool>((dynamic result) => result);
}
