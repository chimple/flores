package org.chimple.flores.application;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.chimple.flores.db.AppDatabase;

public class P2PApplication extends Application {
    private static final String TAG = P2PApplication.class.getName();
    private static Context context;
    private P2PApplication that;
    public static AppDatabase db;

    public static int REGULAR_JOB_TIMINGS_FOR_MIN_LATENCY = 4 * 60 * 1000; // every 4 mins mininum
    public static int REGULAR_JOB_TIMINGS_FOR_PERIOD = 8 * 60 * 1000; // every 8 mins
    public static int IMMEDIATE_JOB_TIMINGS = 5 * 1000; // in next 5 seconds

    @Override
    public void onCreate() {
        super.onCreate();
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