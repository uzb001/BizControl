'use client';

import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, Legend,
} from 'recharts';
import { reportsApi, exportApi, downloadBlob } from '@/lib/api';
import { formatMoney, cn } from '@/lib/utils';
import StatCard from '@/components/ui/StatCard';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import EmptyState from '@/components/ui/EmptyState';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useTranslation } from 'react-i18next';
import {
  ShoppingCart, TrendingUp, TrendingDown, PackageSearch, Users, Truck, Package, PackageX,
  Percent, Download, Lock, AlertOctagon, AlertTriangle, Info, CheckCircle2, ClipboardList,
} from 'lucide-react';

const BRAND = '#6366f1';
const GREEN = '#22c55e';
const AMBER = '#f59e0b';

export default function ReportsPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const PERIODS = [
    { value: 'today',        label: t('reports.today') },
    { value: 'this_week',    label: t('reports.thisWeek') },
    { value: 'this_month',   label: t('reports.thisMonth') },
    { value: 'last_month',   label: t('reports.lastMonth') },
    { value: 'last_3_months',label: t('reports.last3Months') },
    { value: 'this_year',    label: t('reports.thisYear') },
  ];

  const TABS = [
    { id: 'summary',   label: t('reports.tabSummary') },
    { id: 'trend',     label: t('reports.tabTrend') },
    { id: 'health',    label: t('reports.tabHealth') },
    { id: 'stock',     label: t('reports.tabStock') },
    { id: 'low-stock', label: t('reports.tabLowStock') },
  ];
  const [period, setPeriod] = useState('this_month');
  const [activeTab, setActiveTab] = useState('summary');
  const [exporting, setExporting] = useState(false);

  const { data: summary, isLoading } = useQuery({
    queryKey: ['report-summary', period],
    queryFn: () => reportsApi.summary(period).then(r => r.data),
  });

  const { data: trend } = useQuery({
    queryKey: ['monthly-trend'],
    queryFn: () => reportsApi.monthlyTrend(6).then(r => r.data),
    enabled: activeTab === 'trend',
  });

  const { data: health } = useQuery({
    queryKey: ['health-score'],
    queryFn: () => reportsApi.healthScore().then(r => r.data),
    enabled: activeTab === 'health',
  });

  const { data: lowStock } = useQuery({
    queryKey: ['low-stock'],
    queryFn: () => reportsApi.lowStock().then(r => r.data),
    enabled: activeTab === 'low-stock',
  });

  const handleExportSales = async () => {
    setExporting(true);
    try {
      const res = await exportApi.sales({ period });
      downloadBlob(res.data, `sales_${period}.xlsx`);
    } catch { /* handled by interceptor */ } finally { setExporting(false); }
  };

  const QUICK_LINKS = [
    { label: 'Dead Stock', href: '/reports/dead-stock', icon: PackageX, color: 'text-red-600 dark:text-red-400' },
    { label: 'Money Leak', href: '/reports/money-leak', icon: TrendingDown, color: 'text-orange-600 dark:text-orange-400' },
    { label: 'Customer Trust', href: '/reports/customer-scores', icon: Users, color: 'text-blue-600 dark:text-blue-400' },
    { label: 'Daily Close', href: '/daily-close', icon: Lock, color: 'text-purple-600 dark:text-purple-400' },
  ];

  const trendColumns: GridColumn<any>[] = [
    { key: 'month', header: 'Month', width: 120, render: (r) => <span className="font-medium">{r.month}</span> },
    { key: 'sales', header: 'Sales', width: 150, numeric: true, render: (r) => <span className="font-medium text-blue-600 dark:text-blue-400">{formatMoney(r.sales)}</span> },
    { key: 'purchases', header: 'Purchases', width: 150, numeric: true, render: (r) => <span className="text-orange-600 dark:text-orange-400">{formatMoney(r.purchases)}</span> },
    { key: 'profit', header: 'Profit', width: 150, numeric: true, render: (r) => <span className="font-medium text-green-600 dark:text-green-400">{formatMoney(r.profit)}</span> },
    { key: 'margin', header: 'Margin', width: 100, align: 'right', render: (r) => {
        const margin = r.sales > 0 ? ((r.profit / r.sales) * 100).toFixed(1) : '0.0';
        return <span className={`badge text-xs ${parseFloat(margin) >= 20 ? 'badge-green' : parseFloat(margin) >= 10 ? 'badge-yellow' : 'badge-red'}`}>{margin}%</span>;
      } },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Reports</h1>
          <p className="page-subtitle">Financial analytics and business insights</p>
        </div>
        <div className="segment">
          {PERIODS.map(p => (
            <button key={p.value} onClick={() => setPeriod(p.value)} className={cn('segment-item', period === p.value && 'active')}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {/* Smart reports quick links */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
        {QUICK_LINKS.map(item => (
          <button key={item.href} onClick={() => router.push(item.href)} className="card-interactive p-4 text-left group">
            <item.icon className={cn('w-5 h-5 mb-2', item.color)} />
            <div className={cn('text-sm font-semibold group-hover:underline', item.color)}>{item.label}</div>
            <div className="text-xs mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>View report →</div>
          </button>
        ))}
      </div>

      {/* Tabs */}
      <div className="segment mb-5">
        {TABS.map(tab => (
          <button key={tab.id} onClick={() => setActiveTab(tab.id)} className={cn('segment-item', activeTab === tab.id && 'active')}>
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading && activeTab === 'summary' ? <LoadingSpinner /> : (
        <>
          {/* ── SUMMARY TAB ── */}
          {activeTab === 'summary' && (
            <div className="space-y-6">
              <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
                <StatCard label="Total Sales" value={formatMoney(summary?.totalSales)} icon={ShoppingCart} color="blue" />
                <StatCard label="Total Profit" value={formatMoney(summary?.totalProfit)} icon={TrendingUp} color="green" />
                <StatCard label="Total Purchases" value={formatMoney(summary?.totalPurchases)} icon={PackageSearch} color="yellow" />
                <StatCard label="Customer Debts" value={formatMoney(summary?.totalCustomerDebt)} icon={Users} color="yellow" />
                <StatCard label="Supplier Debts" value={formatMoney(summary?.totalSupplierDebt)} icon={Truck} color="red" />
                <StatCard label="Gross Margin" value={`${summary?.grossMargin ?? 0}%`} icon={Percent} color="purple" />
              </div>

              <div className="grid lg:grid-cols-2 gap-6">
                <div className="card p-5">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="section-title">Sales vs Profit</h3>
                    <button onClick={handleExportSales} disabled={exporting} className="btn-secondary btn-sm gap-1.5">
                      <Download size={13} /> {exporting ? 'Exporting...' : 'Export'}
                    </button>
                  </div>
                  <ResponsiveContainer width="100%" height={200}>
                    <BarChart data={[
                      { name: 'Sales', value: parseFloat(summary?.totalSales) || 0 },
                      { name: 'Purchases', value: parseFloat(summary?.totalPurchases) || 0 },
                      { name: 'Profit', value: parseFloat(summary?.totalProfit) || 0 },
                    ]}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--color-border))" />
                      <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000000).toFixed(0)}M`} />
                      <Tooltip formatter={(v: any) => formatMoney(v)} />
                      <Bar dataKey="value" fill={BRAND} radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                <div className="card p-5">
                  <h3 className="section-title mb-4">Daily Trend (Last 7 Days)</h3>
                  {summary?.dailyTrend?.length ? (
                    <ResponsiveContainer width="100%" height={200}>
                      <LineChart data={summary.dailyTrend}>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--color-border))" />
                        <XAxis dataKey="date" tick={{ fontSize: 10 }} tickFormatter={(v: string) => v.slice(5)} />
                        <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000000).toFixed(0)}M`} />
                        <Tooltip formatter={(v: any) => formatMoney(v)} />
                        <Legend />
                        <Line type="monotone" dataKey="sales" stroke={BRAND} strokeWidth={2} dot={false} name="Sales" />
                        <Line type="monotone" dataKey="profit" stroke={GREEN} strokeWidth={2} dot={false} name="Profit" />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="text-sm text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No daily data yet</p>
                  )}
                </div>
              </div>

              <div className="card p-5">
                <h3 className="section-title mb-4">Debt Overview</h3>
                <ResponsiveContainer width="100%" height={160}>
                  <BarChart data={[
                    { name: 'Customer Debt', value: parseFloat(summary?.totalCustomerDebt) || 0 },
                    { name: 'Supplier Debt', value: parseFloat(summary?.totalSupplierDebt) || 0 },
                  ]} layout="vertical">
                    <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000000).toFixed(0)}M`} />
                    <YAxis dataKey="name" type="category" width={110} tick={{ fontSize: 12 }} />
                    <Tooltip formatter={(v: any) => formatMoney(v)} />
                    <Bar dataKey="value" fill={AMBER} radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* ── MONTHLY TREND TAB ── */}
          {activeTab === 'trend' && (
            <div className="space-y-6">
              {!trend ? <LoadingSpinner /> : (
                <>
                  <div className="card p-5">
                    <h3 className="section-title mb-4">6-Month Sales &amp; Profit Trend</h3>
                    <ResponsiveContainer width="100%" height={280}>
                      <LineChart data={trend}>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--color-border))" />
                        <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                        <YAxis tick={{ fontSize: 10 }} tickFormatter={v => `${(v / 1000000).toFixed(0)}M`} />
                        <Tooltip formatter={(v: any) => formatMoney(v)} />
                        <Legend />
                        <Line type="monotone" dataKey="sales" stroke={BRAND} strokeWidth={2} name="Sales" />
                        <Line type="monotone" dataKey="purchases" stroke={AMBER} strokeWidth={2} name="Purchases" />
                        <Line type="monotone" dataKey="profit" stroke={GREEN} strokeWidth={2} name="Profit" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>

                  <DataGrid
                    gridId="report-trend"
                    columns={trendColumns}
                    rows={trend ?? []}
                    getRowId={(r: any) => r.month}
                    toolbar={false}
                    emptyState={<EmptyState icon={TrendingUp} title="No trend data" />}
                  />
                </>
              )}
            </div>
          )}

          {/* ── HEALTH SCORE TAB ── */}
          {activeTab === 'health' && (
            <div className="space-y-6">
              {!health ? <LoadingSpinner /> : (
                <>
                  <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
                    <div className="lg:col-span-1 card p-6 flex flex-col items-center justify-center">
                      <div className={`text-6xl font-black ${
                        health.score >= 80 ? 'text-green-500' :
                        health.score >= 65 ? 'text-blue-500' :
                        health.score >= 50 ? 'text-yellow-500' :
                        health.score >= 35 ? 'text-orange-500' : 'text-red-500'
                      }`}>
                        {health.score}
                      </div>
                      <div className="text-2xl font-bold mt-1" style={{ color: 'rgb(var(--color-text-secondary))' }}>{health.grade}</div>
                      <div className="text-sm mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>{health.label}</div>
                      <div className="w-full rounded-full h-2 mt-3" style={{ backgroundColor: 'rgb(var(--color-border))' }}>
                        <div className={`h-2 rounded-full transition-all ${
                          health.score >= 80 ? 'bg-green-500' :
                          health.score >= 65 ? 'bg-blue-500' :
                          health.score >= 50 ? 'bg-yellow-500' :
                          health.score >= 35 ? 'bg-orange-500' : 'bg-red-500'
                        }`} style={{ width: `${health.score}%` }} />
                      </div>
                    </div>

                    <div className="lg:col-span-3 card p-5">
                      <h3 className="section-title mb-4">Issues Detected</h3>
                      {health.issues?.length === 0 ? (
                        <div className="text-center py-6 text-green-600 dark:text-green-400">
                          <CheckCircle2 className="w-8 h-8 mx-auto mb-2" />
                          <p className="font-medium">No issues detected — great shape!</p>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          {health.issues?.map((issue: any, i: number) => {
                            const Icon = issue.severity === 'HIGH' ? AlertOctagon : issue.severity === 'MEDIUM' ? AlertTriangle : Info;
                            return (
                              <div key={i} className={`flex items-start gap-3 p-3 rounded-lg border ${
                                issue.severity === 'HIGH' ? 'bg-red-500/5 border-red-500/20' :
                                issue.severity === 'MEDIUM' ? 'bg-yellow-500/5 border-yellow-500/20' :
                                'bg-blue-500/5 border-blue-500/20'
                              }`}>
                                <Icon size={16} className={`mt-0.5 shrink-0 ${issue.severity === 'HIGH' ? 'text-red-600 dark:text-red-400' : issue.severity === 'MEDIUM' ? 'text-yellow-600 dark:text-yellow-400' : 'text-blue-600 dark:text-blue-400'}`} />
                                <div>
                                  <p className="text-sm font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>{issue.title}</p>
                                  {issue.description && <p className="text-xs mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>{issue.description}</p>}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
                    {[
                      { label: 'Low Stock Items', value: health.signals?.lowStockCount, icon: Package, warn: health.signals?.lowStockCount > 0 },
                      { label: 'Dead Stock Items', value: health.signals?.deadStockCount, icon: PackageX, warn: health.signals?.deadStockCount > 0 },
                      { label: 'Unpaid Sales', value: health.signals?.unpaidSales, icon: ClipboardList, warn: health.signals?.unpaidSales > 5 },
                      { label: 'Customer Debt', value: formatMoney(health.signals?.customerDebt), icon: Users, warn: false },
                      { label: 'Supplier Debt', value: formatMoney(health.signals?.supplierDebt), icon: Truck, warn: false },
                      { label: 'Profit Trend', value: health.signals?.profitDeclining ? 'Declining' : 'Stable', icon: TrendingUp, warn: health.signals?.profitDeclining },
                    ].map(s => (
                      <div key={s.label} className={`stat-card ${s.warn ? 'border-l-4 border-orange-400' : ''}`}>
                        <p className="stat-label flex items-center gap-1.5"><s.icon size={13} /> {s.label}</p>
                        <p className={`stat-value text-lg mt-1 ${s.warn ? 'text-orange-600 dark:text-orange-400' : ''}`}>{s.value}</p>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}

          {/* ── STOCK TAB ── */}
          {activeTab === 'stock' && <StockReportTab />}

          {/* ── LOW STOCK TAB ── */}
          {activeTab === 'low-stock' && <LowStockTab data={lowStock} />}
        </>
      )}
    </div>
  );
}

function StockReportTab() {
  const { data: stock, isLoading } = useQuery({
    queryKey: ['stock-report'],
    queryFn: () => reportsApi.stock().then(r => r.data),
  });

  const rows = useMemo(() => stock ?? [], [stock]);
  const totalValue = rows.reduce(
    (s: number, p: any) => s + parseFloat(p.currentStock) * parseFloat(p.purchasePrice || 0), 0
  ) || 0;

  const columns: GridColumn<any>[] = [
    { key: 'name', header: 'Product', width: 240, render: (p) => <span className="font-medium truncate">{p.name}</span> },
    { key: 'category', header: 'Category', width: 160, render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.category?.name || '—'}</span> },
    { key: 'currentStock', header: 'Stock', width: 110, numeric: true, render: (p) => <span className="font-medium num">{p.currentStock}</span> },
    { key: 'unit', header: 'Unit', width: 90, render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.unit}</span> },
    { key: 'purchasePrice', header: 'Buy Price', width: 140, numeric: true, render: (p) => <span style={{ color: 'rgb(var(--color-text-secondary))' }}>{formatMoney(p.purchasePrice)}</span> },
    { key: 'stockValue', header: 'Stock Value', width: 160, numeric: true, render: (p) => <span className="font-semibold num">{formatMoney(parseFloat(p.currentStock) * parseFloat(p.purchasePrice || 0))}</span> },
  ];

  return (
    <div>
      <div className="mb-4">
        <StatCard label="Total Stock Value" value={formatMoney(totalValue)} icon={Package} color="blue" />
      </div>
      <DataGrid
        gridId="report-stock"
        columns={columns}
        rows={rows}
        getRowId={(p: any) => p.id}
        loading={isLoading}
        emptyState={<EmptyState icon={Package} title="No stock data" />}
      />
      {rows.length > 0 && (
        <div className="mt-3 flex items-center justify-end gap-3 px-4 py-3 rounded-xl text-sm"
             style={{ backgroundColor: 'rgb(var(--color-surface-2))', border: '1px solid rgb(var(--color-border))' }}>
          <span style={{ color: 'rgb(var(--color-text-muted))' }}>Total stock value</span>
          <span className="font-bold num">{formatMoney(totalValue)}</span>
        </div>
      )}
    </div>
  );
}

function LowStockTab({ data }: { data: any[] | undefined }) {
  const rows = useMemo(() => data ?? [], [data]);
  const columns: GridColumn<any>[] = [
    { key: 'name', header: 'Product', width: 260, render: (p) => <span className="font-medium truncate">{p.name}</span> },
    { key: 'currentStock', header: 'Current Stock', width: 140, numeric: true, render: (p) => <span className={`font-bold num ${p.currentStock === 0 ? 'text-red-600 dark:text-red-400' : 'text-yellow-600 dark:text-yellow-400'}`}>{p.currentStock}</span> },
    { key: 'minStockLevel', header: 'Min Level', width: 120, numeric: true, render: (p) => <span className="num" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.minStockLevel}</span> },
    { key: 'unit', header: 'Unit', width: 90, render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.unit}</span> },
    { key: 'status', header: 'Status', width: 130, render: (p) => p.currentStock === 0 ? <span className="badge-red">Out of stock</span> : <span className="badge-yellow">Low stock</span> },
  ];

  return (
    <div>
      <p className="text-sm mb-4" style={{ color: 'rgb(var(--color-text-muted))' }}>
        {rows.length} products with low or zero stock
      </p>
      <DataGrid
        gridId="report-low-stock"
        columns={columns}
        rows={rows}
        getRowId={(p: any) => p.id}
        rowClassName={(p: any) => p.currentStock === 0 ? 'dgrid-row-danger' : 'dgrid-row-warn'}
        emptyState={<EmptyState icon={CheckCircle2} title="All stock levels healthy" />}
      />
    </div>
  );
}
