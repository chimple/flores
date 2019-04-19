package org.chimple.flores;


import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.chimple.flores.application.P2PContext.uiMessageEvent;

public abstract class AbstractManager {
    private static final String TAG = AbstractManager.class.getSimpleName();
    protected Context context;
    protected final AtomicBoolean isConnected = new AtomicBoolean(false);

    public AbstractManager(Context context) {
        this.context = context;
    }

    protected boolean isHandShakingMessage(String message) {
        boolean isHandShakingMessage = false;
        if (message != null) {
            String handShakeMessage = "\"mt\":\"handshaking\"";
            isHandShakingMessage = message.contains(handShakeMessage);
        }
        return isHandShakingMessage;
    }

    protected boolean isSyncInfoMessage(String message) {
        boolean isSyncInfoMessage = false;
        if (message != null) {
            String syncInfoMessage = "\"mt\":\"syncInfoMessage\"";
            isSyncInfoMessage = message.contains(syncInfoMessage);
        }
        return isSyncInfoMessage;
    }

    protected boolean isGroupNonRepeatMessage(String message) {
        boolean isGroupNonRepeatMessage = false;
        if (message != null) {
            String groupMessageMatch = "\"mt\":\"groupMessage\"";
            isGroupNonRepeatMessage = message.contains(groupMessageMatch);
        }
        return isGroupNonRepeatMessage;
    }

    protected boolean isSyncRequestMessage(String message) {
        String messageType = "\"mt\":\"syncInfoRequestMessage\"";
        return message != null && message.contains(messageType);
    }


    public void notifyUI(String message, String fromIP, String type) {
        final String consoleMessage = "[" + fromIP + "]: " + message + "\n";
        Intent intent = new Intent(uiMessageEvent);
        intent.putExtra("message", consoleMessage);
        intent.putExtra("type", type);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, "notify : " + consoleMessage);
    }

    public abstract void processInComingMessage(final String message, final String fromIP);
}