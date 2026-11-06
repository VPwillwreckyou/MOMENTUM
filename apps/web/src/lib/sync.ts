import type { Workspace } from '../state/types';
import { apiRequest, ApiError } from './api';
import { normalizeWorkspace } from './repository';
import { isoNow } from './dates';
import { asBufferSource, base64ToBytes, bytesToBase64 } from './binary';

const ITERATIONS = 310_000;
const AAD = new TextEncoder().encode('momentum-sync-v1');

interface Envelope {
  version: 1;
  algorithm: 'AES-GCM';
  kdf: 'PBKDF2-SHA-256';
  iterations: number;
  salt: string;
  iv: string;
  ciphertext: string;
  updatedAt: string;
  deviceId: string;
}

interface SyncPayload {
  format: 'momentum-workspace';
  version: 1;
  workspace: Workspace;
}

export interface RemoteEnvelope {
  envelope: string;
  revision: number;
  updatedAt: string;
}

async function deriveKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey(
    'raw',
    asBufferSource(new TextEncoder().encode(passphrase)),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: asBufferSource(salt), iterations, hash: 'SHA-256' },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

export async function encryptWorkspace(workspace: Workspace, passphrase: string): Promise<Envelope> {
  if (passphrase.length < 10) throw new Error('Use a sync passphrase of at least 10 characters.');
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(passphrase, salt, ITERATIONS);
  const payload: SyncPayload = { format: 'momentum-workspace', version: 1, workspace };
  const encoded = new TextEncoder().encode(JSON.stringify(payload));
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: asBufferSource(iv), additionalData: asBufferSource(AAD) },
    key,
    asBufferSource(encoded),
  );
  return {
    version: 1,
    algorithm: 'AES-GCM',
    kdf: 'PBKDF2-SHA-256',
    iterations: ITERATIONS,
    salt: bytesToBase64(salt),
    iv: bytesToBase64(iv),
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
    updatedAt: isoNow(),
    deviceId: workspace.installationId,
  };
}

export async function decryptWorkspace(envelopeJson: string, passphrase: string): Promise<Workspace> {
  let envelope: Envelope;
  try {
    envelope = JSON.parse(envelopeJson) as Envelope;
  } catch {
    throw new Error('The cloud backup is not readable.');
  }
  if (
    envelope.version !== 1 ||
    envelope.algorithm !== 'AES-GCM' ||
    !envelope.salt ||
    !envelope.iv ||
    !envelope.ciphertext ||
    !Number.isFinite(envelope.iterations) ||
    envelope.iterations < 100_000 ||
    envelope.iterations > 1_000_000
  ) {
    throw new Error('The cloud backup uses an unsupported format.');
  }
  const salt = base64ToBytes(envelope.salt);
  const iv = base64ToBytes(envelope.iv);
  const key = await deriveKey(passphrase, salt, envelope.iterations);
  let plain: ArrayBuffer;
  try {
    plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: asBufferSource(iv), additionalData: asBufferSource(AAD) },
      key,
      asBufferSource(base64ToBytes(envelope.ciphertext)),
    );
  } catch {
    throw new Error('The sync passphrase is incorrect, or the cloud backup is damaged.');
  }
  let payload: SyncPayload;
  try {
    payload = JSON.parse(new TextDecoder().decode(plain)) as SyncPayload;
  } catch {
    throw new Error('The decrypted backup is not valid workspace data.');
  }
  if (payload.format !== 'momentum-workspace' || !payload.workspace || !Array.isArray(payload.workspace.goals)) {
    throw new Error('The decrypted backup is not a MOMENTUM workspace.');
  }
  return normalizeWorkspace(payload.workspace);
}

export function mergeWorkspaces(local: Workspace, remote: Workspace): Workspace {
  const goals = new Map(local.goals.map((goal) => [goal.id, goal]));
  for (const remoteGoal of remote.goals) {
    const existing = goals.get(remoteGoal.id);
    if (!existing || remoteGoal.updatedAt > existing.updatedAt) goals.set(remoteGoal.id, remoteGoal);
  }
  const categories = new Map(local.categories.map((category) => [category.id, category]));
  for (const remoteCategory of remote.categories) {
    const existing = categories.get(remoteCategory.id);
    if (!existing || remoteCategory.updatedAt > existing.updatedAt) categories.set(remoteCategory.id, remoteCategory);
  }
  const activities = new Map(local.activities.map((activity) => [activity.id, activity]));
  for (const activity of remote.activities) activities.set(activity.id, activity);
  const merged: Workspace = {
    ...local,
    goals: [...goals.values()],
    categories: [...categories.values()],
    activities: [...activities.values()]
      .sort((a, b) => b.at.localeCompare(a.at))
      .slice(0, 350),
    createdAt: local.createdAt < remote.createdAt ? local.createdAt : remote.createdAt,
    updatedAt: isoNow(),
  };
  return merged;
}

export async function fetchEnvelope(): Promise<RemoteEnvelope | null> {
  const response = await apiRequest<RemoteEnvelope & { envelope: string | null }>('/api/sync/envelope');
  if (!response.envelope) return null;
  return response;
}

export async function pushEnvelope(envelope: Envelope, baseRevision: number): Promise<RemoteEnvelope> {
  return apiRequest<RemoteEnvelope>('/api/sync/envelope', {
    method: 'PUT',
    body: { envelope: JSON.stringify(envelope), baseRevision },
  });
}

export async function deleteEnvelope(): Promise<void> {
  await apiRequest('/api/sync/envelope', { method: 'DELETE' });
}

export interface SyncResult {
  workspace: Workspace;
  revision: number;
  updatedAt: string;
  mergedRemote: boolean;
}

/**
 * Explicit two-way encrypted sync. The server only ever sees ciphertext.
 * Retries once when another device wrote between GET and PUT.
 */
export async function syncEncrypted(local: Workspace, passphrase: string): Promise<SyncResult> {
  if (passphrase.length < 10) throw new Error('Use a sync passphrase of at least 10 characters.');
  let remote = await fetchEnvelope();
  let merged = local;
  let mergedRemote = false;
  if (remote) {
    const remoteWorkspace = await decryptWorkspace(remote.envelope, passphrase);
    merged = mergeWorkspaces(local, remoteWorkspace);
    mergedRemote = true;
  }

  for (let attempt = 0; attempt < 2; attempt += 1) {
    const envelope = await encryptWorkspace(merged, passphrase);
    try {
      const saved = await pushEnvelope(envelope, remote?.revision ?? 0);
      return { workspace: merged, revision: saved.revision, updatedAt: saved.updatedAt, mergedRemote };
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 409 || attempt === 1) throw error;
      remote = await fetchEnvelope();
      if (!remote) throw error;
      const remoteWorkspace = await decryptWorkspace(remote.envelope, passphrase);
      merged = mergeWorkspaces(merged, remoteWorkspace);
      mergedRemote = true;
    }
  }
  throw new Error('The encrypted backup changed while syncing. Try again.');
}
