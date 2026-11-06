import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Apple,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Cloud,
  Github,
  LoaderCircle,
  LockKeyhole,
  MessageCircle,
  ShieldCheck,
  Trash2,
  UserRound,
} from 'lucide-react';
import { useAuth } from '../state/AuthContext';
import { useMomentum } from '../state/MomentumContext';
import { LogoMark } from '../components/ui/Logo';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Fields';
import { Modal } from '../components/ui/Modal';
import { validateEmail, validatePassword } from '../lib/validation';
import { ApiError, apiUrl } from '../lib/api';
import { SyncPanel } from '../components/account/SyncPanel';
import { formatDate } from '../lib/dates';

function GoogleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.92h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.32 2.98-7.41Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.43l-3.24-2.54c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.6-4.12H3.06v2.61A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.4 13.87A6.02 6.02 0 0 1 6.08 12c0-.65.11-1.28.32-1.87V7.52H3.06A10 10 0 0 0 2 12c0 1.61.38 3.14 1.06 4.48l3.34-2.61Z" />
      <path fill="#EA4335" d="M12 5.99c1.47 0 2.79.51 3.82 1.5l2.88-2.87A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.94 5.52l3.34 2.61C7.2 7.77 9.4 5.99 12 5.99Z" />
    </svg>
  );
}

const OAUTH_ICONS: Record<string, React.ReactNode> = {
  google: <GoogleIcon />,
  apple: <Apple size={16} />,
  github: <Github size={16} />,
  discord: <MessageCircle size={16} />,
};

