package org.chimple.flores.db;

import android.content.Context;
import android.util.Log;

import java.util.List;

import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdDeviceIdAndMessage;
import org.chimple.flores.db.entity.P2PUserIdMessage;

public class DBSyncManager {

    private static final String TAG = DBSyncManager.class.getSimpleName();
    private Context context;
    private static DBSyncManager instance;

    public enum MessageTypes {
        PHOTO("Photo"),
        CHAT("Chat"),
        GAME("Game");

        private String type;

        MessageTypes(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }
    }


    public static DBSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DBSyncManager.class) {
                instance = new DBSyncManager(context);
            }
        }

        return instance;
    }

    private DBSyncManager(Context context) {
        this.context = context;
    }


    public Context getContext() {
        return this.context;
    }


    public boolean addMessage(String userId, String recipientId, String messageType, String message, Boolean status, String sessionId) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.addMessage(userId, recipientId, messageType, message, status, sessionId);
    }


    public boolean addMessage(String userId, String recipientId, String messageType, String message) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.addMessage(userId, recipientId, messageType, message);
    }

    public List<P2PUserIdDeviceIdAndMessage> getUsers() {
        Log.i(TAG, "Called getUsers");

        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.getUsers();
    }

    public List<P2PUserIdMessage> fetchLatestMessagesByMessageType(String messageType, List<String> userIds) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.fetchLatestMessagesByMessageType(messageType, userIds);
    }

    public List<P2PSyncInfo> getConversations(String firstUserId, String secondUserId, String messageType) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.getConversations(firstUserId, secondUserId, messageType);
    }

    public List<P2PSyncInfo> getLatestConversations(String firstUserId, String secondUserId, String messageType) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.getLatestConversations(firstUserId, secondUserId, messageType);
    }

    public boolean upsertUser(String userId, String deviceId, String fileName) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.upsertProfileForUserIdAndDevice(userId, deviceId, fileName);
    }

    public List<P2PSyncInfo> getLatestConversationsByUser(String firstUserId) {
        P2PDBApi p2pdbapi = P2PDBApiImpl.getInstance(DBSyncManager.instance.context);
        return p2pdbapi.getLatestConversationsByUser(firstUserId);
    }
}
