# google_sign_in

A Flutter plugin for [Google Sign In](https://developers.google.com/identity/).

Note: This is a work-in-progress, and is not fully supported by the Flutter team.
(For example, we don't have this under continuous integration and testing.)

## Android integration

To access Google Sign-In, you'll need to make sure to [register your
application](https://developers.google.com/mobile/add?platform=android). The
instructions to find the SHA-1 of your signing certificate can be misleading if
you're building in google3. You can get the correct SHA-1 building an APK and
running this command on it:

```
unzip -p /path/to/app.apk META-INF/CERT.?SA|keytool -printcert|grep 'SHA1:'|sed 's/^.*SHA1: //'
```

You don't need to include the google-services.json file in your app unless you
are using Google services that require it. You do need to enable the OAuth APIs
that you want, using the [Google Cloud Platform API
manager](https://console.developers.google.com/). For example, if you
want to mimic the behavior of the Google Sign-In sample app, you'll need to
enable the [Google People API](https://developers.google.com/people/).

Add the following code to your main activity:

```
private GoogleSignInPlugin googleSignIn;

@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    googleSignIn.onActivityResult(requestCode, resultCode, data);
}
```

Add the following to your Activity's `onCreate` method:

```
googleSignIn = GoogleSignInPlugin.register(this);
```

# iOS integration

To access Google Sign-In, you'll need to make sure to [register your
application](https://developers.google.com/mobile/add?platform=ios). Add
the generated GoogleService-Info.plist to root of your Runner project in Xcode,
so that the Google Sign-In framework can determine your client id.

You'll need to add this to the main dictionary of your application's Info.plist:

```
        <key>CFBundleURLTypes</key>
        <array>
                <dict>
                        <key>CFBundleTypeRole</key>
                        <string>Editor</string>
                        <key>CFBundleURLSchemes</key>
                        <array>
                                <!-- bundle id, for example: -->
                                <string>com.yourcompany.myapp</string>
                        </array>
                </dict>
                <dict>
                        <key>CFBundleTypeRole</key>
                        <string>Editor</string>
                        <key>CFBundleURLSchemes</key>
                        <array>
                                <!-- reverse url of your client id, for example: -->
        <string>com.googleusercontent.apps.861823949799-11qfr04mrfh2mndp3el2vgc0e357a2t6</string>
                        </array>
                </dict>
        </array>
```

Add the following private member data to your AppDelegate:

```
@implementation AppDelegate {
  GoogleSignInPlugin* _googleSignInPlugin;
}
```

Add the following code to `application:didFinishLaunchingWithOptions:`:

```
_googleSignInPlugin = [[GoogleSignInPlugin alloc] initWithFlutterView:flutterController];
```

Add the following code to your AppDelegate:

```
- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary *)options {
    NSString *sourceApplication = options[UIApplicationOpenURLOptionsSourceApplicationKey];
    id annotation = options[UIApplicationOpenURLOptionsAnnotationKey];
    return [_googleSignInPlugin handleURL:url
                        sourceApplication:sourceApplication
                               annotation:annotation];
}
```

## Usage

Add the following import to your Dart code:

```
import 'package:google_sign_in/google_sign_in.dart';
```

Initialize GoogleSignIn with the scopes you want:

```
GoogleSignIn.initialize(
    scopes: [
      'email',
      'https://www.googleapis.com/auth/contacts.readonly',
    ],
);
```

You can now use the `GoogleSignIn` class to authenticate in your Dart code, e.g.

```
GoogleSignInAccount account = await (await GoogleSignIn.instance).signIn();
```

See google_sign_in.dart for more API details.

## Issues and feedback

Please file [issues](https://github.com/flutter/flutter/issues/new)
to send feedback or report a bug. Thank you!
