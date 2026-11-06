import { describe, expect, it } from 'vitest';
import { decryptWorkspace, encryptWorkspace, mergeWorkspaces } from '../lib/sync';
import type { Workspace } from '../state/types';
import { emptyWorkspace } from '../lib/defaults';
import { makeGoal } from './factories';

function workspaceWith(overrides: Partial<Workspace> = {}): Workspace {
  return {
    ...emptyWorkspace(),
    goals: [makeGoal()],
    ...overrides,
  };
}

describe('encrypted sync', () => {
  it('round-trips a workspace through AES-GCM', async () => {
    const workspace = workspaceWith({
      goals: [makeGoal({ title: 'Private goal', notes: 'Sensitive notes' })],
    });
    const envelope = await encryptWorkspace(workspace, 'a long sync passphrase');
    const serialized = JSON.stringify(envelope);
    expect(serialized).not.toContain('Private goal');
    expect(serialized).not.toContain('Sensitive notes');
    expect(envelope.ciphertext.length).toBeGreaterThan(20);
    const decrypted = await decryptWorkspace(serialized, 'a long sync passphrase');
    expect(decrypted.goals[0].title).toBe('Private goal');
    expect(decrypted.goals[0].notes).toBe('Sensitive notes');
  });

  it('rejects the wrong passphrase and damaged envelopes', async () => {
    const envelope = await encryptWorkspace(workspaceWith(), 'the correct passphrase');
    await expect(decryptWorkspace(JSON.stringify(envelope), 'the wrong passphrase')).rejects.toThrow(
      /passphrase is incorrect/i,
    );
    await expect(decryptWorkspace('{"version":1}', 'the correct passphrase')).rejects.toThrow(
      /unsupported format/i,
    );
  });

  it('merges by item update time and preserves tombstones', () => {
    const local = workspaceWith({
      goals: [
        makeGoal({ id: 'same', title: 'Local version', updatedAt: '2026-11-02T10:00:00.000Z' }),
        makeGoal({ id: 'local-only', title: 'Local only', updatedAt: '2026-11-01T10:00:00.000Z' }),
      ],
      categories: [],
      activities: [],
    });
    const remote = workspaceWith({
      goals: [
        makeGoal({ id: 'same', title: 'Remote version', updatedAt: '2026-11-03T10:00:00.000Z' }),
        makeGoal({
          id: 'remote-deleted',
          title: 'Deleted remotely',
          updatedAt: '2026-11-04T10:00:00.000Z',
          deletedAt: '2026-11-04T10:00:00.000Z',
        }),
      ],
      categories: [],
      activities: [],
    });
    const merged = mergeWorkspaces(local, remote);
    expect(merged.goals).toHaveLength(3);
    expect(merged.goals.find((goal) => goal.id === 'same')?.title).toBe('Remote version');
    expect(merged.goals.find((goal) => goal.id === 'local-only')).toBeTruthy();
    expect(merged.goals.find((goal) => goal.id === 'remote-deleted')?.deletedAt).not.toBeNull();
  });

  it('keeps the newer local version when it wins', () => {
    const local = workspaceWith({
      goals: [makeGoal({ id: 'same', title: 'Local newer', updatedAt: '2026-11-05T10:00:00.000Z' })],
      categories: [],
      activities: [],
    });
    const remote = workspaceWith({
      goals: [makeGoal({ id: 'same', title: 'Remote older', updatedAt: '2026-11-03T10:00:00.000Z' })],
      categories: [],
      activities: [],
    });
    expect(mergeWorkspaces(local, remote).goals[0].title).toBe('Local newer');
  });
});
