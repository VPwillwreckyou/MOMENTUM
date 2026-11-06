import { Link } from 'react-router-dom';
import { Archive, ArrowUpRight, RotateCcw, Trash2 } from 'lucide-react';
import { useMomentum } from '../state/MomentumContext';
import { PageHeader } from '../components/ui/PageHeader';
import { EmptyState } from '../components/ui/EmptyState';
import { Button, IconButton } from '../components/ui/Button';
import { CategoryLabel } from '../components/goals/CategoryBadge';
import { ProgressBar } from '../components/ui/Progress';
import { computeGoalProgress } from '../lib/progress';
import { formatDate, relativeTime } from '../lib/dates';

export function ArchivePage() {
  const { goals, categories, restoreArchivedGoal, trashGoal } = useMomentum();
  const archived = goals
    .filter((goal) => goal.archivedAt && !goal.deletedAt)
    .sort((a, b) => (b.archivedAt ?? '').localeCompare(a.archivedAt ?? ''));

  return (
    <div className="page archive-page">
      <PageHeader
        eyebrow="Library"
        title="Archive"
        description="Set aside without losing the record. Archived goals are searchable and can return at any time."
      />

      {archived.length === 0 ? (
        <EmptyState
          icon={<Archive size={24} />}
          eyebrow="Nothing set aside"
          title="Your archive is clear."
          description="Archive a goal when it is no longer part of your active focus but you are not ready to let it go."
          actions={
            <Link to="/goals" className="btn btn--secondary btn--md">
              Browse goals
            </Link>
          }
        />
      ) : (
        <>
          <div className="results-meta">
            <span>{archived.length} archived {archived.length === 1 ? 'goal' : 'goals'}</span>
            <span className="results-meta__line" />
          </div>
          <div className="archive-list">
            {archived.map((goal) => {
              const progress = computeGoalProgress(goal);
              const category = categories.find((item) => item.id === goal.categoryId) ?? null;
              return (
                <article className="archive-row" key={goal.id}>
                  <div className="archive-row__main">
                    <div className="archive-row__meta">
                      <CategoryLabel category={category} />
                      <span>·</span>
                      <span>Archived {relativeTime(goal.archivedAt ?? goal.updatedAt)}</span>
                      {goal.deadline ? <span>· Deadline {formatDate(goal.deadline, 'short')}</span> : null}
                    </div>
                    <h2>{goal.title}</h2>
                    {goal.description ? <p className="clamp-2">{goal.description}</p> : null}
                    <div className="archive-row__progress">
                      <ProgressBar value={progress.percent} size="sm" />
                      <span>{progress.percent}%</span>
                      <span className="muted">
                        {progress.completedTasks}/{progress.totalTasks} tasks
                      </span>
                    </div>
                  </div>
                  <div className="archive-row__actions">
                    <Link to={`/goals/${goal.id}`} className="btn btn--ghost btn--sm">
                      Open <ArrowUpRight size={14} />
                    </Link>
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={<RotateCcw size={14} />}
                      onClick={() => restoreArchivedGoal(goal.id)}
                    >
                      Restore
                    </Button>
                    <IconButton label="Move to Recently Deleted" onClick={() => trashGoal(goal.id)} size="sm">
                      <Trash2 size={15} />
                    </IconButton>
                  </div>
                </article>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
