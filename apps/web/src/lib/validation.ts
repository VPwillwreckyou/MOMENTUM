import type { GoalDraft } from '../state/types';
import { parseDateOnly } from './dates';

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

export function validateHex(value: string): string | null {
  const match = /^#?([0-9a-fA-F]{6})$/.exec(value.trim());
  if (!match) return null;
  return `#${match[1].toLowerCase()}`;
}
