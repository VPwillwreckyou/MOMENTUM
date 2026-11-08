import type { Goal, Task } from '../state/types';

function csvEscape(value: string | number | boolean | null | undefined): string {
  const str = value == null ? '' : String(value);
  if (/[",\n\r]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

export function exportGoalsToCsv(goals: Goal[], categories: { id: string; name: string }[]): void {
  const categoryMap = new Map(categories.map((c) => [c.id, c.name]));
  const header = ['ID', 'Title', 'Kind', 'Category', 'Status', 'Priority', 'Deadline', 'Start Date', 'Completed', 'Created', 'Updated', 'Archived'];
  const rows = goals.map((g) => [
    g.id,
    g.title,
    g.kind,
    categoryMap.get(g.categoryId ?? '') ?? '',
    g.status,
    g.priority,
    g.deadline ?? '',
    g.startDate ?? '',
    g.completedAt ? 'Yes' : 'No',
    g.createdAt,
    g.updatedAt,
    g.archivedAt ? 'Yes' : 'No',
  ]);
  const csv = [header, ...rows].map((r) => r.map(csvEscape).join(',')).join('\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  downloadBlob(blob, `momentum-goals-${new Date().toISOString().slice(0, 10)}.csv`);
}

export function exportTasksToCsv(tasks: Task[], goals: Goal[]): void {
  const goalMap = new Map(goals.map((g) => [g.id, g.title]));
  const header = ['ID', 'Title', 'Goal', 'Completed', 'Priority', 'Due Date', 'Created', 'Updated', 'Completed At'];
  const rows = tasks.map((t) => [
    t.id,
    t.title,
    goalMap.get(t.goalId) ?? '',
    t.completed ? 'Yes' : 'No',
    t.priority,
    t.dueDate ?? '',
    t.createdAt,
    t.updatedAt,
    t.completedAt ?? '',
  ]);
  const csv = [header, ...rows].map((r) => r.map(csvEscape).join(',')).join('\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  downloadBlob(blob, `momentum-tasks-${new Date().toISOString().slice(0, 10)}.csv`);
}

export function exportMilestonesToCsv(milestones: { id: string; title: string; dueDate?: string | null; completed: boolean; completedAt?: string | null; order: number }[]): void {
  const header = ['ID', 'Title', 'Completed', 'Due Date', 'Completed At', 'Order'];
  const rows = milestones.map((m) => [
    m.id,
    m.title,
    m.completed ? 'Yes' : 'No',
    m.dueDate ?? '',
    m.completedAt ?? '',
    m.order,
  ]);
  const csv = [header, ...rows].map((r) => r.map(csvEscape).join(',')).join('\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  downloadBlob(blob, `momentum-milestones-${new Date().toISOString().slice(0, 10)}.csv`);
}
