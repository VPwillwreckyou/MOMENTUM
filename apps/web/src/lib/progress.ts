import type { Goal, Task } from '../state/types';
import { isOverdue, parseDateOnly, startOfDay, isThisWeek } from './dates';

export interface GoalProgress {
  percent: number;
  completedTasks: number;
  totalTasks: number;
  completedMilestones: number;
  totalMilestones: number;
  taskPercent: number;
  milestonePercent: number;
}

function ratio(done: number, total: number): number {
  return total === 0 ? 0 : done / total;
}

export function computeGoalProgress(goal: Goal): GoalProgress {
  const totalTasks = goal.tasks.length;
  const completedTasks = goal.tasks.filter((task) => task.completed).length;
  const totalMilestones = goal.milestones.length;
  const completedMilestones = goal.milestones.filter((milestone) => milestone.completed).length;
  const taskPercent = Math.round(ratio(completedTasks, totalTasks) * 100);
  const milestonePercent = Math.round(ratio(completedMilestones, totalMilestones) * 100);

  let percent: number;
  if (goal.status === 'completed') {
    percent = 100;
  } else if (totalTasks > 0 && totalMilestones > 0) {
    percent = Math.round(taskPercent * 0.65 + milestonePercent * 0.35);
  } else if (totalTasks > 0) {
    percent = taskPercent;
  } else if (totalMilestones > 0) {
    percent = milestonePercent;
  } else {
    percent = 0;
  }

  return {
    percent,
    completedTasks,
    totalTasks,
    completedMilestones,
    totalMilestones,
    taskPercent,
    milestonePercent,
  };
}

export function rootTasks(goal: Goal): Task[] {
  return goal.tasks
    .filter((task) => !task.parentTaskId)
    .sort((a, b) => a.order - b.order || a.createdAt.localeCompare(b.createdAt));
}

export function subtasksFor(goal: Goal, taskId: string): Task[] {
  return goal.tasks
    .filter((task) => task.parentTaskId === taskId)
    .sort((a, b) => a.order - b.order || a.createdAt.localeCompare(b.createdAt));
}

export function nextActionableTask(goal: Goal): Task | null {
  const open = goal.tasks
    .filter((task) => !task.completed)
    .sort((a, b) => {
      const aDate = parseDateOnly(a.dueDate)?.getTime() ?? Number.MAX_SAFE_INTEGER;
      const bDate = parseDateOnly(b.dueDate)?.getTime() ?? Number.MAX_SAFE_INTEGER;
      return aDate - bDate || a.order - b.order;
    });
  return open[0] ?? null;
}

export function tasksDueThisWeek(goals: Goal[], weekStartsOn: 0 | 1 = 1, now: Date = new Date()): Task[] {
  return goals
    .flatMap((goal) => goal.tasks.map((task) => ({ ...task, goalId: goal.id })))
    .filter((task) => !task.completed && isThisWeek(task.dueDate, now, weekStartsOn))
    .sort((a, b) => {
      const aDate = parseDateOnly(a.dueDate)?.getTime() ?? Number.MAX_SAFE_INTEGER;
      const bDate = parseDateOnly(b.dueDate)?.getTime() ?? Number.MAX_SAFE_INTEGER;
      return aDate - bDate;
    });
}

export function overdueTasks(goal: Goal): Task[] {
  return goal.tasks.filter((task) => !task.completed && isOverdue(task.dueDate));
}

export function momentumScore(goals: Goal[]): number {
  const active = goals.filter((goal) => goal.status === 'active' && !goal.archivedAt && !goal.deletedAt);
  if (active.length === 0) return 0;
  const now = startOfDay();
  const sevenDaysAgo = new Date(now);
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  let completedRecently = 0;
  let available = 0;
  for (const goal of active) {
    const progress = computeGoalProgress(goal);
    completedRecently += goal.tasks.filter((task) => {
      if (!task.completed) return false;
      const date = task.completedAt ? new Date(task.completedAt) : null;
      return !!date && date >= sevenDaysAgo;
    }).length;
    available += progress.totalTasks + progress.totalMilestones;
  }
  const progressAvg = active.reduce((sum, goal) => sum + computeGoalProgress(goal).percent, 0) / active.length;
  const velocity = available === 0 ? 0 : Math.min(100, Math.round((completedRecently / Math.max(6, available * 0.25)) * 100));
  return Math.round(progressAvg * 0.72 + velocity * 0.28);
}

export function weeklyCompletionSeries(goals: Goal[], days = 7): { label: string; value: number }[] {
  const now = startOfDay();
  return Array.from({ length: days }, (_, index) => {
    const day = new Date(now);
    day.setDate(day.getDate() - (days - 1 - index));
    const key = day.toDateString();
    const value = goals.reduce(
      (sum, goal) =>
        sum +
        goal.tasks.filter((task) => task.completedAt && new Date(task.completedAt).toDateString() === key).length,
      0,
    );
    return { label: new Intl.DateTimeFormat(undefined, { weekday: 'narrow' }).format(day), value };
  });
}

export function progressTone(percent: number): 'danger' | 'warning' | 'accent' | 'success' {
  if (percent >= 100) return 'success';
  if (percent >= 60) return 'accent';
  if (percent >= 25) return 'warning';
  return 'danger';
}
