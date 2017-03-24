import 'dart:async';

import 'package:flutter/services.dart';

class XxPluginXx {
  static const PlatformMethodChannel _channel =
      const PlatformMethodChannel('xxpluginxx');

  static Future<String> get platformVersion =>
      _channel.invokeMethod('getPlatformVersion');
}
