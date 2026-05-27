'use client';

import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { dashboardApi } from '@/lib/api';
import { Activity, CheckCircle2, AlertTriangle, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Daily operational health banner — fetches {@code /dashboard/operational-health}
 * and renders today's sales/cash/bank activity counts with a colour level.
 * Backed by the same backend evaluator the scheduled daily-monitor uses, so the
 * dashboard ↔ scheduled alert ↔ Telegram message agree on the state.
 */
export default function OperationalHealthWidget() {
  const { t } = useTranslation();
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'op-health'],
    queryFn: () => dashboardApi.operationalHealth().then(r => r.data as {
      date: string; level: 'green' | 'yellow' | 'red'; saleCount: number;
      cashCount: number; bankCount: number; missing: string[];
    }),
    retry: false,
    refetchOnWindowFocus: false,
  });
  if (isLoading || !data) return null;

  const palette = {
    green:  { ring: 'border-emerald-400/40', bg: 'bg-emerald-500/10',  text: 'text-emerald-700 dark:text-emerald-400', icon: CheckCircle2 },
    yellow: { ring: 'border-yellow-400/40',  bg: 'bg-yellow-500/10',   text: 'text-yellow-700  dark:text-yellow-400',  icon: AlertTriangle },
    red:    { ring: 'border-red-500/40',     bg: 'bg-red-500/10',      text: 'text-red-700     dark:text-red-400',     icon: AlertCircle },
  }[data.level] || { ring: 'border-slate-400/40', bg: 'bg-slate-500/10', text: 'text-slate-600', icon: Activity };
  const Icon = palette.icon;
  const title = data.level === 'green'
    ? t('opHealth.allGood', { defaultValue: 'All operations active today' })
    : data.level === 'yellow'
      ? t('opHealth.partialMissing', { defaultValue: 'Partial activity — review missing categories' })
      : t('opHealth.criticalMissing', { defaultValue: 'No activity recorded today' });

  return (
    <div className={cn('card border p-4 mb-4', palette.ring, palette.bg)}>
      <div className="flex items-start gap-3">
        <div className={cn('p-2 rounded-xl', palette.bg)}>
          <Icon size={20} className={palette.text} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <div className={cn('font-semibold text-sm', palette.text)}>{title}</div>
            <span className="text-[11px]" style={{ color: 'rgb(var(--color-text-muted))' }}>{data.date}</span>
          </div>
          <div className="flex flex-wrap gap-3 mt-2 text-xs">
            <span className={data.saleCount > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}>
              {t('opHealth.sales', { defaultValue: 'Sales' })}: <b>{data.saleCount}</b>
            </span>
            <span className={data.cashCount > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}>
              {t('opHealth.cash', { defaultValue: 'Cash ops' })}: <b>{data.cashCount}</b>
            </span>
            <span className={data.bankCount > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}>
              {t('opHealth.bank', { defaultValue: 'Bank ops' })}: <b>{data.bankCount}</b>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
