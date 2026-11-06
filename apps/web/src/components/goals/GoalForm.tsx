import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Coins, Repeat2, Sparkles } from 'lucide-react';
import type { CategoryIcon, Goal, GoalDraft, Priority } from '../../state/types';
import { useMomentum } from '../../state/MomentumContext';
import { Button } from '../ui/Button';
import { ConfirmDialog, Modal } from '../ui/Modal';
import { Input, SegmentedControl, Select, Textarea, Toggle } from '../ui/Fields';
import { validateGoalDraft } from '../../lib/validation';
import { duplicateGoalTitle } from '../../lib/mutations';
import { recurrenceLabel } from '../../lib/recurrence';

const CATEGORY_COLORS = ['#3b82f6', '#22d3ee', '#a3e635', '#f472b6', '#fb923c', '#8b5cf6', '#34d399', '#f87171'];
const PRIORITIES: Priority[] = ['low', 'medium', 'high', 'critical'];

interface GoalFormProps {
  open: boolean;
  onClose: () => void;
  goal?: Goal | null;
  onCreated?: (goal: Goal) => void;
}

export function GoalForm({ open, onClose, goal, onCreated }: GoalFormProps) {
  const { categories, goals, createGoal, updateGoal, addCategory, settings, toast } = useMomentum();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [notes, setNotes] = useState('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [priority, setPriority] = useState<Priority>('medium');
  const [deadline, setDeadline] = useState('');
  const [startDate, setStartDate] = useState('');
  const [kind, setKind] = useState<'one-time' | 'recurring'>('one-time');
  const [frequency, setFrequency] = useState<'daily' | 'weekly' | 'monthly'>('weekly');
  const [interval, setInterval] = useState(1);
  const [financeEnabled, setFinanceEnabled] = useState(false);
  const [currency, setCurrency] = useState('USD');
  const [targetAmount, setTargetAmount] = useState('');
  const [currentAmount, setCurrentAmount] = useState('');
  const [newCategory, setNewCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [newCategoryColor, setNewCategoryColor] = useState(CATEGORY_COLORS[0]);
  const [newCategoryIcon, setNewCategoryIcon] = useState<CategoryIcon>('target');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [duplicateOpen, setDuplicateOpen] = useState(false);

  const isEditing = Boolean(goal);

  useEffect(() => {
    if (!open) return;
    setTitle(goal?.title ?? '');
    setDescription(goal?.description ?? '');
    setNotes(goal?.notes ?? '');
    setCategoryId(goal?.categoryId ?? '');
    setPriority(goal?.priority ?? 'medium');
    setDeadline(goal?.deadline ?? '');
    setStartDate(goal?.startDate ?? '');
    setKind(goal?.kind ?? 'one-time');
    setFrequency(goal?.recurrence?.frequency ?? 'weekly');
    setInterval(goal?.recurrence?.interval ?? 1);
    setFinanceEnabled(Boolean(goal?.finance?.enabled));
    setCurrency(goal?.finance?.currency ?? 'USD');
    setTargetAmount(goal?.finance?.targetAmount ? String(goal.finance.targetAmount) : '');
    setCurrentAmount(goal?.finance?.currentAmount ? String(goal.finance.currentAmount) : '');
    setNewCategory(false);
    setNewCategoryName('');
    setNewCategoryColor(CATEGORY_COLORS[0]);
    setErrors({});
    setDuplicateOpen(false);
  }, [open, goal]);

  const draft = useMemo<GoalDraft>(
    () => ({
      title,
      description,
      notes,
      categoryId: categoryId || null,
      priority,
      startDate: startDate || null,
      deadline: deadline || null,
      kind,
      recurrence: kind === 'recurring' ? { frequency, interval: Number(interval) || 1 } : null,
      finance: financeEnabled
        ? {
            enabled: true,
            currency: currency.trim().toUpperCase().slice(0, 5) || 'USD',
            targetAmount: Number(targetAmount) || 0,
            currentAmount: Number(currentAmount) || 0,
            note: '',
          }
        : null,
    }),
    [
      title,
      description,
      notes,
      categoryId,
      priority,
      startDate,
      deadline,
      kind,
      frequency,
      interval,
      financeEnabled,
      currency,
      targetAmount,
      currentAmount,
    ],
  );

  const duplicate = useMemo(
    () => duplicateGoalTitle(goals, title, goal?.id),
    [goals, title, goal?.id],
  );

  const handleCategoryCreate = () => {
    const name = newCategoryName.trim();
    if (!name) {
      setErrors((current) => ({ ...current, newCategory: 'Give the category a name.' }));
      return;
    }
    const created = addCategory(name, newCategoryColor, newCategoryIcon);
    if (created) {
      setCategoryId(created.id);
      setNewCategory(false);
      setNewCategoryName('');
      setErrors((current) => {
        const next = { ...current };
        delete next.newCategory;
        return next;
      });
    }
  };

  const validate = (): boolean => {
    const nextErrors = validateGoalDraft(draft);
    if (newCategory && !newCategoryName.trim()) nextErrors.newCategory = 'Name the new category or cancel it.';
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const submit = () => {
    if (!validate()) return;
    if (duplicate) {
      setDuplicateOpen(true);
      return;
    }
    persist();
  };

  const persist = () => {
    if (goal) {
      updateGoal(
        goal.id,
        {
          title: title.trim(),
          description: description.trim(),
          notes: notes.trim(),
          categoryId: categoryId || null,
          priority,
          startDate: startDate || null,
          deadline: deadline || null,
          kind,
          recurrence: kind === 'recurring' ? { frequency, interval: Math.max(1, Number(interval) || 1) } : null,
          finance: draft.finance,
        },
        'Updated goal details',
      );
      toast('Goal updated.', 'success');
      onClose();
      return;
    }
    const created = createGoal(draft);
    toast('Goal created. The first move is waiting.', 'success');
    onClose();
    onCreated?.(created);
  };

  if (!open) return null;

  const footer = (
    <>
      <Button variant="ghost" onClick={onClose}>
        Cancel
      </Button>
      <Button variant="primary" onClick={submit}>
        {isEditing ? 'Save changes' : 'Create goal'}
      </Button>
    </>
  );

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title={isEditing ? 'Edit goal' : 'Create a goal'}
        description={
          isEditing
            ? 'Refine the target, timing and context. Your plan stays intact.'
            : 'Start with the outcome. Structure can come next.'
        }
        size="lg"
        footer={footer}
      >
        <div className="form-stack">
          <Input
            label="Goal title"
            placeholder="e.g. Launch my portfolio by March"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            error={errors.title}
            maxLength={120}
            autoFocus
            data-testid="goal-title"
          />
          <Textarea
            label="Why this matters / description"
            placeholder="What does success actually look like? Add the context future-you will need."
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            error={errors.description}
            rows={3}
            maxLength={4000}
          />

          <div className="form-grid form-grid--2">
            <Select
              label="Category"
              value={newCategory ? '__new' : categoryId}
              onChange={(event) => {
                if (event.target.value === '__new') setNewCategory(true);
                else {
                  setNewCategory(false);
                  setCategoryId(event.target.value);
                }
              }}
            >
              <option value="">No category</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
              <option value="__new">+ New category</option>
            </Select>
            <Select
              label="Priority"
              value={priority}
              onChange={(event) => setPriority(event.target.value as Priority)}
              hint="Priority is a signal, not a promise."
            >
              {PRIORITIES.map((value) => (
                <option key={value} value={value}>
                  {value.charAt(0).toUpperCase() + value.slice(1)}
                </option>
              ))}
            </Select>
          </div>

          {newCategory ? (
            <div className="inline-create">
              <div className="inline-create__header">
                <span className="eyebrow">New category</span>
                <button type="button" className="link-button" onClick={() => setNewCategory(false)}>
                  Cancel
                </button>
              </div>
              <div className="inline-create__row">
                <Input
                  placeholder="Category name"
                  value={newCategoryName}
                  onChange={(event) => setNewCategoryName(event.target.value)}
                  error={errors.newCategory}
                  maxLength={60}
                />
                <Button variant="secondary" onClick={handleCategoryCreate}>
                  Add
                </Button>
              </div>
              <div className="color-row">
                {CATEGORY_COLORS.map((color) => (
                  <button
                    type="button"
                    key={color}
                    className={`color-swatch${newCategoryColor === color ? ' color-swatch--selected' : ''}`}
                    style={{ background: color }}
                    onClick={() => setNewCategoryColor(color)}
                    aria-label={`Use ${color}`}
                  />
                ))}
                <Select
                  aria-label="Category icon"
                  value={newCategoryIcon}
                  onChange={(event) => setNewCategoryIcon(event.target.value as CategoryIcon)}
                  className="inline-icon-select"
                >
                  <option value="target">Target</option>
                  <option value="work">Work</option>
                  <option value="health">Health</option>
                  <option value="learning">Learning</option>
                  <option value="creative">Creative</option>
                  <option value="finance">Finance</option>
                  <option value="home">Home</option>
                  <option value="relationships">Relationships</option>
                </Select>
              </div>
            </div>
          ) : null}

          <div className="form-grid form-grid--2">
            <Input
              label="Start date"
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
              error={errors.startDate}
              optional
            />
            <Input
              label="Deadline"
              type="date"
              value={deadline}
              onChange={(event) => setDeadline(event.target.value)}
              error={errors.deadline}
              optional
              hint="No deadline is a valid choice."
            />
          </div>

          <div className="form-section">
            <div className="form-section__heading">
              <Repeat2 size={16} />
              <div>
                <strong>Rhythm</strong>
                <p>One-time push or a recurring practice?</p>
              </div>
            </div>
            <SegmentedControl
              value={kind}
              onChange={(value) => setKind(value)}
              ariaLabel="Goal type"
              options={[
                { value: 'one-time', label: 'One-time goal' },
                { value: 'recurring', label: 'Recurring goal' },
              ]}
            />
            {kind === 'recurring' ? (
              <div className="form-grid form-grid--2 form-grid--align-end">
                <Select label="Frequency" value={frequency} onChange={(event) => setFrequency(event.target.value as typeof frequency)}>
                  <option value="daily">Daily</option>
                  <option value="weekly">Weekly</option>
                  <option value="monthly">Monthly</option>
                </Select>
                <Input
                  label="Every"
                  type="number"
                  min={1}
                  max={52}
                  value={interval}
                  onChange={(event) => setInterval(Number(event.target.value))}
                  error={errors.recurrence}
                  hint={recurrenceLabel({ frequency, interval: Number(interval) || 1 })}
                />
              </div>
            ) : null}
          </div>

          <div className="form-section">
            <Toggle
              checked={financeEnabled}
              onChange={setFinanceEnabled}
              label={
                <span className="inline-label">
                  <Coins size={15} /> Track money for this goal
                </span>
              }
              description="Only shown inside this goal. MOMENTUM will never turn every goal into a budget."
            />
            {financeEnabled ? (
              <div className="form-grid form-grid--3">
                <Input
                  label="Currency"
                  value={currency}
                  onChange={(event) => setCurrency(event.target.value)}
                  maxLength={5}
                />
                <Input
                  label="Target amount"
                  type="number"
                  min={0}
                  step="0.01"
                  value={targetAmount}
                  onChange={(event) => setTargetAmount(event.target.value)}
                  error={errors.financeTarget}
                />
                <Input
                  label="Saved so far"
                  type="number"
                  min={0}
                  step="0.01"
                  value={currentAmount}
                  onChange={(event) => setCurrentAmount(event.target.value)}
                  error={errors.financeCurrent}
                />
              </div>
            ) : null}
          </div>

          <Textarea
            label="Notes"
            placeholder="Context, resources, links, constraints — anything worth keeping close."
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            error={errors.notes}
            rows={3}
            maxLength={10000}
            optional
          />

          <div className="form-hint-strip">
            <CalendarDays size={15} />
            <span>{settings.weekStartsOn === 1 ? 'Weeks start on Monday' : 'Weeks start on Sunday'} · everything saves locally the moment you create it.</span>
          </div>
          {duplicate ? (
            <div className="inline-warning">
              <span>An active goal named “{duplicate.title}” already exists. Consider adding tasks to that goal instead.</span>
            </div>
          ) : null}
          {!isEditing ? (
            <div className="form-hint-strip form-hint-strip--accent">
              <Sparkles size={15} />
              <span>Want a structured plan? Use the Goal Architect after creating it.</span>
            </div>
          ) : null}
        </div>
      </Modal>
      <ConfirmDialog
        open={duplicateOpen}
        onClose={() => setDuplicateOpen(false)}
        onConfirm={() => {
          setDuplicateOpen(false);
          persist();
        }}
        title="This goal already exists"
        confirmLabel="Create anyway"
        description={`“${duplicate?.title}” is already active. Creating a second goal with the same title can split your progress and create confusing duplicates.`}
      />
    </>
  );
}
