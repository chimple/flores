package org.chimple.flores.sync.Direct;

import android.app.job.JobParameters;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApi;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.sync.P2PStateFlow;
import org.chimple.flores.sync.SyncUtils;
import org.chimple.flores.sync.sender.CommunicationCallBack;
import org.chimple.flores.sync.sender.CommunicationThread;
import org.chimple.flores.sync.sender.ConnectToThread;
import org.chimple.flores.sync.sender.ConnectedThread;

import static org.chimple.flores.scheduler.P2PHandShakingJobService.JOB_PARAMS;
import static org.chimple.flores.scheduler.P2PHandShakingJobService.P2P_SYNC_RESULT_RECEIVED;
import static org.chimple.flores.sync.Direct.P2POrchester.allMessageExchangedForP2P;
import static org.chimple.flores.sync.Direct.P2POrchester.neighboursUpdateEvent;

public class P2PSyncManager implements P2POrchesterCallBack, CommunicationCallBack, Handler.Callback {
    private static final String TAG = P2PSyncManager.class.getSimpleName();
    private Context context;
    private static P2PSyncManager instance;
    private CountDownTimer shutDownJobTimer;
    private String clientIPAddressToConnect = null;
    private P2POrchester mWDConnector = null;
    private CommunicationThread mTestListenerThread = null;
    private ConnectToThread mTestConnectToThread = null;
    private ConnectedThread mTestConnectedThread = null;
    final private int TestChatPortNumber = 8768;
    private Handler mHandler;
    private HandlerThread handlerThread;
    private P2PStateFlow p2PStateFlow;
    private DBSyncManager dbSyncManager;
    private boolean shouldInitiate;
    //Status
    private int mInterval = 1000; // 1 second by default, can be changed later
    private int timeCounter = 0;
    private int totalTimeTillJobStarted = 0;
    private boolean exitTimerStarted = false;
    Runnable mStatusChecker = null;

    private JobParameters currentJobParams;

    private Map<String, P2PSyncService> neighbours = null;

    //    public static final String profileFileExtension = ".txt";
    public static final String profileFileExtension = ".jpg";
    public static final String customStatusUpdateEvent = "custom-status-update-event";
    public static final String customTimerStatusUpdateEvent = "custom-timer-status-update-event";
    public static final String connectedDevice = "CONNECTED_DEVICE";
    public static final String P2P_SHARED_PREF = "p2pShardPref";
    public static final int EXIT_CURRENT_JOB_TIME = 4 * 60; // 4 mins

