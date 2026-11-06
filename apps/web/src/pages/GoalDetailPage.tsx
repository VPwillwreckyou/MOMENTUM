import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Activity as ActivityIcon,
  ArrowLeft,
  ArrowUpRight,
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronDown,
  Circle,
  Clock3,
  Coins,
  Download,
  Flag,
  History,
  Layers,
  MoreHorizontal,
  Pencil,
  Plus,
  Repeat2,
  RotateCcw,
  Sparkles,
  Target,
  Trash2,
  Undo2,
} from 'lucide-react';
import type { Goal, Milestone, Task } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { Button, IconButton } from '../components/ui/Button';
import { ConfirmDialog } from '../components/ui/Modal';
import { Badge, PriorityBadge } from '../components/ui/Badge';
import { ProgressBar, ProgressRing } from '../components/ui/Progress';
import { CategoryLabel } from '../components/goals/CategoryBadge';
import { GoalForm } from '../components/goals/GoalForm';
import { TaskForm } from '../components/goals/TaskForm';
import { MilestoneForm } from '../components/goals/MilestoneForm';
import { AttachmentsPanel } from '../components/goals/AttachmentsPanel';
import { EmptyState } from '../components/ui/EmptyState';
import { computeGoalProgress, nextActionableTask } from '../lib/progress';
import { daysBetweenISO, deadlineLabel, formatDate, isOverdue, relativeTime } from '../lib/dates';
import { recurrenceLabel } from '../lib/recurrence';
import { exportGoalPDF } from '../lib/pdf';
import { useMomentum as useMomentumData } from '../state/MomentumContext';

type Tab = 'plan' | 'notes' | 'activity';

