// Copyright 2017, the Flutter project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

#import <Flutter/Flutter.h>
#import <Google/SignIn.h>

@interface GoogleSignInPlugin : NSObject<GIDSignInDelegate, GIDSignInUIDelegate>
- initWithFlutterView: (FlutterViewController*)flutterView;
- (BOOL)  handleURL:(NSURL*)url
  sourceApplication:(NSString*)sourceApplication
         annotation:(id)annotation;
@end