    public static P2PSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (P2PSyncManager.class) {
                instance = new P2PSyncManager(context);
            }
        }

        return instance;
    }

    private P2PSyncManager(Context context) {
        this.context = context;
        this.handlerThread = new HandlerThread("P2PSyncManager");
        this.handlerThread.start();
        this.mHandler = new Handler(this.handlerThread.getLooper(), this);
        dbSyncManager = DBSyncManager.getInstance(this.context);
        this.p2PStateFlow = P2PStateFlow.getInstanceUsingDoubleLocking(dbSyncManager);

        LocalBroadcastManager.getInstance(this.context).registerReceiver(mMessageReceiver, new IntentFilter(neighboursUpdateEvent));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(p2pAllMessageExchangedReceiver, new IntentFilter(allMessageExchangedForP2P));

        this.initStatusChecker();
    }

    private void initStatusChecker() {
        mStatusChecker = new Runnable() {
            @Override
            public void run() {
                // call function to update timer
                timeCounter = timeCounter + 1;
                totalTimeTillJobStarted = totalTimeTillJobStarted + 1;
//                Log.i(TAG, "totalTimeTillJobStarted" + totalTimeTillJobStarted + " Time left:" + (EXIT_CURRENT_JOB_TIME - totalTimeTillJobStarted));
                if (!exitTimerStarted) {
                    if (instance != null) {
                        instance.broadcastCustomTimerStatusUpdateEvent();
                        instance.mHandler.postDelayed(mStatusChecker, mInterval);
                    }
                }

                if (totalTimeTillJobStarted > EXIT_CURRENT_JOB_TIME && !exitTimerStarted) {
                    instance.startExitTimer();
                }
            }

        };
    }

    private void broadcastCustomTimerStatusUpdateEvent() {
        Intent intent = new Intent(customTimerStatusUpdateEvent);
//        Log.i(TAG, "totalTimeTillJobStarted" + totalTimeTillJobStarted + " Time left:" + (EXIT_CURRENT_JOB_TIME - totalTimeTillJobStarted));
        intent.putExtra("timeCounter", "T:" + this.timeCounter + " Exit In :" + (EXIT_CURRENT_JOB_TIME - totalTimeTillJobStarted));
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            synchronized (this) {
                neighbours = (HashMap<String, P2PSyncService>) intent.getSerializableExtra("neighbours");
                for (Map.Entry<String, P2PSyncService> entry : neighbours.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        Log.i(TAG, "got neighbour: " + entry.getKey() + " : " + ((P2PSyncService) entry.getValue()).print());
                    }
                }
            }
        }
    };

    private BroadcastReceiver p2pAllMessageExchangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            synchronized (this) {
                P2PDBApi api = P2PDBApiImpl.getInstance(instance.getContext());
                String deviceId = instance.fetchFromSharedPreference(P2PSyncManager.connectedDevice);
                if (deviceId != null) {
                    api.syncCompleted(deviceId);
                }
                Log.i(TAG, ".... calling removeClientIPAddressToConnect ....");
                instance.removeClientIPAddressToConnect();
                Log.i(TAG, ".... calling startExitTimer....");
                instance.resetExitTimer();
                instance.startExitTimer();

            }
        }
    };


    public Map<String, P2PSyncService> getNeighbours() {
        return this.neighbours;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case ConnectedThread.MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;// construct a string from the buffer
                String writeMessage = new String(writeBuf);
                updateStatus(TAG, "Wrote: " + writeMessage);
                break;
            case ConnectedThread.MESSAGE_READ:
                synchronized (P2PSyncManager.class) {
                    byte[] readBuf = (byte[]) msg.obj;// construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.i(TAG, "MESSAGE READ:" + readMessage);
                    this.p2PStateFlow.processMessages(readMessage);
                }
                break;
            case ConnectedThread.SOCKET_DISCONNEDTED: {
                updateStatus(TAG + "CHAT", "WE are Stopped now.");
                stopConnectedThread();
            }
            break;
        }
        return true;
    }

    public void execute(final JobParameters currentJobParams) {
        this.currentJobParams = currentJobParams;
        mStatusChecker.run();
        //changing the time and its interval
        //Start Init

        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            wifiManager.setWifiEnabled(false);
        }

        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
            wifiManager.setWifiEnabled(true);
        }


