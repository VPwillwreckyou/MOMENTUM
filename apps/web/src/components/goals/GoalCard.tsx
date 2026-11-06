import { Link } from 'react-router-dom';
import { ArrowUpRight, CalendarClock, Check, Repeat2, Sparkles } from 'lucide-react';
import type { Category, Goal } from '../../state/types';
import { computeGoalProgress, nextActionableTask } from '../../lib/progress';
import { deadlineLabel, isOverdue } from '../../lib/dates';
import { recurrenceLabel } from '../../lib/recurrence';
import { ProgressRing } from '../ui/Progress';
import { PriorityBadge } from '../ui/Badge';
import { CategoryBadge } from './CategoryBadge';

export function GoalCard({
  goal,
  category,
  compact = false,
}: {
  goal: Goal;
  category?: Category | null;
  compact?: boolean;
}) {
  const progress = computeGoalProgress(goal);
  const next = nextActionableTask(goal);
  const overdue = isOverdue(goal.deadline) && goal.status !== 'completed';
  const completed = goal.status === 'completed';

  return (
    <Link
      to={`/goals/${goal.id}`}
      className={`goal-card${compact ? ' goal-card--compact' : ''}${completed ? ' goal-card--completed' : ''}`}
      style={{ '--category-color': category?.color ?? 'var(--text-faint)' } as React.CSSProperties}
      aria-label={`Open goal ${goal.title}`}
    >
      <div className="goal-card__top">
        <CategoryBadge category={category} />
        <span className="goal-card__open">
          <ArrowUpRight size={16} />
        </span>
      </div>
      <div className="goal-card__body">
        <div className="goal-card__content">
          <h3 className="goal-card__title">{goal.title}</h3>
          {!compact && goal.description ? <p className="goal-card__description clamp-2">{goal.description}</p> : null}
          <div className="goal-card__meta">
            <PriorityBadge priority={goal.priority} />
            {goal.kind === 'recurring' ? (
              <span className="meta-chip">
                <Repeat2 size={12} />
                {recurrenceLabel(goal.recurrence)}
              </span>
            ) : null}
            {goal.aiGenerated ? (
              <span className="meta-chip meta-chip--accent">
                <Sparkles size={12} />
                AI draft
              </span>
            ) : null}
          </div>
        </div>
        <ProgressRing value={progress.percent} size={compact ? 52 : 64} stroke={4}>
          <strong>{progress.percent}%</strong>
        </ProgressRing>
      </div>

      <div className="goal-card__divider" />

      <div className="goal-card__footer">
        <div className={`goal-card__deadline${overdue ? ' goal-card__deadline--overdue' : ''}`}>
          <CalendarClock size={13} />
          <span>{goal.deadline ? deadlineLabel(goal.deadline) : 'No deadline'}</span>
        </div>
        {next ? (
          <div className="goal-card__next">
            <span className="goal-card__next-label">{completed ? 'Plan' : 'Next'}</span>
            <span className="goal-card__next-title truncate">{completed ? 'Completed' : next.title}</span>
          </div>
        ) : completed ? (
          <span className="goal-card__done">
            <Check size={13} /> Completed
          </span>
        ) : (
          <span className="goal-card__next-label">Plan a next move</span>
        )}
      </div>
    </Link>
  );
}
