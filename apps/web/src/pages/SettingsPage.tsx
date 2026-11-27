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
  Monitor,
  Moon,
  Palette,
  ShieldCheck,
  Target,
  Sun,
  Trash2,
  Upload,
  UserRound,
} from 'lucide-react';
import type { AppSettings, AttachmentMeta, ThemeMode, Workspace } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { useAuth } from '../state/AuthContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Input, SegmentedControl, Toggle } from '../components/ui/Fields';
import { ConfirmDialog, Modal } from '../components/ui/Modal';
import { ACCENT_PRESETS } from '../lib/defaults';
import { normalizeSettings, normalizeWorkspace } from '../lib/repository';
import { exportGoalsToCsv, exportTasksToCsv, exportMilestonesToCsv } from '../lib/csv';
import { resolvedApiBase, setApiUrlOverride } from '../lib/api';
import { validateHex } from '../lib/validation';
import { relativeTime } from '../lib/dates';
import { SyncPanel } from '../components/account/SyncPanel';
import { MODIFIER_LABEL } from '../lib/platform';

type SectionId = 'appearance' | 'account' | 'data' | 'about';

const SECTIONS: { id: SectionId; label: string; icon: typeof Palette }[] = [
  { id: 'appearance', label: 'Appearance', icon: Palette },
  { id: 'account', label: 'Account & sync', icon: UserRound },
  { id: 'data', label: 'Data & privacy', icon: Database },
  { id: 'about', label: 'About', icon: CircleHelp },
];

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
        description="Tune the workspace and decide how your data is stored."
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
              <span>{apiReachable ? 'Accounts & sync available' : 'Core app still works'}</span>
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
                Encrypted sync is available there too: <code>http://&lt;pc-ip&gt;:8787</code> on the same Wi-Fi (run
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
                  <p>Goals, categories, notes, tasks, attachments and preferences as a portable JSON file.</p>
                </div>
                <Button variant="secondary" size="sm" onClick={() => void exportGoalBackup()}>
                  Export JSON
                </Button>
              </div>
              <div className="data-action">
                <span className="data-action__icon"><Download size={16} /></span>
                <div>
                  <strong>Export to CSV</strong>
                  <p>Goals, tasks and milestones as spreadsheet-friendly CSV files for analysis in Excel or Google Sheets.</p>
                </div>
                <div className="data-action__buttons">
                  <Button variant="secondary" size="sm" onClick={() => exportGoalsToCsv(workspace.goals, workspace.categories)}>
                    Goals
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => exportTasksToCsv(workspace.goals.flatMap((g: any) => g.tasks ?? []), workspace.goals)}>
                    Tasks
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => exportMilestonesToCsv(workspace.goals.flatMap((g: any) => g.milestones ?? []))}>
                    Milestones
                  </Button>
                </div>
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
              <Target size={18} className="muted" />
            </div>
            <div className="about-grid">
              <div className="about-mark">
                <span>M</span>
              </div>
              <div>
                <h3>MOMENTUM 1.0</h3>
                <p>A local-first goal command center. Turn ambition into a plan, then keep the next move visible.</p>
                <p className="settings-fineprint">Built with a React + TypeScript client, a dependency-free Java 21 service, and IndexedDB persistence.</p>
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
                <Target size={15} /> Replay onboarding
              </button>
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
