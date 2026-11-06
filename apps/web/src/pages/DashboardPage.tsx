import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CalendarClock,
  Check,
  CheckCircle2,
  Circle,
  Flame,
  Goal,
  Plus,
  Sparkles,
  Target,
  Timer,
} from 'lucide-react';
import { useMomentum } from '../state/MomentumContext';
import { useAppShell } from '../components/layout/AppShellContext';
import { useAuth } from '../state/AuthContext';
import { computeGoalProgress, momentumScore, tasksDueThisWeek, weeklyCompletionSeries } from '../lib/progress';
import { deadlineLabel, formatDate, isOverdue, isToday } from '../lib/dates';
import { ProgressRing } from '../components/ui/Progress';
import { GoalCard } from '../components/goals/GoalCard';
import { EmptyState } from '../components/ui/EmptyState';
import { Button } from '../components/ui/Button';
import { PriorityBadge } from '../components/ui/Badge';
import { CategoryLabel } from '../components/goals/CategoryBadge';
import type { Goal as GoalType, Task } from '../state/types';

function greeting(name?: string): string {
  const hour = new Date().getHours();
  const base = hour < 5 ? 'Still moving' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  return name ? `${base}, ${name.split(' ')[0]}` : base;
}

interface FocusTask extends Task {
  goalTitle: string;
  goalId: string;
}

function buildFocusTasks(goals: GoalType[]): FocusTask[] {
  const active = goals.filter((goal) => goal.status === 'active' && !goal.archivedAt && !goal.deletedAt);
  const all = active.flatMap((goal) =>
    goal.tasks
      .filter((task) => !task.completed)
      .map((task) => ({ ...task, goalTitle: goal.title })),
  );
  const priority = { critical: 4, high: 3, medium: 2, low: 1 };
  return all
    .sort((a, b) => {
      const aDue = a.dueDate ? new Date(`${a.dueDate}T23:59:59`).getTime() : Number.MAX_SAFE_INTEGER;
      const bDue = b.dueDate ? new Date(`${b.dueDate}T23:59:59`).getTime() : Number.MAX_SAFE_INTEGER;
      return aDue - bDue || priority[b.priority] - priority[a.priority];
    })
    .slice(0, 5);
}

