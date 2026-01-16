import { registerPlugin } from '@capacitor/core';
import type { GoogleAuthPlugin, SocialAuthPlugin } from './definitions';

// Register Google Auth Plugin (existing - unchanged)
const GoogleAuth = registerPlugin<GoogleAuthPlugin>('GoogleAuth', {
  web: () => import('./web').then((m) => new m.GoogleAuthWeb()),
});

// Register unified Social Auth Plugin (new)
const SocialAuth = registerPlugin<SocialAuthPlugin>('SocialAuth', {
  web: () => import('./social-web').then((m) => new m.SocialAuthWeb()),
});

export * from './definitions';
export { GoogleAuth, SocialAuth };
