package com.yourcompany.xxpluginxx.example;

import android.os.Bundle;
import io.flutter.app.FlutterActivity;
import com.yourcompany.xxpluginxx.XxPluginXx;

public class MainActivity extends FlutterActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        XxPluginXx.register(this);
    }
}

