import type { Goal, Task } from '../state/types';

export function makeTask(overrides: Partial<Task> = {}): Task {
  const now = new Date().toISOString();
  return {
    id: crypto.randomUUID(),
    goalId: 'goal-1',
    milestoneId: null,
    parentTaskId: null,
    title: 'A task',
    notes: '',
    completed: false,
    completedAt: null,
    dueDate: null,
    priority: 'medium',
    order: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

export function makeGoal(overrides: Partial<Goal> = {}): Goal {
  const now = new Date().toISOString();
  return {
    id: 'goal-1',
    title: 'A goal',
    description: '',
    notes: '',
    categoryId: null,
    status: 'active',
    kind: 'one-time',
    recurrence: null,
    priority: 'medium',
    startDate: null,
    deadline: null,
    createdAt: now,
    updatedAt: now,
    completedAt: null,
    archivedAt: null,
    deletedAt: null,
    seriesId: null,
    occurrence: 1,
    milestones: [],
    tasks: [],
    finance: null,
    ...overrides,
  };
}
