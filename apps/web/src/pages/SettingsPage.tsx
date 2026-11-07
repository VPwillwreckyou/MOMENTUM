import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowUpRight,
  Check,
  ChevronRight,
  CircleHelp,
  Cloud,
  Database,
  Download,
  HardDrive,
  KeyRound,
  Laptop,
  LockKeyhole,
  Monitor,
  Moon,
  Palette,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Sun,
  Trash2,
  Upload,
  UserRound,
  WandSparkles,
} from 'lucide-react';
import type { AIProviderId, AppSettings, AttachmentMeta, ThemeMode, Workspace } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { useAuth } from '../state/AuthContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Input, SegmentedControl, Toggle } from '../components/ui/Fields';
import { ConfirmDialog, Modal } from '../components/ui/Modal';
import { ACCENT_PRESETS, AI_PROVIDER_LOCK, PROVIDER_DEFAULTS } from '../lib/defaults';
import { normalizeSettings, normalizeWorkspace } from '../lib/repository';
import { resolvedApiBase, setApiUrlOverride } from '../lib/api';
import { encryptSecret } from '../lib/vault';
import { validateHex } from '../lib/validation';
import { testAIConnection } from '../lib/ai';
import { formatDate, relativeTime } from '../lib/dates';
import { SyncPanel } from '../components/account/SyncPanel';
import { MODIFIER_LABEL } from '../lib/platform';

type SectionId = 'appearance' | 'ai' | 'account' | 'data' | 'about';

const SECTIONS: { id: SectionId; label: string; icon: typeof Palette }[] = [
  { id: 'appearance', label: 'Appearance', icon: Palette },
  { id: 'ai', label: 'AI assistant', icon: Sparkles },
  { id: 'account', label: 'Account & sync', icon: UserRound },
  { id: 'data', label: 'Data & privacy', icon: Database },
  { id: 'about', label: 'About', icon: CircleHelp },
];

const ALL_PROVIDERS: AIProviderId[] = ['deepseek', 'openai', 'gemini', 'groq', 'ollama', 'custom'];
const PROVIDER_ORDER: AIProviderId[] = AI_PROVIDER_LOCK ? [AI_PROVIDER_LOCK] : ALL_PROVIDERS;

