package org.chimple.flores.sync;

import java.net.InetAddress;

public interface HandShakeListenerCallBack {
    public void GotConnection(InetAddress remote, InetAddress local);
    public void ListeningFailed(String reason, int triedSoFar);

}
