import { describe, expect, it } from 'vitest';
import {
  addMilestone,
  addTask,
  createGoal,
  deleteTask,
  duplicateGoalTitle,
  scheduleNextOccurrence,
  toggleTask,
  updateTask,
} from '../lib/mutations';
import { makeGoal, makeTask } from './factories';

describe('goal mutations', () => {
  it('creates a clean goal from a draft', () => {
    const now = new Date(2026, 10, 4, 9, 30);
    const goal = createGoal(
      {
        title: '  Write a novel  ',
        description: '  A first draft  ',
        priority: 'high',
        kind: 'recurring',
        recurrence: { frequency: 'weekly', interval: 2 },
        deadline: '2027-01-01',
      },
      { now, aiGenerated: true },
    );
    expect(goal.title).toBe('Write a novel');
    expect(goal.description).toBe('A first draft');
    expect(goal.status).toBe('active');
    expect(goal.kind).toBe('recurring');
    expect(goal.recurrence).toEqual({ frequency: 'weekly', interval: 2 });
    expect(goal.aiGenerated).toBe(true);
    expect(goal.createdAt).toBe(now.toISOString());
  });

  it('adds, toggles and deletes tasks including subtasks', () => {
    let goal = makeGoal();
    goal = addTask(goal, { title: 'Parent', dueDate: '2026-11-10' });
    const parent = goal.tasks[0];
    goal = addTask(goal, { title: 'Child', parentTaskId: parent.id });
    expect(goal.tasks).toHaveLength(2);
    const toggled = toggleTask(goal, parent.id, new Date('2026-11-04T12:00:00Z'));
    expect(toggled.task?.completed).toBe(true);
    expect(toggled.task?.completedAt).not.toBeNull();
    goal = toggled.goal;
    goal = updateTask(goal, parent.id, { notes: 'context' });
    expect(goal.tasks.find((task) => task.id === parent.id)?.notes).toBe('context');
    goal = deleteTask(goal, parent.id);
    expect(goal.tasks).toHaveLength(0);
  });

  it('adds milestones with stable ordering', () => {
    let goal = makeGoal();
    goal = addMilestone(goal, { title: 'First', dueDate: '2026-11-20' });
    goal = addMilestone(goal, { title: 'Second' });
    expect(goal.milestones.map((milestone) => milestone.order)).toEqual([0, 1]);
  });

  it('creates a future occurrence and remaps milestones', () => {
    const now = new Date(2026, 10, 4, 9, 0);
    const goal = makeGoal({
      kind: 'recurring',
      recurrence: { frequency: 'weekly', interval: 1 },
      deadline: '2026-10-01',
      seriesId: null,
      occurrence: 1,
      milestones: [{ id: 'old-milestone', title: 'Checkpoint', completed: true, order: 0, dueDate: '2026-09-28' }],
      tasks: [
        makeTask({
          id: 'root',
          title: 'Do the thing',
          milestoneId: 'old-milestone',
          dueDate: '2026-09-29',
          completed: true,
          completedAt: '2026-10-01T00:00:00Z',
        }),
        makeTask({
          id: 'child',
          title: 'Smaller thing',
          milestoneId: 'old-milestone',
          parentTaskId: 'root',
          dueDate: '2026-09-30',
          completed: true,
        }),
      ],
    });
    const next = scheduleNextOccurrence(goal, now);
    expect(next).not.toBeNull();
    expect(next!.deadline! > '2026-11-04').toBe(true);
    expect(next!.id).not.toBe(goal.id);
    expect(next!.seriesId).toBe(goal.id);
    expect(next!.occurrence).toBe(2);
    expect(next!.status).toBe('active');
    expect(next!.tasks.every((task) => !task.completed && task.completedAt === null)).toBe(true);
    expect(next!.milestones[0].completed).toBe(false);
    expect(next!.tasks[0].milestoneId).toBe(next!.milestones[0].id);
    expect(next!.tasks[1].parentTaskId).toBe(next!.tasks[0].id);
    expect(next!.tasks[0].id).not.toBe('root');
  });

  it('detects duplicate active titles but allows archived goals', () => {
    const active = makeGoal({ id: 'a', title: 'Same Goal' });
    const archived = makeGoal({ id: 'b', title: 'Other', archivedAt: '2026-10-01T00:00:00.000Z' });
    expect(duplicateGoalTitle([active, archived], 'same goal')?.id).toBe('a');
    expect(duplicateGoalTitle([active], 'same goal', 'a')).toBeNull();
    expect(duplicateGoalTitle([archived], 'other')).toBeNull();
  });
});
