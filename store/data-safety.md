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

# ⚠ Gap to close before a public release: account deletion

Play requires an app that **lets users create an account** to also let them **request deletion of
that account and its data — from inside the app, and through a web URL** that can be reached
without installing the app. PT has a registration gate (email + phone), so this applies.

Today deletion is "email privacy@ya-niv.com", which the policy states. That is a human process,
not the in-app path and public URL Play asks for.

**What closing it takes:**
1. A web page, e.g. `https://pt.prod.ya-niv.com/delete-account`, that explains what gets deleted
   and takes a request.
2. A visible in-app route to the same thing (Settings → delete my account).
3. Server-side deletion of the registration row and the usage records tied to that install.

Not hard, but it is real work and it is required, so it should happen before the closed test ends
rather than after the production application is submitted.
