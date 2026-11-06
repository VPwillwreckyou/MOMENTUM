import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  Bot,
  Boxes,
  CheckSquare,
  CornerDownLeft,
  FileText,
  Goal,
  Layers,
  Plus,
  Search,
  Settings,
  Sparkles,
  Target,
  Trash2,
  Archive,
} from 'lucide-react';
import { useMomentum } from '../../state/MomentumContext';
import { searchWorkspace, type SearchHit } from '../../lib/search';

type PaletteItem =
  | { type: 'hit'; hit: SearchHit }
  | { type: 'action'; id: string; title: string; description: string; icon: typeof Goal; run: () => void };

export function CommandPalette({
  open,
  onClose,
  onNewGoal,
}: {
  open: boolean;
  onClose: () => void;
  onNewGoal: () => void;
}) {
  const navigate = useNavigate();
  const { goals, categories, attachments } = useMomentum();
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    setQuery('');
    setActiveIndex(0);
    previousFocusRef.current = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const timer = window.setTimeout(() => inputRef.current?.focus(), 20);
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = paletteRef.current?.querySelectorAll<HTMLElement>(
        'input, button:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable || focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKey);
    return () => {
      window.clearTimeout(timer);
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKey);
      previousFocusRef.current?.focus?.();
    };
  }, [open, onClose]);

  const hits = useMemo(
    () => searchWorkspace(query, goals, categories, attachments),
    [query, goals, categories, attachments],
  );

  const actions: PaletteItem[] = useMemo(() => {
    const quick = [
      {
        id: 'new',
        title: 'Create a new goal',
        description: 'Start manually — you control every detail',
        icon: Plus,
        run: () => {
          onClose();
          onNewGoal();
        },
      },
      {
        id: 'planner',
        title: 'Open the Goal Architect',
        description: 'Turn an ambition into an editable AI plan',
        icon: Sparkles,
        run: () => {
          onClose();
          navigate('/planner');
        },
      },
      {
        id: 'categories',
        title: 'Browse categories',
        description: 'Organize goals into personal lanes',
        icon: Boxes,
        run: () => {
          onClose();
          navigate('/categories');
        },
      },
      {
        id: 'archive',
        title: 'Open archive',
        description: 'Goals you have set aside',
        icon: Archive,
        run: () => {
          onClose();
          navigate('/archive');
        },
      },
      {
        id: 'trash',
        title: 'Recently deleted',
        description: 'Restore a goal within 30 days',
        icon: Trash2,
        run: () => {
          onClose();
          navigate('/trash');
        },
      },
      {
        id: 'settings',
        title: 'Settings',
        description: 'Appearance, AI providers, account and data',
        icon: Settings,
        run: () => {
          onClose();
          navigate('/settings');
        },
      },
    ];
    const filtered = query.trim()
      ? quick.filter((item) => `${item.title} ${item.description}`.toLowerCase().includes(query.toLowerCase()))
      : quick.slice(0, 4);
    return filtered.map((item) => ({ type: 'action', ...item }));
  }, [query, navigate, onClose, onNewGoal]);

  const items: PaletteItem[] = useMemo(
    () => [...hits.map((hit) => ({ type: 'hit' as const, hit })), ...actions],
    [hits, actions],
  );

  const openHit = (hit: SearchHit) => {
    onClose();
    if (hit.kind === 'category') {
      navigate(`/goals?category=${hit.id}`);
    } else if (hit.goalId) {
      navigate(`/goals/${hit.goalId}${hit.kind === 'attachment' ? '#attachments' : ''}`);
    }
  };

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  useEffect(() => {
    const selected = listRef.current?.querySelector<HTMLElement>('[data-active="true"]');
    selected?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  if (!open) return null;

  const runItem = (item: PaletteItem) => {
    if (item.type === 'hit') openHit(item.hit);
    else item.run();
  };

  const iconFor = (hit: SearchHit) => {
    switch (hit.kind) {
      case 'goal':
        return Goal;
      case 'task':
        return CheckSquare;
      case 'subtask':
        return CheckSquare;
      case 'milestone':
        return Target;
      case 'category':
        return Boxes;
      case 'note':
        return FileText;
      case 'attachment':
        return Layers;
    }
  };

  return createPortal(
    <div
      className="palette-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div ref={paletteRef} className="palette" role="dialog" aria-modal="true" aria-label="Search MOMENTUM">
        <div className="palette__input-row">
          <Search size={18} />
          <input
            ref={inputRef}
            className="palette__input"
            placeholder="Search goals, tasks, notes, categories, files…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown') {
                event.preventDefault();
                setActiveIndex((index) => Math.min(items.length - 1, index + 1));
              } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                setActiveIndex((index) => Math.max(0, index - 1));
              } else if (event.key === 'Enter') {
                event.preventDefault();
                if (items[activeIndex]) runItem(items[activeIndex]);
              }
            }}
            role="combobox"
            aria-expanded="true"
            aria-controls="palette-results"
            aria-autocomplete="list"
            aria-activedescendant={items[activeIndex] ? `palette-item-${activeIndex}` : undefined}
          />
          <kbd className="palette__esc">ESC</kbd>
        </div>

        <div className="palette__results" id="palette-results" role="listbox" ref={listRef}>
          {query.trim().length > 0 && query.trim().length < 2 ? (
            <p className="palette__hint">Type one more character to search.</p>
          ) : null}
          {hits.length > 0 ? (
            <>
              <div className="palette__group-label">
                <span>Matches</span>
                <span>{hits.length}</span>
              </div>
              {hits.map((hit, index) => {
                const Icon = iconFor(hit);
                const globalIndex = index;
                return (
                  <button
                    key={`${hit.kind}-${hit.id}-${index}`}
                    type="button"
                    id={`palette-item-${globalIndex}`}
                    role="option"
                    aria-selected={activeIndex === globalIndex}
                    data-active={activeIndex === globalIndex}
                    className={`palette__item${activeIndex === globalIndex ? ' palette__item--active' : ''}`}
                    onMouseEnter={() => setActiveIndex(globalIndex)}
                    onClick={() => runItem(items[globalIndex])}
                  >
                    <span className="palette__item-icon">
                      <Icon size={16} />
                    </span>
                    <span className="palette__item-copy">
                      <span className="palette__item-title">{hit.title}</span>
                      <span className="palette__item-excerpt">{hit.excerpt}</span>
                    </span>
                    <span className="palette__item-kind">{hit.kind}</span>
                  </button>
                );
              })}
            </>
          ) : null}

          {actions.length > 0 ? (
            <div className="palette__group-label palette__group-label--spaced">
              <span>{query.trim() ? 'Actions' : 'Quick actions'}</span>
            </div>
          ) : null}
          {actions.map((item, index) => {
            if (item.type !== 'action') return null;
            const globalIndex = hits.length + index;
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                id={`palette-item-${globalIndex}`}
                role="option"
                aria-selected={activeIndex === globalIndex}
                data-active={activeIndex === globalIndex}
                className={`palette__item${activeIndex === globalIndex ? ' palette__item--active' : ''}`}
                onMouseEnter={() => setActiveIndex(globalIndex)}
                onClick={() => runItem(item)}
              >
                <span className="palette__item-icon">
                  <Icon size={16} />
                </span>
                <span className="palette__item-copy">
                  <span className="palette__item-title">{item.title}</span>
                  <span className="palette__item-excerpt">{item.description}</span>
                </span>
                <ArrowRight size={14} className="palette__arrow" />
              </button>
            );
          })}
          {query.trim().length >= 2 && hits.length === 0 && actions.length === 0 ? (
            <div className="palette__empty">
              <Bot size={20} />
              <p>No matches for “{query}”.</p>
              <span>Try a shorter phrase or search a category name.</span>
            </div>
          ) : null}
        </div>

        <div className="palette__footer">
          <span>
            <kbd>↑</kbd>
            <kbd>↓</kbd> navigate
          </span>
          <span>
            <kbd>
              <CornerDownLeft size={11} />
            </kbd>{' '}
            open
          </span>
          <span className="palette__footer-note">Search stays on this device</span>
        </div>
      </div>
    </div>,
    document.body,
  );
}
