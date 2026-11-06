import type { AppSettings, AttachmentMeta, Goal, Workspace } from '../state/types';
import { clearAttachments, clearDatabase, deleteDocument, getDocument, listAttachments, putDocument, removeAttachment, removeAttachmentsForGoal, putAttachment } from './db';
import { AI_PROVIDER_LOCK, defaultAIConfig, defaultSettings, emptyWorkspace } from './defaults';
import { openDatabase } from './db';

const WORKSPACE_KEY = 'workspace';
const SETTINGS_KEY = 'settings';

let writeQueue: Promise<void> = Promise.resolve();

function queuedWrite(task: () => Promise<void>): Promise<void> {
  writeQueue = writeQueue.then(task, task);
  return writeQueue;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function normalizeGoal(raw: unknown): Goal | null {
  if (!isRecord(raw) || typeof raw.id !== 'string' || typeof raw.title !== 'string') return null;
  const tasks = Array.isArray(raw.tasks)
    ? raw.tasks.filter(isRecord).map((task, index) => ({
        id: String(task.id ?? `task-${index}`),
        goalId: String(task.goalId ?? raw.id),
        milestoneId: typeof task.milestoneId === 'string' ? task.milestoneId : null,
        parentTaskId: typeof task.parentTaskId === 'string' ? task.parentTaskId : null,
        title: String(task.title ?? 'Untitled task'),
        notes: String(task.notes ?? ''),
        completed: Boolean(task.completed),
        completedAt: typeof task.completedAt === 'string' ? task.completedAt : null,
        dueDate: typeof task.dueDate === 'string' ? task.dueDate : null,
        priority: ['low', 'medium', 'high', 'critical'].includes(String(task.priority))
          ? (task.priority as Goal['priority'])
          : 'medium',
        order: Number.isFinite(task.order) ? Number(task.order) : index,
        createdAt: typeof task.createdAt === 'string' ? task.createdAt : new Date().toISOString(),
        updatedAt: typeof task.updatedAt === 'string' ? task.updatedAt : new Date().toISOString(),
      }))
    : [];
  const milestones = Array.isArray(raw.milestones)
    ? raw.milestones.filter(isRecord).map((milestone, index) => ({
        id: String(milestone.id ?? `milestone-${index}`),
        title: String(milestone.title ?? 'Untitled milestone'),
        description: String(milestone.description ?? ''),
        dueDate: typeof milestone.dueDate === 'string' ? milestone.dueDate : null,
        completed: Boolean(milestone.completed),
        completedAt: typeof milestone.completedAt === 'string' ? milestone.completedAt : null,
        order: Number.isFinite(milestone.order) ? Number(milestone.order) : index,
      }))
    : [];
  return {
    ...(raw as unknown as Goal),
    id: raw.id,
    title: raw.title,
    description: String(raw.description ?? ''),
    notes: String(raw.notes ?? ''),
    categoryId: typeof raw.categoryId === 'string' ? raw.categoryId : null,
    status: raw.status === 'completed' ? 'completed' : 'active',
    kind: raw.kind === 'recurring' ? 'recurring' : 'one-time',
    priority: ['low', 'medium', 'high', 'critical'].includes(String(raw.priority))
      ? (raw.priority as Goal['priority'])
      : 'medium',
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : new Date().toISOString(),
    updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : new Date().toISOString(),
    archivedAt: typeof raw.archivedAt === 'string' ? raw.archivedAt : null,
    deletedAt: typeof raw.deletedAt === 'string' ? raw.deletedAt : null,
    completedAt: typeof raw.completedAt === 'string' ? raw.completedAt : null,
    milestones,
    tasks,
    recurrence: isRecord(raw.recurrence)
      ? {
          frequency: ['daily', 'weekly', 'monthly'].includes(String(raw.recurrence.frequency))
            ? (raw.recurrence.frequency as NonNullable<Goal['recurrence']>['frequency'])
            : 'weekly',
          interval: Math.max(1, Math.min(52, Number(raw.recurrence.interval) || 1)),
        }
      : null,
    finance: isRecord(raw.finance)
      ? {
          enabled: Boolean(raw.finance.enabled),
          currency: String(raw.finance.currency ?? 'USD').slice(0, 8),
          targetAmount: Number(raw.finance.targetAmount) || 0,
          currentAmount: Number(raw.finance.currentAmount) || 0,
          note: String(raw.finance.note ?? ''),
        }
      : null,
  };
}

export function normalizeWorkspace(raw: unknown): Workspace {
  if (!isRecord(raw) || !Array.isArray(raw.goals)) return emptyWorkspace();
  const now = new Date().toISOString();
  const goals = raw.goals.map(normalizeGoal).filter((goal): goal is Goal => !!goal);
  const categories = Array.isArray(raw.categories)
    ? raw.categories.filter(isRecord).map((category) => ({
        id: String(category.id ?? crypto.randomUUID()),
        name: String(category.name ?? 'Category').slice(0, 60),
        color: String(category.color ?? '#3b82f6').slice(0, 9),
        icon: ['target', 'work', 'health', 'learning', 'creative', 'finance', 'home', 'relationships'].includes(
          String(category.icon),
        )
          ? (category.icon as Workspace['categories'][number]['icon'])
          : 'target',
        createdAt: typeof category.createdAt === 'string' ? category.createdAt : now,
        updatedAt: typeof category.updatedAt === 'string' ? category.updatedAt : now,
        deletedAt: typeof category.deletedAt === 'string' ? category.deletedAt : null,
      }))
    : [];
  const activities = Array.isArray(raw.activities)
    ? raw.activities
        .filter(isRecord)
        .slice(0, 400)
        .map((activity) => ({
          id: String(activity.id ?? crypto.randomUUID()),
          goalId: String(activity.goalId ?? ''),
          type: String(activity.type ?? 'goal-edited') as Workspace['activities'][number]['type'],
          message: String(activity.message ?? ''),
          at: typeof activity.at === 'string' ? activity.at : now,
        }))
    : [];
  return {
    schemaVersion: 1,
    installationId: String(raw.installationId ?? crypto.randomUUID()),
    goals,
    categories,
    activities,
    createdAt: typeof raw.createdAt === 'string' ? raw.createdAt : now,
    updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : now,
  };
}

export function normalizeSettings(raw: unknown): AppSettings {
  const base = defaultSettings();
  if (!isRecord(raw)) return base;
  const ai = isRecord(raw.ai) ? raw.ai : {};
  const storedProvider = ['deepseek', 'openai', 'gemini', 'groq', 'ollama', 'custom'].includes(String(ai.provider))
    ? (ai.provider as AppSettings['ai']['provider'])
    : 'groq';
  const provider = AI_PROVIDER_LOCK ?? storedProvider;
  const providerChanged = provider !== storedProvider;
  const providerDefaults = defaultAIConfig(provider);
  const storedModel = String(ai.model ?? '');
  const retiredGroqModel = provider === 'groq' && storedModel === 'llama-3.3-70b-versatile';
  return {
    ...base,
    themeMode: ['dark', 'light', 'system'].includes(String(raw.themeMode))
      ? (raw.themeMode as AppSettings['themeMode'])
      : 'dark',
    accent: /^#[0-9a-fA-F]{6}$/.test(String(raw.accent)) ? String(raw.accent) : base.accent,
    density: raw.density === 'compact' ? 'compact' : 'comfortable',
    reduceMotion: Boolean(raw.reduceMotion),
    weekStartsOn: raw.weekStartsOn === 0 ? 0 : 1,
    displayName: String(raw.displayName ?? '').slice(0, 80),
    onboardingComplete: Boolean(raw.onboardingComplete),
    syncEnabled: Boolean(raw.syncEnabled),
    lastSyncAt: typeof raw.lastSyncAt === 'string' ? raw.lastSyncAt : null,
    ai: {
      ...providerDefaults,
      ...ai,
      provider,
      model:
        providerChanged || retiredGroqModel || !storedModel
          ? providerDefaults.model
          : storedModel.slice(0, 160),
      baseUrl:
        providerChanged || !ai.baseUrl
          ? providerDefaults.baseUrl
          : String(ai.baseUrl).slice(0, 500),
      apiKeyCipher:
        !providerChanged && typeof ai.apiKeyCipher === 'string' ? ai.apiKeyCipher : null,
      hasKey: !providerChanged && Boolean(ai.hasKey),
      consentAcceptedAt: typeof ai.consentAcceptedAt === 'string' ? ai.consentAcceptedAt : null,
      lastTestAt: typeof ai.lastTestAt === 'string' ? ai.lastTestAt : null,
      lastTestOk: typeof ai.lastTestOk === 'boolean' ? ai.lastTestOk : null,
    },
  };
}

export interface LoadedState {
  workspace: Workspace;
  settings: AppSettings;
  attachments: AttachmentMeta[];
  fresh: boolean;
}

export async function loadLocalState(): Promise<LoadedState> {
  await openDatabase();
  const [storedWorkspace, storedSettings, attachments] = await Promise.all([
    getDocument<unknown>(WORKSPACE_KEY),
    getDocument<unknown>(SETTINGS_KEY),
    listAttachments(),
  ]);
  const fresh = !storedWorkspace;
  const workspace = storedWorkspace ? normalizeWorkspace(storedWorkspace) : emptyWorkspace();
  const settings = normalizeSettings(storedSettings);
  if (fresh) {
    await queuedWrite(async () => {
      await putDocument(WORKSPACE_KEY, workspace);
      await putDocument(SETTINGS_KEY, settings);
    });
  }
  return { workspace, settings, attachments, fresh };
}

export function saveWorkspace(workspace: Workspace): Promise<void> {
  return queuedWrite(() => putDocument(WORKSPACE_KEY, workspace));
}

export function saveSettings(settings: AppSettings): Promise<void> {
  return queuedWrite(() => putDocument(SETTINGS_KEY, settings));
}

export async function addLocalAttachment(meta: AttachmentMeta, blob: Blob): Promise<void> {
  await putAttachment(meta, blob);
}

export async function deleteLocalAttachment(id: string): Promise<void> {
  await removeAttachment(id);
}

export async function deleteGoalAttachments(goalId: string): Promise<void> {
  await removeAttachmentsForGoal(goalId);
}

export async function eraseAllLocalData(): Promise<void> {
  await clearDatabase();
}

export async function clearLocalAttachments(): Promise<void> {
  await clearAttachments();
}

export async function resetDocumentsForImport(): Promise<void> {
  await deleteDocument(WORKSPACE_KEY);
  await deleteDocument(SETTINGS_KEY);
}
