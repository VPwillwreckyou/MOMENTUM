import { describe, expect, it } from 'vitest';
import { validateEmail, validateGoalDraft, validateHex, validatePassword } from '../lib/validation';

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


});