function TaskRow({
  task,
  subtasks,
  readOnly,
  onEdit,
  onAddSubtask,
  onToggle,
}: {
  task: Task;
  subtasks: Task[];
  readOnly: boolean;
  onEdit: (task: Task) => void;
  onAddSubtask: (task: Task) => void;
  onToggle: (taskId: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const hasDetail = Boolean(task.notes) || subtasks.length > 0;
  return (
    <div className={`task-item${task.completed ? ' task-item--done' : ''}`}>
      <div className="task-item__row">
        <button
          type="button"
          className={`task-check${task.completed ? ' task-check--checked' : ''}`}
          onClick={() => onToggle(task.id)}
          aria-label={task.completed ? `Reopen ${task.title}` : `Complete ${task.title}`}
          disabled={readOnly}
        >
          {task.completed ? <Check size={13} strokeWidth={3} /> : <Circle size={16} />}
        </button>
        <button
          type="button"
          className="task-item__title"
          onClick={() => hasDetail && setExpanded((current) => !current)}
          aria-expanded={hasDetail ? expanded : undefined}
        >
          <span>{task.title}</span>
        </button>
        <div className="task-item__meta">
          {task.dueDate ? (
            <span className={`task-item__due${!task.completed && isOverdue(task.dueDate) ? ' is-overdue' : ''}`}>
              {formatDate(task.dueDate, 'short')}
            </span>
          ) : null}
          <span className={`priority-dot priority-dot--${task.priority}`} title={`${task.priority} priority`} />
          {!readOnly ? (
            <div className="task-item__actions">
              <IconButton label="Add subtask" size="sm" onClick={() => onAddSubtask(task)}>
                <Plus size={14} />
              </IconButton>
              <IconButton label="Edit task" size="sm" onClick={() => onEdit(task)}>
                <Pencil size={13} />
              </IconButton>
            </div>
          ) : null}
          {hasDetail ? (
            <button
              type="button"
              className="task-item__expand"
              onClick={() => setExpanded((current) => !current)}
              aria-label={expanded ? 'Collapse task details' : 'Expand task details'}
              aria-expanded={expanded}
            >
              <ChevronDown size={14} className={expanded ? 'is-rotated' : ''} />
            </button>
          ) : null}
        </div>
      </div>
      {expanded && hasDetail ? (
        <div className="task-item__detail">
          {task.notes ? <p>{task.notes}</p> : null}
          {subtasks.length > 0 ? (
            <div className="subtasks">
              {subtasks.map((subtask) => (
                <div className={`subtask${subtask.completed ? ' subtask--done' : ''}`} key={subtask.id}>
                  <button
                    type="button"
                    className={`task-check task-check--sm${subtask.completed ? ' task-check--checked' : ''}`}
                    onClick={() => onToggle(subtask.id)}
                    disabled={readOnly}
                    aria-label={subtask.completed ? `Reopen ${subtask.title}` : `Complete ${subtask.title}`}
                  >
                    {subtask.completed ? <Check size={10} strokeWidth={3} /> : <Circle size={12} />}
                  </button>
                  <button type="button" className="subtask__title" onClick={() => onEdit(subtask)}>
                    {subtask.title}
                  </button>
                  {!readOnly ? (
                    <IconButton label="Edit subtask" size="sm" onClick={() => onEdit(subtask)}>
                      <Pencil size={12} />
                    </IconButton>
                  ) : null}
                </div>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function TaskList({
  goal,
  tasks,
  readOnly,
  onEdit,
  onAddSubtask,
  onAddTask,
  onToggle,
}: {
  goal: Goal;
  tasks: Task[];
  readOnly: boolean;
  onEdit: (task: Task) => void;
  onAddSubtask: (task: Task) => void;
  onAddTask: () => void;
  onToggle: (taskId: string) => void;
}) {
  const [showDone, setShowDone] = useState(false);
  const open = tasks.filter((task) => !task.completed);
  const done = tasks.filter((task) => task.completed);
  if (tasks.length === 0) {
    return (
      <button type="button" className="task-empty" onClick={onAddTask} disabled={readOnly}>
        <Plus size={14} /> Add the first concrete task
      </button>
    );
  }
  return (
    <div className="task-list">
      {open.map((task) => (
        <TaskRow
          key={task.id}
          task={task}
          subtasks={goal.tasks.filter((item) => item.parentTaskId === task.id)}
          readOnly={readOnly}
          onEdit={onEdit}
          onAddSubtask={onAddSubtask}
          onToggle={onToggle}
        />
      ))}
      {done.length > 0 ? (
        <>
          <button type="button" className="completed-toggle" onClick={() => setShowDone((current) => !current)}>
            <CheckCircle2 size={13} />
            {showDone ? 'Hide' : 'Show'} {done.length} completed
            <ChevronDown size={12} className={showDone ? 'is-rotated' : ''} />
          </button>
          {showDone
            ? done.map((task) => (
                <TaskRow
                  key={task.id}
                  task={task}
                  subtasks={goal.tasks.filter((item) => item.parentTaskId === task.id)}
                  readOnly={readOnly}
                  onEdit={onEdit}
                  onAddSubtask={onAddSubtask}
                  onToggle={onToggle}
                />
              ))
            : null}
        </>
      ) : null}
    </div>
  );
}

function GoalNotes({ goal, readOnly }: { goal: Goal; readOnly: boolean }) {
  const { updateGoal, toast } = useMomentum();
  const [notes, setNotes] = useState(goal.notes);
  const [savedNotes, setSavedNotes] = useState(goal.notes);
  const dirty = notes !== savedNotes;

  useEffect(() => {
    setNotes(goal.notes);
    setSavedNotes(goal.notes);
  }, [goal.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const save = () => {
    updateGoal(goal.id, { notes }, 'Updated goal notes');
    setSavedNotes(notes);
    toast('Notes saved.', 'success');
  };

  return (
    <div className="notes-panel">
      <div className="notes-panel__header">
        <div>
          <span className="eyebrow">Working memory</span>
          <h2>Notes</h2>
        </div>
        <div className="notes-panel__actions">
          <span className={`save-state${dirty ? ' save-state--dirty' : ''}`}>
            {dirty ? 'Unsaved changes' : 'Saved locally'}
          </span>
          <Button variant="primary" size="sm" onClick={save} disabled={!dirty || readOnly}>
            Save notes
          </Button>
        </div>
      </div>
      <textarea
        className="notes-editor"
        value={notes}
        onChange={(event) => setNotes(event.target.value)}
        placeholder="Capture decisions, links, evidence, blockers — the context that turns a plan into progress."
        readOnly={readOnly}
        rows={16}
        maxLength={10000}
      />
      <div className="notes-panel__footer">
        <span>{notes.length.toLocaleString()} / 10,000 characters</span>
        <span>Notes stay on this device by default.</span>
      </div>
    </div>
  );
}

function FinancePanel({ goal, readOnly }: { goal: Goal; readOnly: boolean }) {
  const { updateGoal, toast } = useMomentum();
  const finance = goal.finance!;
  const [current, setCurrent] = useState(String(finance.currentAmount));
  useEffect(() => setCurrent(String(goal.finance?.currentAmount ?? 0)), [goal.finance?.currentAmount]);
  const percent = finance.targetAmount > 0 ? Math.min(100, (finance.currentAmount / finance.targetAmount) * 100) : 0;
  const update = () => {
    const value = Number(current);
    if (!Number.isFinite(value) || value < 0) {
      toast('Enter a valid amount.', 'error');
      return;
    }
    updateGoal(goal.id, {
      finance: { ...finance, currentAmount: value },
    });
    toast('Saved amount updated.', 'success');
  };
  return (
    <section className="side-panel">
      <div className="side-panel__header">
        <div>
          <span className="eyebrow">Optional tracking</span>
          <h2>Money</h2>
        </div>
        <Coins size={16} className="muted" />
      </div>
      <div className="finance-summary">
        <strong>
          {finance.currency} {finance.currentAmount.toLocaleString()}
        </strong>
        <span>of {finance.currency} {finance.targetAmount.toLocaleString()}</span>
      </div>
      <ProgressBar value={percent} size="sm" />
      {!readOnly ? (
        <div className="finance-update">
          <input
            className="input input--sm"
            type="number"
            min={0}
            step="0.01"
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
            aria-label="Amount saved so far"
          />
          <Button size="sm" variant="secondary" onClick={update}>
            Update
          </Button>
        </div>
      ) : null}
      {finance.note ? <p className="side-panel__empty">{finance.note}</p> : null}
    </section>
  );
}

export function GoalDetailPage() {
  const { goalId } = useParams();
  const navigate = useNavigate();
  const {
    goals,
    categories,
    activities,
    settings,
    setGoalCompleted,
    toggleMilestone,
    archiveGoal,
    restoreArchivedGoal,
    trashGoal,
    restoreTrashedGoal,
    permanentlyDeleteGoal,
    toggleTask,
    toast,
  } = useMomentumData();
  const goal = goals.find((item) => item.id === goalId) ?? null;
  const category = categories.find((item) => item.id === goal?.categoryId) ?? null;
  const [tab, setTab] = useState<Tab>('plan');
  const [editOpen, setEditOpen] = useState(false);
  const [taskModal, setTaskModal] = useState<{
    task?: Task;
    milestoneId?: string | null;
    parentTaskId?: string | null;
    parentTaskTitle?: string;
  } | null>(null);
  const [milestoneModal, setMilestoneModal] = useState<Milestone | 'new' | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    setTab('plan');
    setMenuOpen(false);
  }, [goalId]);

  if (!goal) {
    return (
      <div className="page">
        <EmptyState
          icon={<Target size={24} />}
          eyebrow="Not found"
          title="This goal is not in your workspace."
          description="It may have been permanently deleted, or the link is incorrect."
          actions={
            <Link to="/goals" className="btn btn--secondary btn--md">
              <ArrowLeft size={15} /> Back to goals
            </Link>
          }
        />
      </div>
    );
  }

  const readOnly = Boolean(goal.deletedAt);
  const archived = Boolean(goal.archivedAt);
  const progress = computeGoalProgress(goal);
  const next = nextActionableTask(goal);
  const rootTasks = goal.tasks.filter((task) => !task.parentTaskId);
  const unassigned = rootTasks.filter((task) => !task.milestoneId).sort((a, b) => a.order - b.order);
  const goalActivities = activities.filter((activity) => activity.goalId === goal.id);
  const openTasks = goal.tasks.filter((task) => !task.completed).length;
  const series = goals.filter((item) =>
    goal.seriesId
      ? item.seriesId === goal.seriesId || item.id === goal.seriesId
      : item.id === goal.id || item.seriesId === goal.id,
  );
  const nextOccurrence = series
    .filter((item) => item.id !== goal.id && (item.occurrence ?? 1) > (goal.occurrence ?? 1))
    .sort((a, b) => (a.occurrence ?? 1) - (b.occurrence ?? 1))[0];

  const handleComplete = () => {
    if (goal.status === 'completed') {
      setGoalCompleted(goal.id, false);
      return;
    }
    if (openTasks > 0) {
      setCompleteOpen(true);
      return;
    }
    setGoalCompleted(goal.id, true);
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportGoalPDF(goal, category, settings.accent);
      toast('PDF exported.', 'success');
    } catch {
      toast('The PDF could not be generated. Try again.', 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="page goal-detail-page">
      <div className="detail-back">
        <Link to="/goals" className="text-link text-link--muted">
          <ArrowLeft size={14} /> All goals
        </Link>
        {archived ? <Badge tone="warning">Archived</Badge> : null}
        {goal.deletedAt ? <Badge tone="danger">In Recently Deleted</Badge> : null}
        {goal.status === 'completed' ? <Badge tone="success">Completed</Badge> : null}
      </div>

      {archived ? (
        <div className="detail-banner">
          <span className="detail-banner__icon"><Flag size={17} /></span>
          <div>
            <strong>This goal is archived.</strong>
            <p>It stays searchable and editable, but is out of your active focus.</p>
          </div>
          <Button variant="secondary" size="sm" icon={<RotateCcw size={14} />} onClick={() => restoreArchivedGoal(goal.id)}>
            Restore to active
          </Button>
        </div>
      ) : null}

      {goal.deletedAt ? (
        <div className="detail-banner detail-banner--danger">
          <span className="detail-banner__icon"><Trash2 size={17} /></span>
          <div>
            <strong>This goal is in Recently Deleted.</strong>
            <p>Restore it to keep working. Permanent deletion cannot be undone.</p>
          </div>
          <div className="detail-banner__actions">
            <Button variant="secondary" size="sm" icon={<Undo2 size={14} />} onClick={() => restoreTrashedGoal(goal.id)}>
              Restore
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleteOpen(true)}>
              Delete forever
            </Button>
          </div>
        </div>
      ) : null}

      <header className="detail-header">
        <div className="detail-header__copy">
          <div className="detail-header__eyebrow">
            <CategoryLabel category={category} />
            <span className="detail-separator">/</span>
            <PriorityBadge priority={goal.priority} />
            {goal.aiGenerated ? (
              <span className="meta-chip meta-chip--accent">
                <Sparkles size={12} /> AI-assisted plan
              </span>
            ) : null}
          </div>
          <h1>{goal.title}</h1>
          {goal.description ? <p className="detail-description">{goal.description}</p> : null}
          <div className="detail-meta-row">
            <span>
              <CalendarClock size={14} />
              {goal.deadline ? `${deadlineLabel(goal.deadline)} · ${formatDate(goal.deadline, 'medium')}` : 'No deadline'}
            </span>
            <span>
              <Clock3 size={14} /> Updated {relativeTime(goal.updatedAt)}
            </span>
            {goal.kind === 'recurring' ? (
              <span>
                <Repeat2 size={14} /> {recurrenceLabel(goal.recurrence)}
              </span>
            ) : null}
            {goal.occurrence && goal.occurrence > 1 ? (
              <span>Occurrence {goal.occurrence}</span>
            ) : null}
          </div>
        </div>
        <div className="detail-header__actions">
          <Button variant="secondary" size="md" icon={<Pencil size={15} />} onClick={() => setEditOpen(true)} disabled={readOnly}>
            Edit
          </Button>
          <Button variant="secondary" size="md" icon={<Download size={15} />} onClick={() => void handleExport()} loading={exporting}>
            PDF
          </Button>
          {!readOnly ? (
            <details
              className="action-menu"
              open={menuOpen}
              ref={menuRef}
              onToggle={(event) => setMenuOpen((event.currentTarget as HTMLDetailsElement).open)}
            >
              <summary className="icon-btn icon-btn--outlined icon-btn--md" aria-label="More goal actions">
                <MoreHorizontal size={17} />
              </summary>
              <div className="action-menu__popover">
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    if (menuRef.current) menuRef.current.open = false;
                    setArchiveOpen(true);
                  }}
                >
                  <Flag size={15} /> {archived ? 'Manage archive' : 'Archive goal'}
                </button>
                <button
                  type="button"
                  className="is-danger"
                  onClick={() => {
                    setMenuOpen(false);
                    if (menuRef.current) menuRef.current.open = false;
                    setDeleteOpen(true);
                  }}
                >
                  <Trash2 size={15} /> Move to Recently Deleted
                </button>
              </div>
            </details>
          ) : null}
          {goal.status === 'active' ? (
            <Button variant="primary" size="md" icon={<CheckCircle2 size={16} />} onClick={handleComplete} disabled={readOnly}>
              Complete goal
            </Button>
          ) : goal.kind === 'one-time' ? (
            <Button variant="secondary" size="md" icon={<RotateCcw size={15} />} onClick={handleComplete} disabled={readOnly}>
              Reopen
            </Button>
          ) : (
            <Badge tone="success" className="detail-complete-badge">Occurrence complete</Badge>
          )}
        </div>
      </header>

      {goal.status === 'completed' ? (
        <div className="completion-strip">
          <span className="completion-strip__icon"><CheckCircle2 size={18} /></span>
          <div>
            <strong>{goal.kind === 'recurring' ? 'This occurrence is complete.' : 'Goal complete.'}</strong>
            <p>
              {nextOccurrence
                ? `The next occurrence is scheduled${nextOccurrence.deadline ? ` for ${formatDate(nextOccurrence.deadline, 'medium')}` : ''}.`
                : 'The record remains here with every task and note intact.'}
            </p>
          </div>
          {nextOccurrence ? (
            <Link to={`/goals/${nextOccurrence.id}`} className="text-link">
              Open next <ArrowUpRight size={14} />
            </Link>
          ) : null}
        </div>
      ) : null}

      <div className="detail-layout">
        <div className="detail-main">
          <div className="detail-tabs" role="tablist" aria-label="Goal sections">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'plan'}
              className={`detail-tab${tab === 'plan' ? ' detail-tab--active' : ''}`}
              onClick={() => setTab('plan')}
            >
              <Layers size={15} /> Plan
              <span>{goal.tasks.length}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'notes'}
              className={`detail-tab${tab === 'notes' ? ' detail-tab--active' : ''}`}
              onClick={() => setTab('notes')}
            >
              <Pencil size={14} /> Notes
              {goal.notes ? <span className="detail-tab__dot" /> : null}
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'activity'}
              className={`detail-tab${tab === 'activity' ? ' detail-tab--active' : ''}`}
              onClick={() => setTab('activity')}
            >
              <History size={14} /> Activity
            </button>
          </div>

          {tab === 'plan' ? (
            <div className="plan-panel">
              <div className="plan-panel__toolbar">
                <div>
                  <span className="eyebrow">Execution</span>
                  <h2>The plan</h2>
                </div>
                {!readOnly ? (
                  <div className="plan-panel__actions">
                    <Button variant="ghost" size="sm" icon={<Plus size={14} />} onClick={() => setMilestoneModal('new')}>
                      Milestone
                    </Button>
                    <Button variant="secondary" size="sm" icon={<Plus size={14} />} onClick={() => setTaskModal({})}>
                      Task
                    </Button>
                  </div>
                ) : null}
              </div>

              {goal.milestones.length === 0 && goal.tasks.length === 0 ? (
                <div className="plan-empty">
                  <div className="plan-empty__line" />
                  <Target size={22} />
                  <h3>No plan yet.</h3>
                  <p>Start with one task, or add milestones for a larger arc.</p>
                  {!readOnly ? (
                    <div className="plan-empty__actions">
                      <Button variant="primary" size="sm" icon={<Plus size={14} />} onClick={() => setTaskModal({})}>
                        Add first task
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setMilestoneModal('new')}>
                        Add milestone
                      </Button>
                    </div>
                  ) : null}
                </div>
              ) : (
                <div className="milestone-list">
                  {[...goal.milestones]
                    .sort((a, b) => a.order - b.order)
                    .map((milestone, index) => {
                      const milestoneTasks = rootTasks
                        .filter((task) => task.milestoneId === milestone.id)
                        .sort((a, b) => a.order - b.order);
                      const done = milestoneTasks.filter((task) => task.completed).length;
                      return (
                        <section className={`milestone${milestone.completed ? ' milestone--done' : ''}`} key={milestone.id}>
                          <div className="milestone__rail">
                            <button
                              type="button"
                              className={`milestone__node${milestone.completed ? ' milestone__node--done' : ''}`}
                              onClick={() => toggleMilestone(goal.id, milestone.id)}
                              disabled={readOnly}
                              aria-label={milestone.completed ? `Reopen ${milestone.title}` : `Complete ${milestone.title}`}
                            >
                              {milestone.completed ? <Check size={13} strokeWidth={3} /> : String(index + 1).padStart(2, '0')}
                            </button>
                            {index < goal.milestones.length - 1 ? <span className="milestone__line" /> : null}
                          </div>
                          <div className="milestone__content">
                            <div className="milestone__header">
                              <div>
                                <h3>{milestone.title}</h3>
                                <div className="milestone__meta">
                                  {milestone.dueDate ? <span>{formatDate(milestone.dueDate, 'medium')}</span> : <span>No date</span>}
                                  <span>{done}/{milestoneTasks.length} tasks</span>
                                </div>
                              </div>
                              {!readOnly ? (
                                <div className="milestone__actions">
                                  <IconButton label="Add task to milestone" size="sm" onClick={() => setTaskModal({ milestoneId: milestone.id })}>
                                    <Plus size={14} />
                                  </IconButton>
                                  <IconButton label="Edit milestone" size="sm" onClick={() => setMilestoneModal(milestone)}>
                                    <Pencil size={13} />
                                  </IconButton>
                                </div>
                              ) : null}
                            </div>
                            {milestone.description ? <p className="milestone__description">{milestone.description}</p> : null}
                            <TaskList
                              goal={goal}
                              tasks={milestoneTasks}
                              readOnly={readOnly}
                              onEdit={(task) => setTaskModal({ task })}
                              onAddSubtask={(task) =>
                                setTaskModal({ parentTaskId: task.id, milestoneId: task.milestoneId, parentTaskTitle: task.title })
                              }
                              onAddTask={() => setTaskModal({ milestoneId: milestone.id })}
                              onToggle={(taskId) => toggleTask(goal.id, taskId)}
                            />
                          </div>
                        </section>
                      );
                    })}
                </div>
              )}

              {unassigned.length > 0 || (goal.milestones.length > 0 && goal.tasks.filter((task) => !task.parentTaskId).length === 0) ? (
                <section className="unassigned-tasks">
                  <div className="unassigned-tasks__header">
                    <div>
                      <span className="eyebrow">No milestone</span>
                      <h3>Direct tasks</h3>
                    </div>
                    {!readOnly ? (
                      <Button variant="ghost" size="sm" icon={<Plus size={14} />} onClick={() => setTaskModal({})}>
                        Add
                      </Button>
                    ) : null}
                  </div>
                  <TaskList
                    goal={goal}
                    tasks={unassigned}
                    readOnly={readOnly}
                    onEdit={(task) => setTaskModal({ task })}
                    onAddSubtask={(task) =>
                      setTaskModal({ parentTaskId: task.id, milestoneId: task.milestoneId, parentTaskTitle: task.title })
                    }
                    onAddTask={() => setTaskModal({})}
                    onToggle={(taskId) => toggleTask(goal.id, taskId)}
                  />
                </section>
              ) : null}

              {goal.milestones.length > 0 && unassigned.length === 0 ? (
                <button type="button" className="add-milestone-strip" onClick={() => setMilestoneModal('new')} disabled={readOnly}>
                  <Plus size={15} /> Add another milestone
                </button>
              ) : null}
            </div>
          ) : null}

          {tab === 'notes' ? <GoalNotes goal={goal} readOnly={readOnly} /> : null}

          {tab === 'activity' ? (
            <div className="activity-panel">
              <div className="activity-panel__header">
                <div>
                  <span className="eyebrow">Audit trail</span>
                  <h2>Activity</h2>
                </div>
                <ActivityIcon size={16} className="muted" />
              </div>
              {goalActivities.length === 0 ? (
                <p className="rail-empty">Actions on this goal will appear here.</p>
              ) : (
                <div className="activity-timeline">
                  {goalActivities.map((activity) => (
                    <div className="activity-event" key={activity.id}>
                      <span className="activity-event__dot" />
                      <div>
                        <p>{activity.message}</p>
                        <span>{relativeTime(activity.at)} · {formatDate(activity.at.slice(0, 10), 'medium')}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : null}
        </div>

        <aside className="detail-rail">
          <section className="signal-card">
            <div className="signal-card__top">
              <div>
                <span className="eyebrow">Progress</span>
                <h2>{progress.percent >= 100 ? 'Complete' : progress.percent >= 50 ? 'In motion' : 'Building'}</h2>
              </div>
              <ProgressRing value={progress.percent} size={66} stroke={4}>
                <strong>{progress.percent}%</strong>
              </ProgressRing>
            </div>
            <div className="signal-card__metrics">
              <div>
                <span>Tasks</span>
                <strong>{progress.completedTasks}<small>/{progress.totalTasks}</small></strong>
              </div>
              <div>
                <span>Milestones</span>
                <strong>{progress.completedMilestones}<small>/{progress.totalMilestones}</small></strong>
              </div>
              <div>
                <span>Open</span>
                <strong>{openTasks}</strong>
              </div>
            </div>
            <ProgressBar value={progress.percent} size="sm" />
            {next && goal.status === 'active' ? (
              <div className="signal-card__next">
                <span className="eyebrow">Next move</span>
                <button
                  type="button"
                  onClick={() => {
                    setTab('plan');
                    toggleTask(goal.id, next.id);
                  }}
                  className="signal-next-button"
                >
                  <span className="task-check task-check--sm"><Circle size={12} /></span>
                  <span>{next.title}</span>
                  <Check size={14} />
                </button>
              </div>
            ) : null}
          </section>

          <section className="side-panel">
            <div className="side-panel__header">
              <div>
                <span className="eyebrow">Record</span>
                <h2>Details</h2>
              </div>
            </div>
            <dl className="detail-list">
              <div>
                <dt>Status</dt>
                <dd>
                  {goal.status === 'completed' ? (
                    <span className="status-text status-text--success">Completed</span>
                  ) : (
                    <span className="status-text">Active</span>
                  )}
                </dd>
              </div>
              <div>
                <dt>Deadline</dt>
                <dd>{goal.deadline ? formatDate(goal.deadline, 'medium') : 'None'}</dd>
              </div>
              <div>
                <dt>Started</dt>
                <dd>{goal.startDate ? formatDate(goal.startDate, 'medium') : formatDate(goal.createdAt.slice(0, 10), 'medium')}</dd>
              </div>
              <div>
                <dt>Priority</dt>
                <dd>{goal.priority.charAt(0).toUpperCase() + goal.priority.slice(1)}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>{category?.name ?? 'Uncategorized'}</dd>
              </div>
              <div>
                <dt>Rhythm</dt>
                <dd>{goal.kind === 'recurring' ? recurrenceLabel(goal.recurrence) : 'One-time'}</dd>
              </div>
              {goal.deadline ? (
                <div>
                  <dt>Time left</dt>
                  <dd>
                    {daysBetweenISO(new Date().toISOString(), `${goal.deadline}T23:59:59`) >= 0
                      ? `${daysBetweenISO(new Date().toISOString(), `${goal.deadline}T23:59:59`)} days`
                      : `${Math.abs(daysBetweenISO(new Date().toISOString(), `${goal.deadline}T23:59:59`))} days overdue`}
                  </dd>
                </div>
              ) : null}
            </dl>
            <div className="detail-list__actions">
              <Button
                variant="ghost"
                size="sm"
                icon={<Pencil size={14} />}
                onClick={() => setEditOpen(true)}
                disabled={readOnly}
                fullWidth
              >
                Edit details
              </Button>
            </div>
          </section>

          {goal.finance?.enabled ? <FinancePanel goal={goal} readOnly={readOnly} /> : null}

          <AttachmentsPanel goalId={goal.id} readOnly={readOnly} />

          {series.length > 1 ? (
            <section className="side-panel">
              <div className="side-panel__header">
                <div>
                  <span className="eyebrow">Recurring series</span>
                  <h2>Occurrences</h2>
                </div>
                <Repeat2 size={16} className="muted" />
              </div>
              <div className="series-list">
                {[...series]
                  .sort((a, b) => (a.occurrence ?? 1) - (b.occurrence ?? 1))
                  .map((item) => (
                    <Link
                      to={`/goals/${item.id}`}
                      key={item.id}
                      className={`series-row${item.id === goal.id ? ' series-row--current' : ''}`}
                    >
                      <span>#{item.occurrence ?? 1}</span>
                      <span className="truncate">{item.deadline ? formatDate(item.deadline, 'short') : item.title}</span>
                      <Badge tone={item.status === 'completed' ? 'success' : 'neutral'}>
                        {item.status === 'completed' ? 'Done' : 'Active'}
                      </Badge>
                    </Link>
                  ))}
              </div>
            </section>
          ) : null}
        </aside>
      </div>

      <GoalForm open={editOpen} onClose={() => setEditOpen(false)} goal={goal} />
      {taskModal ? (
        <TaskForm
          open
          onClose={() => setTaskModal(null)}
          goalId={goal.id}
          task={taskModal.task}
          defaultMilestoneId={taskModal.milestoneId}
          defaultParentTaskId={taskModal.parentTaskId}
          parentTaskTitle={taskModal.parentTaskTitle}
        />
      ) : null}
      {milestoneModal ? (
        <MilestoneForm
          open
          onClose={() => setMilestoneModal(null)}
          goalId={goal.id}
          milestone={milestoneModal === 'new' ? null : milestoneModal}
        />
      ) : null}

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => {
          if (goal.deletedAt) {
            void permanentlyDeleteGoal(goal.id);
            navigate('/trash');
          } else {
            trashGoal(goal.id);
            navigate('/trash');
          }
          setDeleteOpen(false);
        }}
        title={goal.deletedAt ? 'Delete permanently?' : 'Move to Recently Deleted?'}
        confirmLabel={goal.deletedAt ? 'Delete forever' : 'Move to Recently Deleted'}
        description={
          goal.deletedAt
            ? 'This goal, its tasks, notes and attachments will be removed immediately. This cannot be undone.'
            : 'The goal will leave your active workspace and wait in Recently Deleted for 30 days. You can restore it anytime.'
        }
      />

      <ConfirmDialog
        open={completeOpen}
        onClose={() => setCompleteOpen(false)}
        onConfirm={() => {
          setCompleteOpen(false);
          setGoalCompleted(goal.id, true);
        }}
        title="Complete with open tasks?"
        confirmLabel="Complete goal"
        tone="primary"
        description={`${openTasks} ${openTasks === 1 ? 'task is' : 'tasks are'} still open. Completing the goal marks it 100% complete while keeping those tasks in the record.`}
      />

      <ConfirmDialog
        open={archiveOpen}
        onClose={() => setArchiveOpen(false)}
        onConfirm={() => {
          setArchiveOpen(false);
          if (archived) restoreArchivedGoal(goal.id);
          else archiveGoal(goal.id);
        }}
        title={archived ? 'Restore this goal?' : 'Archive this goal?'}
        confirmLabel={archived ? 'Restore goal' : 'Archive goal'}
        tone="primary"
        description={
          archived
            ? 'The goal returns to your active workspace. Its progress and plan are untouched.'
            : 'Archiving removes the goal from your active workspace while keeping its full history. You can restore it anytime.'
        }
      />
    </div>
  );
}
