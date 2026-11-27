import type { AppSettings, Workspace } from '../state/types';
import { createId } from './id';
import { isoNow } from './dates';

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
