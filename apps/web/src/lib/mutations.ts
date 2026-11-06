import type { Goal, GoalDraft, Milestone, Priority, Task } from '../state/types';
import { createId } from './id';
import { addDays, diffCalendarDays, parseDateOnly, toDateInput } from './dates';
import { nextOccurrenceDate } from './recurrence';

export interface NewTaskInput {
  title: string;
  notes?: string;
  dueDate?: string | null;
  priority?: Priority;
  milestoneId?: string | null;
  parentTaskId?: string | null;
}

export interface NewMilestoneInput {
  title: string;
  description?: string;
  dueDate?: string | null;
}

function normalizeDate(value: string | null | undefined): string | null {
  if (!value) return null;
  return parseDateOnly(value) ? value : null;
}

export function createGoal(draft: GoalDraft, options?: { now?: Date; aiGenerated?: boolean }): Goal {
  const now = options?.now ?? new Date();
  const timestamp = now.toISOString();
  return {
    id: createId(),
    title: draft.title.trim(),
    description: (draft.description ?? '').trim(),
    notes: (draft.notes ?? '').trim(),
    categoryId: draft.categoryId || null,
    status: 'active',
    kind: draft.kind === 'recurring' ? 'recurring' : 'one-time',
    recurrence: draft.kind === 'recurring' ? draft.recurrence ?? { frequency: 'weekly', interval: 1 } : null,
    priority: draft.priority ?? 'medium',
    startDate: normalizeDate(draft.startDate),
    deadline: normalizeDate(draft.deadline),
    createdAt: timestamp,
    updatedAt: timestamp,
    completedAt: null,
    archivedAt: null,
    deletedAt: null,
    seriesId: null,
    occurrence: 1,
    milestones: [],
    tasks: [],
    finance: draft.finance?.enabled ? draft.finance : null,
    aiGenerated: options?.aiGenerated ?? false,
  };
}

