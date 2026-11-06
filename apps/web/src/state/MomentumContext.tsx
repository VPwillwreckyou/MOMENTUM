import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type {
  ActivityType,
  AppSettings,
  AttachmentMeta,
  Category,
  CategoryIcon,
  AIPlan,
  Goal,
  GoalDraft,
  Milestone,
  Task,
  Toast,
  ToastTone,
  Workspace,
} from './types';
import { emptyWorkspace, defaultSettings } from '../lib/defaults';
import {
  addLocalAttachment,
  clearLocalAttachments,
  deleteGoalAttachments,
  deleteLocalAttachment,
  eraseAllLocalData,
  loadLocalState,
  saveSettings,
  saveWorkspace,
} from '../lib/repository';
import { applyAppearance, watchSystemTheme } from '../lib/appearance';
import { createId } from '../lib/id';
import { isoNow } from '../lib/dates';
import { blobToDataUrl, dataUrlToBlob } from '../lib/binary';
import { getAttachmentBlob } from '../lib/db';
import {
  addMilestone as addMilestoneMutation,
  addTask as addTaskMutation,
  applyPlanToGoal,
  createGoal,
  deleteMilestone as deleteMilestoneMutation,
  deleteTask as deleteTaskMutation,
  scheduleNextOccurrence,
  toggleTask as toggleTaskMutation,
  updateMilestone as updateMilestoneMutation,
  updateTask as updateTaskMutation,
  type NewMilestoneInput,
  type NewTaskInput,
} from '../lib/mutations';

const DELETION_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

interface MomentumActions {
  createGoal: (draft: GoalDraft, options?: { aiGenerated?: boolean }) => Goal;
  createGoalFromPlan: (draft: GoalDraft, plan: AIPlan) => Goal;
  updateGoal: (id: string, patch: Partial<Goal>, activity?: string) => void;
  setGoalCompleted: (id: string, completed: boolean) => Goal | null;
  archiveGoal: (id: string) => void;
  restoreArchivedGoal: (id: string) => void;
  trashGoal: (id: string) => void;
  restoreTrashedGoal: (id: string) => void;
  permanentlyDeleteGoal: (id: string) => Promise<void>;
  emptyTrash: () => Promise<void>;
  purgeExpiredGoals: () => Promise<void>;
  addTask: (goalId: string, input: NewTaskInput) => void;
  updateTask: (goalId: string, taskId: string, patch: Partial<Task>) => void;
  toggleTask: (goalId: string, taskId: string) => void;
  deleteTask: (goalId: string, taskId: string) => void;
  addMilestone: (goalId: string, input: NewMilestoneInput) => void;
  updateMilestone: (goalId: string, milestoneId: string, patch: Partial<Milestone>) => void;
  toggleMilestone: (goalId: string, milestoneId: string) => void;
  deleteMilestone: (goalId: string, milestoneId: string) => void;
  addCategory: (name: string, color: string, icon: CategoryIcon) => Category | null;
  updateCategory: (id: string, patch: Partial<Pick<Category, 'name' | 'color' | 'icon'>>) => void;
  deleteCategory: (id: string) => void;
  updateSettings: (patch: Partial<AppSettings>) => void;
  finishOnboarding: () => void;
  uploadAttachment: (goalId: string, file: File) => Promise<boolean>;
  removeAttachment: (id: string) => Promise<void>;
  exportGoalBackup: () => Promise<void>;
  importGoalBackup: (
    workspace: Workspace,
    settings: AppSettings,
    attachmentData?: { meta: AttachmentMeta; dataUrl: string }[],
  ) => Promise<void>;
  replaceWorkspace: (workspace: Workspace) => Promise<void>;
  eraseEverything: () => Promise<void>;
  toast: (message: string, tone?: ToastTone, action?: { label: string; run: () => void }) => void;
  dismissToast: (id: string) => void;
}

interface MomentumContextValue extends MomentumActions {
  workspace: Workspace;
  goals: Goal[];
  categories: Category[];
  activities: Workspace['activities'];
  attachments: AttachmentMeta[];
  settings: AppSettings;
  loaded: boolean;
  storageError: string | null;
  toasts: Toast[];
}

const MomentumContext = createContext<MomentumContextValue | null>(null);

