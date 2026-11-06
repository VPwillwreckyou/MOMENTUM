import { describe, expect, it } from 'vitest';
import { filterGoals, searchWorkspace } from '../lib/search';
import type { Category } from '../state/types';
import { makeGoal, makeTask } from './factories';

const category: Category = {
  id: 'cat-work',
  name: 'Deep work',
  color: '#3b82f6',
  icon: 'work',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
  deletedAt: null,
};

describe('workspace search', () => {
  it('finds goals, tasks, notes, categories and attachments', () => {
    const goal = makeGoal({
      title: 'Launch portfolio',
      notes: 'Use the interview feedback from the design lead.',
      categoryId: category.id,
      tasks: [
        makeTask({ title: 'Draft case study', notes: 'Use the analytics story' }),
        makeTask({ title: 'Collect testimonial', parentTaskId: 'parent-1' }),
      ],
      milestones: [{ id: 'm1', title: 'Public launch', completed: false, order: 0 }],
    });
    const attachments = [
      {
        id: 'a1',
        goalId: goal.id,
        name: 'portfolio-research.pdf',
        mimeType: 'application/pdf',
        size: 1000,
        createdAt: '2026-01-01T00:00:00.000Z',
      },
    ];
    expect(searchWorkspace('portfolio', [goal], [category], attachments)[0].title).toBe('Launch portfolio');
    expect(searchWorkspace('case study', [goal], [category], attachments)[0].kind).toBe('task');
    expect(searchWorkspace('analytics', [goal], [category], attachments)[0].kind).toBe('note');
    expect(searchWorkspace('deep work', [goal], [category], attachments)[0].kind).toBe('category');
    expect(searchWorkspace('research', [goal], [category], attachments)[0].kind).toBe('attachment');
    expect(searchWorkspace('launch', [goal], [category], attachments).length).toBeGreaterThan(0);
    expect(searchWorkspace('a', [goal], [category], attachments)).toHaveLength(0);
  });

  it('filters and sorts without leaking archived or deleted goals', () => {
    const now = new Date(2026, 10, 4);
    const goals = [
      makeGoal({
        id: 'a',
        title: 'Alpha',
        priority: 'high',
        deadline: '2026-11-06',
        categoryId: category.id,
        updatedAt: '2026-11-03T00:00:00.000Z',
      }),
      makeGoal({
        id: 'b',
        title: 'Beta',
        priority: 'low',
        deadline: '2026-11-30',
        updatedAt: '2026-11-04T00:00:00.000Z',
      }),
      makeGoal({ id: 'archived', title: 'Archived', archivedAt: '2026-10-01T00:00:00.000Z' }),
      makeGoal({ id: 'deleted', title: 'Deleted', deletedAt: '2026-10-01T00:00:00.000Z' }),
      makeGoal({ id: 'done', title: 'Done', status: 'completed' }),
    ];
    const active = filterGoals(goals, {
      query: '',
      status: 'active',
      priority: 'all',
      categoryId: 'all',
      deadline: 'any',
      sort: 'deadline',
    }, now);
    expect(active.map((goal) => goal.id)).toEqual(['a', 'b']);
    expect(
      filterGoals(goals, {
        query: '',
        status: 'all',
        priority: 'high',
        categoryId: category.id,
        deadline: 'week',
        sort: 'priority',
      }, now).map((goal) => goal.id),
    ).toEqual(['a']);
    expect(
      filterGoals(goals, {
        query: 'beta',
        status: 'all',
        priority: 'all',
        categoryId: 'all',
        deadline: 'none',
        sort: 'recent',
      }, now),
    ).toHaveLength(0);
    expect(
      filterGoals(goals, {
        query: '',
        status: 'completed',
        priority: 'all',
        categoryId: 'all',
        deadline: 'any',
        sort: 'title',
      }, now).map((goal) => goal.id),
    ).toEqual(['done']);
  });
});
