package org.chimple.flores;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
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

import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.scheduler.JobUtils;
import org.chimple.flores.sync.P2PSyncManager;

/**
 * FloresPlugin
 */
public class FloresPlugin implements MethodCallHandler, StreamHandler {
    private static final String TAG = FloresPlugin.class.getName();
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
              List<P2PUserIdDeviceIdAndMessage> udList = P2PSyncManager.getInstance(registrar.context()).getUsers();
              List<Map<String, String>> users = new ArrayList<Map<String, String>>();
              for (P2PUserIdDeviceIdAndMessage ud: udList
                      ) {
                  Map<String, String> user = new HashMap<String, String>();
                  user.put("userId", ud.userId);
                  user.put("deviceId", ud.deviceId);
                  user.put("message", ud.message);
                  users.add(user);
              }

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
              String userId = arg.get("userId");
              String deviceId = arg.get("deviceId");
              String message = arg.get("message");
              boolean status = P2PSyncManager.getInstance(registrar.context()).upsertUser(userId, deviceId, message);
              result.success(status);
              break;
          }
          case "start":
          {
              JobUtils.scheduledJob(registrar.context().getApplicationContext(), true);
              break;
          }
          case "addMessage":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String userId = arg.get("userId");
              String recipientId = arg.get("recipientId");
              String messageType = arg.get("messageType");
              String message = arg.get("message");
              boolean status =
                      P2PSyncManager.getInstance(registrar.context())
                              .addMessage(userId, recipientId, messageType, message);
              result.success(status);
              break;
          }
          case "getLatestMessages":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              String secondUserId = arg.get("secondUserId");
              List<String> userIds = new ArrayList<String>();
              userIds.add(userId);
              userIds.add(secondUserId);
              List<P2PUserIdMessage> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .fetchLatestMessagesByMessageType(messageType, userIds);
              List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
              for (P2PUserIdMessage m: messageList
                      ) {
                  Map<String, String> message = new HashMap<String, String>();
                  message.put("userId", m.userId);
                  message.put("message", m.message);
                  messages.add(message);
              }

              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
              break;
          }
          case "getConversations":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              String secondUserId = arg.get("secondUserId");
              List<P2PSyncInfo> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .getConversations(userId, secondUserId, messageType);
              Log.i(TAG, "getConversations: "+messageType+userId+secondUserId);
              List<Map<String, String>> messages = convertToMap(messageList);
              Log.i(TAG, messages.toString());
              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
              break;
          }
          case "getLatestConversations":
          {
              Map<String, String> arg = (Map<String, String>)call.arguments;
              String messageType = arg.get("messageType");
              String userId = arg.get("userId");
              String secondUserId = arg.get("secondUserId");
              List<P2PSyncInfo> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .getLatestConversations(userId, secondUserId, messageType);
              List<Map<String, String>> messages = convertToMap(messageList);

              if (messages.size() >= 0) {
                  result.success(messages);
              } else {
                  result.error("UNAVAILABLE", "Messages are not available.", null);
              }
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

  public List<Map<String, String>> convertToMap(List<P2PSyncInfo> messageList) {
      List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
      for (P2PSyncInfo m: messageList
              ) {
          Map<String, String> message = new HashMap<String, String>();
          message.put("userId", m.userId);
          message.put("message", m.message);
          message.put("messageType", m.messageType);
          message.put("deviceId", m.deviceId);
          message.put("recipientUserId", m.recipientUserId);
          message.put("sessionId", m.sessionId);
          if(m.id != null)
            message.put("id", m.id.toString());
          if(m.loggedAt != null)
            message.put("loggedAt", m.loggedAt.getTime().toString());
          if(m.sequence != null)
            message.put("sequence", m.sequence.toString());
          if(m.status != null)
            message.put("status", m.status.toString());
          if(m.step != null)
            message.put("step", m.step.toString());
          Log.i(TAG, "convertToMap: "+message.toString());
          messages.add(message);
      }
    return messages;
  }

}
