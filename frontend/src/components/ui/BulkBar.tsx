'use client';

import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

interface BulkAction {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  danger?: boolean;
}

interface BulkBarProps {
  count: number;
  onClear: () => void;
  actions: BulkAction[];
}

export default function BulkBar({ count, onClear, actions }: BulkBarProps) {
  if (count === 0) return null;

  return (
    <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 animate-fade-in-up">
      <div
        className="flex items-center gap-3 px-3 py-2 rounded-2xl text-white"
        style={{
          background: 'linear-gradient(180deg, #1f2340 0%, #16182c 100%)',
          border: '1px solid rgb(255 255 255 / 0.1)',
          boxShadow: '0 20px 40px -12px rgb(0 0 0 / 0.5), inset 0 1px 0 0 rgb(255 255 255 / 0.08)',
        }}
      >
        <span className="text-sm font-medium pl-1.5">
          <span className="font-bold" style={{ color: 'rgb(165 180 252)' }}>{count}</span> selected
        </span>
        <div className="w-px h-5" style={{ background: 'rgb(255 255 255 / 0.12)' }} />
        {actions.map(a => (
          <button
            key={a.label}
            onClick={a.onClick}
            className={cn(
              'inline-flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-lg font-medium transition-all',
              a.danger
                ? 'bg-red-500/90 hover:bg-red-500 text-white'
                : 'bg-white/10 hover:bg-white/15 text-white'
            )}
          >
            {a.icon}
            {a.label}
          </button>
        ))}
        <button
          onClick={onClear}
          className="ml-0.5 w-7 h-7 inline-flex items-center justify-center rounded-lg text-white/50 hover:text-white hover:bg-white/10 transition-colors"
          aria-label="Clear selection"
        >
          <X size={16} />
        </button>
      </div>
    </div>
  );
}
