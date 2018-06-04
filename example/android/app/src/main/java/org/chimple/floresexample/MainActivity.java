package org.chimple.floresexample;

import android.os.Bundle;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {

    private static Context context;
    private P2PApplication that;
    public static AppDatabase db;

    public static int REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY = 4 * 60 * 1000; // every 4 mins mininum
    public static int REGULAR_JOB_TIMINGS_FOR_PERIOD = 8 * 60 * 1000; // every 8 mins
    public static int IMMEDIATE_JOB_TIMINGS = 5 * 1000; // in next 5 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GeneratedPluginRegistrant.registerWith(this);
        initialize();
        context = this;
        that = this;
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {
            @Override
            public void run() {
                // Initialize all of the important frameworks and objects
                P2PContext.getInstance().initialize(P2PApplication.this);
                //TODO: for now force the creation here
                db = AppDatabase.getInstance(P2PApplication.this);

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
