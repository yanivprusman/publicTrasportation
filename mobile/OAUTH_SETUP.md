# Google Sign-In — OAuth setup

Everything that can be prepared from this machine is done. The remaining steps
need your Google account, so they are written out with the exact values already
filled in.

## Your values

| Field | Value |
| :--- | :--- |
| **Prod package** | `com.automatelinux.pt` |
| **Dev package** | `com.automatelinux.pt.dev` |
| **Upload key SHA-1** | `53:94:90:F7:B4:21:06:16:8F:7E:F1:74:56:30:99:76:90:48:5B:FE` |
| **Debug key SHA-1** | `CD:1B:FB:90:DF:A0:57:C5:FE:BF:1B:68:3C:E8:83:E8:AE:17:08:C2` |
| **Play App Signing SHA-1** | ⚠️ does not exist yet — see step 5 |

Regenerate these any time:

```bash
# Upload key
keytool -list -v -keystore /root/keystores/pt-upload.jks -alias pt-upload \
  -storepass "$(grep '^storePassword=' /opt/dev/publicTransportation/mobile/keystore.properties | cut -d= -f2)" \
  | grep SHA1

# Debug key
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android | grep SHA1
```

## Steps

### 1. Create a Google Cloud project
<https://console.cloud.google.com/projectcreate> → name it `public-transportation`.

### 2. Configure the OAuth consent screen
**APIs & Services → OAuth consent screen**

- User type: **External**
- App name: `Public Transportation`
- Support email + developer contact: your address
- Scopes: leave default (`email`, `profile`, `openid`) — do **not** add more.
  Extra scopes trigger Google's verification review, which you do not need.
- Publishing status: **Publish app**. While it is in *Testing*, only accounts
  you list manually can sign in.

### 3. Create the **Web** client — this is the one the app actually uses
**APIs & Services → Credentials → Create credentials → OAuth client ID → Web application**

Name it `pt-server`. Copy the **Client ID** (ends in `.apps.googleusercontent.com`).

> This trips almost everyone up: on Android you pass the **Web** client ID to
> Credential Manager, not the Android one. The Android clients below still have
> to exist — they authorise the app — but the ID you paste into the code is the
> Web one.

### 4. Create the **Android** clients — one per package
**Create credentials → OAuth client ID → Android**, twice:

| Name | Package name | SHA-1 |
| :--- | :--- | :--- |
| `pt-android-dev` | `com.automatelinux.pt.dev` | `CD:1B:FB:90:DF:A0:57:C5:FE:BF:1B:68:3C:E8:83:E8:AE:17:08:C2` |
| `pt-android-prod` | `com.automatelinux.pt` | `53:94:90:F7:B4:21:06:16:8F:7E:F1:74:56:30:99:76:90:48:5B:FE` |

The dev flavor has `applicationIdSuffix = ".dev"`, so it is a genuinely
different package and needs its own client. Without it, sign-in fails on every
dev build.

### 5. After your first Play upload — add the third fingerprint ⚠️

**This is the step that silently breaks production.**

Play App Signing means Google re-signs your app with *their* key. Users receive
an app signed with a certificate that does not exist on this machine. If only
the upload key is registered, sign-in works perfectly in all your testing and
**fails for every single Play Store user**.

After uploading the first bundle:

1. Play Console → your app → **Test and release → Setup → App signing**
2. Copy the **App signing key certificate** SHA-1
3. Add a **third** Android OAuth client: package `com.automatelinux.pt`, that SHA-1

### 6. Give me the Web client ID

Put it in `mobile/oauth.properties` (gitignored, alongside `keystore.properties`):

```properties
webClientId=XXXXXXXXX.apps.googleusercontent.com
```

The app reads it at build time. Absent, Google Sign-In stays off and
registration works as it does now — the build does not break.

## Also required by Play once you collect email + phone

- A **privacy policy URL** (Play rejects the listing without one)
- The **Data safety** form: declare Email address + Phone number, why they are
  collected, and that they are not shared or used for ads
