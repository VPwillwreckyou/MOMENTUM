import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Bot,
  CalendarDays,
  Check,
  ChevronRight,
  Circle,
  LoaderCircle,
  LockKeyhole,
  Pencil,
  Plus,
  RotateCcw,
  Settings2,
  Sparkles,
  Target,
  Trash2,
  WandSparkles,
  X,
} from 'lucide-react';
import type { AIPlan, AIPlanMilestone, AIPlanTask, PlannerAnswers, Priority } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button, IconButton } from '../components/ui/Button';
import { Checkbox, Input } from '../components/ui/Fields';
import { Badge } from '../components/ui/Badge';
import { ConfirmDialog } from '../components/ui/Modal';
import { AI_PROVIDER_LOCK, PROVIDER_DEFAULTS } from '../lib/defaults';
import { duplicateGoalTitle } from '../lib/mutations';
import { aiConfigurationIssue, generateAIPlan } from '../lib/ai';
import { formatDate, toDateInput } from '../lib/dates';
import { ApiError } from '../lib/api';
import { useOnlineStatus } from '../lib/useOnlineStatus';

const STEP_COUNT = 6;

const TIME_OPTIONS = ['2–4 hours / week', '5–8 hours / week', '10+ hours / week', 'A little every day', 'I need to figure that out'];

const emptyAnswers: PlannerAnswers = {
  goal: '',
  situation: '',
  success: '',
  timeCommitment: '',
  targetDate: '',
  constraints: '',
};

interface PlanTaskEditorProps {
  task: AIPlanTask;
  onChange: (task: AIPlanTask) => void;
  onRemove: () => void;
}

function PlanTaskEditor({ task, onChange, onRemove }: PlanTaskEditorProps) {
  return (
    <div className="plan-task-editor">
      <span className="plan-task-editor__handle">
        <Circle size={10} />
      </span>
      <input
        className="input input--sm plan-task-editor__title"
        value={task.title}
        onChange={(event) => onChange({ ...task, title: event.target.value })}
        aria-label="Task title"
        maxLength={240}
      />
      <input
        className="input input--sm plan-task-editor__date"
        type="date"
        value={task.dueDate ?? ''}
        onChange={(event) => onChange({ ...task, dueDate: event.target.value || null })}
        aria-label="Task due date"
      />
      <select
        className="select select--sm plan-task-editor__priority"
        value={task.priority ?? 'medium'}
        onChange={(event) => onChange({ ...task, priority: event.target.value as Priority })}
        aria-label="Task priority"
      >
        <option value="low">Low</option>
        <option value="medium">Medium</option>
        <option value="high">High</option>
        <option value="critical">Critical</option>
      </select>
      <IconButton label="Remove task" size="sm" onClick={onRemove}>
        <X size={14} />
      </IconButton>
    </div>
  );
}

