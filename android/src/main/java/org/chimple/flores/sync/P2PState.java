package org.chimple.flores.sync;

public interface P2PState {
    public void onEnter(P2PStateFlow p2PStateFlow, P2PSyncManager manager, String message);

    public void onExit(P2PStateFlow.Transition transition);

    public P2PStateFlow.Transition process(P2PStateFlow.Transition transition);

    public P2PStateFlow.Transition getTransition();

    public String getOutcome();
}


