import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowDownWideNarrow, FilterX, Goal as GoalIcon, Plus, Search, SlidersHorizontal, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useMomentum } from '../state/MomentumContext';
import { useAppShell } from '../components/layout/AppShellContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { GoalCard } from '../components/goals/GoalCard';
import { EmptyState } from '../components/ui/EmptyState';
import { filterGoals, type DeadlineFilter, type GoalSort, type GoalStatusFilter } from '../lib/search';
import { computeGoalProgress } from '../lib/progress';
import { isThisWeek } from '../lib/dates';

export function GoalsPage() {
  const { goals, categories } = useMomentum();
  const { openNewGoal } = useAppShell();
  const [params, setParams] = useSearchParams();
  const [showFilters, setShowFilters] = useState(false);

  useEffect(() => {
    if (params.get('new') !== '1') return;
    openNewGoal();
    const next = new URLSearchParams(params);
    next.delete('new');
    setParams(next, { replace: true });
  }, [params, setParams, openNewGoal]);

  const status = (['active', 'completed', 'all'].includes(params.get('status') ?? '')
    ? params.get('status')
    : 'active') as GoalStatusFilter;
  const priority = params.get('priority') ?? 'all';
  const categoryId = params.get('category') ?? 'all';
  const deadline = (params.get('deadline') ?? 'any') as DeadlineFilter;
  const sort = (params.get('sort') ?? 'recent') as GoalSort;
  const query = params.get('q') ?? '';
  const deferredQuery = useDeferredValue(query);

  const setParam = (key: string, value: string, defaultValue?: string) => {
    const next = new URLSearchParams(params);
    if (!value || value === defaultValue) next.delete(key);
    else next.set(key, value);
    setParams(next, { replace: true });
  };

  const liveGoals = goals.filter((goal) => !goal.deletedAt && !goal.archivedAt);
  const filtered = useMemo(
    () =>
      filterGoals(liveGoals, {
        query: deferredQuery,
        status,
        priority,
        categoryId,
        deadline,
        sort,
      }),
    [liveGoals, deferredQuery, status, priority, categoryId, deadline, sort],
  );

  const activeCount = liveGoals.filter((goal) => goal.status === 'active').length;
  const completedCount = liveGoals.filter((goal) => goal.status === 'completed').length;
  const avgProgress = liveGoals.length
    ? Math.round(liveGoals.reduce((sum, goal) => sum + computeGoalProgress(goal).percent, 0) / liveGoals.length)
    : 0;
  const dueThisWeek = liveGoals.filter(
    (goal) => goal.status === 'active' && isThisWeek(goal.deadline),
  ).length;
  const filtersActive = priority !== 'all' || categoryId !== 'all' || deadline !== 'any' || query.trim() !== '';
  const categoryFor = (id: string | null) => categories.find((category) => category.id === id) ?? null;

  const emptyTitle = liveGoals.length === 0
    ? 'Your first goal starts here.'
    : status === 'completed'
      ? 'No completed goals yet.'
      : 'No goals match these filters.';
  const emptyDescription = liveGoals.length === 0
    ? 'Write down the outcome you want. MOMENTUM will give it structure, progress and a place to live.'
    : status === 'completed'
      ? 'Completed goals gather here. Keep moving — the next win is built one task at a time.'
      : 'Try a broader status, another category, or clear the search phrase.';

  return (
    <div className="page goals-page">
      <PageHeader
        eyebrow="Library"
        title="Goals"
        description="Every active direction, with the evidence of your progress attached."
        actions={
          <>
            <Link to="/planner" className="btn btn--secondary btn--md">
              <Sparkles size={16} />
              AI architect
            </Link>
            <Button variant="primary" icon={<Plus size={16} />} onClick={openNewGoal}>
              New goal
            </Button>
          </>
        }
      />

      <section className="goals-stat-strip" aria-label="Goal statistics">
        <div>
          <span className="eyebrow">Active</span>
          <strong>{activeCount}</strong>
        </div>
        <div>
          <span className="eyebrow">Completed</span>
          <strong>{completedCount}</strong>
        </div>
        <div>
          <span className="eyebrow">Average progress</span>
          <strong>{avgProgress}%</strong>
        </div>
        <div>
          <span className="eyebrow">Due this week</span>
          <strong>{dueThisWeek}</strong>
        </div>
      </section>

      <div className="goals-toolbar">
        <div className="status-tabs" role="tablist" aria-label="Goal status">
          {(
            [
              ['active', `Active ${activeCount}`],
              ['completed', `Completed ${completedCount}`],
              ['all', `All ${liveGoals.length}`],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={status === value}
              className={`status-tab${status === value ? ' status-tab--active' : ''}`}
              onClick={() => setParam('status', value, 'active')}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="goals-toolbar__right">
          <div className="input-with-icon goals-search">
            <Search size={15} />
            <input
              className="input"
              placeholder="Filter goals…"
              aria-label="Filter goals"
              value={query}
              onChange={(event) => setParam('q', event.target.value)}
            />
          </div>
          <button
            type="button"
            className={`filter-toggle${showFilters || filtersActive ? ' filter-toggle--active' : ''}`}
            onClick={() => setShowFilters((current) => !current)}
            aria-expanded={showFilters}
          >
            <SlidersHorizontal size={15} />
            Filters
            {filtersActive ? <span className="filter-toggle__dot" /> : null}
          </button>
          <div className="sort-control">
            <ArrowDownWideNarrow size={15} />
            <select
              aria-label="Sort goals"
              value={sort}
              onChange={(event) => setParam('sort', event.target.value, 'recent')}
            >
              <option value="recent">Recently updated</option>
              <option value="deadline">Nearest deadline</option>
              <option value="priority">Highest priority</option>
              <option value="progress">Most progress</option>
              <option value="created">Newest</option>
              <option value="title">A–Z</option>
            </select>
          </div>
        </div>
      </div>

      {showFilters ? (
        <div className="filter-panel">
          <label className="filter-field">
            <span>Category</span>
            <select value={categoryId} onChange={(event) => setParam('category', event.target.value)}>
              <option value="all">All categories</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            <span>Priority</span>
            <select value={priority} onChange={(event) => setParam('priority', event.target.value)}>
              <option value="all">Any priority</option>
              <option value="critical">Critical</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
          </label>
          <label className="filter-field">
            <span>Deadline</span>
            <select value={deadline} onChange={(event) => setParam('deadline', event.target.value)}>
              <option value="any">Any time</option>
              <option value="week">Next 7 days</option>
              <option value="month">Next 30 days</option>
              <option value="overdue">Overdue</option>
              <option value="none">No deadline</option>
            </select>
          </label>
          <button
            type="button"
            className="filter-clear"
            onClick={() => {
              setParams(new URLSearchParams(), { replace: true });
              setShowFilters(false);
            }}
          >
            <FilterX size={15} /> Clear all
          </button>
        </div>
      ) : null}

      {filtered.length > 0 ? (
        <>
          <div className="results-meta">
            <span>
              {filtered.length} {filtered.length === 1 ? 'goal' : 'goals'}
            </span>
            <span className="results-meta__line" />
          </div>
          <div className="goal-grid">
            {filtered.map((goal) => (
              <GoalCard key={goal.id} goal={goal} category={categoryFor(goal.categoryId)} />
            ))}
          </div>
        </>
      ) : (
        <EmptyState
          icon={<GoalIcon size={24} />}
          eyebrow={liveGoals.length === 0 ? 'Start here' : 'Nothing found'}
          title={emptyTitle}
          description={emptyDescription}
          actions={
            liveGoals.length === 0 ? (
              <>
                <Button variant="primary" icon={<Plus size={16} />} onClick={openNewGoal}>
                  Create a goal
                </Button>
                <Link to="/planner" className="btn btn--secondary btn--md">
                  <Sparkles size={16} /> Plan with AI
                </Link>
              </>
            ) : (
              <Button
                variant="secondary"
                onClick={() => {
                  setParams(new URLSearchParams(), { replace: true });
                  setShowFilters(false);
                }}
              >
                Reset filters
              </Button>
            )
          }
        />
      )}
    </div>
  );
}
