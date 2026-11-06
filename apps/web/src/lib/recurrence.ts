import { addDays, addMonths, parseDateOnly, toDateInput } from './dates';
import type { Recurrence } from '../state/types';

export function nextOccurrenceDate(from: Date, recurrence: Recurrence): string {
  const interval = Math.max(1, Math.min(52, recurrence.interval || 1));
  switch (recurrence.frequency) {
    case 'daily':
      return toDateInput(addDays(from, interval));
    case 'weekly':
      return toDateInput(addDays(from, interval * 7));
    case 'monthly':
      return toDateInput(addMonths(from, interval));
  }
}

export function shiftDate(dateStr: string | null | undefined, days: number): string | null {
  if (!dateStr) return dateStr ?? null;
  const date = parseDateOnly(dateStr);
  if (!date) return dateStr;
  return toDateInput(addDays(date, days));
}

export function recurrenceLabel(recurrence?: Recurrence | null): string {
  if (!recurrence) return 'One-time';
  const every = recurrence.interval > 1 ? `every ${recurrence.interval} ` : 'every ';
  const unit = recurrence.frequency === 'daily' ? 'day' : recurrence.frequency === 'weekly' ? 'week' : 'month';
  return `Repeats ${every}${unit}`;
}
