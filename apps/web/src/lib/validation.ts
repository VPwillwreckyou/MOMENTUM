import type { AIPlan, GoalDraft, Priority } from '../state/types';
import { parseDateOnly, toDateInput } from './dates';

export interface ValidationErrors {
  [field: string]: string;
}

export function validateGoalDraft(draft: GoalDraft): ValidationErrors {
  const errors: ValidationErrors = {};
  const title = draft.title.trim();
  if (!title) errors.title = 'Give your goal a clear title.';
  if (title.length > 120) errors.title = 'Keep the title under 120 characters.';
  if ((draft.description ?? '').length > 4000) errors.description = 'Description must be under 4,000 characters.';
  if ((draft.notes ?? '').length > 10000) errors.notes = 'Notes must be under 10,000 characters.';
  if (draft.startDate && !parseDateOnly(draft.startDate)) errors.startDate = 'Start date is invalid.';
  if (draft.deadline && !parseDateOnly(draft.deadline)) errors.deadline = 'Deadline is invalid.';
  if (draft.startDate && draft.deadline && parseDateOnly(draft.startDate)! > parseDateOnly(draft.deadline)!) {
    errors.deadline = 'Deadline cannot be before the start date.';
  }
  if (draft.kind === 'recurring') {
    if (!draft.recurrence) errors.recurrence = 'Choose how often this goal repeats.';
    else if (draft.recurrence.interval < 1 || draft.recurrence.interval > 52) {
      errors.recurrence = 'Interval must be between 1 and 52.';
    }
  }
  if (draft.finance?.enabled) {
    if (!Number.isFinite(draft.finance.targetAmount) || draft.finance.targetAmount <= 0) {
      errors.financeTarget = 'Target amount must be greater than zero.';
    }
    if (!Number.isFinite(draft.finance.currentAmount) || draft.finance.currentAmount < 0) {
      errors.financeCurrent = 'Current amount cannot be negative.';
    }
  }
  return errors;
}

export function validateTaskTitle(title: string): string | null {
  const trimmed = title.trim();
  if (!trimmed) return 'Task needs a title.';
  if (trimmed.length > 240) return 'Task title must be under 240 characters.';
  return null;
}

export function validateEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim());
}

export function validatePassword(password: string): string | null {
  if (password.length < 8) return 'Use at least 8 characters.';
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) {
    return 'Include at least one letter and one number.';
  }
  if (password.length > 200) return 'Password is too long.';
  return null;
}

const PRIORITIES: Priority[] = ['low', 'medium', 'high', 'critical'];

export function validateHex(value: string): string | null {
  const match = /^#?([0-9a-fA-F]{6})$/.exec(value.trim());
  if (!match) return null;
  return `#${match[1].toLowerCase()}`;
}

function safeDate(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  return parseDateOnly(value) ? toDateInput(parseDateOnly(value)!) : null;
}

function cleanText(value: unknown, max = 600): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

/** Validate untrusted AI output before it touches local data. */
export function normalizeAIPlan(raw: unknown): AIPlan {
  if (!raw || typeof raw !== 'object') throw new Error('The provider returned an invalid plan.');
  const candidate = raw as Record<string, unknown>;
  const title = cleanText(candidate.title, 120);
  if (!title) throw new Error('The provider returned a plan without a title.');

  const validMilestones = Array.isArray(candidate.milestones) ? candidate.milestones : [];
  const milestones = validMilestones.slice(0, 12).map((item) => {
    const milestone = (item ?? {}) as Record<string, unknown>;
    const tasks = Array.isArray(milestone.tasks) ? milestone.tasks : [];
    return {
      title: cleanText(milestone.title, 160) || 'Milestone',
      description: cleanText(milestone.description, 800),
      dueDate: safeDate(milestone.dueDate),
      tasks: tasks.slice(0, 15).map((task) => {
        const record = (task ?? {}) as Record<string, unknown>;
        const priority = typeof record.priority === 'string' && PRIORITIES.includes(record.priority as Priority)
          ? (record.priority as Priority)
          : 'medium';
        return {
          title: cleanText(record.title, 240) || 'Next action',
          notes: cleanText(record.notes, 1000),
          dueDate: safeDate(record.dueDate),
          priority,
        };
      }),
    };
  });

  const firstTasks = Array.isArray(candidate.firstTasks)
    ? candidate.firstTasks.slice(0, 12).map((task) => {
        const record = (task ?? {}) as Record<string, unknown>;
        const priority = typeof record.priority === 'string' && PRIORITIES.includes(record.priority as Priority)
          ? (record.priority as Priority)
          : 'medium';
        return {
          title: cleanText(record.title, 240) || 'Next action',
          notes: cleanText(record.notes, 1000),
          dueDate: safeDate(record.dueDate),
          priority,
        };
      })
    : [];

  if (milestones.length === 0 && firstTasks.length === 0) {
    throw new Error('The provider returned a plan without milestones or tasks.');
  }

  return {
    title,
    description: cleanText(candidate.description, 2000),
    suggestedDeadline: safeDate(candidate.suggestedDeadline),
    milestones,
    firstTasks,
  };
}

export function parseAIJson(text: string): unknown {
  const cleaned = text.trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '');
  try {
    return JSON.parse(cleaned);
  } catch {
    const start = cleaned.indexOf('{');
    const end = cleaned.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return JSON.parse(cleaned.slice(start, end + 1));
    }
    throw new Error('The provider did not return valid JSON. Try generating again.');
  }
}
