'use client';

import { useParams } from 'next/navigation';
import JourneyLiveViewer from './JourneyLiveViewer';

export default function JourneyLivePage() {
  const params = useParams<{ token: string }>();
  return <JourneyLiveViewer token={params.token} />;
}
