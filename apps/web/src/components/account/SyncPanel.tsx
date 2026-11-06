import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Cloud, CloudOff, Eye, EyeOff, KeyRound, LockKeyhole, RefreshCw, ShieldCheck, Trash2 } from 'lucide-react';
import { useAuth } from '../../state/AuthContext';
import { useMomentum } from '../../state/MomentumContext';
import { Button } from '../ui/Button';
import { Checkbox, Input } from '../ui/Fields';
import { ConfirmDialog } from '../ui/Modal';
import { deleteEnvelope, syncEncrypted } from '../../lib/sync';
import { relativeTime } from '../../lib/dates';
import { ApiError } from '../../lib/api';

export function SyncPanel() {
  const { user } = useAuth();
  const { workspace, settings, updateSettings, replaceWorkspace, toast } = useMomentum();
  const [passphrase, setPassphrase] = useState('');
  const [showPassphrase, setShowPassphrase] = useState(false);
  const [understood, setUnderstood] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [removeOpen, setRemoveOpen] = useState(false);

  if (!user) {
    return (
      <section className="settings-section" id="sync">
        <div className="settings-section__heading">
          <div>
            <span className="eyebrow">Account</span>
            <h2>Encrypted sync</h2>
          </div>
          <CloudOff size={18} className="muted" />
        </div>
        <p className="settings-copy">
          Sign in first. Syncing is separate from accounts: MOMENTUM never uploads your goals automatically, even when
          you are signed in.
        </p>
        <Link to="/auth" className="btn btn--secondary btn--md">
          Sign in to configure sync
        </Link>
      </section>
    );
  }

  const enabled = settings.syncEnabled;

  const runSync = async () => {
    setError(null);
    if (passphrase.length < 10) {
      setError('Use a sync passphrase of at least 10 characters.');
      return;
    }
    if (!enabled && !understood) {
      setError('Confirm that you understand the passphrase cannot be recovered.');
      return;
    }
    setBusy(true);
    try {
      const result = await syncEncrypted(workspace, passphrase);
      await replaceWorkspace(result.workspace);
      updateSettings({ syncEnabled: true, lastSyncAt: new Date().toISOString() });
      toast(
        result.mergedRemote
          ? 'Encrypted backup merged and updated. Your data never left this device unencrypted.'
          : 'Encrypted backup created. The server can only see ciphertext.',
        'success',
      );
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        setError('Your session expired. Sign in again, then retry.');
      } else {
        setError(caught instanceof Error ? caught.message : 'Sync failed.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="settings-section" id="sync">
      <div className="settings-section__heading">
        <div>
          <span className="eyebrow">Account · explicit by design</span>
          <h2>Encrypted sync</h2>
        </div>
        <span className={`settings-status${enabled ? ' settings-status--on' : ''}`}>
          {enabled ? <Cloud size={13} /> : <CloudOff size={13} />}
          {enabled ? 'Enabled' : 'Off'}
        </span>
      </div>
      <p className="settings-copy">
        Goal data is encrypted in this browser before upload. The server stores an opaque AES-GCM envelope and cannot read
        titles, tasks, notes or categories. There is no automatic sync — nothing is uploaded until you press the button.
      </p>

      <div className="sync-security-strip">
        <span><ShieldCheck size={15} /> AES-256-GCM</span>
        <span><KeyRound size={15} /> PBKDF2 · 310k iterations</span>
        <span><LockKeyhole size={15} /> Passphrase never leaves this device</span>
      </div>

      <div className="sync-form">
        <div className="sync-passphrase">
          <Input
            label={enabled ? 'Sync passphrase' : 'Create a sync passphrase'}
            type={showPassphrase ? 'text' : 'password'}
            value={passphrase}
            onChange={(event) => {
              setPassphrase(event.target.value);
              setError(null);
            }}
            placeholder="At least 10 characters"
            autoComplete="new-password"
            hint="Use the same passphrase on every device. We cannot recover it if it is lost."
          />
          <button
            type="button"
            className="input-reveal"
            onClick={() => setShowPassphrase((current) => !current)}
            aria-label={showPassphrase ? 'Hide passphrase' : 'Show passphrase'}
          >
            {showPassphrase ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
        <div className="passphrase-meter" aria-hidden="true">
          <span className={passphrase.length >= 10 ? 'is-good' : ''} />
          <span className={passphrase.length >= 14 ? 'is-good' : ''} />
          <span className={passphrase.length >= 18 ? 'is-good' : ''} />
          <span className={passphrase.length >= 24 ? 'is-good' : ''} />
        </div>

        {!enabled ? (
          <Checkbox
            checked={understood}
            onChange={setUnderstood}
            label="I understand this passphrase cannot be recovered by MOMENTUM."
            description="Losing it means the encrypted cloud copy cannot be decrypted. Your local workspace is unaffected."
          />
        ) : null}

        {error ? <p className="form-error-text" role="alert">{error}</p> : null}

        <div className="sync-actions">
          <Button
            variant="primary"
            icon={<RefreshCw size={15} />}
            loading={busy}
            onClick={() => void runSync()}
          >
            {enabled ? 'Sync now' : 'Enable & sync now'}
          </Button>
          {enabled ? (
            <Button
              variant="ghost"
              size="md"
              onClick={() => {
                updateSettings({ syncEnabled: false });
                toast('Sync disabled. The encrypted cloud backup was left in place.', 'info');
              }}
            >
              Disable
            </Button>
          ) : null}
          <Button variant="ghost" size="md" icon={<Trash2 size={14} />} onClick={() => setRemoveOpen(true)}>
            Delete cloud backup
          </Button>
        </div>
        <div className="sync-meta">
          <span>{settings.lastSyncAt ? `Last sync ${relativeTime(settings.lastSyncAt)}` : 'Never synced'}</span>
          <span>Attachments are not synced in this version.</span>
        </div>
      </div>

      <ConfirmDialog
        open={removeOpen}
        onClose={() => setRemoveOpen(false)}
        onConfirm={() => {
          void (async () => {
            try {
              await deleteEnvelope();
              updateSettings({ syncEnabled: false, lastSyncAt: null });
              toast('Encrypted cloud backup deleted.', 'success');
            } catch (caught) {
              toast(caught instanceof Error ? caught.message : 'The backup could not be removed.', 'error');
            } finally {
              setRemoveOpen(false);
            }
          })();
        }}
        title="Delete the encrypted cloud backup?"
        confirmLabel="Delete backup"
        description="This removes the only cloud copy. Your local goals are untouched. Other devices will no longer be able to restore from it."
      />
    </section>
  );
}
