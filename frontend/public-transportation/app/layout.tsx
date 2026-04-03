import type { Metadata } from 'next';
import './globals.css';
import { FeedbackChat } from '@automate/feedback-lib/FeedbackChat';

export const metadata: Metadata = {
  title: 'Public Transportation',
  description: 'Israel Public Transportation Tracker',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <FeedbackChat />
      </body>
    </html>
  );
}
