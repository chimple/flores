import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

class AppStateContainer extends StatefulWidget {
  final Widget child;

  AppStateContainer({this.child});

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
  List<Map<String, String>> users = [];
  String loggedInUserId;
  String loggedInUserName;
  String friendId;

  @override
  void initState() {
    super.initState();
    print('AppStateContainer: main initState');
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
    SharedPreferences prefs = await SharedPreferences.getInstance();
    final deviceId = prefs.getString('deviceId');
    String userId = Uuid().v4();
    try {
      Flores().addUser(userId, deviceId, name);
    } on PlatformException {
      print('Flores: Failed loggedInUser');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
    final userList = prefs.getStringList('users') ?? [];
    userList.add('$userId,$deviceId,$name');
    prefs.setStringList('users', userList);
    getUsers();
    return userId;
  }

  Future<void> setLoggedInUser(String userId, String userName) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    final deviceId = prefs.getString('deviceId');
    try {
      await Flores().loggedInUser(userId, deviceId);
    } on PlatformException {
      print('Flores: Failed loggedInUser');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
    try {
      await Flores().start();
    } on PlatformException {
      print('Flores: Failed start');
    } catch (e, s) {
      print('Exception details:\n $e');
      print('Stack trace:\n $s');
    }
    setState(() {
      loggedInUserId = userId;
      loggedInUserName = userName;
    });
  }

  Future<void> getUsers() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    final userList = prefs.getStringList('users');
    setState(() => users = userList
        .map((u) =>
            Map.fromIterables(['userId', 'deviceId', 'name'], u.split(',')))
        .toList());
    print('getUsers: $users');
  }

  Future<void> getMessages(String friendId) async {
    List<dynamic> msgs;
    try {
      msgs = await Flores().getConversations(loggedInUserId, friendId, 'chat');
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
    await Flores()
        .addMessage(loggedInUserId, friendId, 'chat', message, true, '');
    getMessages(friendId);
  }

  void onReceiveMessage(Map<dynamic, dynamic> message) async {
    if (message['messageType'] == 'Photo') {
      SharedPreferences prefs = await SharedPreferences.getInstance();
      final userList = prefs.getStringList('users');
      userList.add(
          "${message['userId']},${message['deviceId']},${message['message']}");
      prefs.setStringList('users', userList);
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
