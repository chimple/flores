package org.chimple.flores.sync;

import android.content.Context;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectedThread extends Thread {
    private static final String TAG = ConnectedThread.class.getSimpleName();

    public static final int MESSAGE_READ = 0x11;
    public static final int MESSAGE_WRITE = 0x22;
    public static final int SOCKET_DISCONNEDTED = 0x33;

    private volatile boolean sendNonSyncInformation = true;
    private Socket mmSocket = null;
    private Context mmContext = null;
    private InputStream mmInStream = null;
    private OutputStream mmOutStream = null;
    private Handler mHandler = null;

    boolean mRunning = true;

    public ConnectedThread(Socket socket, Handler handler, Context context) {
        Log.d(TAG, "Creating ConnectedThread");
        mHandler = handler;
        mmSocket = socket;
        mmContext = context;

        this.initStreamsIfNull();
        Log.d(TAG, "Creating ConnectedThread finished");
    }

    public void initStreamsIfNull() {
        Log.i(TAG, "initStreamsIfNull ConnectedThread");
        if (mmInStream == null) {
            InputStream tmpIn = null;
            try {
                if (mmSocket != null) {
                    tmpIn = mmSocket.getInputStream();
                }
            } catch (IOException e) {
                Log.e(TAG, "Creating temp sockets failed: ", e);
            }
            Log.i(TAG, "mmInStream initialized");
            mmInStream = tmpIn;
        }

        if (mmOutStream == null) {
            OutputStream tmpOut = null;
            try {
                if (mmSocket != null) {
                    tmpOut = mmSocket.getOutputStream();
                }
            } catch (IOException e) {
                Log.e(TAG, "Creating temp sockets failed: ", e);
            }
            Log.i(TAG, "mmOutStream initialized");
            mmOutStream = tmpOut;
        }
    }

    private void sendSyncTextMessages(StringBuffer sBuffer, byte[] buffer) {
        try {
            int bytes = -1;
            if (mmInStream != null) {
                while ((bytes = mmInStream.read(buffer)) != -1) {
                    if (bytes > 0) {
                        Log.i(TAG, "ConnectedThread read data: " + bytes + " bytes");
                        String whatGot = new String(buffer, 0, bytes);
                        String finalMessage = null;
                        if (whatGot != null) {
                            Log.i(TAG, "what we got" + whatGot);

                            if (sBuffer == null) {
                                sBuffer = new StringBuffer();
                            }

                            if (whatGot.startsWith("START")) {
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
                        Log.i(TAG, "NOT Available on sendSyncTextMessages .... in bytes");
                        break;
                    }
                }
            } else {
                Log.i(TAG, "NOT Available on sendSyncTextMessages .... mmInputStream is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "ConnectedThread Stopped: ", e);
            Stop();
            mHandler.obtainMessage(SOCKET_DISCONNEDTED, -1, -1, e).sendToTarget();
        }

    }

    public void run() {
        Log.i(TAG, "BTConnectedThread started");

        byte[] buffer = new byte[1048576 * 10];
        int bytes;
        StringBuffer sBuffer = null;
        while (mRunning) {
            this.initStreamsIfNull();
            Log.i(TAG, "sendNonSyncInformation:" + sendNonSyncInformation);
            if (this.isSendNonSyncInformation()) {
                Log.i(TAG, "BTConnectedThread in sendSyncTextMessages");
                this.sendSyncTextMessages(sBuffer, buffer);
            } else {
                Log.i(TAG, "BTConnectedThread in receiveProfileImage");
                this.receiveProfileImage();
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

    public void Disconnect() {
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
    }

    public boolean isSendNonSyncInformation() {
        return sendNonSyncInformation;
    }

    public void setSendNonSyncInformation(boolean sendNonSyncInformation) {
        Log.i(TAG, "updating sendNonSyncInformation to" + sendNonSyncInformation);
        this.sendNonSyncInformation = sendNonSyncInformation;
    }

    private void receiveProfileImage() {
        Log.i(TAG, "in receiveProfileImage....." + (mmContext == null));
        try {
            if (mmInStream != null) {
                File folder = new File(mmContext.getExternalFilesDir(null), "P2P_IMAGES");
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                Log.i(TAG, "in receiveProfileImage saving to folder....." + folder.getAbsolutePath());
                int len;
                File file = new File(folder, "Receivedimage.jpg");
                FileOutputStream out = new FileOutputStream(file);
                byte[] bytes = new byte[1024];
                BufferedOutputStream bos = new BufferedOutputStream(out);
                while ((len = mmInStream.read(bytes)) != -1) {
                    bos.write(bytes, 0, len);
                }
                bos.close();
                Log.i("Reading Byte image", String.valueOf(bytes.length));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void sendProfileImage() {
        Log.i(TAG, "sendProfileImage ...." + mmContext);
        try {
            File file = new File(mmContext.getExternalFilesDir(null) + "/Cache", "DefaultImage.jpg");
            InputStream inputStream = null;
            inputStream = new FileInputStream(file.getAbsolutePath());
            Log.i(TAG, "sendProfileImage ....GOT File:" + file.getAbsolutePath());
            byte[] buf = new byte[1024];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    mmOutStream.write(buf, 0, len);
                    Log.d("Writing Byte image", String.valueOf(buf.length) + ":" + buf.toString());
                }
                inputStream.close();
            } catch (IOException e) {
                Log.d("write error", e.toString());
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.d("SendingImageSync", e.toString());
        }
        return;
    }
}