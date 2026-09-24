# Play Store listing — pt

Status: draft. **The app title is not decided** — the code calls it `PT`, and the prod build
labels the launcher icon `PT Prod`. Everything marked `<<NAME>>` below waits on that decision.

Limits Google enforces: title 30 chars, short description 80 chars, full description 4000 chars.
The listing is bilingual: Hebrew is the default language (the audience is Israeli riders),
English is a secondary translation.

---

## Hebrew (default)

**Title (≤30):** `<<NAME>>`

**Short description (≤80):**

> תכנון נסיעה, זמני הגעה בזמן אמת ואוטובוסים חיים על המפה — בכל הארץ.

**Full description:**

```
<<NAME>> היא אפליקציית תחבורה ציבורית לישראל: לתכנן נסיעה, לראות מתי האוטובוס
באמת מגיע, ולדעת מתי לרדת.

התכנון מבוסס על נתוני התחבורה הציבורית הרשמיים (GTFS) ועל מנוע ניתוב מסלולים,
וזמני ההגעה מגיעים ישירות ממשרד התחבורה בזמן אמת.

מה יש באפליקציה
• תכנון מסלול משולב — הליכה, אוטובוס, רכבת ורכבת קלה, עם כמה חלופות לכל נסיעה
• זמני הגעה בזמן אמת — מתי הקו באמת מגיע, ובכמה הוא מקדים או מאחר ללוח
• אוטובוסים חיים על המפה — לראות איפה הקו נמצא עכשיו
• יציאות קרובות — מה יוצא בדקות הקרובות מהתחנות שסביבך
• דפדוף בקווים — כל התחנות של הקו וזמני המעבר בהן
• מועדפים — קווים ותחנות שאתה משתמש בהם כל יום, במקום אחד
• בית ועבודה — שמורים, לתכנון בלחיצה אחת
• תזכורת יציאה — התראה לפני שהאוטובוס יוצא
• "על האוטובוס?" — מעקב אחרי הנסיעה והתראה לפני התחנה שלך
• ווידג'ט למסך הבית — היציאות הקרובות בלי לפתוח את האפליקציה
• שיתוף נסיעה — לשלוח מסלול למישהו בקישור

בלי פרסומות
אין באפליקציה פרסומות, אין מזהי פרסום ואין כלי אנליטיקה של צד שלישי.
מדיניות הפרטיות מפרטת בדיוק מה נשמר ומה נשאר במכשיר.

לגבי מחיר
כרגע הכול פתוח לכולם בחינם. בעתיד האפליקציה תהפוך לבתשלום — במחיר קטן, פחות
מנסיעה אחת באוטובוס בשנה — ותקבל על כך הודעה מראש. אנחנו לא מתכוונים להפתיע
אף אחד בחיוב.
```

---

## English

**Title (≤30):** `<<NAME>>`

**Short description (≤80):**

> Plan a trip, see real-time arrivals, and watch live buses across Israel.

**Full description:**

```
<<NAME>> is a public transport app for Israel: plan a trip, see when the bus is
actually coming, and know when to get off.

Routing is built on the official Israeli transit data (GTFS) and a route planning
engine; arrival times come live from the Ministry of Transport.

What's in the app
• Multimodal trip planning — walking, bus, train and light rail, with several options per trip
• Real-time arrivals — when the line actually arrives, and how early or late it is
• Live buses on the map — see where the line is right now
• Nearby departures — what leaves in the next few minutes from the stops around you
• Line browser — every stop on a line and the times it passes them
• Favourites — the lines and stations you use daily, in one place
• Home and Work — saved, for one-tap planning
• Departure reminders — a notification before the bus leaves
• "On a bus?" — follows your ride and warns you before your stop
• Home-screen widget — upcoming departures without opening the app
• Share a trip — send someone a route as a link

No ads
No advertising, no advertising identifiers, no third-party analytics. The privacy
policy states exactly what is stored and what never leaves your phone.

About price
Everything is free for everyone right now. The app will become paid later — a small
price, less than a single bus fare per year — and you will be told in advance. Nobody
is going to be surprised by a charge.
```

---

## Other listing fields

| Field | Value |
|---|---|
| App category | Maps & Navigation (alternative: Travel & Local) |
| Contact email | needs deciding — the privacy policy uses privacy@ya-niv.com |
| Privacy policy URL | https://pt.prod.ya-niv.com/privacy (live, bilingual, dated 25 July 2026) |
| Website | https://pt.prod.ya-niv.com |
| Default language | Hebrew (he-IL) |
| Countries | Israel first; the data is Israeli, so a worldwide release would collect bad reviews |

## Graphics still needed

- **App icon** 512×512 PNG (source: `mobile/app/src/main/res/mipmap-*`)
- **Feature graphic** 1024×500 PNG — required, shown at the top of the listing
- **Phone screenshots** — at least 2, 4–8 recommended; being captured into `store/screenshots/`
