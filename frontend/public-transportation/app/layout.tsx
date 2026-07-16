import type { Metadata } from 'next';
import './globals.css';
import FeedbackChatMount from './FeedbackChatMount';

export const metadata: Metadata = {
  title: 'Public Transportation',
  description: 'Israel Public Transportation Tracker',
};

// Decides the theme before first paint (saved choice, else OS preference) so
// dark-mode users never see a white flash. Runs inline in <head>; the
// data-theme attribute it sets is what globals.css themes against.
const themeBootScript = `(function(){try{var t=localStorage.getItem('pt-theme');if(t!=='light'&&t!=='dark'){t=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'}document.documentElement.dataset.theme=t}catch(e){document.documentElement.dataset.theme='light'}})()`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeBootScript }} />
      </head>
      <body>
        {children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
