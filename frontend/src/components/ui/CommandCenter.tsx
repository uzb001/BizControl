'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { productsApi, customersApi, suppliersApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import {
  Search, Package, Users, Truck, ShoppingCart, LayoutDashboard,
  BarChart2, FileText, Warehouse, CreditCard, Building2, Settings,
  ArrowRight, Keyboard, X, Plus, PackageSearch, Zap, CornerDownLeft,
} from 'lucide-react';

type ResultItem = {
  id: string;
  label: string;
  sublabel?: string;
  category: string;
  href: string;
  icon: React.ReactNode;
};

const NAV_SHORTCUTS = [
  { label: 'Dashboard', href: '/dashboard', icon: <LayoutDashboard className="w-4 h-4" /> },
  { label: 'Products', href: '/products', icon: <Package className="w-4 h-4" /> },
  { label: 'Sales', href: '/sales', icon: <ShoppingCart className="w-4 h-4" /> },
  { label: 'Purchases', href: '/purchases', icon: <Truck className="w-4 h-4" /> },
  { label: 'Customers', href: '/customers', icon: <Users className="w-4 h-4" /> },
  { label: 'Stock', href: '/stock', icon: <Warehouse className="w-4 h-4" /> },
  { label: 'Reports', href: '/reports', icon: <BarChart2 className="w-4 h-4" /> },
  { label: 'Cashbox', href: '/cashbox', icon: <CreditCard className="w-4 h-4" /> },
  { label: 'Bank', href: '/bank', icon: <Building2 className="w-4 h-4" /> },
  { label: 'Import', href: '/import', icon: <FileText className="w-4 h-4" /> },
  { label: 'Settings', href: '/settings', icon: <Settings className="w-4 h-4" /> },
];

