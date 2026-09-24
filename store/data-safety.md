# Data safety form — prepared answers (pt)

Derived from the live privacy policy (https://pt.prod.ya-niv.com/privacy, 25 July 2026) and from
the code: `AnalyticsRepository`, `IdentityVault`, `RegistrationScreen`, `InstallReferrerReader`,
and the routing calls that carry coordinates to the server.

Answer the Play Console form with exactly this. Do not soften it — a data-safety declaration that
disagrees with the app's behaviour is one of the common reasons a release is pulled.

## Data collected and sent off the device

| Play category → type | Collected | Shared | Purpose | Required? |
|---|---|---|---|---|
| Personal info → **Email address** | Yes | No | Account management; notice before the app becomes paid | Required (registration gate) |
| Personal info → **Phone number** | Yes | No | Same as above | Required (registration gate) |
| Location → **Precise location** | **Yes** | No | App functionality (route planning) | Optional |
| App activity → **App interactions** | Yes | No | Analytics (app opened, route searched, trip shared — max once/day) | Optional |
| App info and performance → **Other** (app version, platform) | Yes | No | Analytics / knowing which release a user runs | Optional |
| Device or other IDs → **Device or other IDs** | Yes | No | Analytics; the random install UUID and the Play install referrer | Optional |

**Why "Precise location: Yes" even though the app does not track you.** Route searches are HTTP
requests, so the start and end coordinates reach the server and land in the web server logs. The
privacy policy already says this plainly. Declaring "no location collected" because there is no
background tracking is the mistake that gets apps flagged.

## Not collected
No advertising ID. No contacts, photos, files, messages, calendar, health, financial or payment
data. No third-party analytics SDK. Nothing is shared with other companies.

## Security section
- **Encrypted in transit:** yes (HTTPS to pt.prod.ya-niv.com).
- **Users can request data deletion:** yes — but see the gap below.
- **Independent security review:** no.
- **Committed to Play Families policy:** not applicable; the app is not directed at children.

## Data stored only on the device (declare nothing — it never leaves)
Saved Home and Work, favourite lines and stations, recent searches, widget settings, map style,
display preferences. Removed on uninstall or clear-data.

---

# ✅ Closed: account deletion (2026-09-24)

Play requires an app that **lets users create an account** to also let them **delete that account
and its data — from inside the app, and through a web URL** reachable without installing it. PT has
a registration gate, so this applied. It is now built:

- **In-app:** Settings (the gear menu) → *מחיקת החשבון שלי* / *Delete my account*, with a
  confirmation dialog that states what goes. On success the app returns to the registration screen,
  which is the honest confirmation — the account really is gone.
- **Web:** https://pt.prod.ya-niv.com/delete-account — bilingual, explains the in-app path and the
  email route for someone who has already uninstalled.
- **Server:** the daemon command `appDeleteAccount` erases, in one transaction, the user row, the
  synced state, every install linked to the account and those installs' events.

Deliberately no delete-by-email endpoint: the install UUID is the credential, because an
unauthenticated address would let anyone erase anyone.

**Data safety form:** answer *"Users can request that their data is deleted" = yes*, and give
https://pt.prod.ya-niv.com/delete-account as the deletion URL.
