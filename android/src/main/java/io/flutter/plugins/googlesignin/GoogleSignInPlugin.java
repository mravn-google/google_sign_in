// Copyright 2017, the Flutter project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package io.flutter.plugins.googlesignin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.flutter.view.FlutterView;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.plugin.common.FlutterMethodChannel;
import io.flutter.plugin.common.FlutterMethodChannel.MethodCallHandler;
import io.flutter.plugin.common.FlutterMethodChannel.Response;
import io.flutter.plugin.common.MethodCall;

import java.util.HashMap;
import java.util.Map;

/**
 * GoogleSignIn
 */
public class GoogleSignInPlugin
    implements MethodCallHandler,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

  private FlutterActivity activity;
  private final String CHANNEL = "plugins.flutter.io/google_sign_in";

  private static final int REQUEST_CODE = 53293;

  private static final String TAG = "flutter";

  private static final String ERROR_REASON_EXCEPTION = "exception";
  private static final String ERROR_REASON_STATUS = "status";
  private static final String ERROR_REASON_CANCELED = "canceled";
  private static final String ERROR_REASON_OPERATION_IN_PROGRESS = "operation_in_progress";
  private static final String ERROR_REASON_CONNECTION_FAILED = "connection_failed";

  private static final String METHOD_INIT = "init";
  private static final String METHOD_SIGN_IN_SILENTLY = "signInSilently";
  private static final String METHOD_SIGN_IN = "signIn";
  private static final String METHOD_GET_TOKEN = "getToken";
  private static final String METHOD_SIGN_OUT = "signOut";
  private static final String METHOD_DISCONNECT = "disconnect";

  private static final class PendingOperation {
    final String method;
    final Queue<Response> responseQueue = Lists.newLinkedList();

    PendingOperation(String method, Response response) {
      this.method = Preconditions.checkNotNull(method);
      responseQueue.add(Preconditions.checkNotNull(response));
    }
  }

  private final BackgroundTaskRunner backgroundTaskRunner;
  private final int requestCode;

  private GoogleApiClient googleApiClient;
  private List<String> requestedScopes;
  private PendingOperation pendingOperation;

  public static GoogleSignInPlugin register(FlutterActivity activity) {
    return new GoogleSignInPlugin(
        activity,
        new BackgroundTaskRunner(1),
        REQUEST_CODE);
  }

  @VisibleForTesting
  private GoogleSignInPlugin(
      FlutterActivity activity,
      BackgroundTaskRunner backgroundTaskRunner,
      int requestCode) {
    this.activity = activity;
    this.backgroundTaskRunner = backgroundTaskRunner;
    this.requestCode = requestCode;
    new FlutterMethodChannel(activity.getFlutterView(), CHANNEL)
      .setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(MethodCall call, Response response) {
    HashMap<String, Object> arguments = (HashMap<String, Object>) call.arguments;
    switch (call.method) {
      case METHOD_INIT:
        init(response,
            (List<String>) arguments.get("scopes"),
            (String) arguments.get("hostedDomain"));
        break;

      case METHOD_SIGN_IN_SILENTLY:
        signInSilently(response);
        break;

      case METHOD_SIGN_IN:
        signIn(response);
        break;

      case METHOD_GET_TOKEN:
        getToken(response, (String) arguments.get("email"));
        break;

      case METHOD_SIGN_OUT:
        signOut(response);
        break;

      case METHOD_DISCONNECT:
        disconnect(response);
        break;

      default:
        throw new IllegalArgumentException("Unknown method " + call.method);
    }
  }

   /**
    * Initializes this listener so that it is ready to perform other operations. The Dart code
    * guarantees that this will be called and completed before any other methods are invoked.
    */
   private void init(Response response, List<String> requestedScopes, String hostedDomain) {
     try {
       if (googleApiClient != null) {
         // This can happen if the scopes change, or a full restart hot reload
         googleApiClient.stopAutoManage(activity);
         googleApiClient = null;
       }
       GoogleSignInOptions.Builder optionsBuilder =
           new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN);
       optionsBuilder.requestEmail();
       for (String scope : requestedScopes) {
         optionsBuilder.requestScopes(new Scope(scope));
       }
       if (!Strings.isNullOrEmpty(hostedDomain)) {
         optionsBuilder.setHostedDomain(hostedDomain);
       }

       this.requestedScopes = requestedScopes;
       this.googleApiClient =
           new GoogleApiClient.Builder(activity)
               .enableAutoManage(activity, this)
               .addApi(Auth.GOOGLE_SIGN_IN_API, optionsBuilder.build())
               .addConnectionCallbacks(this)
               .build();
     } catch (Exception e) {
       Log.e(TAG, "Initialization error", e);
       response.error(ERROR_REASON_EXCEPTION, e.getMessage(), null);
     }

     // We're not initialized until we receive `onConnected`.
     // If initialization fails, we'll receive `onConnectionFailed`
     pendingOperation = new PendingOperation(METHOD_INIT, response);
   }

   /**
    * Handles the case of a concurrent operation already in progress.
    *
    * <p>Only one type of operation is allowed to be executed at a time, so if there's a pending
    * operation for a method type other than the current invocation, this will respond failure on the
    * specified response channel. Alternatively, if there's a pending operation for the same method
    * type, this will signal that the method is already being handled and add the specified response
    * to the pending operation's response queue.
    *
    * <p>If there's no pending operation, this method will set the pending operation to the current
    * invocation.
    *
    * @param currentMethod The current invocation.
    * @param response The response channel for the current invocation.
    * @return true iff an operation is already in progress (and thus the response is already being
    *     handled).
    */
   private boolean checkAndSetPendingOperation(String currentMethod, Response response) {
     if (pendingOperation == null) {
       pendingOperation = new PendingOperation(currentMethod, response);
       return false;
     }

     if (pendingOperation.method.equals(currentMethod)) {
       // This method is already being handled
       pendingOperation.responseQueue.add(response);
     } else {
       // Only one type of operation can be in progress at a time
       response.error(ERROR_REASON_OPERATION_IN_PROGRESS, pendingOperation.method, null);
     }

     return true;
   }

   /**
    * Returns the account information for the user who is signed in to this app. If no user is signed
    * in, tries to sign the user in without displaying any user interface.
    */
   private void signInSilently(Response response) {
     if (checkAndSetPendingOperation(METHOD_SIGN_IN, response)) {
       return;
     }

     OptionalPendingResult<GoogleSignInResult> pendingResult =
         Auth.GoogleSignInApi.silentSignIn(googleApiClient);
     if (pendingResult.isDone()) {
       onSignInResult(pendingResult.get());
     } else {
       pendingResult.setResultCallback(
           new ResultCallback<GoogleSignInResult>() {
             @Override
             public void onResult(GoogleSignInResult result) {
               onSignInResult(result);
             }
           });
     }
   }

   /**
    * Signs the user in via the sign-in user interface, including the OAuth consent flow if scopes
    * were requested.
    */
   private void signIn(Response response) {
     if (checkAndSetPendingOperation(METHOD_SIGN_IN, response)) {
       return;
     }

     Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
     activity.startActivityForResult(signInIntent, requestCode);
   }

   /**
    * Gets an OAuth access token with the scopes that were specified during {@link
    * #init(response,List<String>) initialization} for the user with the specified email
    * address.
    */
   private void getToken(Response response, final String email) {
     if (email == null) {
       response.error(ERROR_REASON_EXCEPTION, "Email is null", null);
       return;
     }

     if (checkAndSetPendingOperation(METHOD_GET_TOKEN, response)) {
       return;
     }

     Callable<String> getTokenTask =
         new Callable<String>() {
           @Override
           public String call() throws Exception {
             Account account = new Account(email, "com.google");
             String scopesStr = "oauth2:" + Joiner.on(' ').join(requestedScopes);
             return GoogleAuthUtil.getToken(activity.getApplication(), account, scopesStr);
           }
         };

     backgroundTaskRunner.runInBackground(
         getTokenTask,
         new BackgroundTaskRunner.Callback<String>() {
           @Override
           public void run(Future<String> tokenFuture) {
             try {
               finishWithSuccess(tokenFuture.get());
             } catch (ExecutionException e) {
               Log.e(TAG, "Exception getting access token", e);
               finishWithError(ERROR_REASON_EXCEPTION, e.getCause().getMessage());
             } catch (InterruptedException e) {
               finishWithError(ERROR_REASON_EXCEPTION, e.getMessage());
             }
           }
         });
   }

   /**
    * Signs the user out. Their credentials may remain valid, meaning they'll be able to silently
    * sign back in.
    */
   private void signOut(Response response) {
     if (checkAndSetPendingOperation(METHOD_SIGN_OUT, response)) {
       return;
     }

     Auth.GoogleSignInApi.signOut(googleApiClient)
         .setResultCallback(
             new ResultCallback<Status>() {
               @Override
               public void onResult(Status status) {
                 // TODO(tvolkert): communicate status back to user
                 finishWithSuccess(null);
               }
             });
   }

   /** Signs the user out, and revokes their credentials. */
   private void disconnect(Response response) {
     if (checkAndSetPendingOperation(METHOD_DISCONNECT, response)) {
       return;
     }

     Auth.GoogleSignInApi.revokeAccess(googleApiClient)
         .setResultCallback(
             new ResultCallback<Status>() {
               @Override
               public void onResult(Status status) {
                 // TODO(tvolkert): communicate status back to user
                 finishWithSuccess(null);
               }
             });
   }

   /**
    * Invoked when the GMS client has successfully connected to the GMS server. This signals that
    * this listener is properly initialized.
    */
   @Override
   public void onConnected(Bundle connectionHint) {
     // We can get reconnected if, e.g. the activity is paused and resumed.
     if (pendingOperation != null && pendingOperation.method.equals(METHOD_INIT)) {
       finishWithSuccess(null);
     }
   }

   /**
    * Invoked when the GMS client was unable to connect to the GMS server, either because of an error
    * the user was unable to resolve, or because the user canceled the resolution (e.g. cancelling a
    * dialog instructing them to upgrade Google Play Services). This signals that we were unable to
    * properly initialize this listener.
    */
   @Override
   public void onConnectionFailed(@NonNull ConnectionResult result) {
     // We can attempt to reconnect if, e.g. the activity is paused and resumed.
     if (pendingOperation != null && pendingOperation.method.equals(METHOD_INIT)) {
       finishWithError(ERROR_REASON_CONNECTION_FAILED, result.toString());
     }
   }

   @Override
   public void onConnectionSuspended(int cause) {
     // TODO(jackson): implement
     Log.w(TAG, "The GMS server connection has been suspended (" + cause + ")");
   }

   public void onActivityResult(int requestCode, int resultCode, Intent data) {
     if (requestCode != this.requestCode) {
       // We're only interested in the "sign in" activity result
       return;
     }

     if (pendingOperation == null || !pendingOperation.method.equals(METHOD_SIGN_IN)) {
       Log.w(TAG, "Unexpected activity result; sign-in not in progress");
       return;
     }

     if (resultCode != Activity.RESULT_OK) {
       finishWithError(ERROR_REASON_CANCELED, String.valueOf(resultCode));
       return;
     }

     onSignInResult(Auth.GoogleSignInApi.getSignInResultFromIntent(data));
   }

   private void onSignInResult(GoogleSignInResult result) {
     if (result.isSuccess()) {
       finishWithSuccess(getSignInResponse(result.getSignInAccount()));
     } else {
       finishWithSuccess(null);
       // TODO(jackson): Communicate status. If they
       // finishWithError(ERROR_REASON_STATUS, result.getStatus().toString());
     }
   }

  private static HashMap<String, String> getSignInResponse(GoogleSignInAccount account) {
    HashMap result = new HashMap<String, String>();
    result.put("displayName", account.getDisplayName());
    result.put("email", account.getEmail());
    result.put("id", account.getId());
    Uri photoUrl = account.getPhotoUrl();
    result.put("photoUrl", photoUrl != null ? photoUrl.toString() : null);
    return result;
   }

   private void finishWithSuccess(Object result) {
     for (Response response : pendingOperation.responseQueue) {
       response.success(result);
     }
     pendingOperation = null;
   }

   private void finishWithError(String errorCode, String errorMessage) {
     for (Response response : pendingOperation.responseQueue) {
       response.error(errorCode, errorMessage, null);
     }
     pendingOperation = null;
   }
 }