const QUICK_ACTIONS = [
  { label: 'New Sale', href: '/sales/new', icon: <ShoppingCart className="w-4 h-4" /> },
  { label: 'New Purchase', href: '/purchases/new', icon: <PackageSearch className="w-4 h-4" /> },
  { label: 'Add Product', href: '/products', icon: <Plus className="w-4 h-4" /> },
  { label: 'Record Cash', href: '/cashbox', icon: <CreditCard className="w-4 h-4" /> },
];

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export default function CommandCenter() {
  const { t } = useTranslation();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const debouncedQ = useDebounce(query.trim(), 250);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        setOpen(o => !o);
      }
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    if (open) {
      setQuery('');
      setSelected(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  const isSearching = debouncedQ.length >= 2;

  const { data: productResults = [], isFetching: pLoading } = useQuery({
    queryKey: ['cc-products', debouncedQ],
    queryFn: () =>
      productsApi.list({ search: debouncedQ, limit: 5 }).then(r =>
        (r.data.content ?? r.data ?? []).slice(0, 5).map((p: any): ResultItem => ({
          id: `p-${p.id}`,
          label: p.name,
          sublabel: p.sku ? `SKU: ${p.sku}` : undefined,
          category: t('nav.products'),
          href: `/products/${p.id}`,
          icon: <Package className="w-4 h-4" />,
        }))
      ),
    enabled: isSearching,
  });

  const { data: customerResults = [] } = useQuery({
    queryKey: ['cc-customers', debouncedQ],
    queryFn: () =>
      customersApi.list({ search: debouncedQ, limit: 5 }).then(r =>
        (r.data.content ?? r.data ?? []).slice(0, 5).map((c: any): ResultItem => ({
          id: `c-${c.id}`,
          label: c.name,
          sublabel: c.phone,
          category: t('nav.customers'),
          href: `/customers/${c.id}`,
          icon: <Users className="w-4 h-4" />,
        }))
      ),
    enabled: isSearching,
  });

  const { data: supplierResults = [] } = useQuery({
    queryKey: ['cc-suppliers', debouncedQ],
    queryFn: () =>
      suppliersApi.list({ search: debouncedQ, limit: 3 }).then(r =>
        (r.data.content ?? r.data ?? []).slice(0, 3).map((s: any): ResultItem => ({
          id: `s-${s.id}`,
          label: s.name,
          sublabel: s.country,
          category: t('nav.suppliers'),
          href: `/suppliers/${s.id}`,
          icon: <Truck className="w-4 h-4" />,
        }))
      ),
    enabled: isSearching,
  });

  const quickItems: ResultItem[] = (!isSearching ? QUICK_ACTIONS : []).map(a => ({
    ...a, id: `qa-${a.href}-${a.label}`, sublabel: undefined, category: t('commandCenter.quickActions', { defaultValue: 'Quick actions' }),
  }));

  const navItems: ResultItem[] = (debouncedQ.length < 2
    ? NAV_SHORTCUTS
    : NAV_SHORTCUTS.filter(n => n.label.toLowerCase().includes(debouncedQ.toLowerCase()))
  ).map(n => ({ ...n, id: `nav-${n.href}`, sublabel: undefined, category: t('commandCenter.navigate') }));

  const allResults: ResultItem[] = [
    ...quickItems,
    ...navItems,
    ...(isSearching ? [...productResults, ...customerResults, ...supplierResults] : []),
  ];

  const grouped = allResults.reduce<Record<string, ResultItem[]>>((acc, item) => {
    (acc[item.category] ||= []).push(item);
    return acc;
  }, {});

  const flatItems = Object.values(grouped).flat();

  const navigate = useCallback((item: ResultItem) => {
    router.push(item.href);
    setOpen(false);
  }, [router]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown') { e.preventDefault(); setSelected(s => Math.min(s + 1, flatItems.length - 1)); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setSelected(s => Math.max(s - 1, 0)); }
      else if (e.key === 'Enter' && flatItems[selected]) { navigate(flatItems[selected]); }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, flatItems, selected, navigate]);

  if (!open) return null;

  const muted = 'rgb(var(--color-text-muted))';

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-[14vh] px-4 animate-fade-in"
      style={{ backgroundColor: 'rgb(15 23 42 / 0.45)', backdropFilter: 'blur(4px)' }}
      onClick={() => setOpen(false)}
    >
      <div
        className="w-full max-w-xl overflow-hidden animate-scale-in"
        style={{
          backgroundColor: 'rgb(var(--color-surface-raised))',
          border: '1px solid rgb(var(--color-border))',
          borderRadius: 'var(--radius-xl)',
          boxShadow: 'var(--shadow-xl)',
        }}
        onClick={e => e.stopPropagation()}
      >
        {/* Search input */}
        <div className="flex items-center gap-3 px-4 py-3.5" style={{ borderBottom: '1px solid rgb(var(--color-border))' }}>
          <Search className="w-5 h-5 shrink-0" style={{ color: muted }} />
          <input
            ref={inputRef}
            className="flex-1 bg-transparent text-sm outline-none"
            style={{ color: 'rgb(var(--color-text-primary))' }}
            placeholder={t('commandCenter.placeholder')}
            value={query}
            onChange={e => { setQuery(e.target.value); setSelected(0); }}
          />
          {query && (
            <button onClick={() => setQuery('')} className="btn-icon !w-7 !h-7"><X className="w-4 h-4" /></button>
          )}
          <kbd className="hidden sm:block">ESC</kbd>
        </div>

        {/* Results */}
        <div className="max-h-[22rem] overflow-y-auto py-2 px-2">
          {flatItems.length === 0 ? (
            <div className="px-4 py-10 text-center text-sm" style={{ color: muted }}>
              {isSearching
                ? t('commandCenter.noResults', { query: debouncedQ })
                : t('commandCenter.placeholder')}
            </div>
          ) : (
            Object.entries(grouped).map(([category, catItems]) => (
              <div key={category} className="mb-1">
                <div className="px-2 py-1.5 text-2xs font-semibold uppercase tracking-wider flex items-center gap-1.5" style={{ color: muted }}>
                  {category === t('commandCenter.quickActions', { defaultValue: 'Quick actions' }) && <Zap className="w-3 h-3" />}
                  {category}
                </div>
                {catItems.map(item => {
                  const idx = flatItems.findIndex(i => i.id === item.id);
                  const isSel = idx === selected;
                  return (
                    <button
                      key={item.id}
                      className={cn(
                        'w-full flex items-center gap-3 px-2.5 py-2 rounded-lg text-left transition-colors',
                      )}
                      style={isSel
                        ? { backgroundColor: 'rgb(var(--color-primary) / 0.12)' }
                        : undefined}
                      onMouseEnter={() => setSelected(idx)}
                      onClick={() => navigate(item)}
                    >
                      <span style={{ color: isSel ? 'rgb(var(--color-primary))' : muted }}>{item.icon}</span>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium truncate" style={{ color: isSel ? 'rgb(var(--color-primary))' : 'rgb(var(--color-text-primary))' }}>
                          {item.label}
                        </div>
                        {item.sublabel && (
                          <div className="text-xs truncate" style={{ color: muted }}>{item.sublabel}</div>
                        )}
                      </div>
                      {isSel
                        ? <CornerDownLeft className="w-3.5 h-3.5 shrink-0" style={{ color: 'rgb(var(--color-primary))' }} />
                        : <ArrowRight className="w-3.5 h-3.5 shrink-0" style={{ color: muted }} />}
                    </button>
                  );
                })}
              </div>
            ))
          )}
          {isSearching && pLoading && (
            <div className="px-4 py-2 text-xs" style={{ color: muted }}>{t('common.loading')}</div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center gap-4 px-4 py-2.5 text-2xs" style={{ borderTop: '1px solid rgb(var(--color-border))', color: muted }}>
          <span className="flex items-center gap-1"><kbd>↑↓</kbd> {t('commandCenter.navigate')}</span>
          <span className="flex items-center gap-1"><kbd>↵</kbd> {t('commandCenter.open')}</span>
          <span className="flex items-center gap-1 ml-auto"><Keyboard className="w-3 h-3" /> Ctrl K</span>
        </div>
      </div>
    </div>
  );
}
