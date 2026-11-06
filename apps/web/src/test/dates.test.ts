import { describe, expect, it } from 'vitest';
import {
  addMonths,
  deadlineLabel,
  diffCalendarDays,
  formatDate,
  isOverdue,
  isThisWeek,
  parseDateOnly,
  startOfWeek,
  toDateInput,
} from '../lib/dates';

describe('date utilities', () => {
  it('formats a local date without UTC drift', () => {
    expect(toDateInput(new Date(2026, 10, 4, 23, 45))).toBe('2026-11-04');
    const parsed = parseDateOnly('2026-11-04');
    expect(parsed?.getFullYear()).toBe(2026);
    expect(parsed?.getMonth()).toBe(10);
    expect(parsed?.getDate()).toBe(4);
  });

  it('clamps month additions to the last valid day', () => {
    expect(toDateInput(addMonths(new Date(2026, 0, 31), 1))).toBe('2026-02-28');
    expect(toDateInput(addMonths(new Date(2024, 0, 31), 1))).toBe('2024-02-29');
  });

  it('starts weeks according to preference', () => {
    const wednesday = new Date(2026, 10, 4);
    expect(toDateInput(startOfWeek(wednesday, 1))).toBe('2026-11-02');
    expect(toDateInput(startOfWeek(wednesday, 0))).toBe('2026-11-01');
  });

  it('describes deadlines in human terms', () => {
    const now = new Date(2026, 10, 4);
    expect(deadlineLabel('2026-11-04', now)).toBe('Due today');
    expect(deadlineLabel('2026-11-05', now)).toBe('Due tomorrow');
    expect(deadlineLabel('2026-11-01', now)).toBe('3 days overdue');
    expect(deadlineLabel(null, now)).toBe('No deadline');
  });

  it('detects overdue dates and current week boundaries', () => {
    const now = new Date(2026, 10, 4);
    expect(isOverdue('2026-11-03', now)).toBe(true);
    expect(isOverdue('2026-11-04', now)).toBe(false);
    expect(isThisWeek('2026-11-06', now, 1)).toBe(true);
    expect(isThisWeek('2026-11-09', now, 1)).toBe(false);
  });

  it('counts calendar days across month boundaries', () => {
    expect(diffCalendarDays(new Date(2026, 10, 28), new Date(2026, 11, 2))).toBe(4);
    expect(formatDate('2026-11-04', 'short')).toContain('Nov');
  });
});
