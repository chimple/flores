package org.chimple.flores;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

import org.chimple.flores.db.entity.P2PUserIdDeviceId;
import org.chimple.flores.scheduler.JobUtils;
import org.chimple.flores.sync.P2PSyncManager;

/**
 * FloresPlugin
 */
public class FloresPlugin implements MethodCallHandler, StreamHandler {
  /**
   * Plugin registration.
   */
  public static void registerWith(PluginRegistry.Registrar registrar) {
    final MethodChannel methodChannel = new MethodChannel(registrar.messenger(), "chimple.org/flores");
    final EventChannel eventChannel =
        new EventChannel(registrar.messenger(), "chimple.org/flores_event");
    final FloresPlugin instance = new FloresPlugin(registrar);
    eventChannel.setStreamHandler(instance);
    methodChannel.setMethodCallHandler(instance);
  }

  FloresPlugin(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
  }

  private final PluginRegistry.Registrar registrar;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
      switch (call.method) {
          case "getUsers":
          {
              List<Map<String, String>> users = getUsers();

              if (users.size() >= 0) {
                  result.success(users);
              } else {
                  result.error("UNAVAILABLE", "Users are not available.", null);
              }
              break;
          }
          case "addUser":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String userId = arg.get("user_id");
              String deviceId = arg.get("device_id");
              boolean status = addUser(userId, deviceId);
              result.success(status);
              break;
          }
          case "start":
          {
              JobUtils.scheduledJob(registrar.context().getApplicationContext(), true);
              break;
          }
          default:
          {
              result.notImplemented();
          }
      }
  }

  @Override
  public void onListen(Object arguments, EventSink events) {}

  @Override
  public void onCancel(Object arguments) {}

  private List<Map<String, String>> getUsers() {
      List<P2PUserIdDeviceId> udList = P2PSyncManager.getInstance(registrar.context()).getUsers();
      List<Map<String, String>> userList = new ArrayList<Map<String, String>>();
      for (P2PUserIdDeviceId ud: udList
           ) {
          Map<String, String> user = new HashMap<String, String>();
          user.put("user_id", ud.userId);
          user.put("device_id", ud.deviceId);
          userList.add(user);
      }
      return userList;
  }

  private boolean addUser(String userId, String deviceId) {
      return P2PSyncManager.getInstance(registrar.context()).upsertUser(userId, deviceId, null);
  }

  private boolean connectTo(String neighbor) {
      return true;
  }
}