export function AuthPage() {
  const [params, setParams] = useSearchParams();
  const { user, providers, loading, apiReachable, login, register, logout, deleteAccount, refresh } = useAuth();
  const { settings, toast, exportGoalBackup } = useMomentum();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [oauthHint, setOauthHint] = useState<string | null>(null);

  const handleOAuthClick = (providerId: string, label: string, configured: boolean) => {
    if (configured) {
      setOauthHint(null);
      return;
    }
    const envKeys: Record<string, [string, string]> = {
      google: ['GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET'],
      github: ['GITHUB_CLIENT_ID', 'GITHUB_CLIENT_SECRET'],
      discord: ['DISCORD_CLIENT_ID', 'DISCORD_CLIENT_SECRET'],
      apple: ['APPLE_CLIENT_ID', 'APPLE_CLIENT_SECRET'],
    };
    const [idKey, secretKey] = envKeys[providerId] ?? [`${providerId.toUpperCase()}_CLIENT_ID`, `${providerId.toUpperCase()}_CLIENT_SECRET`];
    setOauthHint(
      `${label} sign-in is wired up and ready on the server — it just needs the ${label} app credentials in the server's .env file: set ${idKey} and ${secretKey}, then restart the service. See TUTORIAL.md → "Google / GitHub / Discord / Apple sign-in (OAuth)".`,
    );
  };

  useEffect(() => {
    const status = params.get('auth');
    if (status === 'success') {
      void refresh();
      toast('Signed in.', 'success');
      const next = new URLSearchParams(params);
      next.delete('auth');
      next.delete('provider');
      setParams(next, { replace: true });
    } else if (status === 'error') {
      setServerError(params.get('message') ?? 'Sign-in failed. Try again.');
      const next = new URLSearchParams(params);
      next.delete('auth');
      next.delete('message');
      setParams(next, { replace: true });
    }
  }, [params, refresh, setParams, toast]);

  const submit = async () => {
    const errors: Record<string, string> = {};
    if (mode === 'register' && name.trim().length < 2) errors.name = 'Enter your name.';
    if (!validateEmail(email)) errors.email = 'Enter a valid email address.';
    const passwordError = validatePassword(password);
    if (passwordError) errors.password = passwordError;
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setBusy(true);
    setServerError(null);
    try {
      if (mode === 'login') await login(email.trim(), password);
      else await register(name.trim(), email.trim(), password);
      toast(mode === 'login' ? 'Welcome back.' : 'Account created.', 'success');
      setPassword('');
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : 'Sign-in failed. Try again.');
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="page auth-page">
        <div className="auth-loading">
          <LoaderCircle size={20} className="spin" />
          Checking your account…
        </div>
      </div>
    );
  }

  if (user) {
    return (
      <div className="page auth-page">
        <div className="account-page">
          <div className="account-header">
            <Link to="/" className="text-link text-link--muted">
              <ArrowLeft size={14} /> Back to workspace
            </Link>
            <span className="eyebrow">Account</span>
            <h1>Your MOMENTUM identity.</h1>
            <p>Authentication and goal storage are separate. Your workspace stays local until you explicitly enable encrypted sync.</p>
          </div>

          <div className="account-grid">
            <section className="account-card">
              <div className="account-identity">
                <span className="account-identity__avatar">{user.name.charAt(0).toUpperCase()}</span>
                <div>
                  <span className="eyebrow">Signed in</span>
                  <h2>{user.name}</h2>
                  <p>{user.email ?? 'Email hidden by provider'}</p>
                </div>
              </div>
              <dl className="detail-list">
                <div><dt>Provider</dt><dd>{user.provider === 'email' ? 'Email & password' : user.provider}</dd></div>
                <div><dt>Member since</dt><dd>{formatDate(user.createdAt.slice(0, 10), 'medium')}</dd></div>
                <div><dt>Workspace</dt><dd>Local to this device</dd></div>
                <div><dt>Sync</dt><dd>{settings.syncEnabled ? 'Enabled · explicit' : 'Off'}</dd></div>
              </dl>
              <div className="account-card__actions">
                <Button
                  variant="secondary"
                  onClick={() => {
                    void logout().then(() => {
                      setMode('login');
                      setEmail('');
                      setPassword('');
                      setName('');
                      setServerError(null);
                    });
                  }}
                >
                  Sign out
                </Button>
                <Button variant="ghost" onClick={exportGoalBackup}>
                  Export local backup
                </Button>
              </div>
            </section>

            <section className="account-card account-card--sync">
              <div className="account-card__icon"><Cloud size={19} /></div>
              <span className="eyebrow">Cloud boundary</span>
              <h2>You control what leaves this device.</h2>
              <p>
                The encrypted sync backup is optional. It uses a passphrase only you know, and the server stores ciphertext.
              </p>
              <a href="#sync" className="text-link">
                Configure encrypted sync <ArrowRight size={14} />
              </a>
            </section>

            <section className="account-card account-card--danger">
              <div className="account-card__icon"><Trash2 size={19} /></div>
              <span className="eyebrow">Danger zone</span>
              <h2>Delete account</h2>
              <p>
                Deletes your sign-in account and encrypted cloud backup. Your local workspace is separate and will not be
                erased by this action.
              </p>
              <Button variant="danger" onClick={() => setDeleteOpen(true)}>
                Delete account
              </Button>
            </section>
          </div>

          <SyncPanel />

          <div className="auth-legal">
            <ShieldCheck size={14} />
            <span>MOMENTUM never sells or mines personal goal data. There is no advertising tracker.</span>
          </div>
        </div>

        <Modal
          open={deleteOpen}
          onClose={() => setDeleteOpen(false)}
          title="Delete your account?"
          size="sm"
          footer={
            <>
              <Button variant="ghost" onClick={() => setDeleteOpen(false)} disabled={deleteBusy}>
                Keep account
              </Button>
              <Button
                variant="danger"
                loading={deleteBusy}
                onClick={() => {
                  void (async () => {
                    setDeleteBusy(true);
                    try {
                      await deleteAccount(user.hasPassword ? deletePassword : undefined);
                      toast('Account deleted. Local goals remain on this device.', 'success');
                      setDeleteOpen(false);
                      setDeletePassword('');
                    } catch (error) {
                      toast(error instanceof Error ? error.message : 'Account deletion failed.', 'error');
                    } finally {
                      setDeleteBusy(false);
                    }
                  })();
                }}
              >
                Delete permanently
              </Button>
            </>
          }
        >
          <div className="form-stack">
            <p className="settings-copy">
              Your account and encrypted cloud backup will be removed. This does not erase goals in this browser. This
              action cannot be undone.
            </p>
            {user.hasPassword ? (
              <Input
                label="Confirm your password"
                type="password"
                value={deletePassword}
                onChange={(event) => setDeletePassword(event.target.value)}
                autoComplete="current-password"
              />
            ) : null}
          </div>
        </Modal>
      </div>
    );
  }

  return (
    <div className="page auth-page">
      <div className="auth-shell">
        <section className="auth-showcase">
          <Link to="/" className="auth-showcase__back">
            <ArrowLeft size={14} /> Workspace
          </Link>
          <div className="auth-showcase__brand">
            <LogoMark size={44} />
            <span>MOMENTUM</span>
          </div>
          <h1>Your goals should not need a server to exist.</h1>
          <p>
            MOMENTUM is local-first. An account is optional, and signing in does not upload your private workspace.
          </p>
          <div className="auth-showcase__points">
            <div>
              <CheckCircle2 size={17} />
              <span><strong>Local by default.</strong> Your workspace lives in this browser.</span>
            </div>
            <div>
              <CheckCircle2 size={17} />
              <span><strong>Sync is a choice.</strong> Only encrypted, explicit backups leave the device.</span>
            </div>
            <div>
              <CheckCircle2 size={17} />
              <span><strong>Providers are yours.</strong> Use your own AI keys — they are encrypted at rest.</span>
            </div>
          </div>
          <div className="auth-showcase__quote">
            <span className="quote-mark">“</span>
            <p>Ambition becomes momentum when the next move is clear.</p>
            <span className="quote-rule" />
          </div>
        </section>

        <section className="auth-form-wrap">
          <div className="auth-form">
            <div className="auth-form__header">
              <span className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Start locally'}</span>
              <h2>{mode === 'login' ? 'Sign in to MOMENTUM' : 'Create an account'}</h2>
              <p>
                {mode === 'login'
                  ? 'Accounts are for identity and optional encrypted sync — never required for local goals.'
                  : 'Your goals remain local. An account only enables secure backup across devices.'}
              </p>
            </div>

            {apiReachable === false ? (
              <div className="inline-warning">
                <span>The MOMENTUM account service is offline. Local goals and AI configuration still work.</span>
              </div>
            ) : null}
            {serverError ? (
              <div className="form-error-banner" role="alert">
                <span>{serverError}</span>
              </div>
            ) : null}
            {oauthHint ? (
              <div className="form-info-banner" role="status">
                <span>{oauthHint}</span>
                <button type="button" className="form-info-banner__close" onClick={() => setOauthHint(null)} aria-label="Dismiss">
                  ×
                </button>
              </div>
            ) : null}

            <div className="oauth-grid">
              {providers.map((provider) => (
                <a
                  key={provider.id}
                  href={provider.configured ? apiUrl(`/api/auth/oauth/${provider.id}`) : undefined}
                  className={`oauth-button${provider.configured ? '' : ' oauth-button--disabled'}`}
                  aria-disabled={!provider.configured}
                  title={provider.configured ? `Continue with ${provider.label}` : `${provider.label} is not configured on this server`}
                  onClick={(event) => {
                    if (!provider.configured) {
                      event.preventDefault();
                      handleOAuthClick(provider.id, provider.label, provider.configured);
                      return;
                    }
                    handleOAuthClick(provider.id, provider.label, true);
                  }}
                >
                  {OAUTH_ICONS[provider.id] ?? <UserRound size={16} />}
                  <span>{provider.label}</span>
                  {!provider.configured ? <span className="oauth-button__soon">Not configured</span> : null}
                </a>
              ))}
            </div>

            <div className="auth-divider"><span>or use email</span></div>

            <div className="auth-mode-switch">
              <button
                type="button"
                className={mode === 'login' ? 'is-active' : ''}
                onClick={() => setMode('login')}
              >
                Sign in
              </button>
              <button
                type="button"
                className={mode === 'register' ? 'is-active' : ''}
                onClick={() => setMode('register')}
              >
                Create account
              </button>
            </div>

            <form
              className="form-stack"
              onSubmit={(event) => {
                event.preventDefault();
                void submit();
              }}
            >
              {mode === 'register' ? (
                <Input
                  label="Name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  error={fieldErrors.name}
                  autoComplete="name"
                  placeholder="How should we address you?"
                />
              ) : null}
              <Input
                label="Email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                error={fieldErrors.email}
                autoComplete="email"
                placeholder="you@example.com"
              />
              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                error={fieldErrors.password}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                placeholder={mode === 'register' ? 'At least 8 characters with a number' : 'Your password'}
                hint={mode === 'register' ? 'Hashed with PBKDF2-HMAC-SHA256. Never stored in plain text.' : undefined}
              />
              <Button variant="primary" size="lg" type="submit" loading={busy} fullWidth>
                {mode === 'login' ? 'Sign in' : 'Create account'}
              </Button>
            </form>

            <div className="auth-local-note">
              <LockKeyhole size={14} />
              <span>
                No account needed. <Link to="/">Continue to your local workspace</Link>
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
