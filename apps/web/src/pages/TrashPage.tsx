import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock3, RotateCcw, ShieldAlert, Trash2 } from 'lucide-react';
import type { Goal } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { PageHeader } from '../components/ui/PageHeader';
import { EmptyState } from '../components/ui/EmptyState';
import { Button, IconButton } from '../components/ui/Button';
import { ConfirmDialog } from '../components/ui/Modal';
import { CategoryLabel } from '../components/goals/CategoryBadge';
import { formatDate, relativeTime } from '../lib/dates';

const RETENTION_DAYS = 30;

function daysRemaining(deletedAt: string): number {
  const expiry = new Date(deletedAt).getTime() + RETENTION_DAYS * 86400000;
  return Math.max(0, Math.ceil((expiry - Date.now()) / 86400000));
}

export function TrashPage() {
  const { goals, categories, restoreTrashedGoal, permanentlyDeleteGoal, emptyTrash } = useMomentum();
  const [deleting, setDeleting] = useState<Goal | null>(null);
  const [emptyOpen, setEmptyOpen] = useState(false);
  const trashed = goals
    .filter((goal) => goal.deletedAt)
    .sort((a, b) => (b.deletedAt ?? '').localeCompare(a.deletedAt ?? ''));

  return (
    <div className="page trash-page">
      <PageHeader
        eyebrow="Library"
        title="Recently deleted"
        description="A 30-day safety net for goals. Restore them anytime before midnight on day 30."
        actions={
          trashed.length > 0 ? (
            <Button variant="danger" icon={<Trash2 size={15} />} onClick={() => setEmptyOpen(true)}>
              Empty Recently Deleted
            </Button>
          ) : null
        }
      />

      <div className="retention-banner">
        <span className="retention-banner__icon">
          <ShieldAlert size={18} />
        </span>
        <div>
          <strong>Deletion is permanent after 30 days.</strong>
          <p>Only goals use this recovery window. Individual tasks and subtasks can be removed immediately.</p>
        </div>
        <span className="retention-banner__badge">
          <Clock3 size={13} /> 30 days
        </span>
      </div>

      {trashed.length === 0 ? (
        <EmptyState
          icon={<Trash2 size={24} />}
          eyebrow="All clear"
          title="Nothing is waiting for deletion."
          description="Goals you delete move here first, giving you a month to change your mind."
          actions={
            <Link to="/goals" className="btn btn--secondary btn--md">
              Back to goals
            </Link>
          }
        />
      ) : (
        <div className="trash-list">
          {trashed.map((goal) => {
            const category = categories.find((item) => item.id === goal.categoryId) ?? null;
            const remaining = daysRemaining(goal.deletedAt!);
            return (
              <article className="trash-row" key={goal.id}>
                <div className="trash-row__copy">
                  <div className="trash-row__meta">
                    <CategoryLabel category={category} />
                    <span>·</span>
                    <span>Deleted {relativeTime(goal.deletedAt!)}</span>
                    <span>·</span>
                    <span>{goal.tasks.length} tasks</span>
                  </div>
                  <h2>{goal.title}</h2>
                  <div className={`trash-row__retention${remaining <= 5 ? ' trash-row__retention--urgent' : ''}`}>
                    <Clock3 size={12} />
                    {remaining === 0
                      ? 'Removed on next cleanup'
                      : `${remaining} ${remaining === 1 ? 'day' : 'days'} left to restore`}
                    <span>· scheduled for {formatDate(new Date(new Date(goal.deletedAt!).getTime() + RETENTION_DAYS * 86400000).toISOString().slice(0, 10), 'short')}</span>
                  </div>
                </div>
                <div className="trash-row__actions">
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<RotateCcw size={14} />}
                    onClick={() => restoreTrashedGoal(goal.id)}
                  >
                    Restore
                  </Button>
                  <IconButton label={`Delete ${goal.title} permanently`} onClick={() => setDeleting(goal)} size="sm">
                    <Trash2 size={15} />
                  </IconButton>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) void permanentlyDeleteGoal(deleting.id);
          setDeleting(null);
        }}
        title="Delete permanently?"
        confirmLabel="Delete forever"
        description={`“${deleting?.title ?? 'This goal'}” and its tasks, notes and attachments will be removed immediately. This cannot be undone.`}
      />

      <ConfirmDialog
        open={emptyOpen}
        onClose={() => setEmptyOpen(false)}
        onConfirm={() => {
          void emptyTrash();
          setEmptyOpen(false);
        }}
        title="Empty Recently Deleted?"
        confirmLabel="Delete everything"
        description={`All ${trashed.length} goals in Recently Deleted will be removed permanently, including their tasks, notes and attachments.`}
      />
    </div>
  );
}
