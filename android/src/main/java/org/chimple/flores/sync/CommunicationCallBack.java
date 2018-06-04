package org.chimple.flores.sync;

import java.net.Socket;

public interface CommunicationCallBack {
    public void Connected(Socket socket);

    public void GotConnection(Socket socket);

    public void ConnectionFailed(String reason);

    public void ListeningFailed(String reason);

}
