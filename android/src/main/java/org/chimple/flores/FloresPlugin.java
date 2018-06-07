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

import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceId;
import org.chimple.flores.db.entity.P2PUserIdMessage;
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
              List<P2PUserIdDeviceId> udList = P2PSyncManager.getInstance(registrar.context()).getUsers();
              List<Map<String, String>> users = new ArrayList<Map<String, String>>();
              for (P2PUserIdDeviceId ud: udList
                      ) {
                  Map<String, String> user = new HashMap<String, String>();
                  user.put("user_id", ud.userId);
                  user.put("device_id", ud.deviceId);
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
              String userId = arg.get("user_id");
              String deviceId = arg.get("device_id");
              boolean status = P2PSyncManager.getInstance(registrar.context()).upsertUser(userId, deviceId, null);
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
              String userId = arg.get("user_id");
              String recipientId = arg.get("recipient_id");
              String messageType = arg.get("message_type");
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
              String messageType = arg.get("message_type");
              String userId = arg.get("user_id");
              List<String> userIds = new ArrayList<String>();
              userIds.add(userId);
              List<P2PUserIdMessage> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .fetchLatestMessagesByMessageType(messageType, userIds);
              List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
              for (P2PUserIdMessage m: messageList
                      ) {
                  Map<String, String> message = new HashMap<String, String>();
                  message.put("user_id", m.userId);
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
              String messageType = arg.get("message_type");
              String firstUserId = arg.get("first_user_id");
              String secondUserId = arg.get("second_user_id");
              List<P2PSyncInfo> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .getConversations(firstUserId, secondUserId, messageType);
              List<Map<String, String>> messages = convertToMap(messageList);

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
              String messageType = arg.get("message_type");
              String firstUserId = arg.get("first_user_id");
              String secondUserId = arg.get("second_user_id");
              List<P2PSyncInfo> messageList =
                      P2PSyncManager.getInstance(registrar.context())
                              .getLatestConversations(firstUserId, secondUserId, messageType);
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
          message.put("user_id", m.userId);
          message.put("message", m.message);
          message.put("message", m.messageType);
          message.put("message", m.deviceId);
          message.put("message", m.recipientUserId);
          message.put("message", m.sessionId);
          message.put("message", m.id.toString());
          message.put("message", m.loggedAt.toString());
          message.put("message", m.sequence.toString());
          message.put("message", m.status.toString());
          message.put("message", m.step.toString());
          messages.add(message);
      }
    return messages;
  }

}