export function addTask(goal: Goal, input: NewTaskInput, now = new Date()): Goal {
  const timestamp = now.toISOString();
  const order = goal.tasks.length === 0 ? 0 : Math.max(...goal.tasks.map((task) => task.order)) + 1;
  const task: Task = {
    id: createId(),
    goalId: goal.id,
    milestoneId: input.milestoneId ?? null,
    parentTaskId: input.parentTaskId ?? null,
    title: input.title.trim(),
    notes: (input.notes ?? '').trim(),
    completed: false,
    completedAt: null,
    dueDate: normalizeDate(input.dueDate),
    priority: input.priority ?? 'medium',
    order,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  return { ...goal, tasks: [...goal.tasks, task], updatedAt: timestamp };
}

export function addMilestone(goal: Goal, input: NewMilestoneInput, now = new Date()): Goal {
  const timestamp = now.toISOString();
  const order = goal.milestones.length === 0 ? 0 : Math.max(...goal.milestones.map((m) => m.order)) + 1;
  const milestone: Milestone = {
    id: createId(),
    title: input.title.trim(),
    description: (input.description ?? '').trim(),
    dueDate: normalizeDate(input.dueDate),
    completed: false,
    completedAt: null,
    order,
  };
  return { ...goal, milestones: [...goal.milestones, milestone], updatedAt: timestamp };
}

export function updateTask(goal: Goal, taskId: string, patch: Partial<Task>, now = new Date()): Goal {
  const timestamp = now.toISOString();
  const tasks = goal.tasks.map((task) => {
    if (task.id !== taskId) return task;
    const completed = patch.completed ?? task.completed;
    return {
      ...task,
      ...patch,
      title: patch.title !== undefined ? patch.title.trim() : task.title,
      notes: patch.notes !== undefined ? patch.notes : task.notes,
      dueDate: patch.dueDate !== undefined ? normalizeDate(patch.dueDate) : task.dueDate,
      completed,
      completedAt: completed ? (patch.completedAt ?? task.completedAt ?? timestamp) : null,
      updatedAt: timestamp,
    };
  });
  return { ...goal, tasks, updatedAt: timestamp };
}

export function toggleTask(goal: Goal, taskId: string, now = new Date()): { goal: Goal; task: Task | null } {
  const task = goal.tasks.find((item) => item.id === taskId) ?? null;
  if (!task) return { goal, task: null };
  const next = updateTask(goal, taskId, { completed: !task.completed }, now);
  return { goal: next, task: next.tasks.find((item) => item.id === taskId) ?? null };
}

export function deleteTask(goal: Goal, taskId: string, now = new Date()): Goal {
  const nestedIds = new Set(goal.tasks.filter((task) => task.parentTaskId === taskId).map((task) => task.id));
  return {
    ...goal,
    tasks: goal.tasks.filter((task) => task.id !== taskId && !nestedIds.has(task.id)),
    updatedAt: now.toISOString(),
  };
}

export function updateMilestone(
  goal: Goal,
  milestoneId: string,
  patch: Partial<Milestone>,
  now = new Date(),
): Goal {
  const timestamp = now.toISOString();
  const milestones = goal.milestones.map((milestone) => {
    if (milestone.id !== milestoneId) return milestone;
    const completed = patch.completed ?? milestone.completed;
    return {
      ...milestone,
      ...patch,
      title: patch.title !== undefined ? patch.title.trim() : milestone.title,
      dueDate: patch.dueDate !== undefined ? normalizeDate(patch.dueDate) : milestone.dueDate,
      completed,
      completedAt: completed ? (patch.completedAt ?? milestone.completedAt ?? timestamp) : null,
    };
  });
  return { ...goal, milestones, updatedAt: timestamp };
}

export function deleteMilestone(goal: Goal, milestoneId: string, now = new Date()): Goal {
  return {
    ...goal,
    milestones: goal.milestones.filter((milestone) => milestone.id !== milestoneId),
    tasks: goal.tasks.map((task) =>
      task.milestoneId === milestoneId ? { ...task, milestoneId: null, updatedAt: now.toISOString() } : task,
    ),
    updatedAt: now.toISOString(),
  };
}

export function scheduleNextOccurrence(goal: Goal, now = new Date()): Goal | null {
  if (goal.kind !== 'recurring' || !goal.recurrence) return null;
  const baseDate = parseDateOnly(goal.deadline) ?? now;
  let nextDeadline = nextOccurrenceDate(baseDate, goal.recurrence);
  let nextDate = parseDateOnly(nextDeadline);
  if (!nextDate) return null;
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  let safety = 0;
  while (nextDate <= today && safety < 120) {
    nextDeadline = nextOccurrenceDate(nextDate, goal.recurrence);
    nextDate = parseDateOnly(nextDeadline);
    if (!nextDate) return null;
    safety += 1;
  }
  const shift = diffCalendarDays(baseDate, nextDate);
  const timestamp = now.toISOString();
  const createdAt = timestamp;

  const tasks = goal.tasks
    .filter((task) => !task.parentTaskId)
    .map((task, index) => {
      const due = parseDateOnly(task.dueDate);
      return {
        ...task,
        id: createId(),
        goalId: '',
        completed: false,
        completedAt: null,
        dueDate: due ? toDateInput(addDays(due, shift)) : null,
        createdAt,
        updatedAt: createdAt,
        order: index,
      } satisfies Task;
    });
  const taskIdMap = new Map<string, string>();
  goal.tasks
    .filter((task) => !task.parentTaskId)
    .forEach((task, index) => taskIdMap.set(task.id, tasks[index].id));
  const subtasks = goal.tasks
    .filter((task) => task.parentTaskId)
    .map((task) => ({
      ...task,
      id: createId(),
      goalId: '',
      parentTaskId: taskIdMap.get(task.parentTaskId ?? '') ?? null,
      completed: false,
      completedAt: null,
      dueDate: parseDateOnly(task.dueDate) ? toDateInput(addDays(parseDateOnly(task.dueDate)!, shift)) : null,
      createdAt,
      updatedAt: createdAt,
    }));

  const newGoalId = createId();
  const milestoneIdMap = new Map(goal.milestones.map((milestone) => [milestone.id, createId()]));
  const remapMilestone = (task: Task): Task => ({
    ...task,
    goalId: newGoalId,
    milestoneId: task.milestoneId ? milestoneIdMap.get(task.milestoneId) ?? null : null,
  });
  return {
    ...goal,
    id: newGoalId,
    status: 'active',
    completedAt: null,
    archivedAt: null,
    deletedAt: null,
    seriesId: goal.seriesId ?? goal.id,
    occurrence: (goal.occurrence ?? 1) + 1,
    createdAt,
    updatedAt: createdAt,
    deadline: nextDeadline,
    startDate: null,
    tasks: [...tasks, ...subtasks].map(remapMilestone),
    milestones: goal.milestones.map((milestone) => ({
      ...milestone,
      id: milestoneIdMap.get(milestone.id)!,
      completed: false,
      completedAt: null,
      dueDate: parseDateOnly(milestone.dueDate) ? toDateInput(addDays(parseDateOnly(milestone.dueDate)!, shift)) : null,
    })),
    finance: goal.finance ? { ...goal.finance, currentAmount: 0 } : null,
  };
}

export function duplicateGoalTitle(goals: Goal[], title: string, excludeId?: string): Goal | null {
  const normalized = title.trim().toLowerCase();
  if (!normalized) return null;
  return (
    goals.find(
      (goal) =>
        !goal.deletedAt &&
        !goal.archivedAt &&
        goal.id !== excludeId &&
        goal.status === 'active' &&
        goal.title.trim().toLowerCase() === normalized,
    ) ?? null
  );
}

export function applyPlanToGoal(
  goal: Goal,
  plan: { milestones: { title: string; description?: string; dueDate?: string | null; tasks: NewTaskInput[] }[]; tasks: NewTaskInput[] },
  now = new Date(),
): Goal {
  let next = goal;
  for (const item of plan.milestones) {
    next = addMilestone(next, { title: item.title, description: item.description, dueDate: item.dueDate }, now);
    const milestone = next.milestones[next.milestones.length - 1];
    for (const task of item.tasks) {
      next = addTask(next, { ...task, milestoneId: milestone.id }, now);
    }
  }
  for (const task of plan.tasks) {
    next = addTask(next, task, now);
  }
  return next;
}
