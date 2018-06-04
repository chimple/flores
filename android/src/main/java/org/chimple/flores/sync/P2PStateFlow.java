package org.chimple.flores.sync;

import android.util.Log;

import org.chimple.flores.db.P2PDBApi;
import org.chimple.flores.db.P2PDBApiImpl;

import java.util.HashMap;
import java.util.Map;

import static org.chimple.flores.sync.P2PStateFlow.Transition.NONE;
import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.RECEIVE_HANDSHAKING_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_DB_SYNC_INFORMATION;
import static org.chimple.flores.sync.P2PStateFlow.Transition.SEND_HANDSHAKING_INFORMATION;

public class P2PStateFlow {

    public enum Transition {
        RECEIVE_HANDSHAKING_INFORMATION,
        SEND_HANDSHAKING_INFORMATION,
        RECEIVE_DB_SYNC_INFORMATION,
        SEND_DB_SYNC_INFORMATION,
        NONE
    }

    private static final String TAG = P2PStateFlow.class.getSimpleName();

    private boolean handShakingInformationReceived = false;
    private boolean allSyncInformationReceived = false;
    private boolean handShakingInformationSent = false;
    private boolean allSyncInformationSent = false;

    private P2PSyncManager manager;
    private static P2PStateFlow instance;
    private ConnectedThread thread;

    private Map<Transition, P2PState> allPossibleStates = null;

    private P2PState currentState;


    private P2PStateFlow() {
    }


    /**
     * @return the state that handled this message
     */
    public P2PState getState() {
        return currentState;
    }


    public static P2PStateFlow getInstanceUsingDoubleLocking(P2PSyncManager manager) {
        if (instance == null) {
            synchronized (P2PStateFlow.class) {
                if (instance == null) {
                    instance = new P2PStateFlow();
                    instance.manager = manager;
                    instance.initializeAllP2PStates();
                    instance.setInitialState(new NoneState());
                }
            }
        }
        return instance;
    }

    public void setConnectedThread(final ConnectedThread thread) {
        this.thread = thread;
    }

    public ConnectedThread getThread() {
        return thread;
    }

    private void initializeAllP2PStates() {
        allPossibleStates = new HashMap<Transition, P2PState>();
        allPossibleStates.put(NONE, new NoneState());
        allPossibleStates.put(SEND_HANDSHAKING_INFORMATION, new SendInitialHandShakingMessageState());
        allPossibleStates.put(RECEIVE_HANDSHAKING_INFORMATION, new ReceiveInitialHandShakingMessageState());
        allPossibleStates.put(SEND_DB_SYNC_INFORMATION, new SendSyncInfoMessageState());
        allPossibleStates.put(RECEIVE_DB_SYNC_INFORMATION, new ReceiveSyncInfoMessageState());
    }

    private void setInitialState(P2PState initialState) {
        this.currentState = initialState;
    }

    public void processMessages(String receivedMessage) {
        if (receivedMessage != null) {
            if (!handShakingInformationReceived) {
                this.transit(RECEIVE_HANDSHAKING_INFORMATION, receivedMessage);
            } else if (!allSyncInformationReceived) {
                this.transit(RECEIVE_DB_SYNC_INFORMATION, receivedMessage);
            }
        }
    }

    public void resetAllStates() {
        synchronized (P2PStateFlow.class) {
            if (instance != null) {
                this.setHandShakingInformationSent(false);
                this.setHandShakingInformationReceived(false);
                this.setAllSyncInformationSent(false);
                this.setAllSyncInformationReceived(false);
                allPossibleStates = null;
                instance.initializeAllP2PStates();
                instance.setInitialState(new NoneState());
            }
        }
    }

    public void allMessagesExchanged() {
        Log.i(TAG, ".... All messages exchanged ....");
        this.resetAllStates();
        P2PDBApi api = P2PDBApiImpl.getInstance(manager.getContext());
        String deviceId = manager.fetchFromSharedPreference(P2PSyncManager.connectedDevice);
        if (deviceId != null) {
            api.syncCompleted(deviceId);
        }
        Log.i(TAG, ".... calling removeClientIPAddressToConnect ....");
        manager.removeClientIPAddressToConnect();
        Log.i(TAG, ".... calling startExitTimer....");
        manager.resetExitTimer();
        manager.startExitTimer();
    }

    public void transit(Transition command, String message) {
        Log.i(TAG, "checking if thread is not null" + (thread != null));
        Transition transitionTo = this.getState().process(command);
        P2PState currentState = this.getState();
        Transition currentStateTransition = currentState.getTransition();

        if (transitionTo != currentStateTransition) {
            currentState.onExit(transitionTo);
            if (allPossibleStates.containsKey(transitionTo)) {
                P2PState nextState = allPossibleStates.get(transitionTo);
                if (nextState != null) {
                    this.currentState = nextState;
                    nextState.onEnter(instance, manager, message);
                }
            }
        }
    }

    public String getStateResult(Transition t) {
        if (allPossibleStates.containsKey(t)) {
            P2PState nextState = allPossibleStates.get(t);
            return nextState.getOutcome();
        }
        return null;
    }

    public void setHandShakingInformationReceived(boolean handShakingInformationReceived) {
        this.handShakingInformationReceived = handShakingInformationReceived;
    }

    public boolean isHandShakingInformationReceived() {
        return handShakingInformationReceived;
    }

    public boolean isAllSyncInformationReceived() {
        return allSyncInformationReceived;
    }

    public boolean isHandShakingInformationSent() {
        return handShakingInformationSent;
    }

    public boolean isAllSyncInformationSent() {
        return allSyncInformationSent;
    }

    public void setAllSyncInformationReceived(boolean allSyncInformationReceived) {
        this.allSyncInformationReceived = allSyncInformationReceived;
    }

    public void setHandShakingInformationSent(boolean handShakingInformationSent) {
        this.handShakingInformationSent = handShakingInformationSent;
    }

    public void setAllSyncInformationSent(boolean allSyncInformationSent) {
        this.allSyncInformationSent = allSyncInformationSent;
    }
}