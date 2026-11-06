import { describe, expect, it } from 'vitest';
import { normalizeAIPlan, parseAIJson, validateEmail, validateGoalDraft, validateHex, validatePassword } from '../lib/validation';

describe('validation', () => {
  it('validates goal drafts', () => {
    const errors = validateGoalDraft({
      title: '',
      kind: 'recurring',
      deadline: '2026-10-01',
      startDate: '2026-11-01',
      finance: { enabled: true, currency: 'USD', targetAmount: 0, currentAmount: -2 },
    });
    expect(errors.title).toBeTruthy();
    expect(errors.recurrence).toBeTruthy();
    expect(errors.deadline).toBeTruthy();
    expect(errors.financeTarget).toBeTruthy();
    expect(errors.financeCurrent).toBeTruthy();
    expect(
      validateGoalDraft({
        title: 'A valid goal',
        kind: 'recurring',
        recurrence: { frequency: 'weekly', interval: 1 },
        deadline: '2026-12-01',
      }),
    ).toEqual({});
  });

  it('validates auth inputs and hex colors', () => {
    expect(validateEmail('person@example.com')).toBe(true);
    expect(validateEmail('bad-email')).toBe(false);
    expect(validatePassword('short')).toBeTruthy();
    expect(validatePassword('long-enough1')).toBeNull();
    expect(validateHex('#3B82F6')).toBe('#3b82f6');
    expect(validateHex('blue')).toBeNull();
  });

  it('normalizes untrusted AI output and rejects empty plans', () => {
    const plan = normalizeAIPlan({
      title: '  A test plan ',
      description: 'Why it works',
      milestones: [
        {
          title: 'Milestone one',
          description: 'Outcome',
          dueDate: '2026-12-01',
          tasks: [
            { title: 'Do a thing', dueDate: '2026-11-20', priority: 'urgent' },
            { title: '' },
          ],
        },
      ],
      firstTasks: [{ title: 'Start now', priority: 'high' }],
    });
    expect(plan.title).toBe('A test plan');
    expect(plan.milestones[0].tasks[0].priority).toBe('medium');
    expect(plan.milestones[0].tasks[1].title).toBe('Next action');
    expect(plan.firstTasks[0].priority).toBe('high');
    expect(() => normalizeAIPlan({ title: 'Empty', milestones: [], firstTasks: [] })).toThrow(/without milestones/i);
  });

  it('parses JSON wrapped in a markdown fence', () => {
    expect(parseAIJson('```json\n{"ok":true}\n```')).toEqual({ ok: true });
    expect(parseAIJson('noise before {"ok":true} noise after')).toEqual({ ok: true });
    expect(() => parseAIJson('not json')).toThrow(/valid JSON/i);
  });
});
