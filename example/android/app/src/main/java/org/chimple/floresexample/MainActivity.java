package org.chimple.floresexample;

import android.os.Bundle;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.multicast.MulticastManager;
import org.chimple.flores.application.P2PContext;
import static org.chimple.flores.application.P2PContext.CLEAR_CONSOLE_TYPE;
import static org.chimple.flores.application.P2PContext.refreshDevice;


public class MainActivity extends FlutterActivity {
    private static final String TAG = MainActivity.class.getName();
    private static Context context;
    public static AppDatabase db;
    private MulticastManager manager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);
        initialize();
        context = this;
    }
    
    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        manager.onCleanUp();
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {
            @Override
            public void run() {
                // Initialize all of the important frameworks and objects
                P2PContext.getInstance().initialize(MainActivity.this);
                db = AppDatabase.getInstance(MainActivity.this);
                manager = MulticastManager.getInstance(MainActivity.this);
                Log.i(TAG, "app database instance" + String.valueOf(db));

                initializationComplete();
            }
        };

        initializationThread.start();
    }


    private void initializationComplete() {
        Log.i(TAG, "Initialization complete...");
    }

    public static Context getContext() {
        return context;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
