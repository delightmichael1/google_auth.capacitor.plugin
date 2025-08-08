# googleauth

A google auth capacitor plugin

## Install

```bash
npm install @deldev/capacitor-google-auth
npx cap sync
```

## API

<docgen-index>

* [`initialize(...)`](#initialize)
* [`signIn()`](#signin)
* [`refresh()`](#refresh)
* [`signOut()`](#signout)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initialize(...)

```typescript
initialize(options?: InitOptions) => Promise<void>
```

Initializes the GoogleAuthPlugin, loading the gapi library and setting up the plugin.

| Param         | Type                                                | Description                        |
| ------------- | --------------------------------------------------- | ---------------------------------- |
| **`options`** | <code><a href="#initoptions">InitOptions</a></code> | - Optional initialization options. |

**Since:** 3.1.0

--------------------


### signIn()

```typescript
signIn() => Promise<User>
```

Initiates the sign-in process and returns a Promise that resolves with the user information.

**Returns:** <code>Promise&lt;<a href="#user">User</a>&gt;</code>

--------------------


### refresh()

```typescript
refresh() => Promise<Authentication>
```

Refreshes the authentication token and returns a Promise that resolves with the updated authentication details.

**Returns:** <code>Promise&lt;<a href="#authentication">Authentication</a>&gt;</code>

--------------------


### signOut()

```typescript
signOut() => Promise<any>
```

Signs out the user and returns a Promise.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### Interfaces


#### InitOptions

| Prop                     | Type                  | Description                                                                                                                                      | Default            | Since |
| ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------ | ----- |
| **`clientId`**           | <code>string</code>   | The app's client ID, found and created in the Google Developers Console. Common for Android or iOS. The default is defined in the configuration. |                    | 3.1.0 |
| **`scopes`**             | <code>string[]</code> | Specifies the scopes required for accessing Google APIs The default is defined in the configuration.                                             |                    |       |
| **`grantOfflineAccess`** | <code>boolean</code>  | Set if your application needs to refresh access tokens when the user is not present at the browser. In response use `serverAuthCode` key         | <code>false</code> | 3.1.0 |


#### User

| Prop                 | Type                                                      | Description                                                         |
| -------------------- | --------------------------------------------------------- | ------------------------------------------------------------------- |
| **`id`**             | <code>string</code>                                       | The unique identifier for the user.                                 |
| **`email`**          | <code>string</code>                                       | The email address associated with the user.                         |
| **`name`**           | <code>string</code>                                       | The user's full name.                                               |
| **`familyName`**     | <code>string</code>                                       | The family name (last name) of the user.                            |
| **`givenName`**      | <code>string</code>                                       | The given name (first name) of the user.                            |
| **`imageUrl`**       | <code>string</code>                                       | The URL of the user's profile picture.                              |
| **`serverAuthCode`** | <code>string</code>                                       | The server authentication code.                                     |
| **`authentication`** | <code><a href="#authentication">Authentication</a></code> | The authentication details including access, refresh and ID tokens. |


#### Authentication

| Prop               | Type                | Description                                      |
| ------------------ | ------------------- | ------------------------------------------------ |
| **`accessToken`**  | <code>string</code> | The access token obtained during authentication. |
| **`idToken`**      | <code>string</code> | The ID token obtained during authentication.     |
| **`refreshToken`** | <code>string</code> | The refresh token.                               |

</docgen-api>
