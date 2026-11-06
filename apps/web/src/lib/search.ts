import type { AttachmentMeta, Category, Goal } from '../state/types';
import { computeGoalProgress } from './progress';

export interface SearchHit {
  kind: 'goal' | 'task' | 'subtask' | 'milestone' | 'category' | 'note' | 'attachment';
  id: string;
  goalId?: string;
  title: string;
  excerpt: string;
  score: number;
}

function scoreText(text: string, query: string): number {
  const normalized = text.toLowerCase();
  if (!normalized.includes(query)) return 0;
  if (normalized === query) return 120;
  if (normalized.startsWith(query)) return 90;
  const boundary = normalized.split(/\s+/).some((word) => word.startsWith(query));
  return boundary ? 70 : 30;
}

function excerpt(text: string, query: string, length = 110): string {
  const lower = text.toLowerCase();
  const index = lower.indexOf(query);
  if (index < 0) return text.slice(0, length);
  const start = Math.max(0, index - 40);
  return `${start > 0 ? '…' : ''}${text.slice(start, start + length).trim()}${text.length > start + length ? '…' : ''}`;
}

export function searchWorkspace(
  query: string,
  goals: Goal[],
  categories: Category[],
  attachments: AttachmentMeta[],
): SearchHit[] {
  const q = query.trim().toLowerCase();
  if (q.length < 2) return [];
  const liveGoals = goals.filter((goal) => !goal.deletedAt);
  const hits: SearchHit[] = [];

  for (const goal of liveGoals) {
    const goalScore = scoreText(goal.title, q);
    if (goalScore) {
      hits.push({
        kind: 'goal',
        id: goal.id,
        goalId: goal.id,
        title: goal.title,
        excerpt: goal.description || `${computeGoalProgress(goal).percent}% complete`,
        score: goalScore + 10,
      });
    }
    for (const task of goal.tasks) {
      const score = scoreText(task.title, q);
      if (score) {
        hits.push({
          kind: task.parentTaskId ? 'subtask' : 'task',
          id: task.id,
          goalId: goal.id,
          title: task.title,
          excerpt: `${goal.title}${task.notes ? ` · ${excerpt(task.notes, q, 70)}` : ''}`,
          score: score + (task.completed ? -12 : 2),
        });
      } else if (task.notes && scoreText(task.notes, q)) {
        hits.push({
          kind: 'note',
          id: task.id,
          goalId: goal.id,
          title: task.title,
          excerpt: excerpt(task.notes, q),
          score: 40,
        });
      }
    }
    for (const milestone of goal.milestones) {
      const score = scoreText(milestone.title, q);
      if (score) {
        hits.push({
          kind: 'milestone',
          id: milestone.id,
          goalId: goal.id,
          title: milestone.title,
          excerpt: `${goal.title} · Milestone`,
          score: score + 1,
        });
      }
    }
    const notesScore = scoreText(goal.notes, q);
    if (notesScore) {
      hits.push({
        kind: 'note',
        id: goal.id,
        goalId: goal.id,
        title: goal.title,
        excerpt: excerpt(goal.notes, q),
        score: notesScore + 5,
      });
    }
  }

  for (const category of categories.filter((category) => !category.deletedAt)) {
    const score = scoreText(category.name, q);
    if (score) {
      hits.push({ kind: 'category', id: category.id, title: category.name, excerpt: 'Category', score });
    }
  }

  for (const attachment of attachments) {
    const score = scoreText(attachment.name, q);
    if (score) {
      const goal = liveGoals.find((item) => item.id === attachment.goalId);
      if (goal) {
        hits.push({
          kind: 'attachment',
          id: attachment.id,
          goalId: goal.id,
          title: attachment.name,
          excerpt: `Attachment in ${goal.title}`,
          score: score - 30,
        });
      }
    }
  }

  return hits.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, 24);
}

export type GoalStatusFilter = 'active' | 'completed' | 'all';
export type GoalSort = 'recent' | 'deadline' | 'priority' | 'progress' | 'title' | 'created';
export type DeadlineFilter = 'any' | 'week' | 'month' | 'overdue' | 'none';

export interface GoalFilter {
  query: string;
  status: GoalStatusFilter;
  priority: string;
  categoryId: string;
  deadline: DeadlineFilter;
  sort: GoalSort;
}

export const defaultGoalFilter: GoalFilter = {
  query: '',
  status: 'active',
  priority: 'all',
  categoryId: 'all',
  deadline: 'any',
  sort: 'recent',
};

const priorityWeight = { low: 1, medium: 2, high: 3, critical: 4 };

export function filterGoals(goals: Goal[], filter: GoalFilter, now = new Date()): Goal[] {
  const q = filter.query.trim().toLowerCase();
  const weekEnd = new Date(now);
  weekEnd.setDate(weekEnd.getDate() + 7);
  const monthEnd = new Date(now);
  monthEnd.setDate(monthEnd.getDate() + 30);

  const filtered = goals.filter((goal) => {
    if (goal.deletedAt || goal.archivedAt) return false;
    if (filter.status !== 'all' && goal.status !== filter.status) return false;
    if (filter.priority !== 'all' && goal.priority !== filter.priority) return false;
    if (filter.categoryId !== 'all' && goal.categoryId !== filter.categoryId) return false;
    if (filter.deadline !== 'any') {
      const date = goal.deadline ? new Date(`${goal.deadline}T23:59:59`) : null;
      if (filter.deadline === 'none' && goal.deadline) return false;
      if (filter.deadline === 'overdue' && (!date || date >= now)) return false;
      if (filter.deadline === 'week' && (!date || date < now || date > weekEnd)) return false;
      if (filter.deadline === 'month' && (!date || date < now || date > monthEnd)) return false;
    }
    if (q) {
      const haystack = [
        goal.title,
        goal.description,
        goal.notes,
        ...goal.tasks.map((task) => `${task.title} ${task.notes ?? ''}`),
        ...goal.milestones.map((milestone) => milestone.title),
      ]
        .join(' ')
        .toLowerCase();
      if (!haystack.includes(q)) return false;
    }
    return true;
  });

  return filtered.sort((a, b) => {
    switch (filter.sort) {
      case 'deadline': {
        const aTime = a.deadline ? new Date(`${a.deadline}T23:59:59`).getTime() : Number.MAX_SAFE_INTEGER;
        const bTime = b.deadline ? new Date(`${b.deadline}T23:59:59`).getTime() : Number.MAX_SAFE_INTEGER;
        return aTime - bTime;
      }
      case 'priority':
        return priorityWeight[b.priority] - priorityWeight[a.priority];
      case 'progress':
        return computeGoalProgress(b).percent - computeGoalProgress(a).percent;
      case 'title':
        return a.title.localeCompare(b.title);
      case 'created':
        return b.createdAt.localeCompare(a.createdAt);
      case 'recent':
      default:
        return b.updatedAt.localeCompare(a.updatedAt);
    }
  });
}
