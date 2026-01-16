package com.deldev.capacitor.GoogleAuth;

import android.accounts.Account;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.deldev.capacitor.GoogleAuth.capacitorgoogleauth.R;
import com.getcapacitor.JSArray;
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
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@CapacitorPlugin(name = "GoogleAuth")
public class GoogleAuth extends Plugin {

  private static final String TAG = "GoogleAuth";
  private static final int SIGN_IN_CANCELLED = 12501;
  private static final int NETWORK_ERROR = 7;

  private GoogleSignInClient googleSignInClient;

  @Override
  public void load() {
    Log.d(TAG, "Plugin loaded");
  }

  private void loadSignInClient(String clientId, boolean forceCodeForRefreshToken, String[] scopeArray) {
    try {
      GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
          .requestIdToken(clientId)
          .requestEmail()
          .requestProfile();

      if (forceCodeForRefreshToken) {
        builder.requestServerAuthCode(clientId, true);
      }

      // Build scopes list
      if (scopeArray != null && scopeArray.length > 0) {
        List<Scope> scopes = new ArrayList<>();
        for (String scope : scopeArray) {
          String trimmedScope = scope.trim();
          if (!trimmedScope.isEmpty()) {
            scopes.add(new Scope(trimmedScope));
          }
        }

        if (!scopes.isEmpty()) {
          Scope firstScope = scopes.get(0);
          if (scopes.size() > 1) {
            Scope[] additionalScopes = scopes.subList(1, scopes.size()).toArray(new Scope[0]);
            builder.requestScopes(firstScope, additionalScopes);
          } else {
            builder.requestScopes(firstScope);
          }
        }
      }

      googleSignInClient = GoogleSignIn.getClient(getContext(), builder.build());
      Log.d(TAG, "GoogleSignInClient initialized successfully");
    } catch (Exception e) {
      Log.e(TAG, "Error initializing GoogleSignInClient", e);
      throw e;
    }
  }

  @PluginMethod
  public void initialize(PluginCall call) {
    try {
      String configClientId = getConfig().getString("androidClientId",
          getConfig().getString("clientId", getContext().getString(R.string.server_client_id)));
      boolean configForceCodeForRefreshToken = getConfig().getBoolean("forceCodeForRefreshToken", false);
      String configScopeArray = getConfig().getString("scopes", "email,profile");

      String clientId = call.getString("clientId", configClientId);
      boolean forceCodeForRefreshToken = call.getBoolean("grantOfflineAccess", configForceCodeForRefreshToken);
      
      // Handle scopes - can be array or comma-separated string
      String[] scopeArray;
      if (call.hasOption("scopes")) {
        List<String> scopesList = null;
        try {
          JSArray scopesArray = call.getArray("scopes");
          if (scopesArray != null) {
            scopesList = scopesArray.toList();
          }
        } catch (Exception e) {
          Log.e(TAG, "Error parsing scopes array", e);
        }
        
        if (scopesList != null && !scopesList.isEmpty()) {
          scopeArray = scopesList.toArray(new String[0]);
        } else {
          scopeArray = parseScopes(configScopeArray);
        }
      } else {
        scopeArray = parseScopes(configScopeArray);
      }

      if (clientId == null || clientId.isEmpty()) {
        call.reject("Client ID is required");
        return;
      }

      loadSignInClient(clientId, forceCodeForRefreshToken, scopeArray);
      call.resolve();
    } catch (Exception e) {
      Log.e(TAG, "Error in initialize", e);
      call.reject("Failed to initialize: " + e.getMessage(), e);
    }
  }

  private String[] parseScopes(String scopesStr) {
    if (scopesStr == null || scopesStr.isEmpty()) {
      return new String[]{"email", "profile"};
    }
    
    // Remove brackets, quotes, and extra whitespace
    String cleaned = scopesStr
        .replaceAll("[\\[\\]\"]", "")
        .trim();
    
    // Split by comma and clean each scope
    return Arrays.stream(cleaned.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);
  }

