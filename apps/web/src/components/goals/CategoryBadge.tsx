import {
  BookOpen,
  BriefcaseBusiness,
  Coins,
  HeartPulse,
  Home,
  Lightbulb,
  Target,
  Users,
} from 'lucide-react';
import type { Category, CategoryIcon } from '../../state/types';

export const CATEGORY_ICONS: Record<CategoryIcon, typeof Target> = {
  target: Target,
  work: BriefcaseBusiness,
  health: HeartPulse,
  learning: BookOpen,
  creative: Lightbulb,
  finance: Coins,
  home: Home,
  relationships: Users,
};

export function CategoryIconView({ icon, size = 15 }: { icon: CategoryIcon; size?: number }) {
  const Icon = CATEGORY_ICONS[icon] ?? Target;
  return <Icon size={size} />;
}

export function CategoryBadge({ category }: { category?: Category | null }) {
  if (!category) return null;
  return (
    <span
      className="category-badge"
      style={{ '--category-color': category.color } as React.CSSProperties}
    >
      <span className="category-badge__dot" aria-hidden="true" />
      {category.name}
    </span>
  );
}

export function CategoryLabel({ category }: { category?: Category | null }) {
  if (!category) {
    return (
      <span className="category-label category-label--empty">
        <span className="category-label__dot" aria-hidden="true" />
        Uncategorized
      </span>
    );
  }
  return (
    <span className="category-label" style={{ '--category-color': category.color } as React.CSSProperties}>
      <span className="category-label__dot" aria-hidden="true" />
      {category.name}
    </span>
  );
}
