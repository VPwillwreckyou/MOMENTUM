import { useEffect, useState } from 'react';
import type { Milestone } from '../../state/types';
import { useMomentum } from '../../state/MomentumContext';
import { Modal, ConfirmDialog } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input, Textarea } from '../ui/Fields';

interface MilestoneFormProps {
  open: boolean;
  onClose: () => void;
  goalId: string;
  milestone?: Milestone | null;
  defaultDueDate?: string;
}

export function MilestoneForm({ open, onClose, goalId, milestone, defaultDueDate }: MilestoneFormProps) {
  const { addMilestone, updateMilestone, deleteMilestone, toast } = useMomentum();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTitle(milestone?.title ?? '');
    setDescription(milestone?.description ?? '');
    setDueDate(milestone?.dueDate ?? defaultDueDate ?? '');
    setError(null);
    setDeleteOpen(false);
  }, [open, milestone, defaultDueDate]);

  const submit = () => {
    if (!title.trim()) {
      setError('Give the milestone a name.');
      return;
    }
    if (milestone) {
      updateMilestone(goalId, milestone.id, {
        title: title.trim(),
        description: description.trim(),
        dueDate: dueDate || null,
      });
      toast('Milestone updated.', 'success');
    } else {
      addMilestone(goalId, { title: title.trim(), description: description.trim(), dueDate: dueDate || null });
      toast('Milestone added.', 'success');
    }
    onClose();
  };

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title={milestone ? 'Edit milestone' : 'Add milestone'}
        description="An outcome or checkpoint — not a to-do. Tasks hang beneath it."
        size="md"
        footer={
          <>
            {milestone ? (
              <Button variant="ghost" className="modal__footer-start" onClick={() => setDeleteOpen(true)}>
                Delete
              </Button>
            ) : null}
            <Button variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="primary" onClick={submit}>
              {milestone ? 'Save milestone' : 'Add milestone'}
            </Button>
          </>
        }
      >
        <div className="form-stack">
          <Input
            label="Milestone"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value);
              setError(null);
            }}
            error={error}
            placeholder="e.g. Public beta shipped"
            maxLength={160}
            autoFocus
          />
          <Textarea
            label="What will be true when this is done?"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            maxLength={800}
            optional
          />
          <Input
            label="Target date"
            type="date"
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
            optional
          />
        </div>
      </Modal>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => {
          if (milestone) deleteMilestone(goalId, milestone.id);
          toast('Milestone deleted. Its tasks were kept.', 'success');
          setDeleteOpen(false);
          onClose();
        }}
        title="Delete this milestone?"
        confirmLabel="Delete milestone"
        description="The milestone is removed immediately. Tasks inside it are kept and become unassigned."
      />
    </>
  );
}
