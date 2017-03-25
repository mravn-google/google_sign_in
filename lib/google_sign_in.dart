// Copyright 2017, the Flutter project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/services.dart' show PlatformMethodChannel;

class GoogleSignInAccount {
  final String displayName;
  final String email;
  final String id;
  final String photoUrl;

  GoogleSignInAccount._(Map<String, dynamic> message)
      : displayName = message['displayName'],
        email = message['email'],
        id = message['id'],
        photoUrl = message['photoUrl'] {
    assert(displayName != null);
    assert(id != null);
  }

  Future<String> get accessToken async {
    GoogleSignIn googleSignIn = await GoogleSignIn.instance;
    if (googleSignIn.currentUser != this) {
      throw new StateError('User is no longer signed in.');
    }

    String response = await GoogleSignIn._channel.invokeMethod(
      'getToken',
      <String, dynamic>{'email': email},
    );
    return response;
  }

  Future<Map<String, String>> get authHeaders async {
    String token = await accessToken;
    return <String, String>{
      "Authorization": "Bearer $token",
      "X-Goog-AuthUser": "0",
    };
  }

  @override
  String toString() {
    Map<String, dynamic> data = <String, dynamic>{
      'displayName': displayName,
      'email': email,
      'id': id,
      'photoUrl': photoUrl,
    };
    return 'GoogleSignInAccount:$data';
  }
}

/// GoogleSignIn allows you to authenticate Google users.
class GoogleSignIn {
  static const PlatformMethodChannel _channel =
      const PlatformMethodChannel('plugins.flutter.io/google_sign_in');
  static List<String> _scopes;
  static String _hostedDomain;

  /// Initializes global sign-in configuration settings.
  ///
  /// The list of [scopes] are OAuth scope codes to request when signing in.
  /// These scope codes will determine the level of data access that is granted
  /// to your application by the user.
  ///
  /// The [hostedDomain] argument specifies a hosted domain restriction. By
  /// setting this, sign in will be restricted to accounts of the user in the
  /// specified domain. By default, the list of accounts will not be restricted.
  static void initialize({
    List<String> scopes,
    String hostedDomain,
  }) {
    assert(_instance == null);
    _scopes = scopes;
    _hostedDomain = hostedDomain;
  }

  static Future<GoogleSignIn> _instance;

  /// The singleton instance of [GoogleSignIn]. Configuration properties should
  /// be [initialized](initialize) before retrieving this instance for the first
  /// time, as they become immutable thereafter.
  ///
  /// Note that this returns a [Future] because a properly instantiated
  /// sign-in client may require the user to upgrade other software on their
  /// device (e.g. Google Play Services for Android). Only once the user's
  /// device is in a state that allows us to sign in will this future complete.
  /// If action is required of the user, and the user refuses to take that
  /// action, this future will complete with an error, and the app will be
  /// unable to sign the user in.
  static Future<GoogleSignIn> get instance {
    if (_instance == null) {
      _instance = _channel.invokeMethod(
        "init",
        <String, dynamic>{
          'scopes': _scopes,
          'hostedDomain': _hostedDomain,
        },
      ).then((_) => new GoogleSignIn._());
    }
    return _instance;
  }

  /// Private constructor since we enforce a singleton pattern.
  GoogleSignIn._();

  StreamController<GoogleSignInAccount> _streamController =
  new StreamController<GoogleSignInAccount>.broadcast();

  /// Subscribe to this stream to be notified when the current user changes
  Stream<GoogleSignInAccount> get onCurrentUserChanged =>
      _streamController.stream;

  Future<GoogleSignInAccount> _callMethod(String method) async {
    Map<String, dynamic> response = await _channel.invokeMethod(method);
    _currentUser = response != null ? new GoogleSignInAccount._(response): null;
    _streamController.add(_currentUser);
    return _currentUser;
  }

  /// The currently signed in account, or null if the user is signed out
  GoogleSignInAccount _currentUser;
  GoogleSignInAccount get currentUser => _currentUser;

  /// Attempts to sign in a previously authenticated user without interaction.
  Future<GoogleSignInAccount> signInSilently() => _callMethod('signInSilently');

  /// Starts the sign-in process.
  Future<GoogleSignInAccount> signIn() => _callMethod('signIn');

  /// Marks current user as being in the signed out state.
  Future<GoogleSignInAccount> signOut() => _callMethod('signOut');

  /// Disconnects the current user from the app and revokes previous
  /// authentication.
  Future<GoogleSignInAccount> disconnect() => _callMethod('disconnect');
}