import { WebPlugin } from '@capacitor/core';
import { GoogleAuthPlugin, InitOptions, User } from './definitions';

// Declare Google Identity Services types
declare global {
  interface Window {
    google: any;
  }
}

export class GoogleAuthWeb extends WebPlugin implements GoogleAuthPlugin {
  private tokenClient: any;
  private options: InitOptions & { backendUrl?: string };
  private currentUser: User | null = null;

  constructor() {
    super();
  }

  loadScript(): Promise<void> {
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

  async initialize(
    _options: Partial<InitOptions> = {
      clientId: '',
      scopes: [],
      grantOfflineAccess: false,
    },
  ): Promise<void> {
    if (typeof window === 'undefined') {
      throw new Error('Window is not available');
    }

    const metaClientId = (document.getElementsByName('google-signin-client_id')[0] as any)?.content;
    const clientId = _options.clientId || metaClientId || '';

    if (!clientId) {
      throw new Error('GoogleAuthPlugin - clientId is required');
    }

    this.options = {
      clientId,
      grantOfflineAccess: _options.grantOfflineAccess ?? false,
      scopes: _options.scopes || ['email', 'profile'],
      backendUrl: (_options as any).backendUrl,
    };

    await this.loadScript();
    await this.initializeGoogleIdentityServices();
  }

  private async initializeGoogleIdentityServices(): Promise<void> {
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

  async signIn(): Promise<User> {
    return new Promise<User>(async (resolve, reject) => {
      try {
        if (!window.google || !window.google.accounts) {
          throw new Error('Google Identity Services not loaded. Call initialize() first.');
        }

        const needsOfflineAccess = this.options.grantOfflineAccess ?? false;

        if (needsOfflineAccess) {
          // Use code client for offline access
          this.tokenClient = window.google.accounts.oauth2.initCodeClient({
            client_id: this.options.clientId,
            scope: this.options.scopes.join(' '),
            ux_mode: 'popup',
            callback: async (response: any) => {
              if (response.error) {
                reject(new Error(response.error));
                return;
              }

              try {
                // Return serverAuthCode - let client decide what to do
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

                this.currentUser = user;
                this.notifyListeners('userChange', user);
                resolve(user);
              } catch (error) {
                reject(error);
              }
            },
          });

          this.tokenClient.requestCode();
        } else {
          // Use token client for access token only
          this.tokenClient = window.google.accounts.oauth2.initTokenClient({
            client_id: this.options.clientId,
            scope: this.options.scopes.join(' '),
            callback: async (response: any) => {
              if (response.error) {
                reject(new Error(response.error));
                return;
              }

              try {
                const userInfo = await this.getUserInfo(response.access_token);

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

                this.currentUser = user;
                this.notifyListeners('userChange', user);
                resolve(user);
              } catch (error) {
                reject(error);
              }
            },
          });

          this.tokenClient.requestAccessToken();
        }
      } catch (error) {
        reject(error);
      }
    });
  }

  private async getUserInfo(accessToken: string): Promise<User> {
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

  async refresh(): Promise<any> {
    if (!this.currentUser) {
      throw new Error('No user is currently signed in');
    }

    // Request a new token
    return new Promise((resolve, reject) => {
      if (!window.google || !window.google.accounts) {
        reject(new Error('Google Identity Services not loaded'));
        return;
      }

      this.tokenClient = window.google.accounts.oauth2.initTokenClient({
        client_id: this.options.clientId,
        scope: this.options.scopes.join(' '),
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

          if (this.currentUser) {
            this.currentUser.authentication = authentication;
          }

          resolve(authentication);
        },
      });

      this.tokenClient.requestAccessToken({ prompt: '' });
    });
  }

  async signOut(): Promise<any> {
    if (window.google && window.google.accounts && window.google.accounts.id) {
      window.google.accounts.id.disableAutoSelect();
    }

    if (this.currentUser && this.currentUser.authentication.accessToken) {
      try {
        await fetch(`https://oauth2.googleapis.com/revoke?token=${this.currentUser.authentication.accessToken}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        });
      } catch (error) {
        console.error('Error revoking token:', error);
      }
    }

    this.currentUser = null;
    this.notifyListeners('userChange', null);
    return Promise.resolve();
  }
}
