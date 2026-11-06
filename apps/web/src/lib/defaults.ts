import type { AIConfig, AIProviderId, AppSettings, Workspace } from '../state/types';
import { createId } from './id';
import { isoNow } from './dates';

export const PROVIDER_DEFAULTS: Record<AIProviderId, { model: string; baseUrl: string; label: string }> = {
  deepseek: { model: 'deepseek-chat', baseUrl: 'https://api.deepseek.com/v1', label: 'DeepSeek' },
  openai: { model: 'gpt-4o-mini', baseUrl: 'https://api.openai.com/v1', label: 'OpenAI' },
  gemini: { model: 'gemini-2.0-flash', baseUrl: 'https://generativelanguage.googleapis.com/v1beta', label: 'Google Gemini' },
  groq: { model: 'openai/gpt-oss-120b', baseUrl: 'https://api.groq.com/openai/v1', label: 'Groq' },
  ollama: { model: 'llama3.2', baseUrl: 'http://127.0.0.1:11434', label: 'Ollama (local)' },
  custom: { model: '', baseUrl: '', label: 'OpenAI-compatible' },
};

const PROVIDER_IDS: AIProviderId[] = ['deepseek', 'openai', 'gemini', 'groq', 'ollama', 'custom'];
const requestedLock = import.meta.env.VITE_AI_PROVIDER_LOCK as AIProviderId | undefined;

/** Optional build-time lock. The current production build locks provider selection to Groq. */
export const AI_PROVIDER_LOCK: AIProviderId | null =
  requestedLock && PROVIDER_IDS.includes(requestedLock) ? requestedLock : null;

export const DEFAULT_AI_PROVIDER: AIProviderId = AI_PROVIDER_LOCK ?? 'groq';

export function defaultAIConfig(provider: AIProviderId = DEFAULT_AI_PROVIDER): AIConfig {
  return {
    provider,
    model: PROVIDER_DEFAULTS[provider].model,
    baseUrl: PROVIDER_DEFAULTS[provider].baseUrl,
    apiKeyCipher: null,
    hasKey: false,
    consentAcceptedAt: null,
    lastTestAt: null,
    lastTestOk: null,
  };
}

export function defaultSettings(): AppSettings {
  return {
    schemaVersion: 1,
    themeMode: 'dark',
    accent: '#3b82f6',
    accentSecondary: '#8b5cf6',
    density: 'comfortable',
    reduceMotion: false,
    weekStartsOn: 1,
    displayName: '',
    onboardingComplete: false,
    ai: defaultAIConfig(),
    syncEnabled: false,
    lastSyncAt: null,
  };
}

export function emptyWorkspace(): Workspace {
  const now = isoNow();
  return {
    schemaVersion: 1,
    installationId: createId(),
    goals: [],
    categories: [],
    activities: [],
    createdAt: now,
    updatedAt: now,
  };
}

export const ACCENT_PRESETS = [
  { name: 'Signal Blue', value: '#3b82f6' },
  { name: 'Ion Cyan', value: '#22d3ee' },
  { name: 'Volt Lime', value: '#a3e635' },
  { name: 'Pulse Magenta', value: '#f472b6' },
  { name: 'Ember Orange', value: '#fb923c' },
  { name: 'Ultraviolet', value: '#8b5cf6' },
  { name: 'Forest', value: '#34d399' },
  { name: 'Crimson', value: '#f87171' },
] as const;
