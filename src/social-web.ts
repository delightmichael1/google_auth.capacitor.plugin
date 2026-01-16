import { WebPlugin } from '@capacitor/core';
import {
  SocialAuthPlugin,
  InitOptions,
  User,
  Authentication,
  FacebookInitOptions,
  FacebookLoginOptions,
  FacebookLoginResponse,
  FacebookProfileOptions,
  FacebookUser,
  FacebookAuthentication,
} from './definitions';

// Declare global types
declare global {
  interface Window {
    google: any;
    FB: any;
    fbAsyncInit: () => void;
  }
}

export class SocialAuthWeb extends WebPlugin implements SocialAuthPlugin {
  // Google properties
  private googleTokenClient: any;
  private googleOptions: InitOptions & { backendUrl?: string };
  private currentGoogleUser: User | null = null;
  private googleInitialized: boolean = false;

  // Facebook properties
  private facebookAppId: string = '';
  private facebookInitialized: boolean = false;

  constructor() {
    super();
  }

  // ============================================================================
  // GOOGLE AUTH IMPLEMENTATION
  // ============================================================================

  private loadGoogleScript(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (typeof document === 'undefined') {
        reject(new Error('Document is not available'));
        return;
      }

      const scriptId = 'google-identity-services';
      const existingScript = document.getElementById(scriptId);

      if (existingScript) {
        resolve();
        return;
      }

      const head = document.getElementsByTagName('head')[0];
      const script = document.createElement('script');

      script.type = 'text/javascript';
      script.defer = true;
      script.async = true;
      script.id = scriptId;
      script.src = 'https://accounts.google.com/gsi/client';

      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load Google Identity Services'));

      head.appendChild(script);
    });
  }

  private async waitForGoogleServices(): Promise<void> {
    return new Promise((resolve) => {
      if (window.google && window.google.accounts) {
        resolve();
        return;
      }

      const checkGoogle = setInterval(() => {
        if (window.google && window.google.accounts) {
          clearInterval(checkGoogle);
          resolve();
        }
      }, 100);
    });
  }

  async initializeGoogle(options: InitOptions = {}): Promise<void> {
    if (typeof window === 'undefined') {
      throw new Error('Window is not available');
    }

    const metaClientId = (document.getElementsByName('google-signin-client_id')[0] as any)?.content;
    const clientId = options.clientId || metaClientId || '';

    if (!clientId) {
      throw new Error('GoogleAuth - clientId is required');
    }

    this.googleOptions = {
      clientId,
      grantOfflineAccess: options.grantOfflineAccess ?? false,
      scopes: options.scopes || ['email', 'profile'],
      backendUrl: options.backendUrl,
    };

    await this.loadGoogleScript();
    await this.waitForGoogleServices();
    this.googleInitialized = true;
  }

  async signInWithGoogle(): Promise<User> {
    if (!this.googleInitialized) {
      throw new Error('Google not initialized. Call initializeGoogle() first.');
    }

    return new Promise<User>(async (resolve, reject) => {
      try {
        const needsOfflineAccess = this.googleOptions.grantOfflineAccess ?? false;

        if (needsOfflineAccess) {
          this.googleTokenClient = window.google.accounts.oauth2.initCodeClient({
            client_id: this.googleOptions.clientId,
            scope: this.googleOptions.scopes.join(' '),
            ux_mode: 'popup',
            callback: async (response: any) => {
              if (response.error) {
                reject(new Error(response.error));
                return;
              }

              const user: User = {
                id: '',
                email: '',
                name: '',
                givenName: '',
                familyName: '',
                imageUrl: '',
                serverAuthCode: response.code,
                authentication: {
                  accessToken: '',
                  idToken: '',
                  refreshToken: '',
                },
              };

              this.currentGoogleUser = user;
              resolve(user);
            },
          });

          this.googleTokenClient.requestCode();
        } else {
          this.googleTokenClient = window.google.accounts.oauth2.initTokenClient({
            client_id: this.googleOptions.clientId,
            scope: this.googleOptions.scopes.join(' '),
            callback: async (response: any) => {
              if (response.error) {
                reject(new Error(response.error));
                return;
              }

              try {
                const userInfo = await this.getGoogleUserInfo(response.access_token);

                const user: User = {
                  id: userInfo.id,
                  email: userInfo.email,
                  name: userInfo.name,
                  givenName: userInfo.givenName,
                  familyName: userInfo.familyName,
                  imageUrl: userInfo.imageUrl,
                  serverAuthCode: '',
                  authentication: {
                    accessToken: response.access_token,
                    idToken: '',
                    refreshToken: '',
                  },
                };

                this.currentGoogleUser = user;
                resolve(user);
              } catch (error) {
                reject(error);
              }
            },
          });

          this.googleTokenClient.requestAccessToken();
        }
      } catch (error) {
        reject(error);
      }
    });
  }

  private async getGoogleUserInfo(accessToken: string): Promise<User> {
    const response = await fetch('https://www.googleapis.com/oauth2/v2/userinfo', {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error('Failed to fetch user info');
    }

    const data = await response.json();

    return {
      id: data.id || '',
      email: data.email || '',
      name: data.name || '',
      givenName: data.given_name || '',
      familyName: data.family_name || '',
      imageUrl: data.picture || '',
      serverAuthCode: '',
      authentication: {
        accessToken: '',
        idToken: '',
        refreshToken: '',
      },
    };
  }

  async refreshGoogle(): Promise<Authentication> {
    if (!this.currentGoogleUser) {
      throw new Error('No user is currently signed in');
    }

    return new Promise((resolve, reject) => {
      this.googleTokenClient = window.google.accounts.oauth2.initTokenClient({
        client_id: this.googleOptions.clientId,
        scope: this.googleOptions.scopes.join(' '),
        callback: (response: any) => {
          if (response.error) {
            reject(new Error(response.error));
            return;
          }

          const authentication = {
            accessToken: response.access_token,
            idToken: '',
            refreshToken: '',
          };

          if (this.currentGoogleUser) {
            this.currentGoogleUser.authentication = authentication;
          }

          resolve(authentication);
        },
      });

      this.googleTokenClient.requestAccessToken({ prompt: '' });
    });
  }

  async signOutGoogle(): Promise<void> {
    if (window.google && window.google.accounts && window.google.accounts.id) {
      window.google.accounts.id.disableAutoSelect();
    }

    if (this.currentGoogleUser && this.currentGoogleUser.authentication.accessToken) {
      try {
        await fetch(`https://oauth2.googleapis.com/revoke?token=${this.currentGoogleUser.authentication.accessToken}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        });
      } catch (error) {
        console.error('Error revoking Google token:', error);
      }
    }

    this.currentGoogleUser = null;
  }

  // ============================================================================
  // FACEBOOK AUTH IMPLEMENTATION
  // ============================================================================

  private loadFacebookScript(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (typeof window === 'undefined') {
        reject(new Error('Window is not available'));
        return;
      }

      if (typeof window.FB !== 'undefined') {
        resolve();
        return;
      }

      window.fbAsyncInit = () => {
        window.FB.init({
          appId: this.facebookAppId,
          cookie: true,
          xfbml: true,
          version: 'v18.0',
        });
        this.facebookInitialized = true;
        resolve();
      };

      const script = document.createElement('script');
      script.id = 'facebook-jssdk';
      script.src = 'https://connect.facebook.net/en_US/sdk.js';
      script.async = true;
      script.defer = true;
      script.onerror = () => reject(new Error('Failed to load Facebook SDK'));

      const firstScript = document.getElementsByTagName('script')[0];
      firstScript.parentNode?.insertBefore(script, firstScript);
    });
  }

  async initializeFacebook(options: FacebookInitOptions): Promise<void> {
    this.facebookAppId = options.appId;

    if (!this.facebookAppId) {
      throw new Error('Facebook App ID is required');
    }

    await this.loadFacebookScript();
  }

  async signInWithFacebook(options: FacebookLoginOptions = {}): Promise<FacebookLoginResponse> {
    if (!this.facebookInitialized) {
      throw new Error('Facebook not initialized. Call initializeFacebook() first.');
    }

    // Check if we're on HTTP (not HTTPS) and not localhost
    if (
      typeof window !== 'undefined' &&
      window.location.protocol === 'http:' &&
      !window.location.hostname.includes('localhost') &&
      !window.location.hostname.includes('127.0.0.1')
    ) {
      throw new Error('Facebook login requires HTTPS. Please use HTTPS or localhost for development.');
    }

    const permissions = options.permissions || ['public_profile', 'email'];

    return new Promise((resolve, reject) => {
      try {
        window.FB.login(
          (response: any) => {
            if (response.authResponse) {
              const accessToken: FacebookAuthentication = {
                token: response.authResponse.accessToken,
                userId: response.authResponse.userID,
                expirationDate: new Date(response.authResponse.data_access_expiration_time * 1000).toISOString(),
                permissions: response.authResponse.grantedScopes?.split(',') || permissions,
                declinedPermissions: [],
              };

              // Get user profile
              this.getFacebookProfile({
                fields: ['id', 'name', 'email', 'picture', 'first_name', 'last_name'],
              })
                .then((profile) => {
                  resolve({ accessToken, user: profile });
                })
                .catch(() => {
                  // Return access token even if profile fetch fails
                  resolve({ accessToken });
                });
            } else {
              // User cancelled or didn't authorize
              resolve({ accessToken: null });
            }
          },
          { scope: permissions.join(',') },
        );
      } catch (error: any) {
        reject(new Error(error.message || 'Facebook login failed'));
      }
    });
  }

  async getFacebookProfile(options: FacebookProfileOptions = {}): Promise<FacebookUser> {
    if (!this.facebookInitialized) {
      throw new Error('Facebook not initialized. Call initializeFacebook() first.');
    }

    const fields = options.fields || ['id', 'name', 'email', 'picture'];

    return new Promise((resolve, reject) => {
      window.FB.api('/me', { fields: fields.join(',') }, (response: any) => {
        if (response && !response.error) {
          const user: FacebookUser = {
            id: response.id || '',
            email: response.email,
            name: response.name,
            givenName: response.first_name,
            familyName: response.last_name,
            imageUrl: response.picture?.data?.url,
          };

          resolve(user);
        } else {
          reject(new Error(response?.error?.message || 'Failed to get profile'));
        }
      });
    });
  }

  async getFacebookAccessToken(): Promise<FacebookAuthentication | null> {
    if (!this.facebookInitialized) {
      throw new Error('Facebook not initialized. Call initializeFacebook() first.');
    }

    return new Promise((resolve) => {
      window.FB.getLoginStatus((response: any) => {
        if (response.status === 'connected' && response.authResponse) {
          const accessToken: FacebookAuthentication = {
            token: response.authResponse.accessToken,
            userId: response.authResponse.userID,
            expirationDate: new Date(response.authResponse.data_access_expiration_time * 1000).toISOString(),
            permissions: [],
            declinedPermissions: [],
          };
          resolve(accessToken);
        } else {
          resolve(null);
        }
      });
    });
  }

  async signOutFacebook(): Promise<void> {
    if (!this.facebookInitialized) {
      throw new Error('Facebook not initialized. Call initializeFacebook() first.');
    }

    return new Promise((resolve) => {
      window.FB.logout(() => {
        resolve();
      });
    });
  }
}
