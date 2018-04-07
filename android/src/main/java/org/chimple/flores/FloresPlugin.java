package org.chimple.flores;

import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * FloresPlugin
 */
public class FloresPlugin implements MethodCallHandler, StreamHandler {
  /**
   * Plugin registration.
   */
  public static void registerWith(PluginRegistry.Registrar registrar) {
    final MethodChannel methodChannel = new MethodChannel(registrar.messenger(), "chimple.org/flores");
    final EventChannel eventChannel =
        new EventChannel(registrar.messenger(), "chimple.org/flores_event");
    final FloresPlugin instance = new FloresPlugin(registrar);
    eventChannel.setStreamHandler(instance);
    methodChannel.setMethodCallHandler(instance);
  }

  FloresPlugin(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
  }

  private final PluginRegistry.Registrar registrar;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
      switch (call.method) {
          case "getNeighbors":
          {
              List<String> neighbors = getNeighbors();

              if (neighbors.size() >= 0) {
                  result.success(neighbors);
              } else {
                  result.error("UNAVAILABLE", "Neighbors are not available.", null);
              }
              break;
          }
          case "connectTo":
          {
              String neighbor = (String)call.arguments;
              boolean connectionStatus = connectTo(neighbor);

              result.success(connectionStatus);
              break;
          }
          default:
          {
              result.notImplemented();
          }
      }
  }

  @Override
  public void onListen(Object arguments, EventSink events) {}

  @Override
  public void onCancel(Object arguments) {}

  private List<String> getNeighbors() {
//    Context context = registrar.context();
//    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
//      BatteryManager batteryManager =
//              (BatteryManager) context.getSystemService(context.BATTERY_SERVICE);
//      batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
//    } else {
//      Intent intent =
//              new ContextWrapper(context)
//                      .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//      batteryLevel =
//              (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100)
//                      / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
//    }
//
//    return batteryLevel;
      return Arrays.asList("foo", "bar", "baz");
  }

  private boolean connectTo(String neighbor) {
      return true;
  }
}
