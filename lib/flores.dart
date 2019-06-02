import 'dart:async';
import 'package:uuid/uuid.dart';
import 'package:flutter/services.dart';
import 'package:meta/meta.dart' show visibleForTesting;

typedef void MessageReceivedHandler(Map<dynamic, dynamic> message);

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

  void initialize(MessageReceivedHandler handler) {
    _methodChannel.setMethodCallHandler((MethodCall call) {
      print('MessageReceivedHandler: ${call.method} ${call.arguments}');
      assert(call.method == 'messageReceived');
      handler(call.arguments);
    });
  }

  Future<bool> get isAdvertising async => _methodChannel
      .invokeMethod('isAdvertising')
      .then<bool>((dynamic result) => result);



  Future<String> get advName async => _methodChannel
      .invokeMethod('getAdvertisingName')
      .then<String>((dynamic result) => result);

  Future<List<dynamic>> get users async => _methodChannel
      .invokeMethod('getUsers')
      .then<List<dynamic>>((dynamic result) => result);

  Future<List<dynamic>> getConversations(
          String schoolId, String userId, String secondUserId, String messageType) async =>
      _methodChannel.invokeMethod('getConversations', <String, String>{
        'schoolId': schoolId,
        'userId': userId,
        'secondUserId': secondUserId,
        'messageType': messageType
      }).then<List<dynamic>>((dynamic result) => result);

  Future<bool> connectTo(String neighbor) async => _methodChannel
      .invokeMethod('connectTo')
      .then<bool>((dynamic result) => result);

  Future<bool> start() async {
    var uuid = new Uuid();
    String userId = uuid.v4();
//    String schoolId = uuid.v4();
    String deviceId = userId + '-device';
    String message = 'Profile-' + userId;
    final Map<String, String> params = <String, String>{
      'userId': userId,
      'deviceId': deviceId,
      'message': message
    };

    return _methodChannel
        .invokeMethod('start', params)
        .then<bool>((dynamic result) => result);
  }

  Future<bool> addUser(String schoolId, String userId, String deviceId, String message) async {
    final Map<String, String> params = <String, String>{
      'schoolId': schoolId,
      'userId': userId,
      'deviceId': deviceId,
      'message': message
    };

    return _methodChannel
        .invokeMethod('addUser', params)
        .then<bool>((dynamic result) => result);
  }

  Future<bool> addTextMessage(String message) async {
    final Map<String, String> params = <String, String>{'message': message};

    return _methodChannel
        .invokeMethod('addTextMessage', params)
        .then<bool>((dynamic result) => result);
  }

  Future<bool> loggedInUser(
      String schoolId, String userId, String deviceId, bool isTeacher) async {
    final Map<String, String> params = <String, String>{
      'schoolId': schoolId,
      'userId': userId,
      'deviceId': deviceId,
      'isTeacher': isTeacher ? "true" : "false"
    };

    return _methodChannel
        .invokeMethod('loggedInUser', params)
        .then<bool>((dynamic result) => result);
  }

  Future<List<dynamic>> getLatestConversations(
      String schoolId, String userId, String messageType) async {
    final Map<String, String> params = <String, String>{
      'schoolId': schoolId,
      'userId': userId,
      'messageType': messageType
    };

    return _methodChannel
        .invokeMethod('getLatestConversations', params)
        .then<List<dynamic>>((dynamic result) => result);
  }

  Future<bool> addMessage(String schoolId, String userId, String recipientId, String messageType,
      String message, bool status, String sessionId) async {
    final Map<String, String> params = <String, String>{
      'schoolId': schoolId,
      'userId': userId,
      'recipientId': recipientId,
      'messageType': messageType,
      'message': message,
      'status': '$status',
      'sessionId': sessionId
    };

    return _methodChannel
        .invokeMethod('addMessage', params)
        .then<bool>((dynamic result) => result);
  }

  Future<bool> addGroupMessage(String schoolId, String userId, String recipientId,
      String messageType, String message, bool status, String sessionId) async {
    final Map<String, String> params = <String, String>{
      'schoolId': schoolId,
      'userId': userId,
      'recipientId': recipientId,
      'messageType': messageType,
      'message': message,
      'status': '$status',
      'sessionId': sessionId
    };

    return _methodChannel
        .invokeMethod('addGroupMessage', params)
        .then<bool>((dynamic result) => result);
  }
}
