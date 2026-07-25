import type { Metadata } from 'next';

// Public privacy policy. Google Play requires a reachable URL for this before it
// will accept a listing that collects email or phone.
//
// IMPORTANT: this page describes what the app and server ACTUALLY do. If the
// data collected changes, change this page in the same commit — a policy that
// understates collection is worse than having none.

export const metadata: Metadata = {
  title: 'Privacy Policy — Public Transportation',
  description: 'What the Public Transportation app collects, why, and how to have it deleted.',
};

const LAST_UPDATED = '25 July 2026';
const CONTACT = 'privacy@ya-niv.com';

export default function PrivacyPage() {
  return (
    <main
      style={{
        maxWidth: '46rem',
        margin: '0 auto',
        padding: '2rem 1.25rem 4rem',
        lineHeight: 1.65,
        fontFamily: 'system-ui, -apple-system, Segoe UI, Roboto, sans-serif',
      }}
    >
      <section>
        <h1>Privacy Policy</h1>
        <p>
          <em>Last updated: {LAST_UPDATED}</em>
        </p>
        <p>
          This policy covers the <strong>Public Transportation</strong> Android app and the
          website at pt.prod.ya-niv.com. It describes everything we collect. We do not
          sell your data, we do not show ads, and we do not use advertising identifiers
          or third-party analytics products.
        </p>

        <h2>What we store on our servers</h2>
        <ul>
          <li>
            <strong>Install identifier</strong> — a random identifier created on your device
            the first time the app runs. It identifies an installation, not a person, and is
            not derived from any device or advertising ID.
          </li>
          <li>
            <strong>Email address and phone number</strong> — collected when you register.
            We use them only to contact you about the app, in particular to give you notice
            before the app changes from free to paid.
          </li>
          <li>
            <strong>App version and platform</strong> — so we know which release you are running.
          </li>
          <li>
            <strong>Usage records</strong> — that the app was opened, a route was searched, or a
            trip was shared. These are recorded once per day at most, not per action.
          </li>
          <li>
            <strong>Referral source</strong> — if you installed the app after opening a trip link
            someone shared with you, we record which installation shared it.
          </li>
          <li>
            <strong>Web server logs</strong> — your IP address, the time, the address you
            requested, and your browser or app user-agent. Because route searches are made as
            web requests, <strong>these logs include the start and end coordinates of routes you
            search for</strong>. Logs are used for operating and debugging the service.
          </li>
        </ul>

        <h2>What stays on your device</h2>
        <p>
          The following never leaves your phone. It is stored in the app&apos;s local storage
          and is removed when you uninstall the app or clear its data:
        </p>
        <ul>
          <li>Your saved Home and Work places</li>
          <li>Favourite lines and favourite stations</li>
          <li>Recent searches</li>
          <li>Home-screen widget settings, map style, and display preferences</li>
        </ul>

        <h2>Device permissions</h2>
        <ul>
          <li>
            <strong>Location</strong> — used to show where you are on the map and to offer your
            current position as a starting point. Your coordinates are sent to our routing
            server only when you actually search for a route. We do not track your location in
            the background.
          </li>
          <li>
            <strong>Notifications</strong> — used only to deliver departure reminders that you
            set yourself.
          </li>
          <li>
            <strong>Exact alarms</strong> — so a departure reminder arrives at the right minute
            rather than late.
          </li>
        </ul>

        <h2>Other services we rely on</h2>
        <p>
          Using the app causes requests to the following third parties, which will see your
          IP address. We do not send them your email, phone number, or install identifier:
        </p>
        <ul>
          <li><strong>OpenStreetMap</strong> and <strong>ArcGIS</strong> — map and satellite tiles</li>
          <li><strong>OpenRouteService</strong> — driving and cycling directions</li>
          <li>
            <strong>Israel Ministry of Transport</strong> — live vehicle and arrival information
          </li>
          <li>
            <strong>Google Play</strong> — app distribution, and the install referral described above
          </li>
        </ul>

        <h2>How long we keep it</h2>
        <p>
          Registration details are kept while your account exists. Usage records and server
          logs are kept while they are useful for operating the service. Ask us to delete
          your data and we will remove your registration details and the records linked to
          your installation.
        </p>

        <h2>Your rights</h2>
        <p>
          You can ask us what we hold about you, ask us to correct it, or ask us to delete
          it. Write to <a href={`mailto:${CONTACT}`}>{CONTACT}</a> and we will respond.
          Uninstalling the app removes everything held on your device, but does not by
          itself delete your registration — email us for that.
        </p>

        <h2>Children</h2>
        <p>
          This app is not directed at children and we do not knowingly collect information
          from children.
        </p>

        <h2>Changes</h2>
        <p>
          If this policy changes we will update this page and change the date at the top.
        </p>

        <h2>Contact</h2>
        <p>
          <a href={`mailto:${CONTACT}`}>{CONTACT}</a>
        </p>
      </section>

      <hr style={{ margin: '3rem 0' }} />

      <section dir="rtl" lang="he">
        <h1>מדיניות פרטיות</h1>
        <p>
          <em>עודכן לאחרונה: 25 ביולי 2026</em>
        </p>
        <p>
          המדיניות חלה על אפליקציית <strong>תחבורה ציבורית</strong> לאנדרואיד ועל האתר
          pt.prod.ya-niv.com, ומתארת את כל מה שאנחנו אוספים. איננו מוכרים את המידע שלך,
          איננו מציגים פרסומות, ואיננו משתמשים במזהי פרסום או בכלי אנליטיקה של צד שלישי.
        </p>

        <h2>מה נשמר אצלנו בשרת</h2>
        <ul>
          <li>
            <strong>מזהה התקנה</strong> — מזהה אקראי שנוצר במכשיר בהפעלה הראשונה. הוא מזהה
            התקנה ולא אדם, ואינו נגזר ממזהה מכשיר או ממזהה פרסום.
          </li>
          <li>
            <strong>כתובת אימייל ומספר טלפון</strong> — נאספים בעת ההרשמה, ומשמשים רק ליצירת
            קשר בנוגע לאפליקציה, ובעיקר כדי להודיע לך מראש לפני שהאפליקציה תהפוך לבתשלום.
          </li>
          <li><strong>גרסת האפליקציה והפלטפורמה</strong> — כדי לדעת איזו גרסה מותקנת אצלך.</li>
          <li>
            <strong>רישומי שימוש</strong> — שהאפליקציה נפתחה, שבוצע חיפוש מסלול או שיתוף נסיעה.
            נרשמים לכל היותר פעם ביום, לא בכל פעולה.
          </li>
          <li>
            <strong>מקור ההפניה</strong> — אם הגעת דרך קישור נסיעה ששותף איתך, נרשם מי שיתף.
          </li>
          <li>
            <strong>יומני שרת</strong> — כתובת ה־IP שלך, השעה, הכתובת שביקשת וזיהוי הדפדפן או
            האפליקציה. מכיוון שחיפושי מסלול מתבצעים כבקשות רשת,{' '}
            <strong>היומנים כוללים את נקודות המוצא והיעד של המסלולים שחיפשת</strong>. היומנים
            משמשים לתפעול השירות ולאיתור תקלות.
          </li>
        </ul>

        <h2>מה נשאר במכשיר בלבד</h2>
        <p>
          המידע הבא לא יוצא מהטלפון, נשמר באחסון המקומי של האפליקציה, ונמחק בהסרת האפליקציה
          או בניקוי הנתונים שלה:
        </p>
        <ul>
          <li>המקומות השמורים בית ועבודה</li>
          <li>קווים ותחנות מועדפים</li>
          <li>חיפושים אחרונים</li>
          <li>הגדרות הווידג&apos;ט, סגנון המפה והעדפות תצוגה</li>
        </ul>

        <h2>הרשאות במכשיר</h2>
        <ul>
          <li>
            <strong>מיקום</strong> — כדי להציג את מיקומך על המפה ולהציע אותו כנקודת מוצא.
            הקואורדינטות נשלחות לשרת הניתוב רק כשאתה מחפש מסלול בפועל. איננו עוקבים אחרי
            מיקומך ברקע.
          </li>
          <li><strong>התראות</strong> — רק לצורך תזכורות יציאה שאתה מגדיר בעצמך.</li>
          <li><strong>התראות מדויקות</strong> — כדי שתזכורת תגיע בדקה הנכונה ולא באיחור.</li>
        </ul>

        <h2>שירותים נוספים</h2>
        <p>
          השימוש באפליקציה גורם לפניות לגורמים הבאים, שיראו את כתובת ה־IP שלך. איננו שולחים
          להם את האימייל, הטלפון או מזהה ההתקנה שלך:
        </p>
        <ul>
          <li><strong>OpenStreetMap</strong> ו־<strong>ArcGIS</strong> — אריחי מפה ולוויין</li>
          <li><strong>OpenRouteService</strong> — הנחיות נסיעה ורכיבה</li>
          <li><strong>משרד התחבורה</strong> — מידע על הגעות וכלי רכב בזמן אמת</li>
          <li><strong>Google Play</strong> — הפצת האפליקציה ומקור ההפניה שתואר למעלה</li>
        </ul>

        <h2>משך השמירה</h2>
        <p>
          פרטי ההרשמה נשמרים כל עוד החשבון קיים. רישומי השימוש ויומני השרת נשמרים כל עוד הם
          נחוצים לתפעול השירות. אם תבקש מחיקה, נמחק את פרטי ההרשמה ואת הרישומים המקושרים
          להתקנה שלך.
        </p>

        <h2>הזכויות שלך</h2>
        <p>
          אתה רשאי לבקש לדעת מה שמור עליך, לתקן אותו או למחוק אותו. כתוב לנו לכתובת{' '}
          <a href={`mailto:${CONTACT}`}>{CONTACT}</a> ונטפל בפנייה. הסרת האפליקציה מוחקת את
          המידע השמור במכשיר, אך אינה מוחקת את ההרשמה — לשם כך יש לפנות אלינו.
        </p>

        <h2>ילדים</h2>
        <p>האפליקציה אינה מיועדת לילדים ואיננו אוספים ביודעין מידע מילדים.</p>

        <h2>שינויים</h2>
        <p>אם המדיניות תשתנה, נעדכן דף זה ואת התאריך שבראשו.</p>

        <h2>יצירת קשר</h2>
        <p>
          <a href={`mailto:${CONTACT}`}>{CONTACT}</a>
        </p>
      </section>
    </main>
  );
}
