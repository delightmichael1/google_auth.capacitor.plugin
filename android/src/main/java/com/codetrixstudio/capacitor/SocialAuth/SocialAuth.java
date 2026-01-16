package com.deldev.capacitor.SocialAuth;

import android.accounts.Account;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.deldev.capacitor.GoogleAuth.capacitorgoogleauth.R;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
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

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@CapacitorPlugin(name = "SocialAuth")
public class SocialAuth extends Plugin {

    private static final String TAG = "SocialAuth";
    private static final int SIGN_IN_CANCELLED = 12501;
    private static final int NETWORK_ERROR = 7;

    // Google Sign-In
    private GoogleSignInClient googleSignInClient;

    // Facebook Sign-In
    private CallbackManager facebookCallbackManager;
    private final LoginManager facebookLoginManager = LoginManager.getInstance();

    @Override
    public void load() {
        Log.d(TAG, "SocialAuth Plugin loaded");
    }

    // ============================================================================
    // GOOGLE AUTH IMPLEMENTATION
    // ============================================================================

    private void loadGoogleSignInClient(String clientId, boolean forceCodeForRefreshToken, String[] scopeArray) {
        try {
            GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .requestProfile();

            if (forceCodeForRefreshToken) {
                builder.requestServerAuthCode(clientId, true);
            }

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
    public void initializeGoogle(PluginCall call) {
        try {
            String configClientId = getConfig().getString("androidClientId",
                    getConfig().getString("clientId", getContext().getString(R.string.server_client_id)));
            boolean configForceCodeForRefreshToken = getConfig().getBoolean("forceCodeForRefreshToken", false);
            String configScopeArray = getConfig().getString("scopes", "email,profile");

            String clientId = call.getString("clientId", configClientId);
            boolean forceCodeForRefreshToken = call.getBoolean("grantOfflineAccess", configForceCodeForRefreshToken);

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

            loadGoogleSignInClient(clientId, forceCodeForRefreshToken, scopeArray);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error in initializeGoogle", e);
            call.reject("Failed to initialize Google: " + e.getMessage(), e);
        }
    }

    private String[] parseScopes(String scopesStr) {
        if (scopesStr == null || scopesStr.isEmpty()) {
            return new String[]{"email", "profile"};
        }

        String cleaned = scopesStr.replaceAll("[\\[\\]\"]", "").trim();
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @PluginMethod
    public void signInWithGoogle(PluginCall call) {
        if (googleSignInClient == null) {
            call.reject("Google services are not ready. Please call initializeGoogle() first");
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
        if (account != null) {
            Log.d(TAG, "User already signed in, using existing account");
            resolveWithGoogleAccount(call, account);
            return;
        }

        Intent signInIntent = googleSignInClient.getSignInIntent();
        saveCall(call);
        startActivityForResult(call, signInIntent, "googleSignInResult");
    }

    @ActivityCallback
    private void googleSignInResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            Log.e(TAG, "googleSignInResult: call is null");
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

            resolveWithGoogleAccount(call, account);

        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed with status code: " + e.getStatusCode(), e);

            if (e.getStatusCode() == SIGN_IN_CANCELLED) {
                call.reject("The user canceled the sign-in flow.", "USER_CANCELLED", e);
            } else if (e.getStatusCode() == NETWORK_ERROR) {
                call.reject("Network error occurred. Please check your connection.", "NETWORK_ERROR", e);
            } else {
                call.reject("Sign-in failed with code: " + e.getStatusCode(), String.valueOf(e.getStatusCode()), e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during Google sign-in", e);
            call.reject("An unexpected error occurred", e);
        }
    }

    private void resolveWithGoogleAccount(PluginCall call, GoogleSignInAccount account) {
        try {
            JSObject authentication = new JSObject();
            authentication.put("idToken", account.getIdToken());

            Account gAccount = account.getAccount();
            if (gAccount != null) {
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
            Log.e(TAG, "Error resolving with Google account", e);
            call.reject("Failed to process account information", e);
        }
    }

    @PluginMethod
    public void refreshGoogle(PluginCall call) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
        if (account == null) {
            call.reject("User not logged in.");
            return;
        }

        try {
            JSObject authentication = new JSObject();
            authentication.put("idToken", account.getIdToken());
            authentication.put("accessToken", account.getIdToken());
            authentication.put("refreshToken", "");
            call.resolve(authentication);
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing Google token", e);
            call.reject("Failed to refresh token", e);
        }
    }

    @PluginMethod
    public void signOutGoogle(PluginCall call) {
        if (googleSignInClient == null) {
            call.reject("Google services are not ready. Please call initializeGoogle() first");
            return;
        }

        googleSignInClient.signOut()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Google sign out successful");
                    call.resolve();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Google sign out failed", e);
                    call.reject("Sign out failed", e);
                });
    }

    // ============================================================================
    // FACEBOOK AUTH IMPLEMENTATION
    // ============================================================================

    @PluginMethod
    public void initializeFacebook(PluginCall call) {
        String appId = call.getString("appId");

        if (appId == null || appId.isEmpty()) {
            call.reject("Facebook App ID is required");
            return;
        }

        try {
            FacebookSdk.setApplicationId(appId);
            FacebookSdk.sdkInitialize(getContext());
            facebookCallbackManager = CallbackManager.Factory.create();
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Facebook SDK", e);
            call.reject("Failed to initialize Facebook SDK", e);
        }
    }

    @PluginMethod
    public void signInWithFacebook(PluginCall call) {
        if (facebookCallbackManager == null) {
            call.reject("Facebook SDK not initialized. Call initializeFacebook() first.");
            return;
        }

        JSArray permissionsArray = call.getArray("permissions");
        List<String> permissions = new ArrayList<>();

        if (permissionsArray != null) {
            for (int i = 0; i < permissionsArray.length(); i++) {
                try {
                    permissions.add(permissionsArray.getString(i));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing permission at index " + i, e);
                }
            }
        } else {
            permissions.add("public_profile");
            permissions.add("email");
        }

        facebookLoginManager.registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult result) {
                AccessToken accessToken = result.getAccessToken();
                JSObject response = new JSObject();
                response.put("accessToken", createFacebookAccessTokenObject(accessToken));

                // Get user profile
                getFacebookUserProfile(accessToken, call);
            }

            @Override
            public void onCancel() {
                JSObject response = new JSObject();
                response.put("accessToken", null);
                call.resolve(response);
            }

            @Override
            public void onError(FacebookException error) {
                call.reject("Facebook login failed: " + error.getMessage(), error);
            }
        });

