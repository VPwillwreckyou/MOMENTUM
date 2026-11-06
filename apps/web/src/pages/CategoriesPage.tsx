import { useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Boxes, Pencil, Plus, Trash2 } from 'lucide-react';
import type { Category, CategoryIcon } from '../state/types';
import { useMomentum } from '../state/MomentumContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button, IconButton } from '../components/ui/Button';
import { ConfirmDialog, Modal } from '../components/ui/Modal';
import { Input } from '../components/ui/Fields';
import { EmptyState } from '../components/ui/EmptyState';
import { CATEGORY_ICONS, CategoryIconView } from '../components/goals/CategoryBadge';
import { computeGoalProgress } from '../lib/progress';

const COLORS = ['#3b82f6', '#22d3ee', '#a3e635', '#f472b6', '#fb923c', '#8b5cf6', '#34d399', '#f87171'];
const ICONS: CategoryIcon[] = ['target', 'work', 'health', 'learning', 'creative', 'finance', 'home', 'relationships'];

interface CategoryDraft {
  id?: string;
  name: string;
  color: string;
  icon: CategoryIcon;
}

function CategoryEditor({
  open,
  initial,
  onClose,
}: {
  open: boolean;
  initial?: Category | null;
  onClose: () => void;
}) {
  const { addCategory, updateCategory, categories } = useMomentum();
  const [draft, setDraft] = useState<CategoryDraft>({
    name: initial?.name ?? '',
    color: initial?.color ?? COLORS[0],
    icon: initial?.icon ?? 'target',
  });
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    if (!draft.name.trim()) {
      setError('Give the category a name.');
      return;
    }
    const duplicate = categories.find(
      (category) =>
        category.id !== initial?.id && category.name.trim().toLowerCase() === draft.name.trim().toLowerCase(),
    );
    if (duplicate) {
      setError('A category with that name already exists.');
      return;
    }
    if (initial) {
      updateCategory(initial.id, draft);
    } else {
      const created = addCategory(draft.name, draft.color, draft.icon);
      if (!created) return;
    }
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initial ? 'Edit category' : 'New category'}
      description="Categories are lanes for your attention, not extra rules."
      size="sm"
      key={initial?.id ?? 'new'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" onClick={submit}>
            {initial ? 'Save category' : 'Create category'}
          </Button>
        </>
      }
    >
      <div className="form-stack">
        <Input
          label="Name"
          value={draft.name}
          onChange={(event) => {
            setDraft({ ...draft, name: event.target.value });
            setError(null);
          }}
          error={error}
          placeholder="e.g. Deep work"
          maxLength={60}
          autoFocus
        />
        <div>
          <span className="field__label">Color</span>
          <div className="color-row color-row--large">
            {COLORS.map((color) => (
              <button
                key={color}
                type="button"
                className={`color-swatch color-swatch--large${draft.color === color ? ' color-swatch--selected' : ''}`}
                style={{ background: color }}
                onClick={() => setDraft({ ...draft, color })}
                aria-label={`Use ${color}`}
              />
            ))}
          </div>
        </div>
        <div>
          <span className="field__label">Icon</span>
          <div className="icon-choice-grid">
            {ICONS.map((icon) => {
              const Icon = CATEGORY_ICONS[icon];
              return (
                <button
                  type="button"
                  key={icon}
                  className={`icon-choice${draft.icon === icon ? ' icon-choice--active' : ''}`}
                  onClick={() => setDraft({ ...draft, icon })}
                  aria-label={icon}
                  aria-pressed={draft.icon === icon}
                >
                  <Icon size={18} />
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </Modal>
  );
}

export function CategoriesPage() {
  const { categories, goals, deleteCategory } = useMomentum();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);

  const stats = useMemo(() => {
    return categories.map((category) => {
      const categoryGoals = goals.filter(
        (goal) => goal.categoryId === category.id && !goal.deletedAt && !goal.archivedAt,
      );
      const active = categoryGoals.filter((goal) => goal.status === 'active');
      const progress = categoryGoals.length
        ? Math.round(categoryGoals.reduce((sum, goal) => sum + computeGoalProgress(goal).percent, 0) / categoryGoals.length)
        : 0;
      return { category, total: categoryGoals.length, active: active.length, progress };
    });
  }, [categories, goals]);

  const uncategorized = goals.filter(
    (goal) => !goal.categoryId && !goal.deletedAt && !goal.archivedAt,
  ).length;

  return (
    <div className="page categories-page">
      <PageHeader
        eyebrow="Structure"
        title="Categories"
        description="Give different ambitions their own lane. Nothing is forced into a template."
        actions={
          <Button
            variant="primary"
            icon={<Plus size={16} />}
            onClick={() => {
              setEditing(null);
              setEditorOpen(true);
            }}
          >
            New category
          </Button>
        }
      />

      {categories.length === 0 ? (
        <EmptyState
          icon={<Boxes size={24} />}
          eyebrow="Make it yours"
          title="Organize when it helps."
          description="Categories are optional. Create one when you want to see related goals together — work, health, a side project, or anything else."
          actions={
            <Button
              variant="primary"
              icon={<Plus size={16} />}
              onClick={() => {
                setEditing(null);
                setEditorOpen(true);
              }}
            >
              Create a category
            </Button>
          }
        />
      ) : (
        <>
          <div className="category-grid">
            {stats.map(({ category, total, active, progress }) => {
              return (
                <div
                  key={category.id}
                  className="category-card"
                  style={{ '--category-color': category.color } as CSSProperties}
                >
                  <div className="category-card__top">
                    <span className="category-card__icon">
                      <CategoryIconView icon={category.icon} size={19} />
                    </span>
                    <div className="category-card__actions">
                      <IconButton
                        label={`Edit ${category.name}`}
                        size="sm"
                        onClick={() => {
                          setEditing(category);
                          setEditorOpen(true);
                        }}
                      >
                        <Pencil size={14} />
                      </IconButton>
                      <IconButton label={`Delete ${category.name}`} size="sm" onClick={() => setDeleting(category)}>
                        <Trash2 size={14} />
                      </IconButton>
                    </div>
                  </div>
                  <h2>{category.name}</h2>
                  <div className="category-card__metrics">
                    <span>
                      <strong>{total}</strong> {total === 1 ? 'goal' : 'goals'}
                    </span>
                    <span>
                      <strong>{active}</strong> active
                    </span>
                    <span>
                      <strong>{progress}%</strong> avg. progress
                    </span>
                  </div>
                  <div className="category-card__bar">
                    <span style={{ width: `${progress}%` }} />
                  </div>
                  <Link to={`/goals?category=${category.id}`} className="category-card__link">
                    View goals <ArrowRight size={13} />
                  </Link>
                </div>
              );
            })}
          </div>

          {uncategorized > 0 ? (
            <div className="uncategorized-strip">
              <span className="uncategorized-strip__mark" />
              <div>
                <strong>{uncategorized} uncategorized {uncategorized === 1 ? 'goal' : 'goals'}</strong>
                <p>They are still fully visible. Categories are optional.</p>
              </div>
              <Link to="/goals" className="text-link">
                View <ArrowRight size={14} />
              </Link>
            </div>
          ) : null}
        </>
      )}

      {editorOpen ? (
        <CategoryEditor key={editing?.id ?? 'new'} open={editorOpen} initial={editing} onClose={() => setEditorOpen(false)} />
      ) : null}


      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) deleteCategory(deleting.id);
          setDeleting(null);
        }}
        title={`Delete “${deleting?.name ?? ''}”?`}
        confirmLabel="Delete category"
        description="Goals in this category will be kept and become uncategorized. This cannot be undone from Recently Deleted."
      />
    </div>
  );
}