//        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
//        if (!wifiManager.isWifiEnabled()) {
//            wifiManager.setWifiEnabled(false);
//        }
//

        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        if (list != null) {
            for (WifiConfiguration i : list) {
                wifiManager.removeNetwork(i.networkId);
                wifiManager.saveConfiguration();
            }

        }

        StartConnector();
    }

    public void startExitTimer() {
        synchronized (this) {
            shutDownJobTimer = new CountDownTimer(5000, 1000) {
                public void onTick(long millisUntilFinished) {
                    Log.i(TAG, "shutDownJobTimer ticking.....");
                }

                public void onFinish() {
                    Log.i(TAG, "SHUTTING DOWN CURRENT JOB for P2P");
                    Intent result = new Intent(P2P_SYNC_RESULT_RECEIVED);
                    result.putExtra(JOB_PARAMS, currentJobParams);
                    LocalBroadcastManager.getInstance(instance.context).sendBroadcast(result);
                }
            };

            Log.i(TAG, "...... checking if exitTimerStarted ....." + exitTimerStarted);
            if (!exitTimerStarted) {
                exitTimerStarted = true;
                Log.i(TAG, "...... exitTimerStarted .....");
                shutDownJobTimer.start();
                Log.i(TAG, "Exit time reached ... starting shutDownJobTimer");
            }
        }
    }

    public void StartConnector() {
        //lets be ready for incoming test communications
        updateStatus(TAG, "starting listener now, and connector");
        startListenerThread();
        mWDConnector = new P2POrchester(this.context, this, this.mHandler);
    }

    public void stopConnector() {
        stopConnectedThread();
        stopConnectToThread();
        stopListenerThread();

        if (mWDConnector != null) {
            mWDConnector.cleanUp();
            mWDConnector = null;
        }

        updateStatus(TAG, "Stopped");
    }

    public Context getContext() {
        return this.context;
    }

    private void startListenerThread() {
        stopListenerThread();
        mTestListenerThread = new CommunicationThread(this, TestChatPortNumber);
        mTestListenerThread.start();
    }

    private void stopListenerThread() {
        if (mTestListenerThread != null) {
            mTestListenerThread.Stop();
            mTestListenerThread = null;
        }
    }

    private void stopConnectToThread() {
        if (mTestConnectToThread != null) {
            mTestConnectToThread.Stop();
            mTestConnectToThread = null;
        }
    }


    private void stopConnectedThread() {
        if (mTestConnectedThread != null) {
            Log.i(TAG, "Stopping stopConnectedThread....");
            mTestConnectedThread.Stop();
            mTestConnectedThread = null;
        }
    }

    public void connectToClient() {
        stopConnectedThread();
        stopConnectToThread();
        if (instance.shutDownJobTimer != null) {
            instance.shutDownJobTimer.cancel();
        }

        if (clientIPAddressToConnect != null) {
            //With this test we'll just handle each client one-by-one in order they got connected
            String connectToAddress = clientIPAddressToConnect;
            clientIPAddressToConnect = null;
            updateStatus("Connecting state", "Will connect to " + connectToAddress);
            Log.i(TAG, "Connecting state" + "Will connect to " + connectToAddress);
            mTestConnectToThread = new ConnectToThread(this, connectToAddress, TestChatPortNumber);
            mTestConnectToThread.start();
        } else {
            updateStatus("Data state", "All addresses connected, will start exit timer now.");
            // lets just see if we get more connections coming in before the timeout comes
            Log.i(TAG, "Data state" + "All addresses connected, will start exit timer now.");
            this.resetExitTimer();
            this.startExitTimer();
        }
    }

    private void startTestConnection(Socket socket, final boolean shouldInitiate) {
        Log.i(TAG, "Initial Connection established with shouldInitiate:" + shouldInitiate);
        instance.mTestConnectedThread = new ConnectedThread(socket, mHandler, context);
        instance.mTestConnectedThread.start();
        instance.shouldInitiate = shouldInitiate;
        Log.i(TAG, "Initial Connection established - mTestConnectedThread initialized:" + (mTestConnectedThread != null));
        instance.p2PStateFlow.setConnectedThread(mTestConnectedThread);
        if (instance.shouldInitiate) {
            instance.p2PStateFlow.transit(P2PStateFlow.Transition.SEND_HANDSHAKING_INFORMATION, null);
        }
    }

    @Override
    public void Connected(Socket socket) {
        Log.i(TAG, "Connected to ");
        final Socket socketTmp = socket;
        mTestConnectToThread = null;
        this.p2PStateFlow.resetAllStates();
        startTestConnection(socketTmp, true);
    }

    @Override
    public void GotConnection(Socket socket) {
        Log.i(TAG, "We got incoming connection");
        final Socket socketTmp = socket;
        startListenerThread();
        mTestConnectToThread = null;
        this.p2PStateFlow.resetAllStates();
        startTestConnection(socketTmp, false);
    }

    @Override
    public void ConnectionFailed(String reason) {
        connectToClient();
    }

    @Override
    public void ListeningFailed(String reason) {
        startListenerThread();
    }

    @Override
    public void Connected(String address, boolean isGroupOwner) {
        if (isGroupOwner) {
            clientIPAddressToConnect = address;
            updateStatus("Connectec", "Connected From remote host: " + address + ", CTread : " + mTestConnectedThread + ", CtoTread: " + mTestConnectToThread);
            Log.i(TAG, "Connectec" + "Connected From remote host: " + address + ", CTread : " + mTestConnectedThread + ", CtoTread: " + mTestConnectToThread);
            if (mTestConnectedThread == null && mTestConnectToThread == null) {
                Log.i(TAG, "CONNECT TO:" + clientIPAddressToConnect);
                connectToClient();
            }
        } else {
            updateStatus("Connectec", "Connected to remote host: " + address);
            Log.i(TAG, "Connectec" + "Connected to remote host: " + address);
        }
    }

    @Override
    public void GroupInfoChanged(WifiP2pGroup group) {
        // updateStatus("GroupInfoChanged:", "group: " + group.getOwner());
    }

    @Override
    public void ConnectionStateChanged(SyncUtils.ConnectionState newState) {
        updateStatus("ConnectionStateChanged:", "New state: " + newState);
    }

    @Override
    public void ListeningStateChanged(SyncUtils.ReportingState newState) {
        updateStatus("ListeningStateChanged", "New state: " + newState);
    }


    public void updateStatus(String who, String line) {
        final String logWho = who;
        final String status = line;
        this.broadcastCustomStatusUpdateEvent(who, line);
    }

    private void broadcastCustomStatusUpdateEvent(String who, String line) {
        Log.d(TAG, "Broadcasting message customStatusUpdateEvent");
        this.timeCounter = 0;
        Intent intent = new Intent(customStatusUpdateEvent);
        // You can also include some extra data.
        intent.putExtra("who", who);
        intent.putExtra("line", line);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    public void onDestroy() {
        Log.i(TAG, "in P2P Destroy");

        LocalBroadcastManager.getInstance(this.context).unregisterReceiver(mMessageReceiver);
        LocalBroadcastManager.getInstance(this.context).unregisterReceiver(p2pAllMessageExchangedReceiver);
        if (this.shutDownJobTimer != null) {
            this.shutDownJobTimer.cancel();
        }
        this.stopConnector();
        this.mStatusChecker = null;
        P2PSyncManager.instance = null;
        updateStatus(TAG, "onDestroy");
    }

    // Manage photo
    public static String createProfilePhoto(String generateUserId, byte[] contents, Context context) {
        Boolean canWrite = false;
        String fileName = null;
        File pathDir = context.getExternalFilesDir(null);
        if (null == pathDir) {
            pathDir = context.getFilesDir();
        }

        canWrite = pathDir.canWrite();

        if (canWrite) {
            fileName = P2PSyncManager.generateUserPhotoFileName(generateUserId);
            File file = new File(pathDir + "/P2P_IMAGES", fileName);
            try {
                // Make sure the Pictures directory exists.
                if (!checkIfFileExists(fileName, context)) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                OutputStream os = new FileOutputStream(file);
                os.write(contents);
                os.close();
                P2PSyncManager.getInstance(context).updateInSharedPreference("PROFILE_PHOTO", generateUserId);
            } catch (IOException e) {
                // Unable to create file, likely because external storage is
                // not currently mounted.
                Log.w("ExternalStorage", "Error writing " + file, e);
            }

        } else {
            Log.i(TAG, "could not write to external storage");
        }
        return fileName;
    }

    public static String encodeFileToBase64Binary(String fileName) {
        String encodedString = null;
        try {
            File file = new File(fileName);
            byte[] bytes = loadFile(file);
            byte[] encoded = Base64.encode(bytes, Base64.DEFAULT);
            encodedString = new String(encoded);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedString;
    }

    private static byte[] loadFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
        byte[] bytes = new byte[(int) length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        if (offset < bytes.length) {
            throw new IOException("Could not completely read file " + file.getName());
        }

        is.close();
        return bytes;
    }

    public static String getStringFile(File f) {
        InputStream inputStream = null;
        String encodedFile = "", lastVal;
        try {
            inputStream = new FileInputStream(f.getAbsolutePath());

            byte[] buffer = new byte[10240];//specify the size to allow
            int bytesRead;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Base64OutputStream output64 = new Base64OutputStream(output, Base64.DEFAULT);

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output64.write(buffer, 0, bytesRead);
            }
            output64.close();
            encodedFile = output.toString();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        lastVal = encodedFile;
        return lastVal;
    }

    public static String getProfilePhotoContents(String fileName, Context context) {
        String result = "";
        File pathDir = context.getExternalFilesDir(null);
        if (null == pathDir) {
            pathDir = context.getFilesDir();
        }
        try {
            File file = new File(pathDir + "/P2P_IMAGES", fileName);
            Log.i(TAG, " fileSize : " + file.length());
            result = P2PSyncManager.getStringFile(file);
            Log.i(TAG, "FinalResult : " + result);
        } catch (Exception e) {
            Log.i(TAG, e.getMessage());
        }
        return result;
    }


    public static boolean checkIfFileExists(String fileName, Context context) {
        File pathDir = context.getExternalFilesDir(null);
        if (null == pathDir) {
            pathDir = context.getFilesDir();
        }
        File file = new File(pathDir, fileName);
        return file.exists();
    }


    public static String generateUserPhotoFileName(String userId) {
        return "profile-" + userId + profileFileExtension;
    }

    public void updateInSharedPreference(String key, String value) {
        // update shared preferences
        SharedPreferences pref = context.getSharedPreferences(P2P_SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        Log.i(TAG, "Storing value into Shared Preference for key:" + key + ", got value:" + value);
        editor.putString(key, value);
        editor.commit(); // commit changes
    }

    public String fetchFromSharedPreference(String key) {
        // update shared preferences
        SharedPreferences pref = context.getSharedPreferences(P2P_SHARED_PREF, 0); // 0 - for private mode
        String value = pref.getString(key, null); // getting String
        Log.i(TAG, "Fetched value from Shared Preference for key:" + key + ", got value:" + value);
        return value;
    }

    public void removeClientIPAddressToConnect() {
        this.clientIPAddressToConnect = null;
    }

    public void resetExitTimer() {
        if (exitTimerStarted == true) {
            this.exitTimerStarted = false;
        }
    }

}
