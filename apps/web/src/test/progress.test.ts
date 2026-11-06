import { describe, expect, it } from 'vitest';
import { computeGoalProgress, momentumScore, nextActionableTask, tasksDueThisWeek } from '../lib/progress';
import { makeGoal, makeTask } from './factories';

describe('goal progress', () => {
  it('weights tasks and milestones when both exist', () => {
    const goal = makeGoal({
      tasks: [
        makeTask({ completed: true }),
        makeTask({ completed: false }),
        makeTask({ completed: false }),
      ],
      milestones: [
        { id: 'm1', title: 'One', completed: true, order: 0 },
        { id: 'm2', title: 'Two', completed: false, order: 1 },
      ],
    });
    const progress = computeGoalProgress(goal);
    expect(progress.taskPercent).toBe(33);
    expect(progress.milestonePercent).toBe(50);
    expect(progress.percent).toBe(39);
    expect(progress.completedTasks).toBe(1);
  });

  it('uses task-only and milestone-only plans correctly', () => {
    expect(computeGoalProgress(makeGoal({ tasks: [makeTask({ completed: true })] })).percent).toBe(100);
    expect(
      computeGoalProgress(makeGoal({ milestones: [{ id: 'm', title: 'M', completed: true, order: 0 }] })).percent,
    ).toBe(100);
    expect(computeGoalProgress(makeGoal()).percent).toBe(0);
    expect(computeGoalProgress(makeGoal({ status: 'completed' })).percent).toBe(100);
  });

  it('selects the nearest open task', () => {
    const late = makeTask({ title: 'late', dueDate: '2026-12-20', order: 0 });
    const soon = makeTask({ title: 'soon', dueDate: '2026-11-06', order: 1 });
    const none = makeTask({ title: 'none', order: 2 });
    expect(nextActionableTask(makeGoal({ tasks: [late, soon, none] }))?.title).toBe('soon');
  });

  it('finds tasks due in the current week', () => {
    const monday = new Date(2026, 10, 2);
    const goal = makeGoal({
      tasks: [
        makeTask({ dueDate: '2026-11-04' }),
        makeTask({ dueDate: '2026-11-20' }),
        makeTask({ dueDate: '2026-11-05', completed: true }),
      ],
    });
    expect(tasksDueThisWeek([goal], 1, monday)).toHaveLength(1);
    expect(monday.getDay()).toBe(1);
  });

  it('produces a bounded momentum score', () => {
    const goals = [
      makeGoal({ tasks: [makeTask({ completed: true }), makeTask({ completed: false })] }),
      makeGoal({ id: 'goal-2', tasks: [makeTask({ goalId: 'goal-2', completed: true })] }),
    ];
    const score = momentumScore(goals);
    expect(score).toBeGreaterThanOrEqual(0);
    expect(score).toBeLessThanOrEqual(100);
    expect(momentumScore([])).toBe(0);
  });
});