function PlanPreview({
  plan,
  setPlan,
  onBack,
  onRegenerate,
  onAccept,
  targetDate,
}: {
  plan: AIPlan;
  setPlan: (plan: AIPlan) => void;
  onBack: () => void;
  onRegenerate: () => void;
  onAccept: () => void;
  targetDate: string;
}) {
  const totalTasks =
    plan.milestones.reduce((sum, milestone) => sum + milestone.tasks.length, 0) + plan.firstTasks.length;

  const updateMilestone = (index: number, milestone: AIPlanMilestone) => {
    const milestones = plan.milestones.map((item, itemIndex) => (itemIndex === index ? milestone : item));
    setPlan({ ...plan, milestones });
  };

  const updateTask = (milestoneIndex: number, taskIndex: number, task: AIPlanTask) => {
    const milestone = plan.milestones[milestoneIndex];
    updateMilestone(milestoneIndex, {
      ...milestone,
      tasks: milestone.tasks.map((item, index) => (index === taskIndex ? task : item)),
    });
  };

  return (
    <div className="plan-preview">
      <div className="plan-preview__banner">
        <span className="plan-preview__banner-icon"><WandSparkles size={18} /></span>
        <div>
          <strong>Review before this becomes your plan.</strong>
          <p>Every field is editable. The AI made a proposal — you make the decisions.</p>
        </div>
        <div className="plan-preview__banner-actions">
          <Button variant="ghost" size="sm" icon={<ArrowLeft size={14} />} onClick={onBack}>
            Interview
          </Button>
          <Button variant="ghost" size="sm" icon={<RotateCcw size={14} />} onClick={onRegenerate}>
            Regenerate
          </Button>
        </div>
      </div>

      <div className="plan-preview__layout">
        <div className="plan-preview__main">
          <section className="plan-edit-section plan-edit-section--identity">
            <span className="eyebrow">Goal identity</span>
            <textarea
              className="plan-title-input"
              value={plan.title}
              onChange={(event) => setPlan({ ...plan, title: event.target.value })}
              aria-label="Plan title"
              maxLength={120}
              rows={2}
            />
            <textarea
              className="plan-description-input"
              value={plan.description}
              onChange={(event) => setPlan({ ...plan, description: event.target.value })}
              aria-label="Plan description"
              rows={4}
              maxLength={4000}
            />
            <div className="plan-edit-section__meta">
              <CalendarDays size={14} />
              <span>Suggested deadline</span>
              <input
                className="input input--sm"
                type="date"
                value={plan.suggestedDeadline ?? ''}
                onChange={(event) => setPlan({ ...plan, suggestedDeadline: event.target.value || null })}
                aria-label="Suggested deadline"
              />
              {targetDate ? <span className="muted">Target was {targetDate}</span> : null}
            </div>
          </section>

          <section className="plan-edit-section">
            <div className="plan-edit-section__header">
              <div>
                <span className="eyebrow">The arc</span>
                <h2>Milestones</h2>
              </div>
              <Button
                variant="ghost"
                size="sm"
                icon={<Plus size={14} />}
                onClick={() => {
                  const milestone: AIPlanMilestone = {
                    title: 'New milestone',
                    description: '',
                    dueDate: targetDate || null,
                    tasks: [],
                  };
                  setPlan({ ...plan, milestones: [...plan.milestones, milestone] });
                }}
              >
                Add milestone
              </Button>
            </div>

            <div className="plan-milestones">
              {plan.milestones.map((milestone, milestoneIndex) => (
                <article className="plan-milestone-editor" key={milestoneIndex}>
                  <div className="plan-milestone-editor__number">
                    {String(milestoneIndex + 1).padStart(2, '0')}
                  </div>
                  <div className="plan-milestone-editor__body">
                    <div className="plan-milestone-editor__top">
                      <input
                        className="input plan-milestone-editor__title"
                        value={milestone.title}
                        onChange={(event) => updateMilestone(milestoneIndex, { ...milestone, title: event.target.value })}
                        aria-label="Milestone title"
                        maxLength={160}
                      />
                      <input
                        className="input input--sm"
                        type="date"
                        value={milestone.dueDate ?? ''}
                        onChange={(event) =>
                          updateMilestone(milestoneIndex, { ...milestone, dueDate: event.target.value || null })
                        }
                        aria-label="Milestone target date"
                      />
                      <IconButton
                        label="Remove milestone"
                        size="sm"
                        onClick={() =>
                          setPlan({
                            ...plan,
                            milestones: plan.milestones.filter((_, index) => index !== milestoneIndex),
                          })
                        }
                      >
                        <Trash2 size={14} />
                      </IconButton>
                    </div>
                    <textarea
                      className="plan-milestone-editor__description"
                      value={milestone.description ?? ''}
                      onChange={(event) =>
                        updateMilestone(milestoneIndex, { ...milestone, description: event.target.value })
                      }
                      placeholder="What will be true when this milestone is complete?"
                      aria-label="Milestone description"
                      rows={2}
                      maxLength={800}
                    />
                    <div className="plan-task-list">
                      {milestone.tasks.map((task, taskIndex) => (
                        <PlanTaskEditor
                          key={taskIndex}
                          task={task}
                          onChange={(next) => updateTask(milestoneIndex, taskIndex, next)}
                          onRemove={() =>
                            updateMilestone(milestoneIndex, {
                              ...milestone,
                              tasks: milestone.tasks.filter((_, index) => index !== taskIndex),
                            })
                          }
                        />
                      ))}
                    </div>
                    <button
                      type="button"
                      className="add-plan-task"
                      onClick={() =>
                        updateMilestone(milestoneIndex, {
                          ...milestone,
                          tasks: [
                            ...milestone.tasks,
                            { title: 'New task', priority: 'medium', dueDate: milestone.dueDate ?? null },
                          ],
                        })
                      }
                    >
                      <Plus size={13} /> Add task
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="plan-edit-section">
            <div className="plan-edit-section__header">
              <div>
                <span className="eyebrow">Before the first milestone</span>
                <h2>Starting moves</h2>
              </div>
              <Button
                variant="ghost"
                size="sm"
                icon={<Plus size={14} />}
                onClick={() =>
                  setPlan({
                    ...plan,
                    firstTasks: [...plan.firstTasks, { title: 'New starting move', priority: 'medium', dueDate: null }],
                  })
                }
              >
                Add move
              </Button>
            </div>
            <div className="plan-task-list plan-task-list--standalone">
              {plan.firstTasks.map((task, index) => (
                <PlanTaskEditor
                  key={index}
                  task={task}
                  onChange={(next) =>
                    setPlan({
                      ...plan,
                      firstTasks: plan.firstTasks.map((item, itemIndex) => (itemIndex === index ? next : item)),
                    })
                  }
                  onRemove={() =>
                    setPlan({ ...plan, firstTasks: plan.firstTasks.filter((_, itemIndex) => itemIndex !== index) })
                  }
                />
              ))}
              {plan.firstTasks.length === 0 ? <p className="plan-task-list__empty">No starting moves yet.</p> : null}
            </div>
          </section>
        </div>

        <aside className="plan-preview__aside">
          <div className="plan-summary-card">
            <span className="eyebrow">Plan summary</span>
            <div className="plan-summary-stats">
              <div><strong>{plan.milestones.length}</strong><span>milestones</span></div>
              <div><strong>{totalTasks}</strong><span>tasks</span></div>
              <div><strong>{plan.suggestedDeadline ? formatDate(plan.suggestedDeadline, 'short') : '—'}</strong><span>deadline</span></div>
            </div>
            <Button variant="primary" fullWidth icon={<Check size={16} />} onClick={onAccept}>
              Add to MOMENTUM
            </Button>
            <p className="plan-summary-card__note">
              This creates a real goal with milestones, tasks and dates. Everything remains editable.
            </p>
          </div>
          <div className="plan-tips">
            <span className="eyebrow">Before you commit</span>
            <ul>
              <li><Circle size={9} /> Do the tasks start with actions you can take?</li>
              <li><Circle size={9} /> Do the dates fit the time you actually have?</li>
              <li><Circle size={9} /> Is the first move possible this week?</li>
            </ul>
            <p>If not, edit the plan. Generic plans are a failure state, not a feature.</p>
          </div>
        </aside>
      </div>
    </div>
  );
}

export function PlannerPage() {
  const navigate = useNavigate();
  const online = useOnlineStatus();
  const {
    settings,
    goals,
    updateSettings,
    createGoalFromPlan,
    createGoal,
    toast,
  } = useMomentum();
  const [answers, setAnswers] = useState<PlannerAnswers>(emptyAnswers);
  const [step, setStep] = useState(0);
  const [consent, setConsent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [plan, setPlan] = useState<AIPlan | null>(null);
  const [duplicateConfirm, setDuplicateConfirm] = useState<{ kind: 'plan'; plan: AIPlan } | { kind: 'manual' } | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const provider = PROVIDER_DEFAULTS[settings.ai.provider];
  const configIssue = aiConfigurationIssue(settings.ai);
  const aiLocallyConfigured = settings.ai.hasKey || settings.ai.provider === 'ollama' || Boolean(AI_PROVIDER_LOCK);
  const configured = Boolean(settings.ai.model && settings.ai.baseUrl) && !configIssue;
  const alreadyHasGoal = useMemo(
    () => goals.some((goal) => !goal.deletedAt && !goal.archivedAt && goal.title.toLowerCase() === answers.goal.trim().toLowerCase()),
    [goals, answers.goal],
  );

  useEffect(() => () => controllerRef.current?.abort(), []);

  const canAdvance = () => {
    if (step === 0) return answers.goal.trim().length >= 6;
    if (step === 1) return answers.situation.trim().length >= 4;
    if (step === 2) return answers.success.trim().length >= 4;
    if (step === 3) return Boolean(answers.timeCommitment.trim());
    return true;
  };

  const next = () => {
    if (!canAdvance()) return;
    setStep((current) => Math.min(STEP_COUNT - 1, current + 1));
    setError(null);
  };

  const generate = async () => {
    if (loading) return;
    if (answers.goal.trim().length < 6) {
      setStep(0);
      setError('Start with a clear goal before asking the architect to plan it.');
      return;
    }
    if (answers.situation.trim().length < 4 || answers.success.trim().length < 4) {
      setStep(answers.situation.trim().length < 4 ? 1 : 2);
      setError('Add the missing context so the plan is specific to you.');
      return;
    }
    if (!answers.timeCommitment.trim()) {
      setStep(3);
      setError('Add your real available time. The plan depends on it.');
      return;
    }
    if (!consent) {
      setError('Confirm that your interview answers may be sent to the AI provider.');
      return;
    }
    if (configIssue) {
      setError(configIssue);
      return;
    }
    if (!online) {
      setError('You are offline. AI planning needs a connection — you can still create this goal manually.');
      return;
    }
    setError(null);
    setLoading(true);
    updateSettings({
      ai: { ...settings.ai, consentAcceptedAt: new Date().toISOString() },
    });
    const controller = new AbortController();
    controllerRef.current = controller;
    const timer = window.setTimeout(() => controller.abort(), 370_000);
    try {
      const generated = await generateAIPlan(settings.ai, answers, controller.signal);
      setPlan(generated);
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') {
        setError('The generation was cancelled or timed out. You can try again.');
      } else if (caught instanceof ApiError) {
        setError(caught.message);
      } else {
        setError(caught instanceof Error ? caught.message : 'The plan could not be generated.');
      }
    } finally {
      window.clearTimeout(timer);
      controllerRef.current = null;
      setLoading(false);
    }
  };

  const commitManualDraft = () => {
    const goal = createGoal({
      title: answers.goal.trim(),
      description: answers.success.trim() || answers.goal.trim(),
      notes: [
        answers.situation ? `Starting point: ${answers.situation}` : '',
        answers.timeCommitment ? `Available time: ${answers.timeCommitment}` : '',
        answers.constraints ? `Constraints: ${answers.constraints}` : '',
      ]
        .filter(Boolean)
        .join('\n\n'),
      deadline: answers.targetDate || null,
      priority: 'medium',
    });
    toast('Draft goal created. Build the plan at your pace.', 'success');
    navigate(`/goals/${goal.id}`);
  };

  const createManualDraft = () => {
    if (!answers.goal.trim()) {
      setStep(0);
      setError('Add a goal title first.');
      return;
    }
    if (duplicateGoalTitle(goals, answers.goal)) {
      setDuplicateConfirm({ kind: 'manual' });
      return;
    }
    commitManualDraft();
  };

  const commitPlan = (cleaned: AIPlan) => {
    const created = createGoalFromPlan(
      {
        title: cleaned.title,
        description: cleaned.description,
        deadline: cleaned.suggestedDeadline ?? (answers.targetDate || null),
        priority: 'medium',
      },
      cleaned,
    );
    toast('Plan added. Your first move is ready.', 'success');
    navigate(`/goals/${created.id}`);
  };

  const acceptPlan = () => {
    if (!plan) return;
    const cleaned: AIPlan = {
      ...plan,
      title: plan.title.trim(),
      description: plan.description.trim(),
      milestones: plan.milestones
        .map((milestone) => ({
          ...milestone,
          title: milestone.title.trim(),
          tasks: milestone.tasks
            .filter((task) => task.title.trim())
            .map((task) => ({ ...task, title: task.title.trim() })),
        }))
        .filter((milestone) => milestone.title),
      firstTasks: plan.firstTasks.filter((task) => task.title.trim()).map((task) => ({ ...task, title: task.title.trim() })),
    };
    if (!cleaned.title) {
      toast('Give the plan a title before adding it.', 'error');
      return;
    }
    if (cleaned.milestones.length === 0 && cleaned.firstTasks.length === 0) {
      toast('Add at least one milestone or task first.', 'error');
      return;
    }
    if (duplicateGoalTitle(goals, cleaned.title)) {
      setDuplicateConfirm({ kind: 'plan', plan: cleaned });
      return;
    }
    commitPlan(cleaned);
  };

  const generateAnother = () => {
    setPlan(null);
    setStep(5);
  };

  if (plan) {
    return (
      <div className="page planner-page">
        <PageHeader
          eyebrow="AI goal architect"
          title="Shape the plan."
          description="A proposal, not a prescription. Edit anything that does not fit your real life."
          actions={
            <Badge tone="accent">
              <Sparkles size={12} /> {provider.label} · {settings.ai.model}
            </Badge>
          }
        />
        <PlanPreview
          plan={plan}
          setPlan={setPlan}
          onBack={generateAnother}
          onRegenerate={() => {
            setPlan(null);
            setStep(5);
            window.setTimeout(() => void generate(), 50);
          }}
          onAccept={acceptPlan}
          targetDate={answers.targetDate}
        />
      </div>
    );
  }

  const questionIndex = Math.min(step, 5);
  const progress = ((questionIndex + 1) / STEP_COUNT) * 100;

  return (
    <div className="page planner-page">
      <PageHeader
        eyebrow="AI goal architect"
        title="From ambition to an actual plan."
        description="Answer a short, focused interview. The architect uses your context — not a template — to build milestones and next moves."
        actions={
          <Link to="/settings#ai" className="btn btn--ghost btn--md">
            <Settings2 size={15} /> {provider.label}
          </Link>
        }
      />

      <div className="architect-shell">
        <div className="architect-main">
          <div className="architect-progress">
            <div className="architect-progress__rail">
              <span style={{ width: `${progress}%` }} />
            </div>
            <div className="architect-progress__labels">
              <span>Interview</span>
              <span>{questionIndex + 1} / {STEP_COUNT}</span>
            </div>
          </div>

          {!configured ? (
            <div className="inline-warning inline-warning--setup">
              <span className="inline-warning__icon"><Bot size={16} /></span>
              <div>
                <strong>AI provider needs configuration.</strong>
                <p>{configIssue ?? 'Choose a provider, model and API key in settings before generating.'}</p>
              </div>
              <Link to="/settings#ai" className="text-link">Configure <ArrowUpRight size={13} /></Link>
            </div>
          ) : !aiLocallyConfigured ? (
            <div className="planner-note">
              <LockKeyhole size={14} />
              <span>
                No local key is saved for {provider.label}. This server may provide one — otherwise add yours in AI settings.
              </span>
            </div>
          ) : null}

          <div className="architect-card">
            {questionIndex === 0 ? (
              <div className="architect-question">
                <span className="architect-question__index">01 · The ambition</span>
                <h2>What do you want to achieve?</h2>
                <p>Say it in your own words. A rough thought is enough to start.</p>
                <textarea
                  className="architect-input"
                  value={answers.goal}
                  onChange={(event) => setAnswers({ ...answers, goal: event.target.value })}
                  placeholder="e.g. I want to get my first three freelance clients without quitting my job."
                  rows={5}
                  maxLength={800}
                  autoFocus
                />
                {alreadyHasGoal ? (
                  <div className="inline-warning">
                    <span>An active goal with this title already exists. You may want to extend that goal instead.</span>
                  </div>
                ) : null}
                <div className="architect-suggestions">
                  <span>Try to include:</span>
                  <span className="suggestion-chip">The outcome</span>
                  <span className="suggestion-chip">Who it is for</span>
                  <span className="suggestion-chip">Why now</span>
                </div>
              </div>
            ) : null}

            {questionIndex === 1 ? (
              <div className="architect-question">
                <span className="architect-question__index">02 · The starting point</span>
                <h2>Where are you right now?</h2>
                <p>Be honest about the current reality. This is what makes the plan fit you rather than someone else.</p>
                <textarea
                  className="architect-input"
                  value={answers.situation}
                  onChange={(event) => setAnswers({ ...answers, situation: event.target.value })}
                  placeholder="Skills, resources, progress so far, what you have tried, what is not working…"
                  rows={5}
                  maxLength={1500}
                  autoFocus
                />
                <div className="architect-suggestions">
                  <span>Useful context:</span>
                  <span className="suggestion-chip">Current skill level</span>
                  <span className="suggestion-chip">Time you actually have</span>
                  <span className="suggestion-chip">What has stalled before</span>
                </div>
              </div>
            ) : null}

            {questionIndex === 2 ? (
              <div className="architect-question">
                <span className="architect-question__index">03 · The finish line</span>
                <h2>What does success look like?</h2>
                <p>Make it observable. How would you know — with evidence — that this worked?</p>
                <textarea
                  className="architect-input"
                  value={answers.success}
                  onChange={(event) => setAnswers({ ...answers, success: event.target.value })}
                  placeholder="e.g. Three signed client agreements, a repeatable proposal process, and a portfolio case study."
                  rows={5}
                  maxLength={1500}
                  autoFocus
                />
              </div>
            ) : null}

            {questionIndex === 3 ? (
              <div className="architect-question">
                <span className="architect-question__index">04 · The container</span>
                <h2>What time and date are real?</h2>
                <p>The best plan is one that survives your actual week. Choose honestly.</p>
                <span className="field__label">Available time</span>
                <div className="time-options">
                  {TIME_OPTIONS.map((option) => (
                    <button
                      key={option}
                      type="button"
                      className={`time-option${answers.timeCommitment === option ? ' time-option--active' : ''}`}
                      onClick={() => setAnswers({ ...answers, timeCommitment: option })}
                    >
                      {answers.timeCommitment === option ? <Check size={13} /> : null}
                      {option}
                    </button>
                  ))}
                </div>
                <Input
                  label="Custom time budget"
                  value={answers.timeCommitment}
                  onChange={(event) => setAnswers({ ...answers, timeCommitment: event.target.value })}
                  placeholder="Or describe it precisely"
                  optional
                />
                <Input
                  label="Target date"
                  type="date"
                  value={answers.targetDate}
                  min={toDateInput()}
                  onChange={(event) => setAnswers({ ...answers, targetDate: event.target.value })}
                  hint="No fixed date is valid. The architect will propose a realistic one."
                  optional
                />
              </div>
            ) : null}

            {questionIndex === 4 ? (
              <div className="architect-question">
                <span className="architect-question__index">05 · The friction</span>
                <h2>What could get in the way?</h2>
                <p>Constraints are design inputs. Name the real ones so the plan can route around them.</p>
                <textarea
                  className="architect-input"
                  value={answers.constraints}
                  onChange={(event) => setAnswers({ ...answers, constraints: event.target.value })}
                  placeholder="e.g. I travel two weeks a month, budget is limited, evenings are family time, I lose momentum when feedback is slow."
                  rows={5}
                  maxLength={1500}
                  autoFocus
                />
                <div className="architect-suggestions">
                  <span>Worth naming:</span>
                  <span className="suggestion-chip">Time windows</span>
                  <span className="suggestion-chip">Budget</span>
                  <span className="suggestion-chip">Dependencies</span>
                  <span className="suggestion-chip">Failure patterns</span>
                </div>
              </div>
            ) : null}

            {questionIndex === 5 ? (
              <div className="architect-question">
                <span className="architect-question__index">06 · The brief</span>
                <h2>One last look before we build.</h2>
                <p>This is what the architect will use. No assumptions beyond these answers.</p>
                <div className="brief-review">
                  {(
                    [
                      ['Goal', answers.goal, 0],
                      ['Starting point', answers.situation, 1],
                      ['Success', answers.success, 2],
                      ['Time', `${answers.timeCommitment}${answers.targetDate ? ` · by ${answers.targetDate}` : ''}`, 3],
                      ['Constraints', answers.constraints || 'None stated', 4],
                    ] as const
                  ).map(([label, value, index]) => (
                    <div className="brief-row" key={label}>
                      <span>{label}</span>
                      <p>{value || 'Not provided'}</p>
                      <IconButton label={`Edit ${label}`} size="sm" onClick={() => setStep(index)}>
                        <Pencil size={13} />
                      </IconButton>
                    </div>
                  ))}
                </div>

                <div className="ai-consent">
                  <Checkbox
                    checked={consent}
                    onChange={setConsent}
                    label={
                      <span>
                        Send these interview answers to <strong>{provider.label}</strong> for this plan request.
                      </span>
                    }
                    description="Only what is above is sent. Your goals, existing workspace and account data are not included. Nothing is sent until you press Generate."
                  />
                </div>

                {error ? (
                  <div className="form-error-banner" role="alert">
                    <X size={16} />
                    <div>
                      <strong>The plan could not be generated.</strong>
                      <p>{error}</p>
                    </div>
                  </div>
                ) : null}

                <div className="generate-row">
                  <Button
                    variant="primary"
                    size="lg"
                    icon={<Sparkles size={17} />}
                    onClick={() => void generate()}
                    loading={loading}
                    disabled={!configured || loading}
                  >
                    Generate my plan
                  </Button>
                  <span className="generate-row__note">
                    <LockKeyhole size={12} /> Answers are sent only for this request
                  </span>
                </div>
                {!configured ? (
                  <Link to="/settings#ai" className="text-link">
                    Configure an AI provider first <ArrowUpRight size={13} />
                  </Link>
                ) : null}
              </div>
            ) : null}

            <div className="architect-actions">
              <Button
                variant="ghost"
                icon={<ArrowLeft size={15} />}
                onClick={() => setStep((current) => Math.max(0, current - 1))}
                disabled={step === 0 || loading}
              >
                Back
              </Button>
              <div className="architect-actions__right">
                <span className="architect-actions__hint">
                  {step === 5 ? 'Ready when you are.' : 'Your answers stay in this session.'}
                </span>
                {step < 5 ? (
                  <Button
                    variant="primary"
                    trailingIcon={<ArrowRight size={15} />}
                    onClick={next}
                    disabled={!canAdvance()}
                  >
                    Continue
                  </Button>
                ) : null}
              </div>
            </div>
          </div>

          {loading ? (
            <div className="architect-loading" role="status" aria-live="polite">
              <div className="architect-loading__orb">
                <LoaderCircle size={22} className="spin" />
              </div>
              <div>
                <strong>{provider.label} is drafting your plan…</strong>
                <p>Mapping milestones, sequencing tasks and fitting them to your real constraints. Cloud models usually take 10–30 seconds; local models can take longer.</p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  controllerRef.current?.abort();
                }}
              >
                Cancel
              </Button>
            </div>
          ) : null}
        </div>

        <aside className="architect-aside">
          <div className="architect-aside__card">
            <span className="eyebrow">How this works</span>
            <ol className="architect-process">
              <li><span>01</span><div><strong>Context first</strong><p>Five questions establish your reality, not a template.</p></div></li>
              <li><span>02</span><div><strong>Structured draft</strong><p>The model builds milestones and concrete tasks.</p></div></li>
              <li><span>03</span><div><strong>You decide</strong><p>Every date, title and task can be edited before import.</p></div></li>
            </ol>
          </div>

          <div className="architect-aside__card architect-privacy">
            <span className="eyebrow">Data boundary</span>
            <h3><LockKeyhole size={15} /> Private by default</h3>
            <p>
              Your existing workspace stays local. Only the interview answers go to the provider, and only when you press
              Generate. The server proxies the request without logging content or keys.
            </p>
            <Link to="/settings#ai" className="text-link">
              Provider settings <ArrowUpRight size={13} />
            </Link>
          </div>

          <div className="architect-aside__card architect-manual">
            <span className="eyebrow">No AI? No dead end.</span>
            <p>Create the goal now and build the structure yourself. Manual creation is always available and fully featured.</p>
            <Button variant="secondary" size="sm" fullWidth icon={<Target size={15} />} onClick={createManualDraft}>
              Create a manual draft
            </Button>
          </div>

          <div className="architect-provider">
            <span className="provider-dot" />
            <div>
              <span>{provider.label}</span>
              <strong>{settings.ai.model}</strong>
            </div>
            {settings.ai.hasKey ? (
              <Badge tone="success">Local key saved</Badge>
            ) : AI_PROVIDER_LOCK ? (
              <Badge tone="success">Managed by service</Badge>
            ) : (
              <Badge tone="neutral">No local key</Badge>
            )}
          </div>
        </aside>
      </div>

      {step < 5 ? (
        <button type="button" className="planner-skip" onClick={() => setStep(5)}>
          Skip ahead to the brief <ChevronRight size={14} />
        </button>
      ) : null}

      <ConfirmDialog
        open={Boolean(duplicateConfirm)}
        onClose={() => setDuplicateConfirm(null)}
        onConfirm={() => {
          if (!duplicateConfirm) return;
          if (duplicateConfirm.kind === 'plan') commitPlan(duplicateConfirm.plan);
          else commitManualDraft();
          setDuplicateConfirm(null);
        }}
        title="A goal with this title already exists"
        confirmLabel="Create anyway"
        description="An active goal with the same title already exists. Creating another can split progress between duplicate entries."
      />
    </div>
  );
}