function formatBytes(bytes?: number): string {
  if (!bytes || bytes <= 0) return '0 MB';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function SettingsPage() {
  const navigate = useNavigate();
  const { settings, workspace, attachments, updateSettings, exportGoalBackup, importGoalBackup, eraseEverything, toast } = useMomentum();
  const { user, apiReachable } = useAuth();

  const [section, setSection] = useState<SectionId>('appearance');
  const [customAccent, setCustomAccent] = useState(settings.accent);
  const [accentError, setAccentError] = useState<string | null>(null);
  const [aiProvider, setAiProvider] = useState<AIProviderId>(settings.ai.provider);
  const [aiModel, setAiModel] = useState(settings.ai.model);
  const [aiBaseUrl, setAiBaseUrl] = useState(settings.ai.baseUrl);
  const [aiKey, setAiKey] = useState('');
  const [aiBusy, setAiBusy] = useState(false);
  const [aiTest, setAiTest] = useState<string | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiShowKey, setAiShowKey] = useState(false);
  const [storageUsage, setStorageUsage] = useState<{ usage?: number; quota?: number; persisted?: boolean } | null>(null);
  const [apiUrlDraft, setApiUrlDraft] = useState(() => resolvedApiBase());
  const [eraseOpen, setEraseOpen] = useState(false);
  const [eraseText, setEraseText] = useState('');
  const [eraseBusy, setEraseBusy] = useState(false);
  const [importPending, setImportPending] = useState<{
    workspace: Workspace;
    settings: AppSettings;
    attachments: { meta: AttachmentMeta; dataUrl: string }[];
  } | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const importRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const hash = window.location.hash.replace('#', '') as SectionId;
    if (SECTIONS.some((item) => item.id === hash)) {
      setSection(hash);
      window.setTimeout(() => document.getElementById(hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 80);
    }
  }, []);

  useEffect(() => {
    if (!navigator.storage?.estimate) return;
    void (async () => {
      const estimate = await navigator.storage.estimate();
      const persisted = navigator.storage.persisted ? await navigator.storage.persisted() : false;
      setStorageUsage({ usage: estimate.usage, quota: estimate.quota, persisted });
    })();
  }, [workspace.updatedAt, attachments.length]);

  const setTheme = (themeMode: ThemeMode) => updateSettings({ themeMode });
  const applyAccent = (value: string) => {
    const normalized = validateHex(value);
    if (!normalized) {
      setAccentError('Enter a six-digit hex color, like #3b82f6.');
      return;
    }
    setAccentError(null);
    setCustomAccent(normalized);
    updateSettings({ accent: normalized });
  };

  const changeProvider = (provider: AIProviderId) => {
    setAiProvider(provider);
    const defaults = PROVIDER_DEFAULTS[provider];
    setAiModel(defaults.model);
    setAiBaseUrl(defaults.baseUrl);
    setAiTest(null);
    setAiError(null);
  };

  const saveAIConfig = async (runTest: boolean) => {
    setAiError(null);
    setAiTest(null);
    if (!aiModel.trim()) {
      setAiError('Enter a model name.');
      return;
    }
    if (!aiBaseUrl.trim()) {
      setAiError('Enter the provider base URL.');
      return;
    }
    setAiBusy(true);
    let attemptedConfig = settings.ai;
    try {
      let apiKeyCipher = settings.ai.apiKeyCipher ?? null;
      let hasKey = settings.ai.hasKey;
      if (aiProvider !== 'ollama' && aiKey.trim()) {
        apiKeyCipher = await encryptSecret(aiKey.trim());
        hasKey = true;
      }
      if (aiProvider === 'ollama') {
        apiKeyCipher = null;
        hasKey = false;
      }
      const nextAI = {
        ...settings.ai,
        provider: aiProvider,
        model: aiModel.trim(),
        baseUrl: aiBaseUrl.trim().replace(/\/+$/, ''),
        apiKeyCipher,
        hasKey,
      };
      attemptedConfig = nextAI;
      updateSettings({ ai: nextAI });
      setAiKey('');
      if (runTest) {
        const answer = await testAIConnection(nextAI);
        updateSettings({
          ai: { ...nextAI, lastTestAt: new Date().toISOString(), lastTestOk: true },
        });
        setAiTest(answer || 'Connection successful.');
        toast('Provider connection verified.', 'success');
      } else {
        toast('AI settings saved.', 'success');
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'The provider test failed.';
      updateSettings({
        ai: { ...attemptedConfig, lastTestAt: new Date().toISOString(), lastTestOk: false },
      });
      setAiError(message);
    } finally {
      setAiBusy(false);
    }
  };

  const clearKey = () => {
    updateSettings({ ai: { ...settings.ai, apiKeyCipher: null, hasKey: false, lastTestOk: null } });
    setAiKey('');
    toast('Stored API key removed.', 'success');
  };

  const handleImportFile = async (file: File | undefined) => {
    if (!file) return;
    setImportError(null);
    try {
      const text = await file.text();
      const parsed = JSON.parse(text) as Record<string, unknown>;
      if (parsed.app !== 'momentum' || !parsed.workspace) {
        throw new Error('This is not a MOMENTUM backup file.');
      }
      const importedWorkspace = normalizeWorkspace(parsed.workspace);
      const importedSettings = normalizeSettings(parsed.settings ?? settings);
      const restoredAttachments: { meta: AttachmentMeta; dataUrl: string }[] = [];
      if (Array.isArray(parsed.attachments)) {
        for (const item of parsed.attachments) {
          if (!item || typeof item !== 'object') continue;
          const record = item as Record<string, unknown>;
          if (!record.meta || typeof record.meta !== 'object' || typeof record.dataUrl !== 'string') continue;
          const meta = record.meta as Record<string, unknown>;
          if (
            typeof meta.id !== 'string' ||
            typeof meta.goalId !== 'string' ||
            typeof meta.name !== 'string' ||
            record.dataUrl.length > 22 * 1024 * 1024
          ) {
            continue;
          }
          restoredAttachments.push({
            meta: {
              id: meta.id,
              goalId: meta.goalId,
              name: meta.name.slice(0, 180),
              mimeType: typeof meta.mimeType === 'string' ? meta.mimeType : 'application/octet-stream',
              size: Number(meta.size) || 0,
              createdAt: typeof meta.createdAt === 'string' ? meta.createdAt : new Date().toISOString(),
            },
            dataUrl: record.dataUrl,
          });
        }
      }
      setImportPending({ workspace: importedWorkspace, settings: importedSettings, attachments: restoredAttachments });
    } catch (error) {
      setImportError(error instanceof Error ? error.message : 'The backup could not be read.');
      toast('Backup import failed.', 'error');
    } finally {
      if (importRef.current) importRef.current.value = '';
    }
  };

  const requestPersistence = async () => {
    if (!navigator.storage?.persist) {
      toast('Persistent storage is not available in this browser.', 'error');
      return;
    }
    const granted = await navigator.storage.persist();
    setStorageUsage((current) => ({ ...current, persisted: granted }));
    toast(granted ? 'Durable storage granted.' : 'The browser did not grant durable storage.', granted ? 'success' : 'info');
  };

  const goToSection = (id: SectionId) => {
    setSection(id);
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    window.history.replaceState(null, '', `/settings#${id}`);
  };

  return (
    <div className="page settings-page">
      <PageHeader
        eyebrow="Preferences"
        title="Settings"
        description="Tune the workspace, connect your own AI provider, and decide exactly what leaves this device."
      />

      <div className="settings-layout">
        <nav className="settings-nav" aria-label="Settings sections">
          {SECTIONS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`settings-nav__item${section === item.id ? ' settings-nav__item--active' : ''}`}
              onClick={() => goToSection(item.id)}
            >
              <item.icon size={16} />
              <span>{item.label}</span>
              <ChevronRight size={13} className="settings-nav__arrow" />
            </button>
          ))}
          <div className="settings-nav__status">
            <span className={`status-dot${apiReachable ? ' status-dot--online' : ''}`} />
            <div>
              <strong>{apiReachable ? 'Local service online' : 'Local service offline'}</strong>
              <span>{apiReachable ? 'Accounts & AI proxy available' : 'Core app still works'}</span>
            </div>
          </div>
        </nav>

        <div className="settings-content">
          <section className="settings-section" id="appearance">
            <div className="settings-section__heading">
              <div>
                <span className="eyebrow">Atmosphere</span>
                <h2>Appearance</h2>
              </div>
              <Palette size={18} className="muted" />
            </div>
            <p className="settings-copy">Dark-first, but never locked in. Accent colors update the interface immediately.</p>

            <div className="theme-options">
              {(
                [
                  ['dark', 'Dark', Moon],
                  ['light', 'Light', Sun],
                  ['system', 'System', Monitor],
                ] as const
              ).map(([value, label, Icon]) => (
                <button
                  key={value}
                  type="button"
                  className={`theme-option theme-option--${value}${settings.themeMode === value ? ' theme-option--active' : ''}`}
                  onClick={() => setTheme(value)}
                  aria-pressed={settings.themeMode === value}
                >
                  <span className="theme-option__preview">
                    <span className="theme-option__sidebar" />
                    <span className="theme-option__body">
                      <span />
                      <span />
                      <span />
                    </span>
                  </span>
                  <span className="theme-option__label">
                    <Icon size={14} /> {label}
                    {settings.themeMode === value ? <Check size={14} /> : null}
                  </span>
                </button>
              ))}
            </div>

            <div className="settings-field-block">
              <span className="field__label">Accent palette</span>
              <div className="accent-grid">
                {ACCENT_PRESETS.map((preset) => (
                  <button
                    key={preset.value}
                    type="button"
                    className={`accent-swatch${settings.accent === preset.value ? ' accent-swatch--active' : ''}`}
                    onClick={() => {
                      setAccentError(null);
                      setCustomAccent(preset.value);
                      updateSettings({ accent: preset.value });
                    }}
                    aria-label={preset.name}
                    title={preset.name}
                  >
                    <span style={{ background: preset.value }} />
                    {settings.accent === preset.value ? <Check size={13} /> : null}
                  </button>
                ))}
              </div>
              <div className="custom-accent">
                <span className="custom-accent__preview" style={{ background: settings.accent }} />
                <Input
                  aria-label="Custom accent hex color"
                  value={customAccent}
                  onChange={(event) => setCustomAccent(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') applyAccent(customAccent);
                  }}
                  error={accentError}
                  placeholder="#3b82f6"
                  maxLength={7}
                />
                <Button variant="secondary" onClick={() => applyAccent(customAccent)}>
                  Apply
                </Button>
              </div>
              <div className="custom-accent custom-accent--gradient">
                <label className="accent-color-picker">
                  <span
                    className="accent-color-picker__swatch"
                    style={{ background: settings.accent }}
                    aria-hidden="true"
                  />
                  <input
                    type="color"
                    value={/^#[0-9a-fA-F]{6}$/.test(settings.accent) ? settings.accent : '#3b82f6'}
                    onChange={(event) => {
                      setAccentError(null);
                      setCustomAccent(event.target.value);
                      updateSettings({ accent: event.target.value });
                    }}
                    aria-label="Pick the primary color"
                  />
                  <span className="accent-color-picker__label">Primary color</span>
                </label>
                <label className="accent-color-picker">
                  <span
                    className="accent-color-picker__swatch"
                    style={{ background: settings.accentSecondary }}
                    aria-hidden="true"
                  />
                  <input
                    type="color"
                    value={/^#[0-9a-fA-F]{6}$/.test(settings.accentSecondary ?? '') ? settings.accentSecondary : '#8b5cf6'}
                    onChange={(event) => updateSettings({ accentSecondary: event.target.value })}
                    aria-label="Pick the secondary color"
                  />
                  <span className="accent-color-picker__label">Secondary color</span>
                </label>
                <div
                  className="accent-gradient-preview"
                  style={{ background: `linear-gradient(135deg, ${settings.accent}, ${settings.accentSecondary})` }}
                  role="img"
                  aria-label="Preview of your primary and secondary colors blended together"
                />
              </div>
            </div>

            <div className="settings-row-group">
              <div className="settings-row">
                <div>
                  <strong>Density</strong>
                  <p>Compact fits more plan on screen. Comfortable gives the interface room.</p>
                </div>
                <SegmentedControl
                  value={settings.density}
                  onChange={(density) => updateSettings({ density })}
                  ariaLabel="Interface density"
                  options={[
                    { value: 'comfortable', label: 'Comfortable' },
                    { value: 'compact', label: 'Compact' },
                  ]}
                />
              </div>
              <div className="settings-row">
                <div>
                  <strong>Week starts on</strong>
                  <p>Used in weekly focus and momentum calculations.</p>
                </div>
                <SegmentedControl
                  value={String(settings.weekStartsOn)}
                  onChange={(value) => updateSettings({ weekStartsOn: value === '0' ? 0 : 1 })}
                  ariaLabel="Week start day"
                  options={[
                    { value: '1', label: 'Monday' },
                    { value: '0', label: 'Sunday' },
                  ]}
                />
              </div>
              <Toggle
                checked={settings.reduceMotion}
                onChange={(reduceMotion) => updateSettings({ reduceMotion })}
                label="Reduce motion"
                description="Minimize animation throughout the interface."
              />
            </div>

            <div className="settings-field-block">
              <span className="field__label">Display name</span>
              <Input
                value={settings.displayName}
                onChange={(event) => updateSettings({ displayName: event.target.value })}
                placeholder={user?.name ?? 'How should the workspace address you?'}
                maxLength={80}
                hint="Used only for the greeting. Stored locally."
              />
            </div>
          </section>

          <section className="settings-section" id="ai">
            <div className="settings-section__heading">
              <div>
                <span className="eyebrow">Goal architect</span>
                <h2>AI assistant</h2>
              </div>
              <Sparkles size={18} className="muted" />
            </div>
            <p className="settings-copy">
              {AI_PROVIDER_LOCK
                ? 'This MOMENTUM build is configured for Groq. The local Java service manages the Groq key, so AI planning works without pasting a key into the browser. Manual goal tracking always works without AI.'
                : 'Bring your own provider. MOMENTUM is never tied to one model, and manual goal tracking works without any AI.'}
            </p>

            <div className="settings-field-block">
              <span className="field__label">Provider</span>
              <div className="provider-grid">
                {PROVIDER_ORDER.map((provider) => (
                  <button
                    key={provider}
                    type="button"
                    className={`provider-option${aiProvider === provider ? ' provider-option--active' : ''}`}
                    onClick={() => changeProvider(provider)}
                    aria-pressed={aiProvider === provider}
                  >
                    <span className="provider-option__mark">{PROVIDER_DEFAULTS[provider].label.charAt(0)}</span>
                    <span>
                      <strong>{PROVIDER_DEFAULTS[provider].label}</strong>
                      <small>
                        {provider === 'ollama'
                          ? 'Local model'
                          : provider === 'custom'
                            ? 'OpenAI-compatible'
                            : 'Cloud API'}
                      </small>
                    </span>
                    {aiProvider === provider ? <Check size={14} /> : null}
                  </button>
                ))}
              </div>
            </div>

            <div className="form-grid form-grid--2">
              <Input
                label="Model"
                value={aiModel}
                onChange={(event) => setAiModel(event.target.value)}
                placeholder="Model identifier"
                hint="Use the exact model name from your provider."
              />
              <Input
                label="Base URL"
                value={aiBaseUrl}
                onChange={(event) => setAiBaseUrl(event.target.value)}
                placeholder="https://api.example.com/v1"
                hint={aiProvider === 'ollama' ? 'Ollama must run on localhost:11434.' : 'Validated by the local service.'}
              />
            </div>

            {aiProvider !== 'ollama' ? (
              <div className="api-key-field">
                <Input
                  label="API key"
                  type={aiShowKey ? 'text' : 'password'}
                  value={aiKey}
                  onChange={(event) => setAiKey(event.target.value)}
                  placeholder={
                    settings.ai.hasKey
                      ? '•••••••• saved — enter to replace'
                      : AI_PROVIDER_LOCK
                        ? 'Server-managed Groq key active — optional override'
                        : 'Paste your provider API key'
                  }
                  autoComplete="off"
                  hint={
                    AI_PROVIDER_LOCK
                      ? 'The normal Groq key lives in the local service configuration. A key saved here is encrypted at rest and overrides it only on this device.'
                      : 'Encrypted at rest with a non-extractable device key. Never embedded in the app.'
                  }
                />
                <button
                  type="button"
                  className="input-reveal"
                  onClick={() => setAiShowKey((current) => !current)}
                  aria-label={aiShowKey ? 'Hide API key' : 'Show API key'}
                >
                  {aiShowKey ? <LockKeyhole size={16} /> : <KeyRound size={16} />}
                </button>
              </div>
            ) : (
              <div className="settings-callout">
                <Laptop size={16} />
                <div>
                  <strong>Local inference.</strong>
                  <p>No API key needed. Ollama requests are proxied to localhost and never leave this computer.</p>
                </div>
              </div>
            )}

            {aiError ? <div className="form-error-banner"><span>{aiError}</span></div> : null}
            {aiTest ? (
              <div className="provider-test-result">
                <CheckCircle2Icon />
                <div>
                  <strong>Provider responded.</strong>
                  <p>{aiTest}</p>
                </div>
              </div>
            ) : null}

            <div className="ai-actions">
              <Button variant="primary" onClick={() => void saveAIConfig(false)} loading={aiBusy}>
                Save provider settings
              </Button>
              <Button variant="secondary" icon={<RefreshCw size={15} />} onClick={() => void saveAIConfig(true)} loading={aiBusy}>
                Save & test connection
              </Button>
              {settings.ai.hasKey ? (
                <Button variant="ghost" onClick={clearKey}>
                  Remove saved key
                </Button>
              ) : null}
            </div>
            {settings.ai.lastTestAt ? (
              <p className="settings-fineprint">
                Last test {relativeTime(settings.ai.lastTestAt)} · {settings.ai.lastTestOk ? 'successful' : 'failed'}
              </p>
            ) : null}

            <div className="settings-row-group">
              <div className="settings-row">
                <div>
                  <strong>AI data consent</strong>
                  <p>
                    {settings.ai.consentAcceptedAt
                      ? `You accepted AI data transfer on ${formatDate(settings.ai.consentAcceptedAt.slice(0, 10), 'medium')}. Each plan request still asks for explicit consent.`
                      : 'No consent recorded. The Goal Architect asks before every first transfer.'}
                  </p>
                </div>
                {settings.ai.consentAcceptedAt ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      updateSettings({ ai: { ...settings.ai, consentAcceptedAt: null } });
                      toast('AI data consent revoked.', 'info');
                    }}
                  >
                    Revoke
                  </Button>
                ) : null}
              </div>
            </div>

            <div className="privacy-card">
              <ShieldCheck size={18} />
              <div>
                <strong>How credentials and goal data move</strong>
                <p>
                  Provider keys are encrypted before being stored in IndexedDB. They are sent only to the local Java service,
                  which forwards them with your AI request and never logs request bodies or keys. Interview answers are sent
                  only when you press Generate. Your existing goals are never included.
                </p>
              </div>
            </div>

            <div className="settings-callout">
              <CircleHelp size={16} />
              <div>
                <strong>{AI_PROVIDER_LOCK ? 'Provider lock.' : 'Custom providers.'}</strong>
                <p>
                  {AI_PROVIDER_LOCK
                    ? 'The production build exposes Groq only so there is no ambiguity about which provider and key are in use. The Java provider architecture remains capable of additional providers behind configuration.'
                    : 'More OpenAI-compatible providers can be enabled on the server with an explicit host allowlist. This prevents the AI proxy from becoming an SSRF path.'}{' '}
                  See <code>docs/ARCHITECTURE.md</code>.
                </p>
              </div>
            </div>
          </section>

          <section className="settings-section" id="account">
            <div className="settings-section__heading">
              <div>
                <span className="eyebrow">Identity</span>
                <h2>Account & sync</h2>
              </div>
              <UserRound size={18} className="muted" />
            </div>
            {user ? (
              <div className="account-summary">
                <span className="account-summary__avatar">{user.name.charAt(0).toUpperCase()}</span>
                <div>
                  <strong>{user.name}</strong>
                  <span>{user.email ?? 'Email hidden by provider'}</span>
                </div>
                <Link to="/auth" className="btn btn--secondary btn--sm">
                  Manage account <ArrowUpRight size={13} />
                </Link>
              </div>
            ) : (
              <div className="account-summary account-summary--signed-out">
                <span className="account-summary__avatar"><UserRound size={16} /></span>
                <div>
                  <strong>Local workspace</strong>
                  <span>No account. All goals are stored in this browser.</span>
                </div>
                <Link to="/auth" className="btn btn--secondary btn--sm">
                  Sign in or create account
                </Link>
              </div>
            )}
            <div className="settings-field-block">
              <span className="field__label">Service address (Android/iOS shells)</span>
              <Input
                aria-label="MOMENTUM service address"
                value={apiUrlDraft}
                onChange={(event) => {
                  setApiUrlDraft(event.target.value);
                  setApiUrlOverride(event.target.value);
                }}
                placeholder="Empty = same-origin (browser on this PC)"
                spellCheck={false}
                inputMode="url"
                autoCapitalize="off"
                autoCorrect="off"
              />
              <p className="field__hint">
                The phone app itself is offline-first. Enter where it can reach the service running on your PC when you want
                AI replies and encrypted sync there too: <code>http://&lt;pc-ip&gt;:8787</code> on the same Wi-Fi (run
                <code> ipconfig </code> to find it), or your Tailscale <code>100.x.x.x</code> address to use it from anywhere.
                The browser and PWA on this PC leave it empty.
              </p>
            </div>
            <SyncPanel />
          </section>

          <section className="settings-section" id="data">
            <div className="settings-section__heading">
              <div>
                <span className="eyebrow">Ownership</span>
                <h2>Data & privacy</h2>
              </div>
              <ShieldCheck size={18} className="muted" />
            </div>
            <p className="settings-copy">
              Your workspace is stored in IndexedDB on this device. MOMENTUM requests durable storage so the browser does not
              evict it under pressure. Export a portable backup before clearing browser data.
            </p>

            <div className="storage-panel">
              <div className="storage-panel__header">
                <span className="storage-panel__icon"><HardDrive size={17} /></span>
                <div>
                  <strong>{workspace.goals.filter((goal) => !goal.deletedAt).length} goals · {attachments.length} attachments</strong>
                  <p>
                    About {formatBytes(storageUsage?.usage)} used
                    {storageUsage?.quota ? ` of ${formatBytes(storageUsage.quota)} available` : ''} ·
                    {' '}{storageUsage?.persisted ? 'durable storage granted' : 'browser-managed storage'}
                  </p>
                </div>
                {!storageUsage?.persisted ? (
                  <Button variant="secondary" size="sm" onClick={() => void requestPersistence()}>
                    Request durable storage
                  </Button>
                ) : (
                  <span className="settings-status settings-status--on"><Check size={12} /> Durable</span>
                )}
              </div>
              <p className="settings-fineprint">
                Last local write {relativeTime(workspace.updatedAt)}. IndexedDB writes are transactional; a failed save is
                surfaced in the interface rather than silently ignored.
              </p>
            </div>

            <div className="data-actions">
              <div className="data-action">
                <span className="data-action__icon"><Download size={16} /></span>
                <div>
                  <strong>Export backup</strong>
                  <p>Goals, categories, notes, tasks, attachments and preferences as a portable JSON file. API keys are never included.</p>
                </div>
                <Button variant="secondary" size="sm" onClick={() => void exportGoalBackup()}>
                  Export JSON
                </Button>
              </div>
              <div className="data-action">
                <span className="data-action__icon"><Upload size={16} /></span>
                <div>
                  <strong>Import backup</strong>
                  <p>Replaces this workspace and restores included attachments after confirmation. Files over 15 MB are skipped.</p>
                </div>
                <Button variant="secondary" size="sm" onClick={() => importRef.current?.click()}>
                  Choose file
                </Button>
                <input
                  ref={importRef}
                  type="file"
                  accept="application/json,.json"
                  hidden
                  onChange={(event) => void handleImportFile(event.target.files?.[0])}
                />
              </div>
              {importError ? <p className="form-error-text">{importError}</p> : null}
              <div className="data-action data-action--danger">
                <span className="data-action__icon"><Trash2 size={16} /></span>
                <div>
                  <strong>Erase all local data</strong>
                  <p>Permanently removes every goal, attachment, activity and preference from this browser.</p>
                </div>
                <Button variant="danger" size="sm" onClick={() => setEraseOpen(true)}>
                  Erase data
                </Button>
              </div>
            </div>

            <div className="settings-callout">
              <Cloud size={16} />
              <div>
                <strong>Cloud data is separate.</strong>
                <p>
                  Deleting local data does not delete an encrypted cloud backup, and deleting an account does not erase local
                  goals. Each boundary has its own control.
                </p>
              </div>
            </div>
          </section>

          <section className="settings-section" id="about">
            <div className="settings-section__heading">
              <div>
                <span className="eyebrow">The product</span>
                <h2>About MOMENTUM</h2>
              </div>
              <WandSparkles size={18} className="muted" />
            </div>
            <div className="about-grid">
              <div className="about-mark">
                <span>M</span>
              </div>
              <div>
                <h3>MOMENTUM 1.0</h3>
                <p>A local-first goal command center. Turn ambition into a plan, then keep the next move visible.</p>
                <p className="settings-fineprint">Built with a React + TypeScript client, a dependency-free Java 21 service, IndexedDB persistence and provider-neutral AI.</p>
              </div>
            </div>
            <div className="about-links">
              <button
                type="button"
                onClick={() => {
                  updateSettings({ onboardingComplete: false });
                  navigate('/');
                }}
              >
                <Sparkles size={15} /> Replay onboarding
              </button>
              <Link to="/planner">
                <WandSparkles size={15} /> Open the Goal Architect
              </Link>
              <Link to="/archive">
                <Database size={15} /> Browse archive
              </Link>
            </div>
            <div className="shortcut-table">
              <span className="eyebrow">Keyboard</span>
              <div><kbd>{MODIFIER_LABEL}</kbd><kbd>K</kbd><span>Search everything</span></div>
              <div><kbd>N</kbd><span>New goal (when not typing)</span></div>
              <div><kbd>Esc</kbd><span>Close dialogs</span></div>
            </div>
            <p className="settings-fineprint settings-fineprint--bottom">
              Local-first · No advertising trackers · Your goals remain yours.
            </p>
          </section>
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(importPending)}
        onClose={() => setImportPending(null)}
        onConfirm={() => {
          if (!importPending) return;
          void importGoalBackup(importPending.workspace, importPending.settings, importPending.attachments);
          setImportPending(null);
        }}
        title="Replace this workspace?"
        confirmLabel="Restore backup"
        description={
          importPending
            ? `The backup contains ${importPending.workspace.goals.length} goals, ${importPending.workspace.categories.length} categories and ${importPending.attachments.length} attachments. It will replace the current ${workspace.goals.length} goals. Export the current workspace first if you may need it.`
            : ''
        }
      />

      <Modal
        open={eraseOpen}
        onClose={() => {
          setEraseOpen(false);
          setEraseText('');
        }}
        title="Erase all local data?"
        size="sm"
        dismissible={!eraseBusy}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEraseOpen(false)} disabled={eraseBusy}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={eraseBusy}
              disabled={eraseText !== 'ERASE'}
              onClick={() => {
                void (async () => {
                  setEraseBusy(true);
                  try {
                    await eraseEverything();
                    setEraseOpen(false);
                    setEraseText('');
                  } finally {
                    setEraseBusy(false);
                  }
                })();
              }}
            >
              Erase everything
            </Button>
          </>
        }
      >
        <div className="form-stack">
          <p className="settings-copy">
            This permanently deletes all local goals, tasks, notes, attachments and preferences. It does not delete your
            account or encrypted cloud backup. This cannot be undone.
          </p>
          <Input
            label="Type ERASE to confirm"
            value={eraseText}
            onChange={(event) => setEraseText(event.target.value)}
            placeholder="ERASE"
            autoComplete="off"
          />
        </div>
      </Modal>
    </div>
  );
}

function CheckCircle2Icon() {
  return (
    <span className="provider-test-result__icon">
      <Check size={14} />
    </span>
  );
}
