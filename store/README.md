# Play Store release — what is ready, what is not

Everything in this folder is preparation for publishing **pt** on Google Play. Nothing here has
been uploaded: as of 2026-09-24 the developer account does not exist yet.

## Blocked on Yaniv

1. **Create the Play developer account** — personal ("Yourself"), $25 one-time, government ID.
2. **Decide the app's name.** The code calls it `PT`; the prod build labels the launcher
   **"PT Prod"**. Everything marked `<<NAME>>` in `listing.md` waits on this, and so does the
   feature graphic and a fix to `AndroidManifest.xml`'s `android:label`.
3. **Build the store bundle.** The release AAB must be the **prod** flavor, and the build
   deliberately refuses prod-flavor builds outside the prod worktree
   (`mobile/build-logic/src/main/kotlin/android-flavors.gradle.kts`).

## Ready

| Asset | Where | State |
|---|---|---|
| Listing copy, Hebrew + English | `listing.md` | Written; title pending the name |
| Data safety answers | `data-safety.md` | Mapped to Play's form, deletion gap closed |
| Screenshots | `screenshots/` | 8 × 1080×2400, Hebrew, real live data |
| App icon 512×512 | `graphics/play-icon-512.png` | Rendered from the launcher icon — placeholder quality |
| Feature graphic 1024×500 | — | Not made; needs the name |
| Privacy policy | https://pt.prod.ya-niv.com/privacy | Live, bilingual |
| Account deletion | https://pt.prod.ya-niv.com/delete-account + in-app | Built and tested 2026-09-24 |
| Target API 36 | `mobile/app/build.gradle.kts` | Done — Play requires it of new apps since 31 Aug 2026 |

## Content rating (IARC questionnaire)

Answer: no violence, no sexuality, no profanity, no controlled substances, no gambling, no
user-generated content, no user-to-user communication, no ads. The app does share the user's
location **with the app's own server only, for route planning**, and provides no social features.
Expected outcome: the most permissive rating in every region.

## Order of work in Play Console, once the account exists

1. Create the app — default language **Hebrew (he-IL)**, free, app (not game).
2. Store listing: title, short and full description from `listing.md`, icon, feature graphic,
   screenshots.
3. App content: privacy policy URL, **data deletion URL**
   (`https://pt.prod.ya-niv.com/delete-account`), data safety form from `data-safety.md`,
   content rating questionnaire, target audience (adults — not directed at children), ads: none.
4. Closed testing track: upload the prod AAB, add ≥12 testers, start the clock.
5. **Immediately after the first upload**: copy the App signing key SHA-1 from
   *Test and release → Setup → App signing* and add a third Android OAuth client for it
   (`mobile/OAUTH_SETUP.md` step 5). Without it Google Sign-In fails for every Play user while
   working perfectly in testing.
6. After 14 unbroken days with ≥12 testers opted in: apply for production access.

## Known gaps worth closing before the public release

- The **icon is placeholder-grade** — a generic bus on a blue square.
- Open issues found while capturing the screenshots: pt #263 (RTL train label), #264 (line
  directions unnamed and unclickable), #265 (duplicate geocoder results), #266 (panel opacity
  and slider), #267 (grey map tiles after a jump).
- The Ministry of Transport SIRI key in use is personal; its terms for a public app were never
  checked.
