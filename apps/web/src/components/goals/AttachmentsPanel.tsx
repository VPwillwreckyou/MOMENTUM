import { useRef, useState } from 'react';
import { Download, File, Paperclip, Trash2 } from 'lucide-react';
import type { AttachmentMeta } from '../../state/types';
import { useMomentum } from '../../state/MomentumContext';
import { getAttachmentBlob } from '../../lib/db';
import { Button, IconButton } from '../ui/Button';
import { ConfirmDialog } from '../ui/Modal';

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AttachmentsPanel({ goalId, readOnly = false }: { goalId: string; readOnly?: boolean }) {
  const { attachments, uploadAttachment, removeAttachment, toast } = useMomentum();
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [deleting, setDeleting] = useState<AttachmentMeta | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const own = attachments.filter((attachment) => attachment.goalId === goalId);

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      for (const file of Array.from(files)) {
        await uploadAttachment(goalId, file);
      }
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const download = async (attachment: AttachmentMeta) => {
    const blob = await getAttachmentBlob(attachment.id);
    if (!blob) {
      toast('This attachment is no longer available.', 'error');
      return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = attachment.name;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 2000);
  };

  return (
    <section className="side-panel" id="attachments">
      <div className="side-panel__header">
        <div>
          <span className="eyebrow">Files</span>
          <h2>Attachments</h2>
        </div>
        <span className="side-panel__count">{own.length}</span>
      </div>

      {own.length > 0 ? (
        <div className="attachment-list">
          {own.map((attachment) => (
            <div className="attachment-row" key={attachment.id}>
              <span className="attachment-row__icon">
                <File size={15} />
              </span>
              <button type="button" className="attachment-row__copy" onClick={() => void download(attachment)} title={`Download ${attachment.name}`}>
                <span className="truncate">{attachment.name}</span>
                <span>{formatSize(attachment.size)}</span>
              </button>
              <IconButton label={`Download ${attachment.name}`} size="sm" onClick={() => void download(attachment)}>
                <Download size={14} />
              </IconButton>
              {!readOnly ? (
                <IconButton label={`Delete ${attachment.name}`} size="sm" onClick={() => setDeleting(attachment)}>
                  <Trash2 size={14} />
                </IconButton>
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <p className="side-panel__empty">No files yet. Reference material stored locally with this goal.</p>
      )}

      {!readOnly ? (
        <div
          className={`attachment-drop${dragging ? ' attachment-drop--active' : ''}`}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            void handleFiles(event.dataTransfer.files);
          }}
        >
          <Paperclip size={15} />
          <div>
            <strong>Drop a file here</strong>
            <span>Up to 15 MB · stored only on this device</span>
          </div>
          <Button size="sm" variant="ghost" loading={uploading} onClick={() => inputRef.current?.click()}>
            Browse
          </Button>
          <input
            ref={inputRef}
            type="file"
            hidden
            multiple
            onChange={(event) => void handleFiles(event.target.files)}
          />
        </div>
      ) : null}

      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) void removeAttachment(deleting.id);
          setDeleting(null);
        }}
        title="Delete attachment?"
        confirmLabel="Delete file"
        description={`“${deleting?.name ?? 'This file'}” will be removed from this device immediately.`}
      />
    </section>
  );
}
