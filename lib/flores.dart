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

  Future<List<dynamic>> get users async => _methodChannel
      .invokeMethod('getUsers')
      .then<List<dynamic>>((dynamic result) => result);


  Future<bool> connectTo(String neighbor) => _methodChannel
      .invokeMethod('connectTo')
      .then<bool>((dynamic result) => result);

  Future<bool> start() => _methodChannel
      .invokeMethod('start')
      .then<bool>((dynamic result) => result);

  Future<bool> addUser(String userId, String deviceId, String message) {
    final Map<String, String> params = <String, String>{
      'user_id': userId,
      'device_id': deviceId,
      'message': message
    };

    _methodChannel
        .invokeMethod('addUser', params)
        .then<bool>((dynamic result) => result);
  }

  Future<List<dynamic>> getLatestMessages(String userId, String secondUserId, String messageType) {
    final Map<String, String> params = <String, String>{
      'user_id': userId,
      'second_user_id': secondUserId,
      'messageType': messageType
    };

    _methodChannel
        .invokeMethod('getLatestMessages', params)
        .then<List<dynamic>>((dynamic result) => result);
  }

  Future<bool> addMessage(String userId, String recipientId, String messageType, String message) {
    final Map<String, String> params = <String, String>{
      'user_id': userId,
      'recipient_id': recipientId,
      'message_type': messageType,
      'message': message
    };

    _methodChannel
        .invokeMethod('addMessage', params)
        .then<bool>((dynamic result) => result);
  }

}
