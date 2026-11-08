import type { Goal, Task } from '../state/types';

const NOTIFICATION_PERMISSION_KEY = 'momentum.notifications-enabled';
const LAST_CHECK_KEY = 'momentum.notifications-last-check';

export function notificationsSupported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window;
}

export function notificationsEnabled(): boolean {
  if (!notificationsSupported()) return false;
  try {
    return localStorage.getItem(NOTIFICATION_PERMISSION_KEY) === 'true' && Notification.permission === 'granted';
  } catch {
    return false;
  }
}

export async function requestNotificationPermission(): Promise<boolean> {
  if (!notificationsSupported()) return false;
  try {
    const result = await Notification.requestPermission();
    const granted = result === 'granted';
    localStorage.setItem(NOTIFICATION_PERMISSION_KEY, granted ? 'true' : 'false');
    return granted;
  } catch {
    return false;
  }
}

export function disableNotifications(): void {
  try {
    localStorage.setItem(NOTIFICATION_PERMISSION_KEY, 'false');
  } catch {
    // ignore
  }
}

function isDueWithin24h(dateStr: string | null | undefined): boolean {
  if (!dateStr) return false;
  const due = new Date(dateStr);
  if (isNaN(due.getTime())) return false;
  const now = new Date();
  const diffMs = due.getTime() - now.getTime();
  return diffMs >= 0 && diffMs <= 24 * 60 * 60 * 1000;
}

function isOverdue(dateStr: string | null | undefined): boolean {
  if (!dateStr) return false;
  const due = new Date(dateStr);
  if (isNaN(due.getTime())) return false;
  return due.getTime() < Date.now();
}

export function checkDeadlineNotifications(goals: Goal[], tasks: Task[]): void {
  if (!notificationsEnabled()) return;

  // Throttle: only check once per hour
  try {
    const lastCheck = localStorage.getItem(LAST_CHECK_KEY);
    if (lastCheck) {
      const last = parseInt(lastCheck, 10);
      if (Date.now() - last < 60 * 60 * 1000) return;
    }
    localStorage.setItem(LAST_CHECK_KEY, String(Date.now()));
  } catch {
    // ignore
  }

  const overdueGoals = goals.filter((g) => !g.archivedAt && g.status !== 'completed' && isOverdue(g.deadline));
  const dueSoonGoals = goals.filter((g) => !g.archivedAt && g.status !== 'completed' && isDueWithin24h(g.deadline));
  const overdueTasks = tasks.filter((t) => !t.completed && isOverdue(t.dueDate));
  const dueSoonTasks = tasks.filter((t) => !t.completed && isDueWithin24h(t.dueDate));

  if (overdueGoals.length > 0) {
    new Notification('MOMENTUM — Overdue goals', {
      body: `${overdueGoals.length} goal(s) are past their deadline: ${overdueGoals.map((g) => g.title).slice(0, 3).join(', ')}`,
      tag: 'overdue-goals',
    });
  }

  if (dueSoonGoals.length > 0) {
    new Notification('MOMENTUM — Goals due soon', {
      body: `${dueSoonGoals.length} goal(s) due within 24h: ${dueSoonGoals.map((g) => g.title).slice(0, 3).join(', ')}`,
      tag: 'due-soon-goals',
    });
  }

  if (overdueTasks.length > 0) {
    new Notification('MOMENTUM — Overdue tasks', {
      body: `${overdueTasks.length} task(s) are overdue: ${overdueTasks.map((t) => t.title).slice(0, 3).join(', ')}`,
      tag: 'overdue-tasks',
    });
  }

  if (dueSoonTasks.length > 0) {
    new Notification('MOMENTUM — Tasks due soon', {
      body: `${dueSoonTasks.length} task(s) due within 24h: ${dueSoonTasks.map((t) => t.title).slice(0, 3).join(', ')}`,
      tag: 'due-soon-tasks',
    });
  }
}
