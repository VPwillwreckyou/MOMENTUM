import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { EmptyState } from '../components/ui/EmptyState';

export function NotFoundPage() {
  return (
    <div className="page">
      <EmptyState
        icon={<Compass size={24} />}
        eyebrow="404"
        title="This route has no momentum."
        description="The page may have moved, or the address is incomplete. Your goals are still exactly where you left them."
        actions={
          <>
            <Link to="/" className="btn btn--primary btn--md">Return to overview</Link>
            <Link to="/goals" className="btn btn--secondary btn--md">Browse goals</Link>
          </>
        }
      />
    </div>
  );
}
