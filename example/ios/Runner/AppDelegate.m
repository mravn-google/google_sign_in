#include "AppDelegate.h"
#include "GoogleSignInPlugin.h"

@implementation AppDelegate {
  GoogleSignInPlugin* _googleSignInPlugin;
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Override point for customization after application launch.
    FlutterViewController* flutterController = (FlutterViewController*)self.window.rootViewController;
    _googleSignInPlugin = [[GoogleSignInPlugin alloc] initWithFlutterView:flutterController];
    return YES;
}

- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary *)options {
    NSString *sourceApplication = options[UIApplicationOpenURLOptionsSourceApplicationKey];
    id annotation = options[UIApplicationOpenURLOptionsAnnotationKey];
    return [_googleSignInPlugin handleURL:url
                        sourceApplication:sourceApplication
                               annotation:annotation];
}

@end
