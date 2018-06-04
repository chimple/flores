import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';

void main() => runApp(new MaterialApp(home: new MyApp()));

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  Flores _flores = new Flores();
  List<dynamic> _neighbors = [];

  @override
  initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  initPlatformState() async {
    List<dynamic> neighbors;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      neighbors = await _flores.neighbors;
    } on PlatformException {
      print('Failed getting neighbors');
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _neighbors = neighbors;
    });
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        appBar: new AppBar(
          title: new Text('Flores example app'),
        ),
        body: new ListView.builder(
            itemBuilder: (BuildContext context, int index) => new RaisedButton(
                onPressed: () {
                  Navigator.of(context).push(new MaterialPageRoute<Null>(
                      builder: (BuildContext context) {
                    return new ConnectPage(_neighbors[index]);
                  }));
                },
                child: new Text(_neighbors[index])),
            itemCount: _neighbors.length),
      ),
    );
  }
}

class ConnectPage extends StatefulWidget {
  final String neighbor;

  ConnectPage(this.neighbor);

  @override
  ConnectPageState createState() {
    return new ConnectPageState();
  }
}

class ConnectPageState extends State<ConnectPage> {
  Flores _flores = new Flores();
  bool _connectionStatus;

  @override
  initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  initPlatformState() async {
    bool connectionStatus;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      connectionStatus = await _flores.connectTo(widget.neighbor);
    } on PlatformException {
      print('Failed connecting to neighbor');
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _connectionStatus = connectionStatus;
    });
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
        appBar: new AppBar(title: new Text(widget.neighbor)),
        body: new Text(_connectionStatus.toString()));
  }
}
