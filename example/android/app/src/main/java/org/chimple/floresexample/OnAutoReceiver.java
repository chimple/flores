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
import android.os.BatteryManager;

public class OnAutoReceiver extends BroadcastReceiver { 
private static final String TAG = OnAutoReceiver.class.getSimpleName();
@Override
    public void onReceive(Context context, Intent intent) {
    	Log.d(TAG, "launching Main Activity when power connected or boot time");
		Log.d(TAG, "Checking if main activity launched:" + MainActivity.isAppLunched());
		if(MainActivity.isAppLunched())
		{
			Log.d(TAG, "Main Activity ALREADY LAUNCHED");
		} else 
		{
			if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !MainActivity.isAppLunched()) {
				Log.d(TAG, "Boot event received ... Launching Flores");
				MainActivity.launchApp();
		        Intent i = new Intent(context, MainActivity.class);
	    	    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);        
	        	context.startActivity(i);
	        } else if(!MainActivity.isAppLunched() && Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()))
	        {
				Log.d(TAG, "Power event received ... Launching Flores");
    			MainActivity.launchApp();
		        Intent i = new Intent(context, MainActivity.class);
	    	    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);        
	        	context.startActivity(i);  
			} 				    	    				    	        
		}		 
    }   
}   