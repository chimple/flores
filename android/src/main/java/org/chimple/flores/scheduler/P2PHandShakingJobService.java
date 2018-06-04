package org.chimple.flores.scheduler;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)

public class P2PHandShakingJobService extends JobService {
    private static final String TAG = P2PHandShakingJobService.class.getSimpleName();
    private WifiDirectIntentBroadcastReceiver receiver;

    public static final String P2P_SYNC_RESULT_RECEIVED = "P2P_SYNC_RESULT_RECEIVED";
    public static final String JOB_PARAMS = "JOB_PARAMS";


    private Intent wifiDirectServiceIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        //startForeground(1,new Notification());
        this.registerWifiDirectIntentBroadcastReceiver();
        Log.i(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        this.unregisterWifiDirectIntentBroadcastReceiver();
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's MainActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth. See "setUiCallback()"
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        // The work that this service "does" is simply wait for a certain duration and finish
        // the job (on another thread).
        if (!JobUtils.isJobRunning()) {
            wifiDirectServiceIntent = new Intent(getApplicationContext(), WifiDirectIntentService.class);
            wifiDirectServiceIntent.putExtra(JOB_PARAMS, params);
            getApplicationContext().startService(new Intent(wifiDirectServiceIntent));
            JobUtils.setJobRunning(true);
            Log.i(TAG, "on start job: " + params.getJobId());
        } else {
            Log.i(TAG, "Job is already running");
        }

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stop tracking these job parameters, as we've 'finished' executing.
        Log.i(TAG, "on stop job: " + params.getJobId());
        JobUtils.setJobRunning(false);
        // Return false to drop the job.
        return false;
    }

    private void unregisterWifiDirectIntentBroadcastReceiver() {
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            receiver = null;
            Log.i(TAG, "WifiDirectIntentBroadcast Receiver unregistered");
        }
    }

    private void registerWifiDirectIntentBroadcastReceiver() {

        receiver = new WifiDirectIntentBroadcastReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(P2P_SYNC_RESULT_RECEIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        Log.i(TAG, "WifiDirectIntentBroadcast Receiver registered");
    }


    /**
     * BroadcastReceiver used to receive Intents fired from the WifiDirectHandler when P2P events occur
     * Used to update the UI and receive communication messages
     */
    public class WifiDirectIntentBroadcastReceiver extends BroadcastReceiver {
        private P2PHandShakingJobService service;

        public WifiDirectIntentBroadcastReceiver(P2PHandShakingJobService service) {
            this.service = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            JobParameters params = intent.getExtras().getParcelable(JOB_PARAMS);
            Log.i(TAG, "on finished job: " + params.getJobId());
            JobUtils.setJobRunning(false);
            JobUtils.cancelAllJobs(context);
            JobUtils.scheduledJob(context, false);
            getApplicationContext().stopService(wifiDirectServiceIntent);
            jobFinished(params, false);
        }
    }
}
