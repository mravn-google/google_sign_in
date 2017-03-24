package com.yourcompany.xxpluginxx;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.FlutterMethodChannel;
import io.flutter.plugin.common.FlutterMethodChannel.MethodCallHandler;
import io.flutter.plugin.common.FlutterMethodChannel.Response;
import io.flutter.plugin.common.MethodCall;

import java.util.HashMap;
import java.util.Map;

/**
 * XxPluginXx
 */
public class XxPluginXx implements MethodCallHandler {
  private FlutterActivity activity;

  public static void register(FlutterActivity activity) {
    new XxPluginXx(activity);
  }

  private XxPluginXx(FlutterActivity activity) {
    this.activity = activity;
    new FlutterMethodChannel(activity.getFlutterView(), "xxpluginxx").setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(MethodCall call, Response response) {
    if (call.method.equals("getPlatformVersion")) {
      response.success("Android " + android.os.Build.VERSION.RELEASE);
    } else {
      throw new IllegalArgumentException("Unknown method " + call.method);
    }
  }
}
