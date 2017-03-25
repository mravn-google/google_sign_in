package com.yourcompany.googlesignin.example;

import android.os.Bundle;
import android.content.Intent;
import io.flutter.app.FlutterActivity;
import io.flutter.plugins.googlesignin.GoogleSignInPlugin;

public class MainActivity extends FlutterActivity {
    private GoogleSignInPlugin googleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleSignIn = GoogleSignInPlugin.register(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      googleSignIn.onActivityResult(requestCode, resultCode, data);
    }
}