function makeActivity(goalId: string, type: ActivityType, message: string, at = isoNow()) {
  return { id: createId(), goalId, type, message, at };
}

function withActivity(workspace: Workspace, activity: ReturnType<typeof makeActivity>): Workspace {
  return {
    ...workspace,
    activities: [activity, ...workspace.activities].slice(0, 350),
    updatedAt: activity.at,
  };
}

function isExpired(goal: Goal, now: number): boolean {
  if (!goal.deletedAt) return false;
  return new Date(goal.deletedAt).getTime() + DELETION_WINDOW_MS <= now;
}

export function MomentumProvider({ children }: { children: ReactNode }) {
  const [workspace, setWorkspace] = useState<Workspace>(emptyWorkspace);
  const [settings, setSettings] = useState<AppSettings>(defaultSettings);
  const [attachments, setAttachments] = useState<AttachmentMeta[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [storageError, setStorageError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const workspaceRef = useRef(workspace);
  const settingsRef = useRef(settings);
  const bootRef = useRef(false);

  const dismissToast = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, tone: ToastTone = 'success', action?: { label: string; run: () => void }) => {
      const id = createId();
      setToasts((current) => [...current.slice(-2), { id, message, tone, actionLabel: action?.label, onAction: action?.run }]);
      window.setTimeout(() => dismissToast(id), action ? 8500 : 4800);
    },
    [dismissToast],
  );

  const commitWorkspace = useCallback(
    (updater: (current: Workspace) => Workspace) => {
      const current = workspaceRef.current;
      const next = updater(current);
      if (next === current) return;
      const stamped = { ...next, updatedAt: isoNow() };
      workspaceRef.current = stamped;
      setWorkspace(stamped);
      void saveWorkspace(stamped)
        .then(() => setStorageError(null))
        .catch((error: unknown) => {
          const message = error instanceof Error ? error.message : 'Could not save to local storage.';
          setStorageError(message);
          toast('Changes could not be saved locally.', 'error');
        });
    },
    [toast],
  );

  const updateGoal = useCallback(
    (id: string, patch: Partial<Goal>, activityMessage?: string) => {
      const now = isoNow();
      commitWorkspace((ws) => {
        const existing = ws.goals.find((goal) => goal.id === id);
        if (!existing) return ws;
        const protectedPatch = { ...patch };
        delete protectedPatch.id;
        delete protectedPatch.createdAt;
        delete protectedPatch.tasks;
        delete protectedPatch.milestones;
        const updated: Goal = { ...existing, ...protectedPatch, updatedAt: now };
        let next: Workspace = {
          ...ws,
          goals: ws.goals.map((goal) => (goal.id === id ? updated : goal)),
        };
        if (activityMessage) {
          next = withActivity(next, makeActivity(id, 'goal-edited', activityMessage, now));
        }
        return next;
      });
    },
    [commitWorkspace],
  );

  const purgeExpiredGoals = useCallback(async () => {
    const now = Date.now();
    const expired = workspaceRef.current.goals.filter((goal) => isExpired(goal, now));
    if (expired.length === 0) return;
    await Promise.all(expired.map((goal) => deleteGoalAttachments(goal.id)));
    setAttachments((current) => current.filter((attachment) => !expired.some((goal) => goal.id === attachment.goalId)));
    commitWorkspace((ws) => {
      const expiredIds = new Set(expired.map((goal) => goal.id));
      return {
        ...ws,
        goals: ws.goals.filter((goal) => !expiredIds.has(goal.id)),
        activities: ws.activities.filter((activity) => !expiredIds.has(activity.goalId)),
      };
    });
    toast(`${expired.length} expired ${expired.length === 1 ? 'goal was' : 'goals were'} permanently removed.`, 'info');
  }, [commitWorkspace, toast]);

  useEffect(() => {
    if (bootRef.current) return;
    bootRef.current = true;
    let alive = true;
    void (async () => {
      try {
        const loadedState = await loadLocalState();
        if (!alive) return;
        workspaceRef.current = loadedState.workspace;
        settingsRef.current = loadedState.settings;
        setWorkspace(loadedState.workspace);
        setSettings(loadedState.settings);
        setAttachments(loadedState.attachments);
        applyAppearance(loadedState.settings);
        setLoaded(true);
        const expired = loadedState.workspace.goals.filter((goal) => isExpired(goal, Date.now()));
        if (expired.length > 0) {
          await Promise.all(expired.map((goal) => deleteGoalAttachments(goal.id)));
          if (!alive) return;
          setAttachments((current) => current.filter((attachment) => !expired.some((goal) => goal.id === attachment.goalId)));
          const ids = new Set(expired.map((goal) => goal.id));
          const cleaned: Workspace = {
            ...loadedState.workspace,
            goals: loadedState.workspace.goals.filter((goal) => !ids.has(goal.id)),
            activities: loadedState.workspace.activities.filter((activity) => !ids.has(activity.goalId)),
            updatedAt: isoNow(),
          };
          workspaceRef.current = cleaned;
          setWorkspace(cleaned);
          await saveWorkspace(cleaned);
        }
      } catch (error) {
        if (!alive) return;
        setStorageError(error instanceof Error ? error.message : 'Local storage could not be opened.');
        setLoaded(true);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (!loaded) return;
    applyAppearance(settings);
    settingsRef.current = settings;
    void saveSettings(settings).catch((error: unknown) => {
      setStorageError(error instanceof Error ? error.message : 'Settings could not be saved.');
    });
    if (settings.themeMode === 'system') {
      return watchSystemTheme(() => applyAppearance(settingsRef.current));
    }
  }, [settings, loaded]);

  useEffect(() => {
    const timer = window.setInterval(() => void purgeExpiredGoals(), 60 * 60 * 1000);
    return () => window.clearInterval(timer);
  }, [purgeExpiredGoals]);

  const setGoalCompleted = useCallback(
    (id: string, completed: boolean): Goal | null => {
      const current = workspaceRef.current.goals.find((goal) => goal.id === id);
      if (!current) return null;
      if (!completed && current.kind === 'recurring') {
        toast('This recurring occurrence is already complete. Reopen it from the series instead.', 'info');
        return null;
      }
      const now = isoNow();
      let scheduled: Goal | null = null;
      if (completed && current.kind === 'recurring') {
        scheduled = scheduleNextOccurrence(current, new Date());
        if (scheduled) {
          scheduled = {
            ...scheduled,
            tasks: scheduled.tasks.map((task) => ({ ...task, goalId: scheduled!.id })),
          };
        }
      }
      commitWorkspace((ws) => {
        const target = ws.goals.find((goal) => goal.id === id);
        if (!target) return ws;
        const updated: Goal = {
          ...target,
          status: completed ? 'completed' : 'active',
          completedAt: completed ? now : null,
          updatedAt: now,
        };
        let next: Workspace = {
          ...ws,
          goals: ws.goals.map((goal) => (goal.id === id ? updated : goal)),
        };
        next = withActivity(
          next,
          makeActivity(
            id,
            completed ? 'goal-completed' : 'task-reopened',
            completed ? `Completed “${target.title}”` : `Reopened “${target.title}”`,
            now,
          ),
        );
        if (scheduled) {
          next = {
            ...next,
            goals: [...next.goals, scheduled],
          };
          next = withActivity(
            next,
            makeActivity(
              scheduled.id,
              'goal-created',
              `Next occurrence scheduled for ${scheduled.deadline ?? 'the next cycle'}`,
              now,
            ),
          );
        }
        return next;
      });
      if (completed && scheduled) {
        toast(`Goal complete. Next occurrence: ${scheduled.deadline ?? 'scheduled'}.`, 'success');
      }
      return scheduled;
    },
    [commitWorkspace, toast],
  );

  const createGoalAction = useCallback(
    (draft: GoalDraft, options?: { aiGenerated?: boolean }): Goal => {
      const goal = createGoal(draft, { aiGenerated: options?.aiGenerated });
      commitWorkspace((ws) =>
        withActivity(
          {
            ...ws,
            goals: [...ws.goals, goal],
          },
          makeActivity(goal.id, 'goal-created', `Created “${goal.title}”`),
        ),
      );
      return goal;
    },
    [commitWorkspace],
  );

  const createGoalFromPlan = useCallback(
    (draft: GoalDraft, plan: AIPlan): Goal => {
      const now = new Date();
      const baseGoal = createGoal(draft, { aiGenerated: true, now });
      const goal = applyPlanToGoal(
        baseGoal,
        {
          milestones: plan.milestones.map((milestone) => ({
            title: milestone.title,
            description: milestone.description,
            dueDate: milestone.dueDate,
            tasks: milestone.tasks,
          })),
          tasks: plan.firstTasks,
        },
        now,
      );
      commitWorkspace((ws) =>
        withActivity(
          {
            ...ws,
            goals: [...ws.goals, goal],
          },
          makeActivity(goal.id, 'plan-imported', `Added AI-generated plan “${goal.title}”`),
        ),
      );
      return goal;
    },
    [commitWorkspace],
  );

  const archiveGoal = useCallback(
    (id: string) => {
      const now = isoNow();
      commitWorkspace((ws) => {
        const goal = ws.goals.find((item) => item.id === id);
        if (!goal) return ws;
        return withActivity(
          {
            ...ws,
            goals: ws.goals.map((item) =>
              item.id === id ? { ...item, archivedAt: now, deletedAt: null, updatedAt: now } : item,
            ),
          },
          makeActivity(id, 'goal-archived', `Archived “${goal.title}”`, now),
        );
      });
      toast('Goal archived.', 'success', {
        label: 'Undo',
        run: () => {
          commitWorkspace((ws) => ({
            ...ws,
            goals: ws.goals.map((goal) => (goal.id === id ? { ...goal, archivedAt: null, updatedAt: isoNow() } : goal)),
          }));
        },
      });
    },
    [commitWorkspace, toast],
  );

  const restoreArchivedGoal = useCallback(
    (id: string) => {
      const now = isoNow();
      commitWorkspace((ws) => {
        const goal = ws.goals.find((item) => item.id === id);
        if (!goal) return ws;
        return withActivity(
          {
            ...ws,
            goals: ws.goals.map((item) =>
              item.id === id ? { ...item, archivedAt: null, updatedAt: now } : item,
            ),
          },
          makeActivity(id, 'goal-restored', `Restored “${goal.title}” from archive`, now),
        );
      });
      toast('Goal restored.', 'success');
    },
    [commitWorkspace, toast],
  );

  const trashGoal = useCallback(
    (id: string) => {
      const now = isoNow();
      commitWorkspace((ws) => {
        const goal = ws.goals.find((item) => item.id === id);
        if (!goal) return ws;
        return {
          ...ws,
          goals: ws.goals.map((item) =>
            item.id === id ? { ...item, deletedAt: now, archivedAt: null, updatedAt: now } : item,
          ),
        };
      });
      toast('Moved to Recently Deleted. It will be removed after 30 days.', 'info', {
        label: 'Undo',
        run: () => {
          commitWorkspace((ws) => ({
            ...ws,
            goals: ws.goals.map((goal) => (goal.id === id ? { ...goal, deletedAt: null, updatedAt: isoNow() } : goal)),
          }));
        },
      });
    },
    [commitWorkspace, toast],
  );

  const restoreTrashedGoal = useCallback(
    (id: string) => {
      const now = isoNow();
      commitWorkspace((ws) => {
        const goal = ws.goals.find((item) => item.id === id);
        if (!goal) return ws;
        return withActivity(
          {
            ...ws,
            goals: ws.goals.map((item) => (item.id === id ? { ...item, deletedAt: null, updatedAt: now } : item)),
          },
          makeActivity(id, 'goal-restored', `Restored “${goal.title}” from Recently Deleted`, now),
        );
      });
      toast('Goal restored.', 'success');
    },
    [commitWorkspace, toast],
  );

  const permanentlyDeleteGoal = useCallback(
    async (id: string) => {
      await deleteGoalAttachments(id);
      setAttachments((current) => current.filter((attachment) => attachment.goalId !== id));
      commitWorkspace((ws) => ({
        ...ws,
        goals: ws.goals.filter((goal) => goal.id !== id),
        activities: ws.activities.filter((activity) => activity.goalId !== id),
      }));
    },
    [commitWorkspace],
  );

  const emptyTrash = useCallback(async () => {
    const trashed = workspaceRef.current.goals.filter((goal) => goal.deletedAt);
    await Promise.all(trashed.map((goal) => deleteGoalAttachments(goal.id)));
    const ids = new Set(trashed.map((goal) => goal.id));
    setAttachments((current) => current.filter((attachment) => !ids.has(attachment.goalId)));
    commitWorkspace((ws) => ({
      ...ws,
      goals: ws.goals.filter((goal) => !ids.has(goal.id)),
      activities: ws.activities.filter((activity) => !ids.has(activity.goalId)),
    }));
    toast('Recently Deleted emptied.', 'success');
  }, [commitWorkspace, toast]);

  const mutateGoal = useCallback(
    (goalId: string, mutate: (goal: Goal) => Goal, activity?: { type: ActivityType; message: string }) => {
      commitWorkspace((ws) => {
        const goal = ws.goals.find((item) => item.id === goalId);
        if (!goal) return ws;
        const updated = mutate(goal);
        let next: Workspace = {
          ...ws,
          goals: ws.goals.map((item) => (item.id === goalId ? updated : item)),
        };
        if (activity) next = withActivity(next, makeActivity(goalId, activity.type, activity.message));
        return next;
      });
    },
    [commitWorkspace],
  );

  const addTask = useCallback(
    (goalId: string, input: NewTaskInput) => {
      mutateGoal(goalId, (goal) => addTaskMutation(goal, input));
    },
    [mutateGoal],
  );

  const updateTask = useCallback(
    (goalId: string, taskId: string, patch: Partial<Task>) => {
      mutateGoal(goalId, (goal) => updateTaskMutation(goal, taskId, patch));
    },
    [mutateGoal],
  );

  const toggleTask = useCallback(
    (goalId: string, taskId: string) => {
      const goal = workspaceRef.current.goals.find((item) => item.id === goalId);
      const task = goal?.tasks.find((item) => item.id === taskId);
      mutateGoal(
        goalId,
        (current) => toggleTaskMutation(current, taskId).goal,
        task
          ? {
              type: task.completed ? 'task-reopened' : 'task-completed',
              message: task.completed ? `Reopened “${task.title}”` : `Completed “${task.title}”`,
            }
          : undefined,
      );
    },
    [mutateGoal],
  );

  const deleteTask = useCallback(
    (goalId: string, taskId: string) => {
      mutateGoal(goalId, (goal) => deleteTaskMutation(goal, taskId));
    },
    [mutateGoal],
  );

  const addMilestone = useCallback(
    (goalId: string, input: NewMilestoneInput) => {
      mutateGoal(goalId, (goal) => addMilestoneMutation(goal, input));
    },
    [mutateGoal],
  );

  const updateMilestone = useCallback(
    (goalId: string, milestoneId: string, patch: Partial<Milestone>) => {
      mutateGoal(goalId, (goal) => updateMilestoneMutation(goal, milestoneId, patch));
    },
    [mutateGoal],
  );

  const toggleMilestone = useCallback(
    (goalId: string, milestoneId: string) => {
      const goal = workspaceRef.current.goals.find((item) => item.id === goalId);
      const milestone = goal?.milestones.find((item) => item.id === milestoneId);
      mutateGoal(
        goalId,
        (current) => updateMilestoneMutation(current, milestoneId, { completed: !milestone?.completed }),
        milestone
          ? {
              type: 'milestone-completed',
              message: milestone.completed ? `Reopened milestone “${milestone.title}”` : `Completed milestone “${milestone.title}”`,
            }
          : undefined,
      );
    },
    [mutateGoal],
  );

  const deleteMilestone = useCallback(
    (goalId: string, milestoneId: string) => {
      mutateGoal(goalId, (goal) => deleteMilestoneMutation(goal, milestoneId));
    },
    [mutateGoal],
  );

  const addCategory = useCallback(
    (name: string, color: string, icon: CategoryIcon): Category | null => {
      const trimmed = name.trim();
      if (!trimmed) return null;
      const duplicate = workspaceRef.current.categories.find(
        (category) => !category.deletedAt && category.name.toLowerCase() === trimmed.toLowerCase(),
      );
      if (duplicate) {
        toast('A category with that name already exists.', 'error');
        return null;
      }
      const now = isoNow();
      const category: Category = {
        id: createId(),
        name: trimmed.slice(0, 60),
        color,
        icon,
        createdAt: now,
        updatedAt: now,
        deletedAt: null,
      };
      commitWorkspace((ws) => ({ ...ws, categories: [...ws.categories, category] }));
      return category;
    },
    [commitWorkspace, toast],
  );

  const updateCategory = useCallback(
    (id: string, patch: Partial<Pick<Category, 'name' | 'color' | 'icon'>>) => {
      commitWorkspace((ws) => ({
        ...ws,
        categories: ws.categories.map((category) =>
          category.id === id
            ? {
                ...category,
                ...patch,
                name: patch.name !== undefined ? patch.name.trim().slice(0, 60) : category.name,
                updatedAt: isoNow(),
              }
            : category,
        ),
      }));
    },
    [commitWorkspace],
  );

  const deleteCategory = useCallback(
    (id: string) => {
      const now = isoNow();
      commitWorkspace((ws) => ({
        ...ws,
        categories: ws.categories.map((category) =>
          category.id === id ? { ...category, deletedAt: now, updatedAt: now } : category,
        ),
        goals: ws.goals.map((goal) =>
          goal.categoryId === id ? { ...goal, categoryId: null, updatedAt: now } : goal,
        ),
      }));
      toast('Category removed. Its goals were kept.', 'success');
    },
    [commitWorkspace, toast],
  );

  const updateSettings = useCallback((patch: Partial<AppSettings>) => {
    setSettings((current) => {
      const next = { ...current, ...patch, ai: patch.ai ? { ...current.ai, ...patch.ai } : current.ai };
      settingsRef.current = next;
      return next;
    });
  }, []);

  const finishOnboarding = useCallback(() => {
    setSettings((current) => {
      const next = { ...current, onboardingComplete: true };
      settingsRef.current = next;
      return next;
    });
  }, []);

  const uploadAttachment = useCallback(
    async (goalId: string, file: File): Promise<boolean> => {
      if (file.size > 15 * 1024 * 1024) {
        toast('Attachments must be 15 MB or smaller.', 'error');
        return false;
      }
      if (file.size === 0) {
        toast('That file is empty.', 'error');
        return false;
      }
      const meta: AttachmentMeta = {
        id: createId(),
        goalId,
        name: file.name.slice(0, 180),
        mimeType: file.type || 'application/octet-stream',
        size: file.size,
        createdAt: isoNow(),
      };
      try {
        await addLocalAttachment(meta, file);
        setAttachments((current) => [...current, meta]);
        return true;
      } catch (error) {
        toast(error instanceof Error ? error.message : 'Attachment could not be stored.', 'error');
        return false;
      }
    },
    [toast],
  );

  const removeAttachment = useCallback(
    async (id: string) => {
      await deleteLocalAttachment(id);
      setAttachments((current) => current.filter((attachment) => attachment.id !== id));
    },
    [],
  );

  const exportGoalBackup = useCallback(async () => {
    try {
      const serializedAttachments: { meta: AttachmentMeta; dataUrl: string }[] = [];
      let totalBytes = 0;
      for (const meta of attachments) {
        const blob = await getAttachmentBlob(meta.id);
        if (!blob) continue;
        totalBytes += blob.size;
        if (totalBytes > 150 * 1024 * 1024) {
          toast('The backup is larger than 150 MB. Export cancelled to protect browser memory.', 'error');
          return;
        }
        serializedAttachments.push({ meta, dataUrl: await blobToDataUrl(blob) });
      }
      const payload = {
        app: 'momentum',
        version: 2,
        exportedAt: isoNow(),
        workspace,
        settings: { ...settings, ai: { ...settings.ai, apiKeyCipher: null, hasKey: false } },
        attachments: serializedAttachments,
      };
      const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `momentum-backup-${new Date().toISOString().slice(0, 10)}.json`;
      link.click();
      URL.revokeObjectURL(url);
      toast(
        `Backup downloaded with ${serializedAttachments.length} ${serializedAttachments.length === 1 ? 'attachment' : 'attachments'}.`,
        'success',
      );
    } catch (error) {
      toast(error instanceof Error ? error.message : 'The backup could not be created.', 'error');
    }
  }, [attachments, settings, toast, workspace]);

  const importGoalBackup = useCallback(
    async (
      importedWorkspace: Workspace,
      importedSettings: AppSettings,
      attachmentData: { meta: AttachmentMeta; dataUrl: string }[] = [],
    ) => {
      await clearLocalAttachments();
      const restored: AttachmentMeta[] = [];
      for (const item of attachmentData) {
        try {
          if (item.dataUrl.length > 22 * 1024 * 1024) continue;
          const blob = dataUrlToBlob(item.dataUrl);
          if (blob.size > 15 * 1024 * 1024) continue;
          const meta: AttachmentMeta = {
            ...item.meta,
            size: blob.size,
          };
          await addLocalAttachment(meta, blob);
          restored.push(meta);
        } catch {
          // Skip a corrupt attachment rather than losing the workspace import.
        }
      }
      workspaceRef.current = importedWorkspace;
      setWorkspace(importedWorkspace);
      setSettings(importedSettings);
      setAttachments(restored);
      await Promise.all([saveWorkspace(importedWorkspace), saveSettings(importedSettings)]);
      toast(`Backup restored with ${restored.length} ${restored.length === 1 ? 'attachment' : 'attachments'}.`, 'success');
    },
    [toast],
  );

  const replaceWorkspace = useCallback(async (next: Workspace) => {
    const stamped = { ...next, updatedAt: isoNow() };
    workspaceRef.current = stamped;
    setWorkspace(stamped);
    await saveWorkspace(stamped);
  }, []);

  const eraseEverything = useCallback(async () => {
    await eraseAllLocalData();
    const fresh = emptyWorkspace();
    workspaceRef.current = fresh;
    setWorkspace(fresh);
    const freshSettings = defaultSettings();
    setSettings(freshSettings);
    setAttachments([]);
    toast('All local data erased.', 'success');
  }, [toast]);

  const goals = workspace.goals;
  const categories = workspace.categories.filter((category) => !category.deletedAt);
  const value = useMemo<MomentumContextValue>(
    () => ({
      workspace,
      goals,
      categories,
      activities: workspace.activities,
      attachments,
      settings,
      loaded,
      storageError,
      toasts,
      createGoal: createGoalAction,
      createGoalFromPlan,
      updateGoal,
      setGoalCompleted,
      archiveGoal,
      restoreArchivedGoal,
      trashGoal,
      restoreTrashedGoal,
      permanentlyDeleteGoal,
      emptyTrash,
      purgeExpiredGoals,
      addTask,
      updateTask,
      toggleTask,
      deleteTask,
      addMilestone,
      updateMilestone,
      toggleMilestone,
      deleteMilestone,
      addCategory,
      updateCategory,
      deleteCategory,
      updateSettings,
      finishOnboarding,
      uploadAttachment,
      removeAttachment,
      exportGoalBackup,
      importGoalBackup,
      replaceWorkspace,
      eraseEverything,
      toast,
      dismissToast,
    }),
    [
      workspace,
      goals,
      categories,
      attachments,
      settings,
      loaded,
      storageError,
      toasts,
      createGoalAction,
      createGoalFromPlan,
      updateGoal,
      setGoalCompleted,
      archiveGoal,
      restoreArchivedGoal,
      trashGoal,
      restoreTrashedGoal,
      permanentlyDeleteGoal,
      emptyTrash,
      purgeExpiredGoals,
      addTask,
      updateTask,
      toggleTask,
      deleteTask,
      addMilestone,
      updateMilestone,
      toggleMilestone,
      deleteMilestone,
      addCategory,
      updateCategory,
      deleteCategory,
      updateSettings,
      finishOnboarding,
      uploadAttachment,
      removeAttachment,
      exportGoalBackup,
      importGoalBackup,
      replaceWorkspace,
      eraseEverything,
      toast,
      dismissToast,
    ],
  );

  return <MomentumContext.Provider value={value}>{children}</MomentumContext.Provider>;
}

export function useMomentum(): MomentumContextValue {
  const context = useContext(MomentumContext);
  if (!context) throw new Error('useMomentum must be used inside MomentumProvider');
  return context;
}

export function useGoal(goalId?: string): Goal | null {
  const { goals } = useMomentum();
  return goals.find((goal) => goal.id === goalId) ?? null;
}
