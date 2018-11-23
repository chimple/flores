package org.chimple.floresexample;

import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;

public class OnAutoReceiver extends BroadcastReceiver { 
private static final String TAG = OnAutoReceiver.class.getSimpleName();
@Override
    public void onReceive(Context context, Intent intent) {
    	Log.d(TAG, "launching Main Activity when power connected");
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);   
    }   
}   