  @PluginMethod
  public void signIn(PluginCall call) {
    if (googleSignInClient == null) {
      call.reject("Google services are not ready. Please call initialize() first");
      return;
    }

    // Check if user is already signed in
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
    if (account != null) {
      Log.d(TAG, "User already signed in, using existing account");
      resolveWithAccount(call, account);
      return;
    }

    Intent signInIntent = googleSignInClient.getSignInIntent();
    saveCall(call);
    startActivityForResult(call, signInIntent, "signInResult");
  }

  @ActivityCallback
  private void signInResult(PluginCall call, ActivityResult result) {
    if (call == null) {
      Log.e(TAG, "signInResult: call is null");
      return;
    }

    if (result.getData() == null) {
      call.reject("Sign-in failed: No data returned");
      return;
    }

    try {
      Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
      GoogleSignInAccount account = task.getResult(ApiException.class);
      
      if (account == null) {
        call.reject("Sign-in failed: Account is null");
        return;
      }

      resolveWithAccount(call, account);

    } catch (ApiException e) {
      Log.e(TAG, "Sign-in failed with status code: " + e.getStatusCode(), e);
      
      if (e.getStatusCode() == SIGN_IN_CANCELLED) {
        call.reject("The user canceled the sign-in flow.", "USER_CANCELLED", e);
      } else if (e.getStatusCode() == NETWORK_ERROR) {
        call.reject("Network error occurred. Please check your connection.", "NETWORK_ERROR", e);
      } else {
        call.reject("Sign-in failed with code: " + e.getStatusCode(), String.valueOf(e.getStatusCode()), e);
      }
    } catch (Exception e) {
      Log.e(TAG, "Unexpected error during sign-in", e);
      call.reject("An unexpected error occurred", e);
    }
  }

  private void resolveWithAccount(PluginCall call, GoogleSignInAccount account) {
    try {
      JSObject authentication = new JSObject();
      authentication.put("idToken", account.getIdToken());
      
      // Get access token if available
      Account gAccount = account.getAccount();
      if (gAccount != null) {
        // Note: Getting access token requires async operation
        // For now, we'll use the ID token
        authentication.put("accessToken", account.getIdToken());
      } else {
        authentication.put("accessToken", "");
      }
      
      authentication.put("refreshToken", "");

      JSObject user = new JSObject();
      user.put("serverAuthCode", account.getServerAuthCode() != null ? account.getServerAuthCode() : "");
      user.put("idToken", account.getIdToken());
      user.put("authentication", authentication);
      user.put("name", account.getDisplayName() != null ? account.getDisplayName() : "");
      user.put("displayName", account.getDisplayName() != null ? account.getDisplayName() : "");
      user.put("email", account.getEmail() != null ? account.getEmail() : "");
      user.put("familyName", account.getFamilyName() != null ? account.getFamilyName() : "");
      user.put("givenName", account.getGivenName() != null ? account.getGivenName() : "");
      user.put("id", account.getId() != null ? account.getId() : "");
      user.put("imageUrl", account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "");

      call.resolve(user);
    } catch (Exception e) {
      Log.e(TAG, "Error resolving with account", e);
      call.reject("Failed to process account information", e);
    }
  }

  @PluginMethod
  public void refresh(PluginCall call) {
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
    if (account == null) {
      call.reject("User not logged in.");
      return;
    }

    try {
      JSObject authentication = new JSObject();
      authentication.put("idToken", account.getIdToken());
      authentication.put("accessToken", account.getIdToken()); // Using ID token as access token
      authentication.put("refreshToken", "");
      call.resolve(authentication);
    } catch (Exception e) {
      Log.e(TAG, "Error refreshing token", e);
      call.reject("Failed to refresh token", e);
    }
  }

  @PluginMethod
  public void signOut(PluginCall call) {
    if (googleSignInClient == null) {
      call.reject("Google services are not ready. Please call initialize() first");
      return;
    }
    
    googleSignInClient.signOut()
        .addOnSuccessListener(aVoid -> {
          Log.d(TAG, "Sign out successful");
          call.resolve();
        })
        .addOnFailureListener(e -> {
          Log.e(TAG, "Sign out failed", e);
          call.reject("Sign out failed", e);
        });
  }
}