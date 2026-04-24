import { MAINTENANCE_PROMPTS } from '@addnewfeature/feedback-lib-launcher';
import FeedbackIssuesClient from './feedback-issues-client';

export { generateFeedbackIssuesMetadata as generateMetadata } from '@claudecontrol/feedback-lib';

export default async function IssuesPage({
  searchParams,
}: {
  searchParams: Promise<{ app?: string }>;
}) {
  const { app } = await searchParams;
  return (
    <FeedbackIssuesClient
      initialAppName={app ?? null}
      maintenancePrompts={MAINTENANCE_PROMPTS}
    />
  );
}
