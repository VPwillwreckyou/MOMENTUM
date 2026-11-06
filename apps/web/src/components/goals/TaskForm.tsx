import { useEffect, useState } from 'react';
import type { Priority, Task } from '../../state/types';
import { useMomentum } from '../../state/MomentumContext';
import { Modal, ConfirmDialog } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Input, Select, Textarea } from '../ui/Fields';
import { validateTaskTitle } from '../../lib/validation';

interface TaskFormProps {
  open: boolean;
  onClose: () => void;
  goalId: string;
  task?: Task | null;
  defaultMilestoneId?: string | null;
  defaultParentTaskId?: string | null;
  parentTaskTitle?: string;
}

export function TaskForm({
  open,
  onClose,
  goalId,
  task,
  defaultMilestoneId,
  defaultParentTaskId,
  parentTaskTitle,
}: TaskFormProps) {
  const { addTask, updateTask, deleteTask, toast } = useMomentum();
  const [title, setTitle] = useState('');
  const [notes, setNotes] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [priority, setPriority] = useState<Priority>('medium');
  const [milestoneId, setMilestoneId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const goal = useMomentum().goals.find((item) => item.id === goalId);

  useEffect(() => {
    if (!open) return;
    setTitle(task?.title ?? '');
    setNotes(task?.notes ?? '');
    setDueDate(task?.dueDate ?? '');
    setPriority(task?.priority ?? 'medium');
    setMilestoneId(task?.milestoneId ?? defaultMilestoneId ?? '');
    setError(null);
    setDeleteOpen(false);
  }, [open, task, defaultMilestoneId]);

  const submit = () => {
    const validation = validateTaskTitle(title);
    if (validation) {
      setError(validation);
      return;
    }
    if (task) {
      updateTask(goalId, task.id, {
        title: title.trim(),
        notes: notes.trim(),
        dueDate: dueDate || null,
        priority,
        milestoneId: milestoneId || null,
      });
      toast('Task updated.', 'success');
    } else {
      addTask(goalId, {
        title: title.trim(),
        notes: notes.trim(),
        dueDate: dueDate || null,
        priority,
        milestoneId: milestoneId || null,
        parentTaskId: defaultParentTaskId ?? null,
      });
      toast(defaultParentTaskId ? 'Subtask added.' : 'Task added.', 'success');
    }
    onClose();
  };

  const remove = () => {
    if (!task) return;
    deleteTask(goalId, task.id);
    toast('Task deleted.', 'success');
    setDeleteOpen(false);
    onClose();
  };

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        size="md"
        title={task ? 'Edit task' : defaultParentTaskId ? 'Add subtask' : 'Add task'}
        description={
          defaultParentTaskId && parentTaskTitle
            ? `A smaller move inside “${parentTaskTitle}”.`
            : 'Make it concrete enough to start without thinking.'
        }
        footer={
          <>
            {task ? (
              <Button variant="ghost" className="modal__footer-start" onClick={() => setDeleteOpen(true)}>
                Delete
              </Button>
            ) : null}
            <Button variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="primary" onClick={submit}>
              {task ? 'Save task' : 'Add task'}
            </Button>
          </>
        }
      >
        <div className="form-stack">
          <Input
            label="Task"
            placeholder="Start with a verb — draft, call, outline, ship…"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value);
              setError(null);
            }}
            error={error}
            maxLength={240}
            autoFocus
          />
          <Textarea
            label="Notes"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={3}
            placeholder="What does done look like? Any links or context?"
            maxLength={2000}
            optional
          />
          <div className="form-grid form-grid--2">
            <Input
              label="Due date"
              type="date"
              value={dueDate}
              onChange={(event) => setDueDate(event.target.value)}
              optional
            />
            <Select
              label="Priority"
              value={priority}
              onChange={(event) => setPriority(event.target.value as Priority)}
            >
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
              <option value="critical">Critical</option>
            </Select>
          </div>
          {!defaultParentTaskId ? (
            <Select
              label="Milestone"
              value={milestoneId}
              onChange={(event) => setMilestoneId(event.target.value)}
              optional
            >
              <option value="">No milestone</option>
              {goal?.milestones.map((milestone) => (
                <option key={milestone.id} value={milestone.id}>
                  {milestone.title}
                </option>
              ))}
            </Select>
          ) : null}
        </div>
      </Modal>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={remove}
        title="Delete this task?"
        confirmLabel="Delete task"
        description="The task will be removed immediately. Tasks do not use the 30-day Recently Deleted window."
      />
    </>
  );
}
