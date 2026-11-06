export type Priority = 'low' | 'medium' | 'high' | 'critical';
export type GoalStatus = 'active' | 'completed';
export type GoalKind = 'one-time' | 'recurring';
export type RecurrenceFrequency = 'daily' | 'weekly' | 'monthly';

export interface Recurrence {
  frequency: RecurrenceFrequency;
  interval: number;
}

export interface Milestone {
  id: string;
  title: string;
  description?: string;
  dueDate?: string | null;
  completed: boolean;
  completedAt?: string | null;
  order: number;
}

export interface Task {
  id: string;
  goalId: string;
  milestoneId?: string | null;
  parentTaskId?: string | null;
  title: string;
  notes?: string;
  completed: boolean;
  completedAt?: string | null;
  dueDate?: string | null;
  priority: Priority;
  order: number;
  createdAt: string;
  updatedAt: string;
}

export interface FinancePlan {
  enabled: boolean;
  currency: string;
  targetAmount: number;
  currentAmount: number;
  note?: string;
}

export interface Goal {
  id: string;
  title: string;
  description: string;
  notes: string;
  categoryId: string | null;
  status: GoalStatus;
  kind: GoalKind;
  recurrence?: Recurrence | null;
  priority: Priority;
  startDate?: string | null;
  deadline?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
  archivedAt?: string | null;
  deletedAt?: string | null;
  seriesId?: string | null;
  occurrence?: number;
  milestones: Milestone[];
  tasks: Task[];
  finance?: FinancePlan | null;
  aiGenerated?: boolean;
}

export type CategoryIcon =
  | 'target'
  | 'work'
  | 'health'
  | 'learning'
  | 'creative'
  | 'finance'
  | 'home'
  | 'relationships';

export interface Category {
  id: string;
  name: string;
  color: string;
  icon: CategoryIcon;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
}

export type ActivityType =
  | 'goal-created'
  | 'goal-edited'
  | 'goal-completed'
  | 'goal-archived'
  | 'goal-restored'
  | 'task-created'
  | 'task-completed'
  | 'task-reopened'
  | 'milestone-completed'
  | 'note-updated'
  | 'plan-imported';

export interface Activity {
  id: string;
  goalId: string;
  type: ActivityType;
  message: string;
  at: string;
}

export interface AttachmentMeta {
  id: string;
  goalId: string;
  name: string;
  mimeType: string;
  size: number;
  createdAt: string;
}

export type ThemeMode = 'dark' | 'light' | 'system';
export type Density = 'comfortable' | 'compact';

export type AIProviderId = 'deepseek' | 'openai' | 'gemini' | 'groq' | 'ollama' | 'custom';

export interface AIConfig {
  provider: AIProviderId;
  model: string;
  baseUrl: string;
  apiKeyCipher?: string | null;
  hasKey: boolean;
  consentAcceptedAt?: string | null;
  lastTestAt?: string | null;
  lastTestOk?: boolean | null;
}

export interface AppSettings {
  schemaVersion: 1;
  themeMode: ThemeMode;
  accent: string;
  density: Density;
  reduceMotion: boolean;
  weekStartsOn: 0 | 1;
  displayName: string;
  onboardingComplete: boolean;
  ai: AIConfig;
  syncEnabled: boolean;
  lastSyncAt?: string | null;
}

export interface Workspace {
  schemaVersion: 1;
  installationId: string;
  goals: Goal[];
  categories: Category[];
  activities: Activity[];
  createdAt: string;
  updatedAt: string;
}

export interface GoalDraft {
  title: string;
  description?: string;
  notes?: string;
  categoryId?: string | null;
  priority?: Priority;
  startDate?: string | null;
  deadline?: string | null;
  kind?: GoalKind;
  recurrence?: Recurrence | null;
  finance?: FinancePlan | null;
}

export interface AIPlanTask {
  title: string;
  notes?: string;
  dueDate?: string | null;
  priority?: Priority;
}

export interface AIPlanMilestone {
  title: string;
  description?: string;
  dueDate?: string | null;
  tasks: AIPlanTask[];
}

export interface AIPlan {
  title: string;
  description: string;
  suggestedDeadline?: string | null;
  milestones: AIPlanMilestone[];
  firstTasks: AIPlanTask[];
}

export interface PlannerAnswers {
  goal: string;
  situation: string;
  success: string;
  timeCommitment: string;
  targetDate: string;
  constraints: string;
}

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  provider: string;
  providers?: string[];
  hasPassword?: boolean;
  createdAt: string;
}

export interface OAuthProviderStatus {
  id: string;
  label: string;
  configured: boolean;
}

export type ToastTone = 'success' | 'error' | 'info';
export interface Toast {
  id: string;
  message: string;
  tone: ToastTone;
  actionLabel?: string;
  onAction?: () => void;
}
