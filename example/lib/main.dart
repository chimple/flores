import 'dart:async';
import 'package:flores_example/app_state_container.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

void main() async {
  SharedPreferences prefs = await SharedPreferences.getInstance();
  if (prefs.getString('deviceId') == null) {
    prefs.setString('deviceId', Uuid().v4());
  }
  runApp(AppStateContainer(child: MyApp()));
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      AppStateContainer.of(context).getUsers();
    });
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(home: UserScreen());
  }
}

class UserScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: new AppBar(
        title: new Text('Login'),
      ),
      body: Column(
        children: <Widget>[
          Flexible(
            child: ListView(
                children: AppStateContainer.of(context)
                    .users
                    .map((u) => Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: RaisedButton(
                            onPressed: () async {
                              await AppStateContainer.of(context)
                                  .setLoggedInUser(u['userId'], u['name']);
                              Navigator.of(context).push(
                                  MaterialPageRoute<Null>(
                                      builder: (BuildContext context) =>
                                          FriendScreen()));
                            },
                            child: Text(u['name']),
                          ),
                        ))
                    .toList(growable: false)),
          ),
          TextField(
              onSubmitted: (text) {
                AppStateContainer.of(context).addUser(text);
              },
              decoration: new InputDecoration.collapsed(hintText: 'Add User'))
        ],
      ),
    );
  }
}

class FriendScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    String userId = AppStateContainer.of(context).loggedInUserId;
    final users = AppStateContainer.of(context).users;
    print('FriendScreen userId: $userId users: $users');
    return Scaffold(
      appBar: new AppBar(
        title: new Text(AppStateContainer.of(context).loggedInUserName),
      ),
      body: ListView(
          children: AppStateContainer.of(context)
              .users
              .where((u) => u['userId'] != userId)
              .map((u) => Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: RaisedButton(
                      onPressed: () async {
                        await AppStateContainer.of(context)
                            .getMessages(u['userId']);
                        Navigator.of(context).push(MaterialPageRoute<Null>(
                            builder: (BuildContext context) => ChatScreen(
                                  friendId: u['userId'],
                                  friendName: u['name'],
                                )));
                      },
                      child: Text(u['name']),
                    ),
                  ))
              .toList(growable: false)),
    );
  }
}

class ChatScreen extends StatelessWidget {
  final String friendId;
  final String friendName;

  const ChatScreen({Key key, this.friendId, this.friendName}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    final messages = AppStateContainer.of(context).messages;
    return Scaffold(
      appBar: AppBar(
        title: Text(
            '${AppStateContainer.of(context).loggedInUserName} $friendName'),
      ),
      body: Column(
        children: <Widget>[
          Flexible(
              child: ListView.builder(
                  itemCount: messages.length,
                  reverse: true,
                  itemBuilder: (context, index) {
                    return (messages[index]['userId'] == friendId)
                        ? Row(
                            mainAxisAlignment: MainAxisAlignment.start,
                            children: <Widget>[
                                Padding(
                                    padding: const EdgeInsets.all(8.0),
                                    child: CircleAvatar(
                                      child: Text(friendName),
                                    )),
                                Flexible(
                                    child: Text(messages[index]['message']))
                              ])
                        : Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: <Widget>[
                                Flexible(
                                    child: Text(messages[index]['message'])),
                                Padding(
                                    padding: const EdgeInsets.all(8.0),
                                    child: CircleAvatar(
                                      child: Text(AppStateContainer.of(context)
                                          .loggedInUserName),
                                    )),
                              ]);
                  })),
          Divider(height: 1.0),
          CommentTextField(
            addComment: (message) =>
                AppStateContainer.of(context).sendMessage(friendId, message),
          )
        ],
      ),
    );
  }
}

typedef void AddComment(String comment);

class CommentTextField extends StatefulWidget {
  final AddComment addComment;

  const CommentTextField({Key key, this.addComment}) : super(key: key);

  @override
  CommentTextFieldState createState() {
    return new CommentTextFieldState();
  }
}

class CommentTextFieldState extends State<CommentTextField> {
  final TextEditingController _textController = new TextEditingController();
  FocusNode _focusNode;
  bool _isComposing = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(children: <Widget>[
      Flexible(
        child: new TextField(
          maxLength: null,
          keyboardType: TextInputType.multiline,
          controller: _textController,
          focusNode: _focusNode,
          onChanged: (String text) {
            setState(() {
              _isComposing = text.trim().isNotEmpty;
            });
          },
          onSubmitted: (String text) => _handleSubmitted(context, text),
          decoration: new InputDecoration.collapsed(hintText: 'Send message'),
        ),
      ),
      Container(
          margin: new EdgeInsets.symmetric(horizontal: 4.0),
          child: IconButton(
            icon: new Icon(Icons.send),
            onPressed: _isComposing
                ? () => _handleSubmitted(context, _textController.text)
                : null,
          )),
    ]);
  }

  Future<Null> _handleSubmitted(BuildContext context, String text) async {
    _textController.clear();
    setState(() {
      _isComposing = false;
    });
    widget.addComment(text);
    _focusNode.unfocus();
  }
}
