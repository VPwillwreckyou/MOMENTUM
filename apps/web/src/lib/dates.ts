const DAY_MS = 86_400_000;

/** A local calendar date in YYYY-MM-DD form. */
export function toDateInput(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Parse a date-only value in local time, avoiding UTC-midnight drift. */
export function parseDateOnly(value?: string | null): Date | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function addDays(date: Date, amount: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + amount);
  return copy;
}

export function addMonths(date: Date, amount: number): Date {
  const copy = new Date(date);
  const originalDay = copy.getDate();
  copy.setDate(1);
  copy.setMonth(copy.getMonth() + amount);
  const daysInMonth = new Date(copy.getFullYear(), copy.getMonth() + 1, 0).getDate();
  copy.setDate(Math.min(originalDay, daysInMonth));
  return copy;
}

export function diffCalendarDays(from: Date, to: Date): number {
  const a = new Date(from.getFullYear(), from.getMonth(), from.getDate()).getTime();
  const b = new Date(to.getFullYear(), to.getMonth(), to.getDate()).getTime();
  return Math.round((b - a) / DAY_MS);
}

export function startOfDay(date: Date = new Date()): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function startOfWeek(date: Date = new Date(), weekStartsOn: 0 | 1 = 1): Date {
  const copy = startOfDay(date);
  const difference = (copy.getDay() - weekStartsOn + 7) % 7;
  return addDays(copy, -difference);
}

export function eachDay(from: Date, count: number): Date[] {
  return Array.from({ length: count }, (_, index) => addDays(from, index));
}

export function isOverdue(dateStr?: string | null, now: Date = new Date()): boolean {
  const date = parseDateOnly(dateStr);
  if (!date) return false;
  return startOfDay(date).getTime() < startOfDay(now).getTime();
}

export function isToday(dateStr?: string | null, now: Date = new Date()): boolean {
  const date = parseDateOnly(dateStr);
  return !!date && toDateInput(date) === toDateInput(now);
}

export function isThisWeek(dateStr?: string | null, now: Date = new Date(), weekStartsOn: 0 | 1 = 1): boolean {
  const date = parseDateOnly(dateStr);
  if (!date) return false;
  const start = startOfWeek(now, weekStartsOn);
  const end = addDays(start, 7);
  return date >= start && date < end;
}

export function formatDate(dateStr?: string | null, style: 'short' | 'medium' | 'long' = 'medium'): string {
  const date = parseDateOnly(dateStr);
  if (!date) return 'No date';
  const options: Intl.DateTimeFormatOptions =
    style === 'short'
      ? { month: 'short', day: 'numeric' }
      : style === 'long'
        ? { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }
        : { month: 'short', day: 'numeric', year: 'numeric' };
  return new Intl.DateTimeFormat(undefined, options).format(date);
}

export function deadlineLabel(dateStr?: string | null, now: Date = new Date()): string {
  const date = parseDateOnly(dateStr);
  if (!date) return 'No deadline';
  const delta = diffCalendarDays(startOfDay(now), startOfDay(date));
  if (delta === 0) return 'Due today';
  if (delta === 1) return 'Due tomorrow';
  if (delta === -1) return '1 day overdue';
  if (delta < -1) return `${Math.abs(delta)} days overdue`;
  if (delta < 7) return `Due in ${delta} days`;
  return formatDate(dateStr, 'short');
}

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${Math.max(1, Math.round(seconds))}s`;
  return `${Math.round(seconds / 60)}m`;
}

export function relativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const seconds = Math.max(0, Math.round((now.getTime() - then.getTime()) / 1000));
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`;
  return formatDate(toDateInput(then), 'short');
}

export function deadlineSortValue(goalDeadline?: string | null): number {
  if (!goalDeadline) return Number.MAX_SAFE_INTEGER;
  const time = parseDateOnly(goalDeadline)?.getTime();
  return time ?? Number.MAX_SAFE_INTEGER;
}

export function isoNow(): string {
  return new Date().toISOString();
}

export function daysBetweenISO(fromISO: string, toISOValue: string): number {
  return diffCalendarDays(new Date(fromISO), new Date(toISOValue));
}
