export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface ApiOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
}

const BUILD_TIME_API_BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, '') ?? '';

/**
 * Runtime service address override for native shells (Android/iOS), where a
 * relative /api inside the WebView would never reach the local service.
 * Web/PWA users leave it empty and get same-origin API routing.
 */
const API_URL_STORAGE_KEY = 'momentum.api-url';

export function resolvedApiBase(): string {
  if (BUILD_TIME_API_BASE) return BUILD_TIME_API_BASE;
  try {
    const configured = window.localStorage.getItem(API_URL_STORAGE_KEY)?.trim().replace(/\/+$/, '') ?? '';
    return /^https?:\/\//i.test(configured) ? configured : '';
  } catch {
    return '';
  }
}

export function setApiUrlOverride(value: string): void {
  try {
    const trimmed = value.trim().replace(/\/+$/, '');
    if (trimmed) window.localStorage.setItem(API_URL_STORAGE_KEY, trimmed);
    else window.localStorage.removeItem(API_URL_STORAGE_KEY);
  } catch {
    // Storage unavailable — the field simply has no effect.
  }
}

/** Supports same-origin web/PWA and explicitly configured native shells. */
export function apiUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  return `${resolvedApiBase()}${path}`;
}

export async function apiRequest<T>(path: string, options: ApiOptions = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(apiUrl(path), {
      method: options.method ?? 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError('MOMENTUM services are unreachable. Local features still work.', 0);
  }
  let payload: unknown = null;
  const text = await response.text();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }
  if (!response.ok) {
    const message =
      payload && typeof payload === 'object' && 'error' in payload && typeof payload.error === 'string'
        ? payload.error
        : `Request failed (${response.status}).`;
    throw new ApiError(message, response.status);
  }
  return payload as T;
}

export async function checkApiHealth(): Promise<boolean> {
  try {
    await apiRequest<{ status: string }>('/api/health');
    return true;
  } catch {
    return false;
  }
}
