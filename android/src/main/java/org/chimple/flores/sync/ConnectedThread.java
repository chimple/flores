package org.chimple.flores.sync;

import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectedThread extends Thread {
    private static final String TAG = ConnectedThread.class.getSimpleName();

    public static final int MESSAGE_READ = 0x11;
    public static final int MESSAGE_WRITE = 0x22;
    public static final int SOCKET_DISCONNEDTED = 0x33;
    public static final int SOCKET_STOPPED = 0x44;

    private final Socket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    boolean mRunning = true;

    public ConnectedThread(Socket socket, Handler handler) {
        Log.d(TAG, "Creating ConnectedThread");
        mHandler = handler;
        mmSocket = socket;

        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        // Get the Socket input and output streams
        try {
            if (mmSocket != null) {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            }
        } catch (IOException e) {
            Log.e(TAG, "Creating temp sockets failed: ", e);
        }
        mmInStream = tmpIn;
        mmOutStream = tmpOut;
        Log.d(TAG, "Creating ConnectedThread finished");
    }

    public void run() {
        Log.i(TAG, "BTConnectedThread started");
        byte[] buffer = new byte[1048576 * 10];
        int bytes;
        StringBuffer sBuffer = null;
        while (mRunning) {
            try {
                bytes = mmInStream.read(buffer);
                if (bytes > 0) {
                    Log.i(TAG, "ConnectedThread read data: " + bytes + " bytes");
                    String whatGot = new String(buffer, 0, bytes);
                    String finalMessage = null;
                    if (whatGot != null) {
                        Log.i(TAG, "what we got" + whatGot);

                        if (whatGot.startsWith("START")) {
                            sBuffer = new StringBuffer();
                            Log.i(TAG, "MESSAGE READ:" + whatGot);
                            if (whatGot.endsWith("END")) {
                                sBuffer.append(whatGot);
                                finalMessage = sBuffer.toString();
                                sBuffer = null;
                            } else {
                                sBuffer.append(whatGot);
                            }
                        } else {
                            if (!whatGot.endsWith("END")) {
                                sBuffer.append(whatGot);
                                Log.i(TAG, "APPEND TO BUFFER READ:" + sBuffer.toString());
                            } else {
                                sBuffer.append(whatGot);
                                finalMessage = sBuffer.toString();
                                sBuffer = null;
                            }
                        }

                        if (finalMessage != null) {
                            finalMessage = finalMessage.replaceAll("START", "");
                            finalMessage = finalMessage.replaceAll("END", "");
                            Log.i(TAG, "final data to be processed: " + finalMessage);
                            mHandler.obtainMessage(MESSAGE_READ, finalMessage.getBytes().length, -1, finalMessage.getBytes()).sendToTarget();
                            finalMessage = null;
                        }
                    }
                } else {
                    Stop();
                    mHandler.obtainMessage(SOCKET_DISCONNEDTED, -1, -1, "Disconnected").sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread Stopped: ", e);
                Stop();
                mHandler.obtainMessage(SOCKET_DISCONNEDTED, -1, -1, e).sendToTarget();
                break;
            }
        }

        Log.i(TAG, "BTConnectedThread disconnect now !");
    }


    public void write(byte[] buffer, int from, int length) {
        try {
            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
                if (mmOutStream != null) {
                    mmOutStream.write(buffer, from, length);
                    mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer).sendToTarget();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  write failed: ", e);
        }
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            int SDK_INT = android.os.Build.VERSION.SDK_INT;
            if (SDK_INT > 8) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                        .permitAll().build();
                StrictMode.setThreadPolicy(policy);
                if (mmOutStream != null) {
                    mmOutStream.write(buffer);
                    mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer).sendToTarget();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  write failed: ", e);
        }
    }

    public void Stop() {
        mRunning = false;
        try {
            if (mmInStream != null) {
                mmInStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  mmInStream close failed: ", e);
        }
        try {
            if (mmOutStream != null) {
                mmOutStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  mmOutStream close failed: ", e);
        }

        try {

            if (mmSocket != null) {
                mmSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  socket close failed: ", e);
        }
    }
}
