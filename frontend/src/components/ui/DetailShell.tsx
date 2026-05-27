'use client';

import { useRouter } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { LucideIcon } from 'lucide-react';

/** Consistent header for every detail ([id]) page: back, title, status, actions. */
export function DetailHeader({
  title, subtitle, badge, actions, backHref, onBack,
}: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  badge?: React.ReactNode;
  actions?: React.ReactNode;
  backHref?: string;
  onBack?: () => void;
}) {
  const router = useRouter();
  const goBack = () => { if (onBack) onBack(); else if (backHref) router.push(backHref); else router.back(); };

  return (
    <div className="flex items-start gap-3 mb-6">
      <button onClick={goBack} className="btn-icon mt-0.5 shrink-0" aria-label="Back">
        <ArrowLeft size={18} />
      </button>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2.5 flex-wrap">
          <h1 className="page-title truncate">{title}</h1>
          {badge}
        </div>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  );
}

/** A compact KPI tile for the detail summary strip. */
export function DetailStat({
  label, value, icon: Icon, accent = 'default',
}: {
  label: string;
  value: React.ReactNode;
  icon?: LucideIcon;
  accent?: 'default' | 'green' | 'red' | 'yellow';
}) {
  const color =
    accent === 'green' ? 'text-green-600 dark:text-green-400' :
    accent === 'red' ? 'text-red-600 dark:text-red-400' :
    accent === 'yellow' ? 'text-yellow-600 dark:text-yellow-400' : '';
  return (
    <div className="card p-4">
      <div className="flex items-center gap-1.5">
        {Icon && <Icon size={13} style={{ color: 'rgb(var(--color-text-muted))' }} />}
        <p className="stat-label">{label}</p>
      </div>
      <p className={cn('stat-value num mt-1.5', color)}>{value}</p>
    </div>
  );
}

/** A titled section card used to group detail content consistently. */
export function DetailSection({
  title, actions, children, className, noPadding,
}: {
  title?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  noPadding?: boolean;
}) {
  return (
    <div className={cn('card', className)}>
      {title && (
        <div className="flex items-center justify-between px-5 py-3.5" style={{ borderBottom: '1px solid rgb(var(--color-border))' }}>
          <h3 className="section-title">{title}</h3>
          {actions}
        </div>
      )}
      <div className={noPadding ? '' : 'p-5'}>{children}</div>
    </div>
  );
}

/** Label/value row for detail info grids. */
export function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs font-medium" style={{ color: 'rgb(var(--color-text-muted))' }}>{label}</span>
      <span className="text-sm" style={{ color: 'rgb(var(--color-text-primary))' }}>{value}</span>
    </div>
  );
}
