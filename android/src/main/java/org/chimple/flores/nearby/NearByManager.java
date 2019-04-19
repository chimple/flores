package org.chimple.flores.nearby;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.chimple.flores.AbstractManager;
import org.chimple.flores.application.P2PContext;
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.SyncInfoItem;
import org.chimple.flores.db.entity.SyncInfoRequestMessage;
import org.chimple.flores.manager.MessageStatus;
import org.chimple.flores.multicast.MulticastManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.chimple.flores.application.P2PContext.CLEAR_CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.LOG_TYPE;
import static org.chimple.flores.application.P2PContext.NEW_GROUP_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.NEW_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PContext.bluetoothMessageEvent;
import static org.chimple.flores.application.P2PContext.newGroupMessageAddedOnDevice;
import static org.chimple.flores.application.P2PContext.newMessageAddedOnDevice;
import static org.chimple.flores.application.P2PContext.refreshDevice;
import static org.chimple.flores.db.AppDatabase.SYNC_NUMBER_OF_LAST_MESSAGES;

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
    private Handler mHandler = null;
    private CountDownTimer repeatHandShakeTimer = null;
    private static final int REPEAT_HANDSHAKE_TIMER = 1 * 60 * 1000; // 1 min

    public static NearByManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NearByManager.class) {
                Log.d(TAG, "NearByManager initialize");
                instance = new NearByManager(context);
                instance.mHandler = new Handler(context.getMainLooper());
                instance.mAdapter = BluetoothAdapter.getDefaultAdapter();
                instance.dbSyncManager = DBSyncManager.getInstance(context);
                instance.p2PDBApiImpl = P2PDBApiImpl.getInstance(context);
                instance.nearbyHelper = NearbyHelper.getInstance(instance, context);
                instance.registerReceivers();
                instance.nearbyHelper.setBluetooth(true);
                instance.createRepeatHandShakeTimer();
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
        if (message != null) {
            notifyUI("sending text message:" + message, "--------->", CONSOLE_TYPE);
            Payload bytesPayload = Payload.fromBytes(message.getBytes());
            instance.nearbyHelper.sendToAllConnected(bytesPayload);
        }
    }

    private boolean isBluetoothSyncCompleteMessage(String message) {
        boolean isSyncCompletedMessage = false;
        if (message != null && message.equalsIgnoreCase("BLUETOOTH-SYNC-COMPLETED")) {
            isSyncCompletedMessage = true;
        }
        return isSyncCompletedMessage;
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


    private Set<HandShakingInfo> sortHandShakingInfos(final Map<String, HandShakingMessage> messages) {
        final Set<HandShakingInfo> allHandShakingInfos = new TreeSet<HandShakingInfo>(new Comparator<HandShakingInfo>() {
            @Override
            public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                if (o1.getDeviceId().equalsIgnoreCase(o2.getDeviceId())) {
                    if (o1.getSequence().longValue() > o2.getSequence().longValue()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
                return o1.getDeviceId().compareToIgnoreCase(o2.getDeviceId());
            }
        });

        Iterator<Map.Entry<String, HandShakingMessage>> entries = messages.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, HandShakingMessage> entry = entries.next();
            Iterator<HandShakingInfo> it = entry.getValue().getInfos().iterator();
            while (it.hasNext()) {
                HandShakingInfo i = it.next();
                i.setFrom(entry.getKey());
            }

            allHandShakingInfos.addAll(entry.getValue().getInfos());
        }
        return allHandShakingInfos;
    }

    private MessageStatus validIncomingSyncMessage(P2PSyncInfo info, MessageStatus status) {
        // DON'T reject out of order message, send handshaking request for only missing data
        // reject duplicate messages if any
        boolean isValid = true;
        String iKey = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
        String iPreviousKey = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue() - 1);
        //Log.d(TAG, "validIncomingSyncMessage previousKey" + iPreviousKey);
        // remove duplicates
        if (allSyncInfosReceived.contains(iKey)) {
            //Log.d(TAG, "sync data message as key already found" + iKey);
            status.setDuplicateMessage(true);
            status.setOutOfSyncMessage(false);
            isValid = false;
        } else if ((info.getSequence().longValue() - 1) != 0
                && !allSyncInfosReceived.contains(iPreviousKey)) {
            //Log.d(TAG, "found sync data message as out of sequence => previous key not found " + iPreviousKey + " for key:" + iKey);
            isValid = false;
            status.setDuplicateMessage(false);
            status.setOutOfSyncMessage(true);
        }

        if (isValid) {
            //Log.d(TAG, "validIncomingSyncMessage adding to allSyncInfosReceived for key:" + iKey);
            allSyncInfosReceived.add(iKey);
        }

        return status;
    }

    private Collection<HandShakingInfo> computeSyncInfoRequired(final Map<String, HandShakingMessage> messages) {
        // sort by device id and sequence desc order
        synchronized (NearByManager.class) {
            final Set<HandShakingInfo> allHandShakingInfos = sortHandShakingInfos(messages);
            Iterator<HandShakingInfo> itReceived = allHandShakingInfos.iterator();
            final Map<String, HandShakingInfo> uniqueHandShakeInfosReceived = new ConcurrentHashMap<String, HandShakingInfo>();
            final Map<String, HandShakingInfo> photoProfileUpdateInfosReceived = new ConcurrentHashMap<String, HandShakingInfo>();

            while (itReceived.hasNext()) {
                HandShakingInfo info = itReceived.next();
                HandShakingInfo existingInfo = uniqueHandShakeInfosReceived.get(info.getUserId());
                if (existingInfo == null) {
                    uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                } else {
                    if (existingInfo.getSequence().longValue() < info.getSequence().longValue()) {
                        uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                    } else if (existingInfo.getSequence().longValue() == info.getSequence().longValue()) {

                        String myMissingMessageSequences = existingInfo.getMissingMessages();
                        String otherDeviceMissingMessageSequences = info.getMissingMessages();
                        List<String> list1 = new ArrayList<String>();
                        List<String> list2 = new ArrayList<String>();
                        if (myMissingMessageSequences != null) {
                            list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                        }
                        if (otherDeviceMissingMessageSequences != null) {
                            list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                        }
                        if (list1.size() > list2.size()) {
                            uniqueHandShakeInfosReceived.put(info.getUserId(), info);
                        }
                    }
                }
            }

            final Map<String, HandShakingInfo> myHandShakingMessages = p2PDBApiImpl.handShakingInformationFromCurrentDevice();

            Iterator<String> keys = uniqueHandShakeInfosReceived.keySet().iterator();
            while (keys.hasNext()) {
                String userKey = keys.next();
                //Log.d(TAG, "computeSyncInfoRequired user key:" + userKey);
                if (myHandShakingMessages.keySet().contains(userKey)) {
                    HandShakingInfo infoFromOtherDevice = uniqueHandShakeInfosReceived.get(userKey);
                    HandShakingInfo infoFromMyDevice = myHandShakingMessages.get(userKey);
                    if (infoFromOtherDevice != null && infoFromMyDevice != null) {

                        Long latestProfilePhotoInfo = infoFromOtherDevice.getProfileSequence();
                        Long latestUserProfileId = p2PDBApiImpl.findLatestProfilePhotoId(infoFromOtherDevice.getUserId(), infoFromOtherDevice.getDeviceId());

                        if (latestUserProfileId != null && latestUserProfileId != null
                                && latestUserProfileId.longValue() < latestProfilePhotoInfo.longValue()) {
                            photoProfileUpdateInfosReceived.put(infoFromOtherDevice.getUserId(), infoFromOtherDevice);
                        }

                        final long askedThreshold = infoFromMyDevice.getSequence().longValue() > SYNC_NUMBER_OF_LAST_MESSAGES ? infoFromMyDevice.getSequence().longValue() + 1 - SYNC_NUMBER_OF_LAST_MESSAGES : -1;
                        if (infoFromMyDevice.getSequence().longValue() > infoFromOtherDevice.getSequence().longValue()) {
                            //Log.d(TAG, "removing from uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromMyDevice.getSequence()" + infoFromMyDevice.getSequence() + " infoFromOtherDevice.getSequence()" + infoFromOtherDevice.getSequence());
                            uniqueHandShakeInfosReceived.remove(userKey);
                        } else if (infoFromMyDevice.getSequence().longValue() == infoFromOtherDevice.getSequence().longValue()) {
                            //check for missing keys, if the same then remove otherwise only add missing key for infoFromMyDevice
                            String myMissingMessageSequences = infoFromMyDevice.getMissingMessages();
                            String otherDeviceMissingMessageSequences = infoFromOtherDevice.getMissingMessages();
                            List<String> list1 = new ArrayList<String>();
                            List<String> list2 = new ArrayList<String>();
                            if (myMissingMessageSequences != null) {
                                list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                            }
                            if (otherDeviceMissingMessageSequences != null) {
                                list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                            }
                            List<String> missingSequencesToAsk = new ArrayList<>(CollectionUtils.subtract(list1, list2));
                            if (askedThreshold > -1) {
                                CollectionUtils.filter(missingSequencesToAsk, new Predicate<String>() {
                                    @Override
                                    public boolean evaluate(String o) {
                                        return o.compareTo(String.valueOf(askedThreshold)) >= 0;
                                    }
                                });
                            }
                            Set<String> missingMessagesSetToAsk = ImmutableSet.copyOf(missingSequencesToAsk);
                            if (missingMessagesSetToAsk != null && missingMessagesSetToAsk.size() > 0) {
                                infoFromOtherDevice.setMissingMessages(StringUtils.join(missingMessagesSetToAsk, ","));
                                infoFromOtherDevice.setStartingSequence(infoFromOtherDevice.getSequence() + 1);
                            } else {
                                //Log.d(TAG, "removing from uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromMyDevice.getSequence()" + infoFromMyDevice.getSequence() + " infoFromOtherDevice.getSequence()" + infoFromOtherDevice.getSequence());
                                uniqueHandShakeInfosReceived.remove(userKey);
                            }
                            missingSequencesToAsk = null;
                            missingMessagesSetToAsk = null;

                        } else {
                            //Log.d(TAG, "uniqueHandShakeInfosReceived for key:" + userKey + " as infoFromOtherDevice.setStartingSequence" + infoFromMyDevice.getSequence().longValue());
                            // take other device's missing keys remove
                            // take my missing keys and remove if the same as other device's missing keys
                            // ask for all messages my sequence + 1
                            // ask for all my missing keys messages also

                            String myMissingMessageSequences = infoFromMyDevice.getMissingMessages();
                            String otherDeviceMissingMessageSequences = infoFromOtherDevice.getMissingMessages();
                            List<String> list1 = new ArrayList<String>();
                            List<String> list2 = new ArrayList<String>();
                            if (myMissingMessageSequences != null) {
                                list1 = Lists.newArrayList(Splitter.on(",").split(myMissingMessageSequences));
                            }
                            if (otherDeviceMissingMessageSequences != null) {
                                list2 = Lists.newArrayList(Splitter.on(",").split(otherDeviceMissingMessageSequences));
                            }
                            List<String> missingSequencesToAsk = new ArrayList<>(CollectionUtils.subtract(list1, list2));
                            if (askedThreshold > -1) {
                                CollectionUtils.filter(missingSequencesToAsk, new Predicate<String>() {
                                    @Override
                                    public boolean evaluate(String o) {
                                        return o.compareTo(String.valueOf(askedThreshold)) >= 0;
                                    }
                                });
                            }
                            Set<String> missingMessagesSetToAsk = ImmutableSet.copyOf(missingSequencesToAsk);
                            if (missingMessagesSetToAsk != null && missingMessagesSetToAsk.size() > 0) {
                                infoFromOtherDevice.setMissingMessages(StringUtils.join(missingMessagesSetToAsk, ","));
                            }
                            //infoFromOtherDevice.setStartingSequence(infoFromMyDevice.getSequence().longValue() + 1);
                            if (infoFromOtherDevice.getSequence() > SYNC_NUMBER_OF_LAST_MESSAGES) {
                                infoFromOtherDevice.setStartingSequence(infoFromOtherDevice.getSequence() - SYNC_NUMBER_OF_LAST_MESSAGES + 1);
                            } else {
                                infoFromOtherDevice.setStartingSequence(infoFromMyDevice.getSequence().longValue() + 1);
                            }

                            missingSequencesToAsk = null;
                            missingMessagesSetToAsk = null;
                        }
                    }
                }
            }


            List<HandShakingInfo> valuesToSend = new ArrayList<HandShakingInfo>();

            Collection<HandShakingInfo> photoValues = photoProfileUpdateInfosReceived.values();
            Iterator itPhotoValues = photoValues.iterator();
            while (itPhotoValues.hasNext()) {
                HandShakingInfo t = (HandShakingInfo) itPhotoValues.next();
                HandShakingInfo n = new HandShakingInfo(t.getUserId(), t.getDeviceId(), t.getProfileSequence(), null, null);
                n.setFrom(t.getFrom());
                n.setStartingSequence(Long.valueOf(t.getProfileSequence()));
                n.setSequence(Long.valueOf(t.getProfileSequence()));
                valuesToSend.add(n);
            }


            Collection<HandShakingInfo> values = uniqueHandShakeInfosReceived.values();
            Iterator itValues = values.iterator();
            while (itValues.hasNext()) {
                HandShakingInfo t = (HandShakingInfo) itValues.next();
                //Log.d(TAG, "validating : " + t.getUserId() + " " + t.getDeviceId() + " " + t.getStartingSequence() + " " + t.getSequence());

                if (t.getMissingMessages() != null && t.getMissingMessages().length() > 0) {

                    List<String> missingMessages = Lists.newArrayList(Splitter.on(",").split(t.getMissingMessages()));
                    Set<String> missingMessagesSet = ImmutableSet.copyOf(missingMessages);
                    missingMessages = null;
                    for (String m : missingMessagesSet) {
                        HandShakingInfo n = new HandShakingInfo(t.getUserId(), t.getDeviceId(), t.getSequence(), null, null);
                        n.setFrom(t.getFrom());
                        n.setStartingSequence(Long.valueOf(m));
                        n.setSequence(Long.valueOf(m));
                        valuesToSend.add(n);
                    }
                }


                if (t.getStartingSequence() == null) {
                    t.setMissingMessages(null);
                    valuesToSend.add(t);
                } else if (t.getStartingSequence() != null && t.getStartingSequence().longValue() <= t.getSequence().longValue()) {
                    t.setMissingMessages(null);
                    valuesToSend.add(t);
                }
            }
            return valuesToSend;
        }
    }

    private void sendMessages(List<String> computedMessages) {
        if (computedMessages != null && computedMessages.size() > 0) {
            Iterator<String> it = computedMessages.iterator();
            while (it.hasNext()) {
                String p = it.next();
                instance.sendMulticastMessage(p);
            }
        }
    }

    private boolean shouldSendAckForHandShakingMessage(HandShakingMessage handShakingMessage) {
        if (handShakingMessage != null) {
            boolean sendAck = handShakingMessage.getReply().equalsIgnoreCase("true");
            //Log.d(TAG, "shouldSendAckForHandShaking: " + handShakingMessage.getFrom() + " sendAck:" + sendAck);
            return sendAck;
        } else {
            return false;
        }
    }

    private void startRepeatHandShakeTimer() {
        if (instance.repeatHandShakeTimer != null) {
            instance.repeatHandShakeTimer.cancel();
            instance.repeatHandShakeTimer.start();
        }
    }

    private void createRepeatHandShakeTimer() {
        try {
            synchronized (NearByManager.class) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (instance != null) {
                            instance.repeatHandShakeTimer = new CountDownTimer(REPEAT_HANDSHAKE_TIMER, 10000) {
                                public void onTick(long millisUntilFinished) {
                                    Log.d(TAG, "repeatHandShakeTimer ticking ..." + millisUntilFinished);
                                }

                                public void onFinish() {
                                    Log.d(TAG, "repeatHandShakeTimer finished ... sending initial handshaking ...");
                                    instance.sendFindBuddyMessage();
                                    instance.startRepeatHandShakeTimer();
                                }
                            };
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendFindBuddyMessage() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                instance.sendInitialHandShakingMessage(true);
            }
        });
    }

    public void sendInitialHandShakingMessage(boolean needAck) {
        try {
            // construct handshaking message(s)
            // put in queue - TBD
            // send one by one from queue - TBD
            String serializedHandShakingMessage = instance.p2PDBApiImpl.serializeHandShakingMessage(needAck);
            Log.d(TAG, "sending initial handshaking message: " + serializedHandShakingMessage);
            instance.sendMulticastMessage(serializedHandShakingMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    public boolean isBluetoothEnabled() {
        return !instance.isConnected.get() && instance.mAdapter != null && instance.mAdapter.isEnabled();
    }

    @Override
    public void processInComingMessage(final String message, final String fromIP) {
        if (instance.isBluetoothEnabled()) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (instance.isHandShakingMessage(message)) {
                        instance.processInComingHandShakingMessage(message);
                    } else if (instance.isSyncRequestMessage(message)) {
                        List<String> syncInfoMessages = instance.processInComingSyncRequestMessage(message);
                        instance.sendMessages(syncInfoMessages);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                instance.sendMulticastMessage("BLUETOOTH-SYNC-COMPLETED");
                            }
                        }, 1000);

                    } else if (instance.isSyncInfoMessage(message)) {
                        instance.processInComingSyncInfoMessage(message);
                    } else if (instance.isBluetoothSyncCompleteMessage(message)) {
                        Log.d(TAG, "completed bluetooth sync" + message);
                    }
                }
            });
        }
    }

    public HandShakingMessage parseHandShakingMessage(String message) {
        HandShakingMessage handShakingMessage = p2PDBApiImpl.deSerializeHandShakingInformationFromJson(message);
        if (handShakingMessage != null) {
            Log.d(TAG, "storing handShakingMessage from : " + handShakingMessage.getFrom() + " in handShakingMessagesInCurrentLoop");
            instance.handShakingMessagesInCurrentLoop.put(handShakingMessage.getFrom(), handShakingMessage);
        }
        return handShakingMessage;
    }


    public void processInComingHandShakingMessage(String message) {

        //Log.d(TAG, "processInComingHandShakingMessage: " + message);
        notifyUI("handshaking message received", " ------> ", LOG_TYPE);
        //parse message and add to all messages
        HandShakingMessage handShakingMessage = instance.parseHandShakingMessage(message);
        if (handShakingMessage.getBt() != null && handShakingMessage.getFrom() != null) {
            instance.p2PDBApiImpl.saveBtAddress(handShakingMessage.getFrom(), handShakingMessage.getBt());
        }

        boolean shouldSendAck = shouldSendAckForHandShakingMessage(handShakingMessage);

        // send handshaking information if message received "from" first time
        if (shouldSendAck) {
            //Log.d(TAG, "replying back with initial hand shaking message with needAck => false");
            notifyUI("handshaking message sent with ack false", " ------> ", LOG_TYPE);
            sendInitialHandShakingMessage(false);
        }

        synchronized (NearByManager.class) {
            instance.generateSyncInfoPullRequest(instance.getAllHandShakeMessagesInCurrentLoop());
        }
    }

    public Map<String, HandShakingMessage> getAllHandShakeMessagesInCurrentLoop() {
        synchronized (NearByManager.class) {
            Map<String, HandShakingMessage> messagesTillNow = Collections.unmodifiableMap(handShakingMessagesInCurrentLoop);
            CollectionUtils.subtract(handShakingMessagesInCurrentLoop.keySet(), messagesTillNow.keySet());
            return messagesTillNow;
        }
    }


    public List<String> processInComingSyncRequestMessage(String message) {
        Log.d(TAG, "processInComingSyncRequestMessage => " + message);
        List<String> jsonRequests = new CopyOnWriteArrayList<String>();
        SyncInfoRequestMessage request = p2PDBApiImpl.buildSyncRequestMessage(message);
        // process only if matching current device id
        if (request != null && request.getmDeviceId().equalsIgnoreCase(P2PContext.getCurrentDevice())) {
            Log.d(TAG, "processInComingSyncRequestMessage => device id matches with: " + P2PContext.getCurrentDevice());
            notifyUI("sync request message received", " ------> ", LOG_TYPE);
            List<SyncInfoItem> items = request.getItems();
            for (SyncInfoItem a : items) {
                Log.d(TAG, "processInComingSyncRequestMessage => adding to jsonRequest for sync messages");
                jsonRequests.addAll(p2PDBApiImpl.fetchP2PSyncInfoBySyncRequest(a));
            }
        }

        return jsonRequests;
    }


    public List<String> generateSyncInfoPullRequest(final Map<String, HandShakingMessage> messages) {
        List<String> jsons = new ArrayList<String>();
        final Collection<HandShakingInfo> pullSyncInfo = instance.computeSyncInfoRequired(messages);
        //Log.d(TAG, "generateSyncInfoPullRequest -> computeSyncInfoRequired ->" + pullSyncInfo.size());
        notifyUI("generateSyncInfoPullRequest -> computeSyncInfoRequired ->" + pullSyncInfo.size(), " ------> ", LOG_TYPE);
        if (pullSyncInfo != null && pullSyncInfo.size() > 0) {
            jsons = p2PDBApiImpl.serializeSyncRequestMessages(pullSyncInfo);
            instance.sendMessages(jsons);
        } else {
            Log.d(TAG, "generateSyncInfoPullRequest -> sync completed:");
        }
        return jsons;
    }

    public void processInComingSyncInfoMessage(final String message) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                //Log.d(TAG, "processInComingSyncInfoMessage -> " + message);
                Iterator<P2PSyncInfo> infos = p2PDBApiImpl.deSerializeP2PSyncInfoFromJson(message).iterator();
                while (infos.hasNext()) {
                    P2PSyncInfo info = infos.next();
                    MessageStatus status = new MessageStatus(false, false);
                    status = instance.validIncomingSyncMessage(info, status);
                    if (status.isDuplicateMessage()) {
                        notifyUI(info.message + " ---------> duplicate - rejected ", info.getSender(), LOG_TYPE);
                        infos.remove();
                    } else if (status.isOutOfSyncMessage()) {
                        notifyUI(info.message + " with sequence " + info.getSequence() + " ---------> out of sync processed with filling Missing type message ", info.getSender(), LOG_TYPE);
                        String key = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
                        //Log.d(TAG, "processing out of sync data message for key:" + key + " and sequence:" + info.sequence);
                        String rMessage = p2PDBApiImpl.persistOutOfSyncP2PSyncMessage(info);
                        // generate handshaking request
                        if (status.isOutOfSyncMessage()) {
                            //Log.d(TAG, "validIncomingSyncMessage -> out of order -> sendInitialHandShakingMessage");
                            sendInitialHandShakingMessage(true);
                        }
                    } else if (!status.isOutOfSyncMessage() && !status.isDuplicateMessage()) {
                        String key = info.getDeviceId() + "_" + info.getUserId() + "_" + Long.valueOf(info.getSequence().longValue());
                        //Log.d(TAG, "processing sync data message for key:" + key + " and message:" + info.message);
                        String rMessage = p2PDBApiImpl.persistP2PSyncInfo(info);
                    } else {
                        infos.remove();
                    }
                }
            }
        });

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
        if (instance.repeatHandShakeTimer != null) {
            instance.repeatHandShakeTimer.cancel();
        }
        instance.nearbyHelper.setState(NearbyHelper.State.STOP_ADVERTISING);
        instance.nearbyHelper.setState(NearbyHelper.State.STOP_DISCOVERING);
        instance.nearbyHelper.disconnectFromAllEndpoints();
        instance.nearbyHelper.stopAllEndpoints();
    }

    public void onCleanUp() {
        instance.unRegisterReceivers();
        instance.onStop();
        if (instance.repeatHandShakeTimer != null) {
            instance.repeatHandShakeTimer.cancel();
            instance.repeatHandShakeTimer = null;
        }
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
        instance.sendFindBuddyMessage();
        instance.startRepeatHandShakeTimer();
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
        String message = new String(payload.asBytes());
        notifyUI("message received from " + endPoint.getName() + " with content :" + message, "-------> ", CONSOLE_TYPE);
        Intent intent = new Intent(bluetoothMessageEvent);
        // You can also include some extra data.
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(instance.context).sendBroadcast(intent);
    }

    public void setTeacher(boolean b) {
        this.isTeacher = b;
    }
}

