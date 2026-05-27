'use client';

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, Circle, ChevronDown, ChevronUp, X } from 'lucide-react';
import Link from 'next/link';
import { cn } from '@/lib/utils';

interface ChecklistItem {
  id: string;
  labelKey: string;
  href: string;
}

const ITEMS: ChecklistItem[] = [
  { id: 'profile',   labelKey: 'onboarding.completeProfile',  href: '/settings' },
  { id: 'product',   labelKey: 'onboarding.addProduct',       href: '/products' },
  { id: 'customer',  labelKey: 'onboarding.addCustomer',      href: '/customers' },
  { id: 'supplier',  labelKey: 'onboarding.addSupplier',      href: '/suppliers' },
  { id: 'purchase',  labelKey: 'onboarding.firstPurchase',    href: '/purchases/new' },
  { id: 'sale',      labelKey: 'onboarding.firstSale',        href: '/sales/new' },
  { id: 'dashboard', labelKey: 'onboarding.viewDashboard',    href: '/dashboard' },
  { id: 'report',    labelKey: 'onboarding.exportReport',     href: '/reports' },
];

const STORAGE_KEY = 'bizcontrol_onboarding';

function loadState(): Record<string, boolean> {
  if (typeof window === 'undefined') return {};
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
  } catch {
    return {};
  }
}

function saveState(s: Record<string, boolean>) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
}

export default function OnboardingChecklist() {
  const { t } = useTranslation();
  const [checked, setChecked] = useState<Record<string, boolean>>({});
  const [open, setOpen] = useState(true);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const state = loadState();
    setChecked(state);
    if (state.__dismissed) setDismissed(true);
  }, []);

  const toggle = (id: string) => {
    const next = { ...checked, [id]: !checked[id] };
    setChecked(next);
    saveState(next);
  };

  const dismiss = () => {
    const next = { ...checked, __dismissed: true };
    setChecked(next);
    saveState(next);
    setDismissed(true);
  };

  if (dismissed) return null;

  const completedCount = ITEMS.filter(i => checked[i.id]).length;
  const totalCount = ITEMS.length;
  const pct = Math.round((completedCount / totalCount) * 100);

  if (completedCount === totalCount) {
    return null;
  }

  return (
    <div className="card mb-6 overflow-hidden">
      {/* Header */}
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between p-4 hover:bg-[rgb(var(--color-border-subtle))] transition-colors"
      >
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
            <CheckCircle2 size={16} className="text-white" />
          </div>
          <div className="text-left">
            <p className="text-sm font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
              {t('onboarding.title')}
            </p>
            <p className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
              {completedCount}/{totalCount} {t('onboarding.complete')}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {/* Progress bar */}
          <div className="w-24 hidden sm:block">
            <div className="h-1.5 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-600 rounded-full transition-all duration-500"
                style={{ width: `${pct}%` }}
              />
            </div>
            <p className="text-xs text-right mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>{pct}%</p>
          </div>
          {open ? <ChevronUp size={16} style={{ color: 'rgb(var(--color-text-muted))' }} /> : <ChevronDown size={16} style={{ color: 'rgb(var(--color-text-muted))' }} />}
        </div>
      </button>

      {open && (
        <div style={{ borderTop: '1px solid rgb(var(--color-border))' }}>
          <div className="p-3 space-y-0.5">
            {ITEMS.map(item => (
              <div key={item.id} className="flex items-center gap-2">
                <button
                  onClick={() => toggle(item.id)}
                  className="shrink-0 transition-colors"
                  style={{ color: checked[item.id] ? '#2563eb' : 'rgb(var(--color-text-muted))' }}
                >
                  {checked[item.id]
                    ? <CheckCircle2 size={18} />
                    : <Circle size={18} />
                  }
                </button>
                <Link
                  href={item.href}
                  className={cn(
                    'flex-1 checklist-item',
                    checked[item.id] ? 'done' : ''
                  )}
                >
                  <span className="checklist-label">{t(item.labelKey)}</span>
                </Link>
              </div>
            ))}
          </div>
          <div
            className="px-4 py-3 flex justify-end"
            style={{ borderTop: '1px solid rgb(var(--color-border))' }}
          >
            <button
              onClick={dismiss}
              className="flex items-center gap-1.5 text-xs text-[rgb(var(--color-text-muted))] hover:text-[rgb(var(--color-text-secondary))] transition-colors"
            >
              <X size={12} />
              {t('onboarding.dismiss')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
