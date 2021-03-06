package org.chimple.flores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.AsyncTask;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.content.Intent;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

import org.chimple.flores.application.P2PContext;
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.multicast.MulticastManager;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.nearby.NearByManager;
import org.chimple.flores.nearby.NearbyHelper;

/**
 * FloresPlugin
 */
public class FloresPlugin implements MethodCallHandler, StreamHandler {
    private static final String TAG = FloresPlugin.class.getName();
    private static MethodChannel methodChannel;
    private static boolean isAppLaunched = false;

    /**
     * Plugin registration.
     */
    public static void registerWith(PluginRegistry.Registrar registrar) {
        methodChannel = new MethodChannel(registrar.messenger(), "chimple.org/flores");

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

    public static void onMessageReceived(P2PSyncInfo message) {
        Log.i(TAG, "messageReceived: " + convertToMap(message));
        methodChannel.invokeMethod("messageReceived", convertToMap(message));
    }

    @Override
    public void onMethodCall(final MethodCall call, final Result result) {
        switch (call.method) {
            case "getUsers": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        String schoolId = P2PContext.getInstance().getSchool();
                        Log.d(TAG, "current school: =====>" + schoolId);
                        List<P2PUserIdDeviceIdAndMessage> udList = DBSyncManager.getInstance(registrar.context()).getUsers(schoolId);
                        List<Map<String, String>> users = new ArrayList<Map<String, String>>();
                        //Log.i(TAG, "getUsers: "+users);

                        for (P2PUserIdDeviceIdAndMessage ud : udList
                        ) {
                            Map<String, String> user = new HashMap<String, String>();
                            user.put("schoolId", ud.schoolId);
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
                    }
                });
                break;
            }
            case "isAdvertising": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        boolean isAdv = NearByManager.getInstance(registrar.context()).isAdvertising();
                        Log.d(TAG, "isAdv: =====>" + isAdv);
                        result.success(isAdv);
                    }
                });
                break;
            }
            case "getAdvertisingName": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        String advName = NearByManager.getInstance(registrar.context()).getAdvertisingLocalName();
                        Log.d(TAG, "advName: =====>" + advName);
                        result.success(advName);
                    }
                });
                break;
            }
            case "addUser": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String schoolId = arg.get("schoolId");
                        String userId = arg.get("userId");
                        String deviceId = arg.get("deviceId");
                        Log.i(TAG, "school: " + schoolId + " addUser with user id: " + userId + " and device id:" + deviceId);
                        String message = arg.get("message");
                        boolean status = DBSyncManager.getInstance(registrar.context()).upsertUser(schoolId, userId, deviceId, message);
                        String bluetoothAddress = NearByManager.getInstance(registrar.context()).getBluetoothMacAddress();
                        DBSyncManager.getInstance(registrar.context()).saveBtAddress(deviceId, bluetoothAddress);
                        NearByManager.getInstance(registrar.context()).setCurrentUserAsTeacher();
                        result.success(status);
                    }
                });
                break;
            }
            case "start": {
                break;
            }
            case "addTextMessage": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String userId = P2PContext.getInstance().getLoggedInUser();
                        String schoolId = P2PContext.getInstance().getSchool();
                        String recipientId = null;
                        String messageType = "Chat";
                        String message = arg.get("message");
                        boolean retStatus =
                                DBSyncManager.getInstance(registrar.context())
                                        .addMessage(schoolId, userId, recipientId, messageType, message);
                        result.success(retStatus);
                    }
                });

                break;
            }
            case "addMessage": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String schoolId = arg.get("schoolId");
                        String userId = arg.get("userId");
                        String recipientId = arg.get("recipientId");
                        String messageType = arg.get("messageType");
                        String message = arg.get("message");
                        String statusStr = arg.get("status");
                        Boolean status = Boolean.valueOf(statusStr);
                        String sessionId = arg.get("sessionId");
                        boolean retStatus =
                                DBSyncManager.getInstance(registrar.context())
                                        .addMessage(schoolId, userId, recipientId, messageType, message, status, sessionId);
                        result.success(retStatus);
                    }
                });

                break;
            }
            case "addGroupMessage": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String userId = arg.get("userId");
                        String schoolId = arg.get("schoolId");
                        String recipientId = arg.get("recipientId");
                        String messageType = arg.get("messageType");
                        String message = arg.get("message");
                        String statusStr = arg.get("status");
                        Boolean status = Boolean.valueOf(statusStr);
                        String sessionId = arg.get("sessionId");
                        boolean retStatus =
                                DBSyncManager.getInstance(registrar.context())
                                        .addGroupMessage(schoolId, userId, recipientId, messageType, message, status, sessionId);
                        result.success(retStatus);
                    }
                });

                break;
            }
            case "getLatestMessages": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String messageType = arg.get("messageType");
                        String userId = arg.get("userId");
                        String schoolId = arg.get("schoolId");
                        String secondUserId = arg.get("secondUserId");
                        List<String> userIds = new ArrayList<String>();
                        userIds.add(userId);
                        userIds.add(secondUserId);
                        List<P2PUserIdMessage> messageList =
                                DBSyncManager.getInstance(registrar.context())
                                        .fetchLatestMessagesByMessageType(schoolId, messageType, userIds);
                        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
                        for (P2PUserIdMessage m : messageList
                        ) {
                            Map<String, String> message = new HashMap<String, String>();
                            message.put("userId", m.userId);
                            message.put("schoolId", m.schoolId);
                            message.put("message", m.message);
                            messages.add(message);
                        }

                        if (messages.size() >= 0) {
                            result.success(messages);
                        } else {
                            result.error("UNAVAILABLE", "Messages are not available.", null);
                        }
                    }
                });

                break;
            }
            case "getConversations": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String messageType = arg.get("messageType");
                        String userId = arg.get("userId");
                        String schoolId = arg.get("schoolId");
                        String secondUserId = arg.get("secondUserId");
                        List<P2PSyncInfo> messageList =
                                DBSyncManager.getInstance(registrar.context())
                                        .getConversations(schoolId, userId, secondUserId, messageType);
                        Log.i(TAG, "getConversations: " + messageType + userId + secondUserId);
                        List<Map<String, String>> messages = convertToListOfMaps(messageList);
                        //Log.i(TAG, messages.toString());
                        if (messages.size() >= 0) {
                            result.success(messages);
                        } else {
                            result.error("UNAVAILABLE", "Messages are not available.", null);
                        }
                    }
                });

                break;
            }
            case "getLatestConversations": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String messageType = arg.get("messageType");
                        String userId = arg.get("userId");
                        String schoolId = arg.get("schoolId");
                        List<P2PSyncInfo> messageList =
                                DBSyncManager.getInstance(registrar.context())
                                        .getLatestConversations(schoolId, userId, messageType);
                        List<Map<String, String>> messages = convertToListOfMaps(messageList);

                        if (messages.size() >= 0) {
                            result.success(messages);
                        } else {
                            result.error("UNAVAILABLE", "Messages are not available.", null);
                        }
                    }
                });

                break;
            }
            case "loggedInUser": {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, String> arg = (Map<String, String>) call.arguments;
                        String userId = arg.get("userId");
                        String deviceId = arg.get("deviceId");
                        String schoolId = arg.get("schoolId");
                        String isTeacher = arg.get("isTeacher");
                        boolean status = DBSyncManager.getInstance(registrar.context())
                                .loggedInUser(schoolId, userId, deviceId);

                        if (isTeacher.equalsIgnoreCase("true")) {
                            P2PContext.getInstance().setLoggedInUserAsTeacher();
                            NearByManager.getInstance(registrar.context()).setCurrentUserAsTeacher();
                        }
                        MulticastManager.getInstance(registrar.context()).sendFindBuddyMessage();
                        result.success(status);
                    }
                });

                break;

            }
            default: {
                result.notImplemented();
            }
        }
    }

    @Override
    public void onListen(Object arguments, EventSink events) {
    }

    @Override
    public void onCancel(Object arguments) {
    }

    static private List<Map<String, String>> convertToListOfMaps(List<P2PSyncInfo> messageList) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        for (P2PSyncInfo m : messageList
        ) {
            Map<String, String> message = convertToMap(m);
            messages.add(message);
        }
        return messages;
    }

    @NonNull
    static private Map<String, String> convertToMap(P2PSyncInfo m) {
        Map<String, String> message = new HashMap<String, String>();
        message.put("schoolId", m.schoolId);
        message.put("userId", m.userId);
        message.put("message", m.message);
        message.put("messageType", m.messageType);
        message.put("deviceId", m.deviceId);
        message.put("recipientUserId", m.recipientUserId);
        message.put("sessionId", m.sessionId);
        if (m.id != null)
            message.put("id", m.id.toString());
        if (m.loggedAt != null)
            message.put("loggedAt", Long.toString(m.loggedAt.getTime()));
        if (m.sequence != null)
            message.put("sequence", m.sequence.toString());
        if (m.status != null)
            message.put("status", m.status.toString());
        if (m.step != null)
            message.put("step", m.step.toString());
        //Log.i(TAG, "convertToMap: "+message.toString());
        return message;
    }

    /**
     * Handle the incoming message and immediately closes the activity.
     *
     * <p>Needs to be invocable by Android system; hence it is public.
     */
    public static class MessageReceivedActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            //Log.i(TAG, "messageReceivedActivity created");

            // Get the Intent that started this activity and extract the string
            Intent intent = getIntent();
            String userId = intent.getStringExtra("userId");
            String message = intent.getStringExtra("message");
            String deviceId = intent.getStringExtra("deviceId");
            String messageType = intent.getStringExtra("messageType");
            String recipientUserId = intent.getStringExtra("recipientUserId");
            String sessionId = intent.getStringExtra("sessionId");
            Long id = intent.getLongExtra("id", 0);
            Long loggedAt = intent.getLongExtra("loggedAt", 0);
            Long sequence = intent.getLongExtra("sequence", 0);
            boolean status = intent.getBooleanExtra("status", true);
            Long step = intent.getLongExtra("step", 0);
            //Log.i(TAG, "messageReceivedActivity: "+message);

            methodChannel.invokeMethod("messageReceived", message);
            finish();
        }
    }


    public static void launchApp() {
        FloresPlugin.isAppLaunched = true;
    }

    public static boolean isAppLunched() {
        return FloresPlugin.isAppLaunched;
    }
}
