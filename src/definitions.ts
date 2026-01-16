/// <reference types="@capacitor/cli" />

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    GoogleAuth: GoogleAuthPluginOptions;
    SocialAuth: SocialAuthPluginOptions;
  }
}

// ============================================================================
// GOOGLE AUTH TYPES (Existing - unchanged)
// ============================================================================

export interface User {
  /**
   * The unique identifier for the user.
   */
  id: string;

  /**
   * The email address associated with the user.
   */
  email: string;

  /**
   * The user's full name.
   */
  name: string;

  /**
   * The family name (last name) of the user.
   */
  familyName: string;

  /**
   * The given name (first name) of the user.
   */
  givenName: string;

  /**
   * The URL of the user's profile picture.
   */
  imageUrl: string;

  /**
   * The server authentication code.
   * Use this to exchange for tokens on your backend.
   */
  serverAuthCode: string;

  /**
   * The authentication details including access, refresh and ID tokens.
   */
  authentication: Authentication;
}

export interface Authentication {
  /**
   * The access token obtained during authentication.
   */
  accessToken: string;

  /**
   * The ID token obtained during authentication.
   */
  idToken: string;

  /**
   * The refresh token.
   * @warning This property is applicable only for mobile platforms (iOS and Android).
   */
  refreshToken?: string;
}

export interface GoogleAuthPluginOptions {
  /**
   * The default app's client ID, found and created in the Google Developers Console.
   * common for Android or iOS
   * @example xxxxxx-xxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
   * @since 3.1.0
   */
  clientId?: string;

  /**
   * Specific client ID key for iOS
   * @since 3.1.0
   */
  iosClientId?: string;

  /**
   * Specific client ID key for Android
   * @since 3.1.0
   */
  androidClientId?: string;

  /**
   * Specifies the default scopes required for accessing Google APIs.
   * @example ["profile", "email"]
   * @default ["email", "profile", "openid"]
   * @see [Google OAuth2 Scopes](https://developers.google.com/identity/protocols/oauth2/scopes)
   */
  scopes?: string[];

  /**
   * This is used for offline access and server side handling
   * @example xxxxxx-xxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
   * @default false
   */
  serverClientId?: string;

  /**
   * Force user to select email address to regenerate AuthCode used to get a valid refreshtoken (work on iOS and Android)
   * @default false
   */
  forceCodeForRefreshToken?: boolean;
}

export interface InitOptions {
  /**
   * The app's client ID, found and created in the Google Developers Console.
   * Common for Android or iOS.
   * The default is defined in the configuration.
   * @example xxxxxx-xxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
   * @since 3.1.0
   */
  clientId?: string;

  /**
   * Specifies the scopes required for accessing Google APIs
   * The default is defined in the configuration.
   * @example ["profile", "email"]
   * @see [Google OAuth2 Scopes](https://developers.google.com/identity/protocols/oauth2/scopes)
   */
  scopes?: string[];

  /**
   * Set if your application needs to refresh access tokens when the user is not present at the browser.
   * In response use `serverAuthCode` key
   *
   * @default false
   * @since 3.1.0
   * */
  grantOfflineAccess?: boolean;

  /**
   * Backend URL for token exchange (optional)
   * If provided, the plugin can help with backend communication
   * @example "https://api.yourapp.com/auth"
   */
  backendUrl?: string;
}

export interface GoogleAuthPlugin {
  /**
   * Initializes the GoogleAuthPlugin, loading the gapi library and setting up the plugin.
   * @param options - Optional initialization options.
   * @since 3.1.0
   */
  initialize(options?: InitOptions): Promise<void>;

  /**
   * Initiates the sign-in process and returns a Promise that resolves with the user information.
   *
   * When `grantOfflineAccess` is true, the `serverAuthCode` will be populated.
   * You should send this code to your backend to exchange for tokens.
   *
   * When `grantOfflineAccess` is false, you'll receive an `accessToken` directly.
   */
  signIn(): Promise<User>;

  /**
   * Refreshes the authentication token and returns a Promise that resolves with the updated authentication details.
   */
  refresh(): Promise<Authentication>;

  /**
   * Signs out the user and returns a Promise.
   */
  signOut(): Promise<any>;
}

// ============================================================================
// FACEBOOK AUTH TYPES (New)
// ============================================================================

export interface FacebookUser {
  /**
   * The unique identifier for the user.
   */
  id: string;

  /**
   * The email address associated with the user.
   */
  email?: string;

  /**
   * The user's full name.
   */
  name?: string;

  /**
   * The family name (last name) of the user.
   */
  familyName?: string;

  /**
   * The given name (first name) of the user.
   */
  givenName?: string;

  /**
   * The URL of the user's profile picture.
   */
  imageUrl?: string;

  /**
   * The authentication details including access token.
   * Note: When returned from signInWithFacebook, this contains the full token info.
   * When returned from getFacebookProfile, this may be partially populated.
   */
  authentication?: FacebookAuthentication;
}

export interface FacebookAuthentication {
  /**
   * The access token string obtained during authentication.
   */
  token: string;

  /**
   * User ID associated with the token.
   */
  userId: string;

  /**
   * Token expiration date (ISO 8601 format).
   */
  expirationDate: string;

  /**
   * Permissions granted.
   */
  permissions: string[];

  /**
   * Permissions declined.
   */
  declinedPermissions: string[];
}

export interface FacebookInitOptions {
  /**
   * Facebook App ID
   */
  appId: string;
}

export interface FacebookLoginOptions {
  /**
   * Permissions to request
   * @default ['public_profile', 'email']
   */
  permissions?: string[];
}

export interface FacebookLoginResponse {
  /**
   * Access token information
   */
  accessToken: FacebookAuthentication | null;

  /**
   * User profile information (if login successful)
   */
  user?: FacebookUser;
}

export interface FacebookProfileOptions {
  /**
   * Fields to request from the profile
   * @default ['id', 'name', 'email', 'picture']
   */
  fields?: string[];
}

// ============================================================================
// UNIFIED SOCIAL AUTH PLUGIN (New)
// ============================================================================

export interface SocialAuthPluginOptions {
  /**
   * Google OAuth configuration
   */
  google?: GoogleAuthPluginOptions;

  /**
   * Facebook OAuth configuration
   */
  facebook?: {
    appId: string;
  };
}

export interface SocialAuthPlugin {
  // Google Auth Methods
  /**
   * Initializes Google authentication
   * @param options - Google auth options
   */
  initializeGoogle(options?: InitOptions): Promise<void>;

  /**
   * Sign in with Google
   */
  signInWithGoogle(): Promise<User>;

  /**
   * Refresh Google authentication token
   */
  refreshGoogle(): Promise<Authentication>;

  /**
   * Sign out from Google
   */
  signOutGoogle(): Promise<void>;

  // Facebook Auth Methods
  /**
   * Initializes Facebook authentication
   * @param options - Facebook auth options
   */
  initializeFacebook(options: FacebookInitOptions): Promise<void>;

  /**
   * Sign in with Facebook
   * @param options - Login options including permissions
   */
  signInWithFacebook(options?: FacebookLoginOptions): Promise<FacebookLoginResponse>;

  /**
   * Get Facebook user profile
   * @param options - Profile options including fields to fetch
   */
  getFacebookProfile(options?: FacebookProfileOptions): Promise<FacebookUser>;

  /**
   * Get current Facebook access token
   */
  getFacebookAccessToken(): Promise<FacebookAuthentication | null>;

  /**
   * Sign out from Facebook
   */
  signOutFacebook(): Promise<void>;
}