export function DashboardPage() {
  const { goals, categories, settings, toggleTask, activities } = useMomentum();
  const { openNewGoal } = useAppShell();
  const { user } = useAuth();

  const liveGoals = goals.filter((goal) => !goal.deletedAt && !goal.archivedAt);
  const active = liveGoals.filter((goal) => goal.status === 'active');
  const completed = liveGoals.filter((goal) => goal.status === 'completed');
  const score = momentumScore(liveGoals);
  const dueWeek = tasksDueThisWeek(active, settings.weekStartsOn);
  const focusTasks = useMemo(() => buildFocusTasks(liveGoals), [liveGoals]);
  const series = useMemo(() => weeklyCompletionSeries(liveGoals, 14), [liveGoals]);
  const maxSeries = Math.max(1, ...series.map((day) => day.value));
  const completedThisWeek = series.slice(-7).reduce((sum, day) => sum + day.value, 0);
  const todayCompleted = liveGoals.reduce(
    (sum, goal) =>
      sum + goal.tasks.filter((task) => task.completedAt && isToday(task.completedAt.slice(0, 10))).length,
    0,
  );
  const overdue = active.flatMap((goal) => goal.tasks.filter((task) => !task.completed && isOverdue(task.dueDate)));
  const featured = [...active]
    .sort((a, b) => {
      const aDate = a.deadline ? new Date(a.deadline).getTime() : Number.MAX_SAFE_INTEGER;
      const bDate = b.deadline ? new Date(b.deadline).getTime() : Number.MAX_SAFE_INTEGER;
      return aDate - bDate;
    })
    .slice(0, 4);
  const upcoming = active
    .filter((goal) => goal.deadline)
    .sort((a, b) => new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime())
    .slice(0, 4);
  const today = new Date();
  const displayName = settings.displayName || user?.name;
  const categoryFor = (id: string | null) => categories.find((category) => category.id === id) ?? null;
  const leadTask = focusTasks[0];
  const recentActivity = activities.slice(0, 4);

  return (
    <div className="page dashboard-page">
      <header className="dashboard-greeting">
        <div>
          <span className="eyebrow">{formatDate(today.toISOString().slice(0, 10), 'long')}</span>
          <h1>{greeting(displayName)}</h1>
          <p>
            {liveGoals.length === 0
              ? 'Your command center is ready. Give it a direction.'
              : active.length === 0
                ? 'Everything is complete. Take the win, then choose what is next.'
                : `${active.length} active ${active.length === 1 ? 'goal' : 'goals'} in motion. ${dueWeek.length ? `${dueWeek.length} ${dueWeek.length === 1 ? 'task' : 'tasks'} due this week.` : 'No tasks due this week.'}`}
          </p>
        </div>
        <div className="dashboard-greeting__actions">
          <Button variant="primary" icon={<Plus size={16} />} onClick={openNewGoal}>
            New goal
          </Button>
          <Link to="/planner" className="btn btn--secondary btn--md">
            <Sparkles size={16} />
            Plan with AI
          </Link>
        </div>
      </header>

      {liveGoals.length === 0 ? (
        <section className="dashboard-empty">
          <div className="dashboard-empty__visual" aria-hidden="true">
            <div className="dashboard-empty__orbit dashboard-empty__orbit--one" />
            <div className="dashboard-empty__orbit dashboard-empty__orbit--two" />
            <div className="dashboard-empty__core">
              <Target size={32} />
            </div>
            <span className="dashboard-empty__node dashboard-empty__node--a" />
            <span className="dashboard-empty__node dashboard-empty__node--b" />
            <span className="dashboard-empty__node dashboard-empty__node--c" />
          </div>
          <EmptyState
            icon={<Goal size={24} />}
            eyebrow="The first move"
            title="A direction becomes momentum here."
            description="Create a goal manually or let the Goal Architect interview you and draft an editable plan. Nothing leaves your device unless you choose to use AI."
            actions={
              <>
                <Button variant="primary" icon={<Plus size={16} />} onClick={openNewGoal}>
                  Create your first goal
                </Button>
                <Link to="/planner" className="btn btn--secondary btn--md">
                  <Sparkles size={16} />
                  Build it with AI
                </Link>
              </>
            }
          />
          <div className="dashboard-empty__principles">
            <div>
              <span>01</span>
              <strong>Local by default</strong>
              <p>Goals live in this browser, not on a server.</p>
            </div>
            <div>
              <span>02</span>
              <strong>Structure without friction</strong>
              <p>Milestones and tasks stay out of the way until you need them.</p>
            </div>
            <div>
              <span>03</span>
              <strong>Your providers</strong>
              <p>DeepSeek, OpenAI, Gemini, Groq or a local Ollama model.</p>
            </div>
          </div>
        </section>
      ) : (
        <>
          <section className="command-hero">
            <div className="command-hero__main">
              <div className="command-hero__signal">
                <div className="signal-label">
                  <span className="signal-label__dot" />
                  Momentum signal
                </div>
                <div className="signal-score">
                  <span className="signal-score__number">{score}</span>
                  <span className="signal-score__unit">/ 100</span>
                </div>
                <p className="signal-caption">
                  {score >= 75
                    ? 'Strong cadence. Protect the rhythm.'
                    : score >= 45
                      ? 'Progress is compounding. Close the next loop.'
                      : score >= 20
                        ? 'Momentum is forming. One completed task changes the shape.'
                        : 'A quiet start. Pick one small move and break inertia.'}
                </p>
                <div className="signal-stats">
                  <div>
                    <span>Active goals</span>
                    <strong>{active.length}</strong>
                  </div>
                  <div>
                    <span>Cleared this week</span>
                    <strong>{completedThisWeek}</strong>
                  </div>
                  <div>
                    <span>Overdue</span>
                    <strong className={overdue.length > 0 ? 'is-warning' : undefined}>{overdue.length}</strong>
                  </div>
                </div>
              </div>
              <div className="command-hero__ring">
                <ProgressRing value={score} size={190} stroke={7} label={`Momentum score ${score} out of 100`}>
                  <Flame size={22} className="signal-flame" />
                  <strong>{score}</strong>
                  <span>Momentum</span>
                </ProgressRing>
                <div className="hero-orbit-label hero-orbit-label--top">velocity</div>
                <div className="hero-orbit-label hero-orbit-label--bottom">consistency</div>
              </div>
            </div>

            <div className="command-hero__focus">
              <div className="focus-panel__header">
                <span className="eyebrow">Next best move</span>
                <Timer size={15} />
              </div>
              {leadTask ? (
                <div className="focus-move">
                  <div className="focus-move__goal">
                    <CategoryLabel category={categoryFor(goals.find((goal) => goal.id === leadTask.goalId)?.categoryId ?? null)} />
                  </div>
                  <h2>{leadTask.title}</h2>
                  <Link to={`/goals/${leadTask.goalId}`} className="focus-move__goal-title">
                    {leadTask.goalTitle}
                    <ArrowRight size={13} />
                  </Link>
                  <div className="focus-move__meta">
                    <span className={isOverdue(leadTask.dueDate) ? 'is-overdue' : ''}>
                      <CalendarClock size={13} />
                      {leadTask.dueDate ? deadlineLabel(leadTask.dueDate) : 'No due date'}
                    </span>
                    <PriorityBadge priority={leadTask.priority} />
                  </div>
                  <button
                    type="button"
                    className="focus-move__complete"
                    onClick={() => toggleTask(leadTask.goalId, leadTask.id)}
                  >
                    <span className="focus-move__check">
                      <Check size={14} />
                    </span>
                    Mark complete
                  </button>
                </div>
              ) : (
                <div className="focus-move focus-move--clear">
                  <CheckCircle2 size={24} />
                  <h2>No open tasks</h2>
                  <p>Open a goal and add the next concrete move.</p>
                </div>
              )}
              <div className="focus-panel__footer">
                <span>{todayCompleted} completed today</span>
                <span>{dueWeek.length} on this week’s radar</span>
              </div>
            </div>
          </section>

          <div className="dashboard-grid">
            <div className="dashboard-main">
              <section className="section-block">
                <div className="section-heading">
                  <div>
                    <span className="eyebrow">In motion</span>
                    <h2>Active goals</h2>
                  </div>
                  <Link to="/goals" className="text-link">
                    View all <ArrowRight size={14} />
                  </Link>
                </div>
                {featured.length > 0 ? (
                  <div className="goal-grid goal-grid--dashboard">
                    {featured.map((goal) => (
                      <GoalCard key={goal.id} goal={goal} category={categoryFor(goal.categoryId)} />
                    ))}
                  </div>
                ) : (
                  <div className="panel panel--empty-row">
                    <CheckCircle2 size={20} />
                    <div>
                      <strong>All active goals are complete.</strong>
                      <p>Review what you finished or start the next chapter.</p>
                    </div>
                    <Button variant="secondary" size="sm" onClick={openNewGoal}>
                      New goal
                    </Button>
                  </div>
                )}
              </section>

              <section className="section-block">
                <div className="section-heading">
                  <div>
                    <span className="eyebrow">The trail</span>
                    <h2>Momentum over 14 days</h2>
                  </div>
                  <div className="chart-legend">
                    <span />
                    completed tasks
                  </div>
                </div>
                <div className="momentum-chart panel">
                  <div className="momentum-chart__summary">
                    <div>
                      <strong>{completedThisWeek}</strong>
                      <span>tasks cleared this week</span>
                    </div>
                    <div className="momentum-chart__trend">
                      <Flame size={14} /> {score >= 45 ? 'Building' : 'Warming up'}
                    </div>
                  </div>
                  <div className="momentum-bars" role="img" aria-label="Completed tasks per day over the last 14 days">
                    {series.map((day, index) => (
                      <div className="momentum-bar" key={`${day.label}-${index}`}>
                        <div className="momentum-bar__track">
                          <div
                            className={`momentum-bar__fill${day.value > 0 ? ' momentum-bar__fill--active' : ''}`}
                            style={{ height: `${Math.max(5, (day.value / maxSeries) * 100)}%` }}
                            title={`${day.value} completed`}
                          />
                        </div>
                        <span>{index % 2 === 0 ? day.label : ''}</span>
                      </div>
                    ))}
                  </div>
                  <div className="momentum-chart__footer">
                    <span>Last 14 days</span>
                    <span>{todayCompleted} today</span>
                  </div>
                </div>
              </section>
            </div>

            <aside className="dashboard-rail">
              <section className="rail-panel">
                <div className="rail-panel__header">
                  <div>
                    <span className="eyebrow">Queue</span>
                    <h2>Next moves</h2>
                  </div>
                  <span className="rail-count">{focusTasks.length}</span>
                </div>
                {focusTasks.length === 0 ? (
                  <p className="rail-empty">Nothing queued. Your plan is clear.</p>
                ) : (
                  <div className="next-moves">
                    {focusTasks.map((task) => {
                      const goal = goals.find((item) => item.id === task.goalId);
                      return (
                        <div className="next-move" key={`${task.goalId}-${task.id}`}>
                          <button
                            type="button"
                            className="task-check task-check--ghost"
                            aria-label={`Complete ${task.title}`}
                            onClick={() => toggleTask(task.goalId, task.id)}
                          >
                            <Circle size={16} />
                          </button>
                          <Link to={`/goals/${task.goalId}`} className="next-move__copy">
                            <span className="next-move__title">{task.title}</span>
                            <span className="next-move__meta">
                              {goal?.title}
                              {task.dueDate ? ` · ${deadlineLabel(task.dueDate)}` : ''}
                            </span>
                          </Link>
                          {task.dueDate && isOverdue(task.dueDate) ? <span className="next-move__flag">late</span> : null}
                        </div>
                      );
                    })}
                  </div>
                )}
                <Link to="/goals" className="rail-panel__link">
                  Open the full plan <ArrowRight size={13} />
                </Link>
              </section>

              <section className="rail-panel">
                <div className="rail-panel__header">
                  <div>
                    <span className="eyebrow">Horizon</span>
                    <h2>Deadlines</h2>
                  </div>
                </div>
                {upcoming.length === 0 ? (
                  <p className="rail-empty">No dated goals. That is allowed — not everything needs a deadline.</p>
                ) : (
                  <div className="deadline-list">
                    {upcoming.map((goal) => {
                      const progress = computeGoalProgress(goal);
                      return (
                        <Link to={`/goals/${goal.id}`} className="deadline-row" key={goal.id}>
                          <span className="deadline-row__date">
                            <strong>{new Date(`${goal.deadline}T12:00:00`).getDate()}</strong>
                            <span>
                              {new Intl.DateTimeFormat(undefined, { month: 'short' })
                                .format(new Date(`${goal.deadline}T12:00:00`))
                                .toUpperCase()}
                            </span>
                          </span>
                          <span className="deadline-row__copy">
                            <span className="deadline-row__title truncate">{goal.title}</span>
                            <span className="deadline-row__meta">{progress.percent}% · {deadlineLabel(goal.deadline)}</span>
                          </span>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </section>

              <section className="rail-panel rail-panel--activity">
                <div className="rail-panel__header">
                  <div>
                    <span className="eyebrow">Latest</span>
                    <h2>Activity</h2>
                  </div>
                </div>
                {recentActivity.length === 0 ? (
                  <p className="rail-empty">Your actions will appear here as the plan moves.</p>
                ) : (
                  <div className="activity-mini">
                    {recentActivity.map((activity) => (
                      <div className="activity-mini__item" key={activity.id}>
                        <span className="activity-mini__dot" />
                        <div>
                          <p>{activity.message}</p>
                          <span>{formatDate(activity.at.slice(0, 10), 'short')}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </aside>
          </div>

          {completed.length > 0 ? (
            <section className="wins-strip">
              <span className="wins-strip__icon">
                <CheckCircle2 size={18} />
              </span>
              <div>
                <strong>{completed.length} completed {completed.length === 1 ? 'goal' : 'goals'}</strong>
                <p>Proof of motion, kept out of your active focus until you need it.</p>
              </div>
              <Link to="/goals?status=completed" className="text-link">
                Review wins <ArrowRight size={14} />
              </Link>
            </section>
          ) : null}
        </>
      )}
    </div>
  );
}
