import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Live journey',
  description: 'Watch a journey live',
  robots: { index: false },
};

export default function JourneyLiveLayout({ children }: { children: React.ReactNode }) {
  return children;
}
