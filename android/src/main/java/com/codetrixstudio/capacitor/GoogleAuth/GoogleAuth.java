package com.deldev.capacitor.GoogleAuth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.deldev.capacitor.GoogleAuth.capacitorgoogleauth.R;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "GoogleAuth")
public class GoogleAuth extends Plugin {

  private static final String VERIFY_TOKEN_URL = "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=";
  private static final String FIELD_TOKEN_EXPIRES_IN = "expires_in";
  private static final String FIELD_ACCESS_TOKEN = "accessToken";
  private static final String FIELD_TOKEN_EXPIRES = "expires";
  private static final int SIGN_IN_CANCELLED = 12501;
  public static final int KAssumeStaleTokenSec = 60;

  private GoogleSignInClient googleSignInClient;

  @Override
  public void load() {
    Log.d("GoogleAuth", "Plugin loaded");
  }

  private void loadSignInClient(String clientId, boolean forceCodeForRefreshToken, String[] scopeArray) {
    GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(clientId)
        .requestEmail();

    if (forceCodeForRefreshToken) {
      builder.requestServerAuthCode(clientId, true);
    }

    Scope[] scopes = new Scope[scopeArray.length - 1];
    Scope firstScope = new Scope(scopeArray[0]);
    for (int i = 1; i < scopeArray.length; i++) {
      scopes[i - 1] = new Scope(scopeArray[i]);
    }
    builder.requestScopes(firstScope, scopes);

    googleSignInClient = GoogleSignIn.getClient(getContext(), builder.build());
  }

  @PluginMethod
  public void initialize(PluginCall call) {
    String configClientId = getConfig().getString("androidClientId",
        getConfig().getString("clientId", getContext().getString(R.string.server_client_id)));
    boolean configForceCodeForRefreshToken = getConfig().getBoolean("forceCodeForRefreshToken", false);
    String configScopeArray = getConfig().getString("scopes", "");

    String clientId = call.getString("clientId", configClientId);
    boolean forceCodeForRefreshToken = call.getBoolean("grantOfflineAccess", configForceCodeForRefreshToken);
    String scopesStr = call.getString("scopes", configScopeArray)
        .replaceAll("[\"\\[\\] ]", "")
        .replace("\\", "");

    String[] scopeArray = scopesStr.split(",");

    loadSignInClient(clientId, forceCodeForRefreshToken, scopeArray);
    call.resolve();
  }

  @PluginMethod
  public void signIn(PluginCall call) {
    if (googleSignInClient == null) {
      call.reject("Google services are not ready. Please call initialize() first");
      return;
    }
    Intent signInIntent = googleSignInClient.getSignInIntent();
    saveCall(call);
    startActivityForResult(call, signInIntent, "signInResult");
  }

  @ActivityCallback
  private void signInResult(PluginCall call, ActivityResult result) {
    if (call == null)
      return;

    if (result.getData() == null) {
      call.reject("Sign-in failed: No data returned");
      return;
    }

    try {
      GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
          .getResult(ApiException.class);

      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.execute(() -> {
        try {
          JSONObject accessTokenObject = getAuthToken(account.getAccount(), true);

          JSObject authentication = new JSObject();
          authentication.put("idToken", account.getIdToken());
          authentication.put(FIELD_ACCESS_TOKEN, accessTokenObject.get(FIELD_ACCESS_TOKEN));
          authentication.put(FIELD_TOKEN_EXPIRES, accessTokenObject.get(FIELD_TOKEN_EXPIRES));
          authentication.put(FIELD_TOKEN_EXPIRES_IN, accessTokenObject.get(FIELD_TOKEN_EXPIRES_IN));

          JSObject user = new JSObject();
          user.put("serverAuthCode", account.getServerAuthCode());
          user.put("idToken", account.getIdToken());
          user.put("authentication", authentication);
          user.put("name", account.getDisplayName());
          user.put("displayName", account.getDisplayName());
          user.put("email", account.getEmail());
          user.put("familyName", account.getFamilyName());
          user.put("givenName", account.getGivenName());
          user.put("id", account.getId());
          user.put("imageUrl", account.getPhotoUrl());

          call.resolve(user);
        } catch (Exception e) {
          call.reject("Something went wrong while retrieving access token", e);
        }
      });

    } catch (ApiException e) {
      if (e.getStatusCode() == SIGN_IN_CANCELLED) {
        call.reject("The user canceled the sign-in flow.", String.valueOf(e.getStatusCode()));
      } else {
        call.reject("Sign-in failed", String.valueOf(e.getStatusCode()));
      }
    }
  }

  @PluginMethod
  public void refresh(PluginCall call) {
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
    if (account == null) {
      call.reject("User not logged in.");
    } else {
      try {
        JSONObject accessTokenObject = getAuthToken(account.getAccount(), true);
        JSObject authentication = new JSObject();
        authentication.put("idToken", account.getIdToken());
        authentication.put(FIELD_ACCESS_TOKEN, accessTokenObject.get(FIELD_ACCESS_TOKEN));
        authentication.put("refreshToken", "");
        call.resolve(authentication);
      } catch (Exception e) {
        call.reject("Something went wrong while retrieving access token", e);
      }
    }
  }

  @PluginMethod
  public void signOut(PluginCall call) {
    if (googleSignInClient == null) {
      call.reject("Google services are not ready. Please call initialize() first");
      return;
    }
    googleSignInClient.signOut().addOnSuccessListener(aVoid -> call.resolve())
        .addOnFailureListener(e -> call.reject("Sign out failed", e));
  }

  private JSONObject getAuthToken(Account account, boolean retry) throws Exception {
    AccountManager manager = AccountManager.get(getContext());
    AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "oauth2:profile email", null, false, null,
        null);
    Bundle bundle = future.getResult();
    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
    try {
      return verifyToken(authToken);
    } catch (IOException e) {
      if (retry) {
        manager.invalidateAuthToken("com.google", authToken);
        return getAuthToken(account, false);
      } else {
        throw e;
      }
    }
  }

  private JSONObject verifyToken(String authToken) throws IOException, JSONException {
    @SuppressWarnings("deprecation")
    URL url = new URL(VERIFY_TOKEN_URL + authToken);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(true);
    String response = fromStream(new BufferedInputStream(conn.getInputStream()));

    JSONObject json = new JSONObject(response);
    int expires_in = json.getInt(FIELD_TOKEN_EXPIRES_IN);
    if (expires_in < KAssumeStaleTokenSec) {
      throw new IOException("Auth token soon expiring.");
    }
    json.put(FIELD_ACCESS_TOKEN, authToken);
    json.put(FIELD_TOKEN_EXPIRES, expires_in + (System.currentTimeMillis() / 1000));
    return json;
  }

  private static String fromStream(InputStream is) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line).append("\n");
    }
    reader.close();
    return sb.toString();
  }
}
