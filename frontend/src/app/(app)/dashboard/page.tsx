'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { dashboardApi, alertsApi } from '@/lib/api';
import { formatMoney, formatValue } from '@/lib/utils';
import StatCard from '@/components/ui/StatCard';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { useAuthStore } from '@/store/authStore';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import OnboardingChecklist from '@/components/ui/OnboardingChecklist';
import OperationalHealthWidget from '@/components/dashboard/OperationalHealthWidget';
import {
  ShoppingCart,
  TrendingUp,
  Banknote,
  Building2,
  Users,
  Truck,
  AlertTriangle,
  CreditCard,
  Package,
  PackageSearch,
  UserPlus,
  TruckIcon,
  DollarSign,
  BarChart3,
  ClipboardList,
  AlertCircle,
  Info,
  CheckCircle2,
  Activity,
  Lock,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

const PERIODS = [
  { value: 'today', labelKey: 'reports.today' },
  { value: 'yesterday', labelKey: 'reports.yesterday' },
  { value: 'this_week', labelKey: 'reports.thisWeek' },
  { value: 'this_month', labelKey: 'reports.thisMonth' },
  { value: 'last_month', labelKey: 'reports.lastMonth' },
];

function HealthGauge({ score }: { score: number }) {
  const safe = Number.isFinite(score) ? Math.max(0, Math.min(100, score)) : 0;
  const grade =
    safe >= 90 ? 'A+' : safe >= 80 ? 'A' : safe >= 70 ? 'B' :
    safe >= 60 ? 'C' : safe >= 50 ? 'D' : 'F';

  const color =
    safe >= 70 ? 'text-emerald-400' :
    safe >= 50 ? 'text-yellow-400' : 'text-red-400';

  const ringColor =
    safe >= 70 ? 'stroke-emerald-400' :
    safe >= 50 ? 'stroke-yellow-400' : 'stroke-red-400';

  const radius = 36;
  const circumference = 2 * Math.PI * radius;
  const dash = (safe / 100) * circumference;

  return (
    <div className="flex items-center gap-5">
      <div className="relative w-24 h-24 shrink-0">
        <svg viewBox="0 0 90 90" className="w-24 h-24 -rotate-90">
          <circle cx="45" cy="45" r={radius} fill="none" stroke="rgba(255,255,255,0.1)" strokeWidth="7" />
          <circle
            cx="45" cy="45" r={radius}
            fill="none"
            className={ringColor}
            strokeWidth="7"
            strokeLinecap="round"
            strokeDasharray={`${dash} ${circumference}`}
            style={{ transition: 'stroke-dasharray 0.8s ease' }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className={cn('text-2xl font-bold', color)}>{safe}</span>
          <span className="text-white/60 text-xs">/100</span>
        </div>
      </div>
      <div>
        <div className={cn('text-3xl font-bold', color)}>{grade}</div>
        <div className="text-white/80 text-sm font-medium mt-0.5">
          {safe >= 70 ? 'Excellent' : safe >= 50 ? 'Good' : 'Needs attention'}
        </div>
        <div className="text-white/50 text-xs mt-1">Business Health Score</div>
      </div>
    </div>
  );
}

interface StatConfig {
  key: string;
  show: boolean;
  label: string;
  value: string | number;
  icon: LucideIcon;
  color: 'blue' | 'green' | 'red' | 'yellow';
  change?: number;
  sub?: string;
}

export default function DashboardPage() {
  const { user } = useAuthStore();
  const { can } = usePermission();
  const { t } = useTranslation();
  const [period, setPeriod] = useState('this_month');

  const { data: dash, isLoading } = useQuery({
    queryKey: ['dashboard', period],
    queryFn: () => dashboardApi.get(period).then(r => r.data),
    // A 403 / network error must never crash the page — fall back to empty data.
    retry: false,
  });

  const { data: alerts } = useQuery({
    queryKey: ['alerts'],
    queryFn: () => alertsApi.list().then(r => r.data),
    retry: false,
  });

  const firstName = user?.fullName?.split(' ')[0] ?? '';
  const d = dash ?? {};

  // Each KPI is gated by the permission that governs its underlying data, so a
  // restricted role never sees (or even receives) figures it shouldn't.
  const stats: StatConfig[] = [
    { key: 'sales', show: can('sales.view'), label: t('dashboard.totalSales'), value: formatMoney(d.sales), icon: ShoppingCart, color: 'blue', change: d.salesGrowth },
    { key: 'profit', show: can('sales.view_profit'), label: t('dashboard.totalProfit'), value: formatMoney(d.profit), icon: TrendingUp, color: 'green', change: d.profitGrowth },
    { key: 'cash', show: can('cashbox.view'), label: t('dashboard.cashBalance'), value: formatMoney(d.cashBalance), icon: Banknote, color: 'green' },
    { key: 'bank', show: can('bank.view'), label: t('dashboard.bankBalance'), value: formatMoney(d.bankBalance), icon: Building2, color: 'blue' },
    { key: 'custDebt', show: can('debts.view_customer') || can('customers.view_debt'), label: t('dashboard.customerDebts'), value: formatMoney(d.totalCustomerDebt), icon: Users, color: 'yellow' },
    { key: 'suppDebt', show: can('debts.view_supplier') || can('suppliers.view_debt'), label: t('dashboard.supplierDebts'), value: formatMoney(d.totalSupplierDebt), icon: Truck, color: 'red' },
    { key: 'lowStock', show: can('stock.view') || can('products.view'), label: t('dashboard.lowStockItems'), value: d.lowStockProducts ?? 0, icon: AlertTriangle, color: 'yellow', sub: t('dashboard.productsBelowMin') },
    { key: 'unpaidSales', show: can('sales.view'), label: t('dashboard.unpaidSales'), value: d.unpaidSales ?? 0, icon: CreditCard, color: 'red', sub: t('dashboard.needCollection') },
  ];
  const visibleStats = stats.filter(s => s.show);

  const quickActions = [
    { href: '/products', labelKey: 'dashboard.addProduct', icon: Package, show: can('products.view') },
    { href: '/sales/new', labelKey: 'dashboard.newSale', icon: ShoppingCart, show: can('sales.create') },
    { href: '/purchases/new', labelKey: 'dashboard.newPurchase', icon: PackageSearch, show: can('purchases.create') },
    { href: '/customers', labelKey: 'dashboard.addCustomer', icon: UserPlus, show: can('customers.view') },
    { href: '/suppliers', labelKey: 'dashboard.addSupplier', icon: TruckIcon, show: can('suppliers.view') },
    { href: '/cashbox', labelKey: 'dashboard.addCash', icon: DollarSign, show: can('cashbox.view') },
    { href: '/reports', labelKey: 'dashboard.viewReports', icon: BarChart3, show: can('reports.view') },
    { href: '/debts', labelKey: 'nav.debts', icon: ClipboardList, show: can('debts.view_customer') },
  ].filter(a => a.show);

  const showHealth = can('reports.view') && d.healthScore !== undefined && d.healthScore !== null;
  const hasAnyWidget = visibleStats.length > 0 || quickActions.length > 0 || showHealth;

  return (
    <div className="max-w-7xl mx-auto">
      <OnboardingChecklist />
      {/* Daily operational health (V19) */}
      {can('dashboard.view') && <OperationalHealthWidget />}
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="page-title">{t('dashboard.greeting')}, {firstName}!</h1>
          <p className="text-sm mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>
            {user?.companyName}
          </p>
        </div>
        <div className="segment">
          {PERIODS.map(p => (
            <button
              key={p.value}
              onClick={() => setPeriod(p.value)}
              className={cn('segment-item', period === p.value && 'active')}
            >
              {t(p.labelKey)}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <StatCard key={i} label="" value="" loading />
          ))}
        </div>
      ) : !hasAnyWidget ? (
        <div className="card p-10 flex flex-col items-center justify-center text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center">
            <Lock size={22} style={{ color: 'rgb(var(--color-text-muted))' }} />
          </div>
          <p className="text-sm font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
            {t('dashboard.noAccessTitle', { defaultValue: 'You do not have dashboard access.' })}
          </p>
          <p className="text-xs max-w-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
            {t('dashboard.noAccessBody', { defaultValue: 'Contact your company owner to request access to dashboard widgets.' })}
          </p>
        </div>
      ) : (
        <ErrorBoundary area="dashboard">
          {/* Health Score Banner */}
          {showHealth && (
            <div className="card-hero p-5 mb-6 animate-fade-in-up">
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <HealthGauge score={Number(d.healthScore) || 0} />
                {Array.isArray(d.healthIssues) && d.healthIssues.length > 0 && (
                  <div className="flex-1 max-w-sm space-y-1.5">
                    {d.healthIssues.slice(0, 3).map((issue: unknown, i: number) => (
                      <div key={i} className="flex items-start gap-2 text-xs text-white/70">
                        <AlertCircle size={13} className="shrink-0 mt-0.5 text-yellow-400" />
                        <span>{formatValue(issue)}</span>
                      </div>
                    ))}
                  </div>
                )}
                <Link
                  href="/reports"
                  className="shrink-0 px-3 py-1.5 bg-white/10 hover:bg-white/20 text-white text-xs font-medium rounded-lg transition-colors flex items-center gap-1.5"
                >
                  <Activity size={13} />
                  Full Report
                </Link>
              </div>
            </div>
          )}

          {/* KPI grid — only widgets the role can see */}
          {visibleStats.length > 0 && (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              {visibleStats.map(s => (
                <StatCard
                  key={s.key}
                  label={s.label}
                  value={s.value}
                  icon={s.icon}
                  color={s.color}
                  change={typeof s.change === 'number' && Number.isFinite(s.change) ? s.change : undefined}
                  sub={s.sub}
                />
              ))}
            </div>
          )}

          {/* Quick Actions + Alerts */}
          <div className="grid lg:grid-cols-3 gap-5 animate-fade-in-up">
            {/* Quick Actions */}
            {quickActions.length > 0 && (
              <div className="card p-5">
                <h3 className="section-title mb-4 flex items-center gap-2">
                  <Activity size={15} className="text-blue-500" />
                  {t('dashboard.quickActions')}
                </h3>
                <div className="grid grid-cols-2 gap-2">
                  {quickActions.map(({ href, labelKey, icon: Icon }) => (
                    <Link
                      key={href}
                      href={href}
                      className={cn(
                        'flex items-center gap-2 p-2.5 rounded-lg text-xs font-medium transition-all duration-150',
                        'border text-[rgb(var(--color-text-secondary))]',
                        'hover:border-blue-300 hover:text-blue-600 hover:bg-blue-50/50',
                        'dark:hover:bg-blue-950/20 dark:hover:border-blue-700 dark:hover:text-blue-400'
                      )}
                      style={{ borderColor: 'rgb(var(--color-border))' }}
                    >
                      <Icon size={14} className="shrink-0" />
                      <span className="truncate">{t(labelKey)}</span>
                    </Link>
                  ))}
                </div>
              </div>
            )}

            {/* Alerts */}
            <div className={cn('card p-5', quickActions.length > 0 ? 'lg:col-span-2' : 'lg:col-span-3')}>
              <div className="flex items-center justify-between mb-4">
                <h3 className="section-title flex items-center gap-2">
                  <AlertCircle size={15} className="text-orange-500" />
                  {t('dashboard.alerts')}
                </h3>
                {Array.isArray(alerts) && alerts.length > 0 && (
                  <span className="badge-red">{alerts.length} new</span>
                )}
              </div>
              {!Array.isArray(alerts) || alerts.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <div className="w-12 h-12 bg-green-50 rounded-full flex items-center justify-center mb-3">
                    <CheckCircle2 size={22} className="text-green-500" />
                  </div>
                  <p className="text-sm font-medium" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                    {t('dashboard.noAlerts')}
                  </p>
                </div>
              ) : (
                <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
                  {alerts.map((alert: any) => {
                    const isOutOfStock = alert.alertType?.includes('out_of_stock');
                    const isLowStock = alert.alertType?.includes('low_stock');
                    return (
                      <div
                        key={alert.id}
                        className={cn(
                          'flex items-start gap-3 p-3 rounded-xl border text-sm',
                          isOutOfStock
                            ? 'border-red-200 bg-red-50 dark:border-red-900/40 dark:bg-red-950/20'
                            : isLowStock
                            ? 'border-yellow-200 bg-yellow-50 dark:border-yellow-900/40 dark:bg-yellow-950/20'
                            : 'border-blue-200 bg-blue-50 dark:border-blue-900/40 dark:bg-blue-950/20'
                        )}
                      >
                        <div className={cn(
                          'w-7 h-7 rounded-full flex items-center justify-center shrink-0 mt-0.5',
                          isOutOfStock ? 'bg-red-100' : isLowStock ? 'bg-yellow-100' : 'bg-blue-100'
                        )}>
                          {isOutOfStock
                            ? <AlertTriangle size={13} className="text-red-600" />
                            : isLowStock
                            ? <AlertTriangle size={13} className="text-yellow-600" />
                            : <Info size={13} className="text-blue-600" />
                          }
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="font-medium text-sm leading-snug" style={{ color: 'rgb(var(--color-text-primary))' }}>
                            {formatValue(alert.title)}
                          </p>
                          <p className="text-xs mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>
                            {formatValue(alert.message)}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </ErrorBoundary>
      )}
    </div>
  );
}
