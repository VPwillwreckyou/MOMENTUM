import type { AppSettings } from '../state/types';

function hexToRgb(hex: string): [number, number, number] {
  const normalized = hex.replace('#', '');
  return [
    Number.parseInt(normalized.slice(0, 2), 16),
    Number.parseInt(normalized.slice(2, 4), 16),
    Number.parseInt(normalized.slice(4, 6), 16),
  ];
}

function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((channel) => {
    const value = channel / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function resolveTheme(mode: AppSettings['themeMode']): 'dark' | 'light' {
  if (mode !== 'system') return mode;
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function applyAppearance(settings: AppSettings): void {
  const root = document.documentElement;
  const theme = resolveTheme(settings.themeMode);
  root.dataset.theme = theme;
  root.dataset.density = settings.density;
  root.dataset.reduceMotion = settings.reduceMotion ? 'true' : 'false';
  root.style.setProperty('--accent', settings.accent);
  const [r, g, b] = hexToRgb(settings.accent);
  root.style.setProperty('--accent-rgb', `${r} ${g} ${b}`);
  root.style.setProperty('--accent-contrast', luminance(settings.accent) > 0.55 ? '#071018' : '#ffffff');
  const secondary = /^#[0-9a-fA-F]{6}$/.test(settings.accentSecondary ?? '') ? settings.accentSecondary : '#7c3aed';
  root.style.setProperty('--accent-2', secondary);
  const [r2, g2, b2] = hexToRgb(secondary);
  root.style.setProperty('--accent-2-rgb', `${r2} ${g2} ${b2}`);
  root.style.setProperty('--accent-2-contrast', luminance(secondary) > 0.55 ? '#071018' : '#ffffff');
  root.style.setProperty('--accent-linear', `linear-gradient(135deg, ${settings.accent}, ${secondary})`);
  const meta = document.querySelector('meta[name="theme-color"]');
  meta?.setAttribute('content', theme === 'dark' ? '#070a10' : '#f4f6fa');
}

export function watchSystemTheme(callback: () => void): () => void {
  const media = window.matchMedia?.('(prefers-color-scheme: light)');
  if (!media) return () => undefined;
  media.addEventListener('change', callback);
  return () => media.removeEventListener('change', callback);
}
