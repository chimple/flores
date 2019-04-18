package org.chimple.flores.nearby;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import org.chimple.flores.AbstractManager;
import org.chimple.flores.application.P2PContext;
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.chimple.flores.application.P2PContext.CLEAR_CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.NEW_GROUP_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.NEW_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.bluetoothMessageEvent;
import static org.chimple.flores.application.P2PContext.newGroupMessageAddedOnDevice;
import static org.chimple.flores.application.P2PContext.newMessageAddedOnDevice;
import static org.chimple.flores.application.P2PContext.refreshDevice;

public class NearByManager extends AbstractManager implements NearbyInfo {

    private static final String TAG = NearByManager.class.getSimpleName();
    private Context context;
    private static NearByManager instance;
    private P2PDBApiImpl p2PDBApiImpl;
    private DBSyncManager dbSyncManager;
    private Map<String, HandShakingMessage> handShakingMessagesInCurrentLoop = new ConcurrentHashMap<>();
    private Set<String> allSyncInfosReceived = new HashSet<String>();
    private BluetoothAdapter mAdapter;
    private NearbyHelper nearbyHelper;
    private boolean isTeacher = false;

    public static NearByManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NearByManager.class) {
                Log.d(TAG, "NearByManager initialize");
                instance = new NearByManager(context);
                instance.mAdapter = BluetoothAdapter.getDefaultAdapter();
                instance.dbSyncManager = DBSyncManager.getInstance(context);
                instance.p2PDBApiImpl = P2PDBApiImpl.getInstance(context);
                instance.nearbyHelper = NearbyHelper.getInstance(instance, context);
                instance.registerReceivers();
                instance.nearbyHelper.setBluetooth(true);
            }
        }
        return instance;
    }

    private NearByManager(Context context) {
        super(context);
        this.context = context;
    }


    private void registerReceivers() {
        LocalBroadcastManager.getInstance(instance.context).registerReceiver(mMessageEventReceiver, new IntentFilter(bluetoothMessageEvent));
        LocalBroadcastManager.getInstance(instance.context).registerReceiver(newMessageAddedReceiver, new IntentFilter(newMessageAddedOnDevice));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(newGroupMessageAddedReceiver, new IntentFilter(newGroupMessageAddedOnDevice));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(refreshDeviceReceiver, new IntentFilter(refreshDevice));
    }

    private void unRegisterReceivers() {
        if (newMessageAddedReceiver != null) {
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(newMessageAddedReceiver);
        }

        if (newGroupMessageAddedReceiver != null) {
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(newGroupMessageAddedReceiver);
        }


        if (refreshDeviceReceiver != null) {
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(refreshDeviceReceiver);
        }

        if (mMessageEventReceiver != null) {
            LocalBroadcastManager.getInstance(instance.context).unregisterReceiver(mMessageEventReceiver);
        }
    }

    private final BroadcastReceiver mMessageEventReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            String fromIP = intent.getStringExtra("fromIP");
            processInComingMessage(message, fromIP);
        }
    };

    private final BroadcastReceiver newMessageAddedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            P2PSyncInfo info = (P2PSyncInfo) intent.getSerializableExtra(NEW_MESSAGE_ADDED);
            if (info != null) {
                String syncMessage = p2PDBApiImpl.convertSingleP2PSyncInfoToJsonUsingStreaming(info);
                instance.sendMulticastMessage(syncMessage);
            }
        }
    };

    private final BroadcastReceiver newGroupMessageAddedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            P2PSyncInfo info = (P2PSyncInfo) intent.getSerializableExtra(NEW_GROUP_MESSAGE_ADDED);
            if (info != null) {
                String syncMessage = p2PDBApiImpl.convertSingleGroupSyncInfoToJsonUsingStreaming(info);
                instance.sendMulticastMessage(syncMessage);
            }
        }
    };

    private void sendMulticastMessage(String message) {
        notifyUI("sending text message:" + message, "--------->", CONSOLE_TYPE);
        Payload bytesPayload = Payload.fromBytes(message.getBytes());
        instance.nearbyHelper.sendToAllConnected(bytesPayload);
    }

    private final BroadcastReceiver refreshDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            synchronized (NearByManager.class) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyUI("Clear ALL...", " ------> ", CLEAR_CONSOLE_TYPE);
                        List<P2PSyncInfo> allInfos = new ArrayList<P2PSyncInfo>();
                        try {
                            allInfos = p2PDBApiImpl.refreshAllMessages();
                            if (allInfos != null) {
                                Iterator<P2PSyncInfo> allInfosIt = allInfos.iterator();
                                while (allInfosIt.hasNext()) {
                                    P2PSyncInfo p = allInfosIt.next();
                                    instance.getAllSyncInfosReceived().add(p.getDeviceId() + "_" + p.getUserId() + "_" + Long.valueOf(p.getSequence().longValue()));
                                    String sender = p.getSender().equals(P2PContext.getCurrentDevice()) ? "You" : p.getSender();
                                    notifyUI(p.message, sender, CONSOLE_TYPE);
                                }
                            }
                            //Log.d(TAG, "rebuild sync info received cache and updated UI");
                        } catch (Exception e) {

                        }
                    }

                });
            }
        }
    };


    public void updateNetworkConnected(boolean connected) {
        synchronized (NearByManager.class) {
            instance.isConnected.set(connected);

            if (instance.isConnected.get()) {
                instance.onStop();
            } else {
                instance.nearbyHelper.startNearbyActivity(instance.isTeacher);
            }
        }
    }

    public BluetoothAdapter getmAdapter() {
        return mAdapter;
    }


    @Override
    public void processInComingMessage(String message, String fromIP) {

    }

    public String getBluetoothMacAddress() {
        String bluetoothMacAddress = null;
        try {
            BluetoothAdapter bluetoothAdapter = instance.getmAdapter();
            if (bluetoothAdapter != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        Field mServiceField = bluetoothAdapter.getClass().getDeclaredField("mService");
                        mServiceField.setAccessible(true);

                        Object btManagerService = mServiceField.get(bluetoothAdapter);

                        if (btManagerService != null) {
                            bluetoothMacAddress = (String) btManagerService.getClass().getMethod("getAddress").invoke(btManagerService);
                            //Log.d(TAG, "inside getBluetoothMacAddress 222: " + bluetoothMacAddress);
                        }
                    } catch (NoSuchFieldException e) {

                    } catch (NoSuchMethodException e) {

                    } catch (IllegalAccessException e) {

                    } catch (InvocationTargetException e) {

                    }
                } else {
                    bluetoothMacAddress = bluetoothAdapter.getAddress();
                    //Log.d(TAG, "inside getBluetoothMacAddress 222: " + bluetoothMacAddress);
                }
                return bluetoothMacAddress;
            } else {
                return bluetoothMacAddress;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return bluetoothMacAddress;
        }
    }

    public Set<String> getAllSyncInfosReceived() {
        return allSyncInfosReceived;
    }


    public void onStop() {
        instance.nearbyHelper.stopAdvertising();
        instance.nearbyHelper.stopDiscovering();
        instance.nearbyHelper.disconnectFromAllEndpoints();
        instance.nearbyHelper.stopAllEndpoints();
    }

    public void onCleanUp() {
        instance.unRegisterReceivers();
        instance.onStop();
        instance.nearbyHelper = null;
        instance = null;
    }


    @Override
    public void onAdvertisingStarted(String name) {
        notifyUI("onAdvertisingStarted " + name, "-------->", CONSOLE_TYPE);
    }

    @Override
    public void onAdvertisingFailed() {
        notifyUI("onAdvertisingStarted ", "-------->", CONSOLE_TYPE);
    }

    @Override
    public void onConnectionInitiated(EndPoint endpoint, ConnectionInfo connectionInfo) {
        instance.nearbyHelper.acceptConnection(endpoint);
        notifyUI("onConnectionInitiated " + endpoint.getName(), "-------->", CONSOLE_TYPE);
    }

    @Override
    public Strategy getStrategy() {
        return Strategy.P2P_CLUSTER;
    }

    @Override
    public void onDiscoveryStarted() {
        notifyUI("onDiscoveryStarted ", "-------->", CONSOLE_TYPE);
    }

    @Override
    public void onDiscoveryFailed() {
        notifyUI("onDiscoveryFailed ", "-------->", CONSOLE_TYPE);
    }

    @Override
    public void onEndpointDiscovered(EndPoint endpoint) {
        instance.nearbyHelper.logD("onEndpointDiscovered id:" + endpoint.getId() + " name:" + endpoint.getName());
        notifyUI("onEndpointDiscovered id:" + endpoint.getId() + " name:" + endpoint.getName(), " --------->", CONSOLE_TYPE);
    }

    @Override
    public void onConnectionFailed(EndPoint endpoint, int numberOfTimes) {
        notifyUI("onConnectionFailed id:" + endpoint.getId() + " name:" + endpoint.getName(), " --------->", CONSOLE_TYPE);
        instance.nearbyHelper.resetOnConnectionFailed();
    }

    @Override
    public void onEndpointConnected(EndPoint endpoint) {
        notifyUI("*******onEndpointConnected********* id:" + endpoint.getId() + " name:" + endpoint.getName(), " --------->", CONSOLE_TYPE);
    }

    @Override
    public void onEndpointDisconnected(EndPoint endpoint) {
        notifyUI("********onEndpointDisconnected******  id:" + endpoint.getId() + " name:" + endpoint.getName(), " --------->", CONSOLE_TYPE);
    }

    @Override
    public void notifyMessage(String message) {
        notifyUI("********notifyMessage******  :" + message, " --------->", CONSOLE_TYPE);
    }

    @Override
    public void onReceive(EndPoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.BYTES) {
            handleBytesPayload(endpoint, payload);
        }
        // send payload to all connected addresses
        Set<EndPoint> allEstablishedConnections = instance.nearbyHelper.getConnectedEndpoints();
        allEstablishedConnections.remove(endpoint);
        sendMulticastMessageToEndPoints(allEstablishedConnections, payload);


    }

    @Override
    public void onStopAdvertising() {
        notifyUI("onStopAdvertising id:", " --------->", CONSOLE_TYPE);
    }

    private void sendMulticastMessageToEndPoints(Set<EndPoint> endpoints, Payload bytesPayload) {
        Set<String> endPointIds = new TreeSet<String>();
        Iterator<EndPoint> it = endpoints.iterator();
        while (it.hasNext()) {
            EndPoint e = it.next();
            endPointIds.add(e.getId());
        }
        instance.nearbyHelper.send(bytesPayload, endPointIds);
    }

    public void handleBytesPayload(EndPoint endPoint, Payload payload) {
        String result = new String(payload.asBytes());
        notifyUI("message received from " + endPoint.getName() + " with content :" + result, "-------> ", CONSOLE_TYPE);
    }

    public void setTeacher(boolean b) {
        this.isTeacher = b;
    }
}

