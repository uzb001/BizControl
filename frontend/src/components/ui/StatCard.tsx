import React from 'react';
import { cn } from '@/lib/utils';
import { TrendingUp, TrendingDown } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: string | number;
  icon?: LucideIcon | React.ReactNode;
  change?: number;
  color?: 'blue' | 'green' | 'red' | 'yellow' | 'purple' | 'orange';
  sub?: string;
  loading?: boolean;
}

const colorTokens = {
  blue:   { bg: 'bg-blue-50',   text: 'text-blue-600',   dark: 'dark:bg-blue-500/10 dark:text-blue-400' },
  green:  { bg: 'bg-green-50',  text: 'text-green-600',  dark: 'dark:bg-green-500/10 dark:text-green-400' },
  red:    { bg: 'bg-red-50',    text: 'text-red-600',    dark: 'dark:bg-red-500/10 dark:text-red-400' },
  yellow: { bg: 'bg-yellow-50', text: 'text-yellow-600', dark: 'dark:bg-yellow-500/10 dark:text-yellow-400' },
  purple: { bg: 'bg-purple-50', text: 'text-purple-600', dark: 'dark:bg-purple-500/10 dark:text-purple-400' },
  orange: { bg: 'bg-orange-50', text: 'text-orange-600', dark: 'dark:bg-orange-500/10 dark:text-orange-400' },
};

export default function StatCard({
  label,
  value,
  icon: Icon,
  change,
  color = 'blue',
  sub,
  loading = false,
}: StatCardProps) {
  const tokens = colorTokens[color];

  if (loading) {
    return (
      <div className="stat-card">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 space-y-2.5">
            <div className="skeleton h-3 w-24 rounded" />
            <div className="skeleton h-7 w-32 rounded-md" />
            <div className="skeleton h-3 w-20 rounded" />
          </div>
          <div className="skeleton w-10 h-10 rounded-xl" />
        </div>
      </div>
    );
  }

  const up = (change ?? 0) >= 0;

  return (
    <div className="stat-card card-interactive group">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <p className="stat-label">{label}</p>
          <p className="stat-value num mt-2 truncate">{value}</p>
          {sub && <p className="hint mt-1">{sub}</p>}
          {change !== undefined && (
            <div
              className={cn(
                'inline-flex items-center gap-1 mt-2.5 px-1.5 py-0.5 rounded-md text-xs font-semibold num',
                up
                  ? 'text-green-700 bg-green-500/10 dark:text-green-400'
                  : 'text-red-700 bg-red-500/10 dark:text-red-400'
              )}
            >
              {up ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
              <span>{Math.abs(change).toFixed(1)}%</span>
            </div>
          )}
        </div>
        {Icon && (
          <div className={cn(
            'w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-transform duration-200 group-hover:scale-110 group-hover:-rotate-3',
            tokens.bg, tokens.text, tokens.dark
          )}>
            {React.isValidElement(Icon)
              ? Icon
              : (() => { const C = Icon as LucideIcon; return <C size={18} strokeWidth={2} />; })()}
          </div>
        )}
      </div>
    </div>
  );
}