        facebookLoginManager.logInWithReadPermissions(getActivity(), permissions);
    }

    private void getFacebookUserProfile(AccessToken accessToken, PluginCall call) {
        GraphRequest request = GraphRequest.newMeRequest(accessToken, (jsonObject, response) -> {
            if (response.getError() != null) {
                Log.e(TAG, "Error fetching Facebook profile: " + response.getError().getErrorMessage());
                // Return just the access token if profile fetch fails
                JSObject result = new JSObject();
                result.put("accessToken", createFacebookAccessTokenObject(accessToken));
                call.resolve(result);
            } else {
                try {
                    JSObject user = convertFacebookProfile(jsonObject);
                    JSObject result = new JSObject();
                    result.put("accessToken", createFacebookAccessTokenObject(accessToken));
                    result.put("user", user);
                    call.resolve(result);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Facebook profile", e);
                    JSObject result = new JSObject();
                    result.put("accessToken", createFacebookAccessTokenObject(accessToken));
                    call.resolve(result);
                }
            }
        });

        android.os.Bundle parameters = new android.os.Bundle();
        parameters.putString("fields", "id,name,email,picture,first_name,last_name");
        request.setParameters(parameters);
        request.executeAsync();
    }

    @PluginMethod
    public void getFacebookProfile(PluginCall call) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();

        if (accessToken == null || accessToken.isExpired()) {
            call.reject("No valid Facebook access token available");
            return;
        }

        JSArray fieldsArray = call.getArray("fields");
        List<String> fields = new ArrayList<>();

        if (fieldsArray != null) {
            for (int i = 0; i < fieldsArray.length(); i++) {
                try {
                    fields.add(fieldsArray.getString(i));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing field at index " + i, e);
                }
            }
        } else {
            fields.addAll(Arrays.asList("id", "name", "email", "picture"));
        }

        GraphRequest request = GraphRequest.newMeRequest(accessToken, (jsonObject, response) -> {
            if (response.getError() != null) {
                call.reject("Failed to get Facebook profile: " + response.getError().getErrorMessage());
            } else {
                try {
                    JSObject user = convertFacebookProfile(jsonObject);
                    call.resolve(user);
                } catch (Exception e) {
                    call.reject("Failed to parse Facebook profile data", e);
                }
            }
        });

        android.os.Bundle parameters = new android.os.Bundle();
        parameters.putString("fields", String.join(",", fields));
        request.setParameters(parameters);
        request.executeAsync();
    }

    @PluginMethod
    public void getFacebookAccessToken(PluginCall call) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();

        if (accessToken != null && !accessToken.isExpired()) {
            call.resolve(createFacebookAccessTokenObject(accessToken));
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void signOutFacebook(PluginCall call) {
        facebookLoginManager.logOut();
        call.resolve();
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        if (facebookCallbackManager != null) {
            facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private JSObject createFacebookAccessTokenObject(AccessToken accessToken) {
        JSObject token = new JSObject();
        token.put("token", accessToken.getToken());
        token.put("userId", accessToken.getUserId());
        token.put("expirationDate", formatDate(accessToken.getExpires()));
        token.put("permissions", new JSArray(accessToken.getPermissions()));
        token.put("declinedPermissions", new JSArray(accessToken.getDeclinedPermissions()));
        return token;
    }

    private JSObject convertFacebookProfile(JSONObject jsonObject) throws Exception {
        JSObject user = new JSObject();
        user.put("id", jsonObject.optString("id", ""));
        user.put("email", jsonObject.optString("email"));
        user.put("name", jsonObject.optString("name"));
        user.put("givenName", jsonObject.optString("first_name"));
        user.put("familyName", jsonObject.optString("last_name"));

        if (jsonObject.has("picture")) {
            JSONObject picture = jsonObject.getJSONObject("picture");
            if (picture.has("data")) {
                JSONObject pictureData = picture.getJSONObject("data");
                user.put("imageUrl", pictureData.optString("url"));
            }
        }

        return user;
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}