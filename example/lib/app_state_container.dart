import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

class AppStateContainer extends StatefulWidget {
  final Widget child;
  final String deviceId;
  final String schoolId;

  AppStateContainer({this.child, this.deviceId, this.schoolId});

  static AppStateContainerState of(BuildContext context) {
    return (context.inheritFromWidgetOfExactType(_InheritedAppStateContainer)
            as _InheritedAppStateContainer)
        ?.data;
  }

  @override
  AppStateContainerState createState() => new AppStateContainerState();
}

class AppStateContainerState extends State<AppStateContainer> {
  List<dynamic> messages = [];
  List<dynamic> users = [];
  String advName;
  bool isAdv;
  String loggedInUserId;
  String loggedInUserName;
  String friendId;

  String get deviceId => widget.deviceId;
  String get schoolId => widget.schoolId;

  @override
  void initState() {
    super.initState();
    print('AppStateContainer: main initState');
    print('AppStateContainer: main initState with device' + deviceId);
    print('AppStateContainer: main initState with school' + schoolId);
    try {
      Flores().initialize((Map<dynamic, dynamic> message) {
        print('Flores received message: $message');
        onReceiveMessage(message);
      });
    } on PlatformException {
      print('Flores: Failed initialize');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
  }

  Future<String> addUser(String name) async {
    String userId = Uuid().v4();
    try {
      Flores().addUser(schoolId, userId, deviceId, name);
    } on PlatformException {
      print('Flores: Failed loggedInUser');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }

    await getUsers();
    return userId;
  }

  Future<void> setLoggedInUser(
      String userId, String userName, bool isTeacher) async {
    try {
      await Flores().loggedInUser(schoolId, userId, deviceId, isTeacher);
    } on PlatformException {
      print('Flores: Failed loggedInUser');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
    setState(() {
      loggedInUserId = userId;
      loggedInUserName = userName;
    });
  }

  Future<void> isAdvertisingStatus() async {
    bool isAdv = false;
    try {
      isAdv = await Flores().isAdvertising;
    } on PlatformException {
      print('Flores: Failed isAdv');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }

    setState(() => this.isAdv = isAdv);
    print('isAdvertising: $isAdv');
  }

  Future<void> getAdvName() async {
    String advName;
    try {
      advName = await Flores().advName;
    } on PlatformException {
      print('Flores: Failed adv name');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }

    setState(() => this.advName = advName);
    print('getAdvName: $advName');
  }

  Future<void> getUsers() async {
    List<dynamic> users;
    try {
      users = await Flores().users;
    } on PlatformException {
      print('Flores: Failed users');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }

    setState(() => this.users = users);
    print('getUsers: $users');
  }

  Future<void> getMessages(String friendId) async {
    List<dynamic> msgs;
    try {
      msgs = await Flores()
          .getConversations(schoolId, loggedInUserId, friendId, 'chat');
    } on PlatformException {
      print('Failed getting messages');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
    print('_fetchMessages: $msgs');
    msgs ??= List<Map<String, String>>();
    setState(() {
      this.friendId = friendId;
      messages = msgs.reversed.toList(growable: true);
    });
    print('getMessages: $messages');
  }

  Future<void> sendMessage(String friendId, String message) async {
    await Flores().addMessage(
        schoolId, loggedInUserId, friendId, 'chat', message, true, '');
    getMessages(friendId);
  }

  void onReceiveMessage(Map<dynamic, dynamic> message) async {
    if (message['messageType'] == 'Photo') {
      getUsers();
    } else if (message['messageType'] == 'chat' &&
        message['recipientUserId'] == loggedInUserId &&
        message['userId'] == friendId) {
      getMessages(friendId);
    }
  }

  @override
  Widget build(BuildContext context) {
    return new _InheritedAppStateContainer(
      data: this,
      child: widget.child,
    );
  }
}

class _InheritedAppStateContainer extends InheritedWidget {
  final AppStateContainerState data;

  _InheritedAppStateContainer(
      {Key key, @required this.data, @required Widget child})
      : super(key: key, child: child);

  bool updateShouldNotify(_InheritedAppStateContainer old) => true;
}
