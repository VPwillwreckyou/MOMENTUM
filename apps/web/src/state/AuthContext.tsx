import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { AuthUser, OAuthProviderStatus } from './types';
import { ApiError, apiRequest, clearNativeSession, saveNativeSession } from '../lib/api';
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';

interface AuthContextValue {
  user: AuthUser | null;
  providers: OAuthProviderStatus[];
  loading: boolean;
  apiReachable: boolean | null;
  nativeOAuthError: string | null;
  clearNativeOAuthError: () => void;
  refresh: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  deleteAccount: (password?: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [providers, setProviders] = useState<OAuthProviderStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [apiReachable, setApiReachable] = useState<boolean | null>(null);
  const [nativeOAuthError, setNativeOAuthError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [me, providerList] = await Promise.all([
        apiRequest<{ user: AuthUser }>('/api/auth/me').catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 401) return null;
          throw error;
        }),
        apiRequest<{ providers: OAuthProviderStatus[] }>('/api/auth/providers').catch(() => null),
      ]);
      setUser(me?.user ?? null);
      if (providerList) setProviders(providerList.providers);
      setApiReachable(true);
    } catch {
      setApiReachable(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    let active = true;
    const handledCallbacks = new Set<string>();
    const handleOAuthReturn = async (url: string) => {
      let callback: URL;
      try {
        callback = new URL(url);
      } catch {
        return;
      }
      if (callback.protocol !== 'com.momentum.app:' || callback.hostname !== 'oauth' || callback.pathname !== '/callback') return;
      const ticket = callback.searchParams.get('ticket');
      const error = callback.searchParams.get('error');
      const callbackKey = ticket ? `ticket:${ticket}` : `error:${error ?? ''}`;
      if (handledCallbacks.has(callbackKey)) return;
      handledCallbacks.add(callbackKey);
      try {
        if (ticket) {
          const result = await apiRequest<{ user: AuthUser; sessionToken: string }>('/api/auth/oauth/exchange', {
            method: 'POST',
            body: { ticket },
          });
          await saveNativeSession(result.sessionToken);
          if (active) {
            setUser(result.user);
            setApiReachable(true);
            setNativeOAuthError(null);
          }
        } else {
          if (active) setNativeOAuthError(error || 'Sign-in failed. Please try again.');
        }
      } catch (exchangeError) {
        if (active) setNativeOAuthError(exchangeError instanceof Error ? exchangeError.message : 'Sign-in could not be completed. Please try again.');
      } finally {
        await Browser.close().catch(() => undefined);
      }
    };
    let removeListener: (() => void) | undefined;
    void App.addListener('appUrlOpen', ({ url }) => { void handleOAuthReturn(url); }).then((listener) => {
      removeListener = () => { void listener.remove(); };
    });
    void App.getLaunchUrl().then((launch) => {
      if (active && launch?.url) void handleOAuthReturn(launch.url);
    }).catch(() => undefined);
    return () => {
      active = false;
      removeListener?.();
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiRequest<{ user: AuthUser }>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    setUser(result.user);
    setApiReachable(true);
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    const result = await apiRequest<{ user: AuthUser }>('/api/auth/register', {
      method: 'POST',
      body: { name, email, password },
    });
    setUser(result.user);
    setApiReachable(true);
  }, []);

  const logout = useCallback(async () => {
    await apiRequest('/api/auth/logout', { method: 'POST' });
    await clearNativeSession();
    setUser(null);
  }, []);

  const deleteAccount = useCallback(async (password?: string) => {
    await apiRequest('/api/account', { method: 'DELETE', body: password ? { password } : {} });
    await clearNativeSession();
    setUser(null);
  }, []);

  const clearNativeOAuthError = useCallback(() => setNativeOAuthError(null), []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, providers, loading, apiReachable, nativeOAuthError, clearNativeOAuthError, refresh, login, register, logout, deleteAccount }),
    [user, providers, loading, apiReachable, nativeOAuthError, clearNativeOAuthError, refresh, login, register, logout, deleteAccount],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
