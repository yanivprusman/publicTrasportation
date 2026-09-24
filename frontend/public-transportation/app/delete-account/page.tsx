import type { Metadata } from 'next';

// Public account-deletion page. Google Play requires that an app which lets
// people create an account also publish a URL, reachable WITHOUT installing the
// app, where deletion can be requested — alongside the in-app path.
//
// Deliberately not a delete-by-email form: an unauthenticated address would let
// anyone erase anyone. The phone that holds the install proves ownership, so the
// one-tap route lives in the app; this page is for someone who no longer has it.

export const metadata: Metadata = {
  title: 'Delete your account — Public Transportation',
  description:
    'How to delete your Public Transportation account and everything stored about you.',
};

const CONTACT = 'privacy@ya-niv.com';

export default function DeleteAccountPage() {
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
        <h1>Delete your account</h1>

        <h2>From the app (immediate)</h2>
        <p>
          Open the app, go to <strong>Settings</strong> and choose{' '}
          <strong>Delete my account</strong>. It happens straight away, and nothing has
          to be approved by us.
        </p>

        <h2>Without the app</h2>
        <p>
          If the app is already uninstalled, email{' '}
          <a href={`mailto:${CONTACT}?subject=Delete my account`}>{CONTACT}</a> from the
          address you registered with. We reply to confirm, and delete within 30 days.
          We ask you to write from that address because it is the only thing that shows
          the account is yours.
        </p>

        <h2>What gets deleted</h2>
        <ul>
          <li>Your registration — email address and phone number</li>
          <li>Any preferences synced to the account</li>
          <li>Every installation linked to the account</li>
          <li>The usage records belonging to those installations</li>
        </ul>
        <p>
          Nothing is kept afterwards. Web server logs, which hold IP addresses and the
          requests made, are not tied to an account and age out on their own schedule.
        </p>
        <p>
          Anything stored only on your phone — saved Home and Work, favourites, recent
          searches, widget and display settings — is removed when you uninstall the app
          or clear its data.
        </p>
      </section>

      <hr style={{ margin: '3rem 0' }} />

      <section dir="rtl" lang="he">
        <h1>מחיקת החשבון</h1>

        <h2>מתוך האפליקציה (מיידי)</h2>
        <p>
          יש לפתוח את האפליקציה, להיכנס ל<strong>הגדרות</strong> ולבחור{' '}
          <strong>מחיקת החשבון שלי</strong>. המחיקה מתבצעת מיד, בלי אישור מצידנו.
        </p>

        <h2>בלי האפליקציה</h2>
        <p>
          אם כבר הסרת את האפליקציה, שלח אימייל לכתובת{' '}
          <a href={`mailto:${CONTACT}?subject=מחיקת חשבון`}>{CONTACT}</a> מהכתובת שאיתה
          נרשמת. נשיב לאישור ונמחק בתוך 30 יום. אנחנו מבקשים שתכתוב מאותה כתובת כי זה
          הדבר היחיד שמראה שהחשבון שלך.
        </p>

        <h2>מה נמחק</h2>
        <ul>
          <li>ההרשמה שלך — כתובת האימייל ומספר הטלפון</li>
          <li>העדפות שסונכרנו לחשבון</li>
          <li>כל ההתקנות המקושרות לחשבון</li>
          <li>רישומי השימוש השייכים לאותן התקנות</li>
        </ul>
        <p>
          לא נשאר דבר אחרי המחיקה. יומני השרת, שכוללים כתובות IP ואת הבקשות שבוצעו,
          אינם מקושרים לחשבון ונמחקים בלוח הזמנים שלהם.
        </p>
        <p>
          מה ששמור רק בטלפון — בית ועבודה, מועדפים, חיפושים אחרונים, הגדרות ווידג׳ט
          ותצוגה — נמחק בהסרת האפליקציה או בניקוי הנתונים שלה.
        </p>
      </section>
    </main>
  );
}
