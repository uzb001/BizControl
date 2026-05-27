'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { salesApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDateTime } from '@/lib/utils';
import { PaymentBadge, StatusBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import { ConfirmModal } from '@/components/ui/Modal';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import FilterPresets from '@/components/ui/FilterPresets';
import { useFilterPresets } from '@/lib/useTableFilters';
import { useTranslation } from 'react-i18next';
import { ShoppingCart, Eye, Ban, RotateCcw, Plus } from 'lucide-react';

export default function SalesPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [sortBy, setSortBy] = useState('saleDate');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [cancelId, setCancelId] = useState<number | null>(null);

  const { data: sales, isLoading } = useQuery({
    queryKey: ['sales', page, search, paymentStatus, paymentMethod, fromDate, toDate, sortBy, sortDir],
    queryFn: () => salesApi.list({ page, size: 20, search, paymentStatus, paymentMethod, fromDate, toDate, sortBy, sortDir }).then(r => r.data),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => salesApi.cancel(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sales'] });
      toast.success(t('sales.cancelled'));
      setCancelId(null);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.generic')),
  });

  const { presets, save: savePreset, remove: deletePreset } = useFilterPresets('sales');
  const currentFilters = () => ({ search, paymentStatus, paymentMethod, fromDate, toDate, sortBy, sortDir });
  const applyView = (f: Record<string, string>) => {
    setSearch(f.search || ''); setPaymentStatus(f.paymentStatus || ''); setPaymentMethod(f.paymentMethod || '');
    setFromDate(f.fromDate || ''); setToDate(f.toDate || '');
    setSortBy(f.sortBy || 'saleDate'); setSortDir((f.sortDir as 'asc' | 'desc') || 'desc'); setPage(0);
  };

  const items = useMemo(() => sales?.content ?? [], [sales]);
  const sums = useMemo(() => items.reduce((acc: any, r: any) => ({
    total: acc.total + parseFloat(r.totalAmount || 0),
    paid: acc.paid + parseFloat(r.paidAmount || 0),
    unpaid: acc.unpaid + parseFloat(r.unpaidAmount || 0),
  }), { total: 0, paid: 0, unpaid: 0 }), [items]);

  const columns: GridColumn<any>[] = [
    { key: 'saleNumber', header: t('sales.saleNum'), width: 150, render: (s) => <span className="font-mono text-xs" style={{ color: 'rgb(var(--color-text-secondary))' }}>{s.saleNumber}</span> },
    { key: 'customer', header: t('sales.customer'), width: 190, groupable: true, groupValue: (s) => s.customer?.name || t('sales.walkIn'), render: (s) => s.customer?.name
        ? <span className="text-sm font-medium truncate">{s.customer.name}</span>
        : <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('sales.walkIn')}</span> },
    { key: 'saleDate', header: t('common.date'), width: 150, sortable: true, sortKey: 'saleDate', render: (s) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(s.saleDate)}</span> },
    { key: 'totalAmount', header: t('common.total'), width: 130, numeric: true, sortable: true, sortKey: 'totalAmount', aggregate: 'sum', aggregateValue: (s) => parseFloat(s.totalAmount || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (s) => <span className="font-semibold">{formatMoney(s.totalAmount)}</span> },
    { key: 'paidAmount', header: t('common.paid'), width: 120, numeric: true, aggregate: 'sum', aggregateValue: (s) => parseFloat(s.paidAmount || 0), aggregateRender: (v) => <span className="num font-bold text-green-600 dark:text-green-400">{formatMoney(v)}</span>, render: (s) => <span className="text-sm text-green-600 dark:text-green-400">{formatMoney(s.paidAmount)}</span> },
    { key: 'unpaidAmount', header: t('common.unpaid'), width: 120, numeric: true, aggregate: 'sum', aggregateValue: (s) => parseFloat(s.unpaidAmount || 0), aggregateRender: (v) => <span className="num font-bold text-red-600 dark:text-red-400">{formatMoney(v)}</span>, render: (s) => <span className={`text-sm font-medium ${s.unpaidAmount > 0 ? 'text-red-600 dark:text-red-400' : ''}`} style={s.unpaidAmount > 0 ? undefined : { color: 'rgb(var(--color-text-muted))' }}>{s.unpaidAmount > 0 ? formatMoney(s.unpaidAmount) : '—'}</span> },
    { key: 'paymentMethod', header: t('sales.method'), width: 92, groupable: true, groupValue: (s) => s.paymentMethod, render: (s) => <span className="text-xs capitalize" style={{ color: 'rgb(var(--color-text-muted))' }}>{s.paymentMethod}</span> },
    { key: 'payment', header: t('common.payment'), width: 110, render: (s) => <PaymentBadge paid={s.paidAmount} total={s.totalAmount} /> },
    { key: 'status', header: t('common.status'), width: 100, groupable: true, groupValue: (s) => s.status, render: (s) => <StatusBadge status={s.status} /> },
    {
      key: 'actions', header: t('common.actions'), width: 110,
      render: (s) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => router.push(`/sales/${s.id}`)} className="btn-icon !w-7 !h-7" title={t('common.view')}><Eye size={14} /></button>
          {s.status !== 'cancelled' && (
            <button onClick={() => setCancelId(s.id)} className="btn-icon !w-7 !h-7 hover:!text-red-600" title={t('common.cancel')}><Ban size={14} /></button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">
          {t('nav.sales')}
          {sales?.totalElements != null && <span className="ml-2 text-sm font-normal" style={{ color: 'rgb(var(--color-text-muted))' }}>({sales.totalElements})</span>}
        </h1>
        <div className="flex gap-2">
          <FilterPresets presets={presets} onApply={applyView} onSave={(n) => savePreset(n, currentFilters() as Record<string, string>)} onDelete={deletePreset} />
          <ExportButton permission="sales.export" filename="sales" fetcher={() => exportApi.sales({ search, paymentStatus, paymentMethod, fromDate, toDate })} />
          <button onClick={() => router.push('/sales/new')} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('sales.newSale')}</button>
        </div>
      </div>

      <div className="filter-bar">
        <input className="filter-input" placeholder={t('sales.searchPlaceholder')} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        <select className="filter-input" value={paymentStatus} onChange={e => { setPaymentStatus(e.target.value); setPage(0); }}>
          <option value="">{t('sales.allPayments')}</option>
          <option value="paid">{t('common.paid')}</option>
          <option value="partial">{t('common.partial')}</option>
          <option value="unpaid">{t('common.unpaid')}</option>
        </select>
        <select className="filter-input" value={paymentMethod} onChange={e => { setPaymentMethod(e.target.value); setPage(0); }}>
          <option value="">{t('sales.allMethods')}</option>
          <option value="cash">{t('common.cash')}</option>
          <option value="bank">{t('common.bank')}</option>
          <option value="debt">{t('common.debt')}</option>
        </select>
        <div className="flex items-center gap-2">
          <input type="date" className="filter-input" value={fromDate} onChange={e => { setFromDate(e.target.value); setPage(0); }} />
          <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>—</span>
          <input type="date" className="filter-input" value={toDate} onChange={e => { setToDate(e.target.value); setPage(0); }} />
        </div>
        <button onClick={() => { setSearch(''); setPaymentStatus(''); setPaymentMethod(''); setFromDate(''); setToDate(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="sales"
        columns={columns}
        rows={items}
        getRowId={(s: any) => s.id}
        loading={isLoading}
        groupable
        sortBy={sortBy}
        sortDir={sortDir}
        onSortChange={(key, dir) => { setSortBy(key); setSortDir(dir); setPage(0); }}
        onRowOpen={(s: any) => router.push(`/sales/${s.id}`)}
        rowClassName={(s: any) => s.status === 'cancelled' ? 'opacity-55' : ''}
        emptyState={
          <EmptyState icon={ShoppingCart} title={t('sales.emptyTitle')} description={t('sales.emptyDesc')}
            action={<button onClick={() => router.push('/sales/new')} className="btn-primary btn-sm">{t('sales.newSale')}</button>} />
        }
      />

      {/* Page totals summary */}
      {items.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-x-8 gap-y-2 px-4 py-3 rounded-xl text-sm"
             style={{ backgroundColor: 'rgb(var(--color-surface-2))', border: '1px solid rgb(var(--color-border))' }}>
          <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.total')} ({items.length})</span>
          <span className="font-semibold num">{formatMoney(sums.total)}</span>
          <span className="num text-green-600 dark:text-green-400">{t('common.paid')}: {formatMoney(sums.paid)}</span>
          <span className="num text-red-600 dark:text-red-400">{t('common.unpaid')}: {formatMoney(sums.unpaid)}</span>
        </div>
      )}

      {items.length > 0 && (
        <Pagination page={page} totalPages={sales?.totalPages} totalElements={sales?.totalElements} size={20} onChange={setPage} />
      )}

      <ConfirmModal
        open={!!cancelId}
        onClose={() => setCancelId(null)}
        onConfirm={() => cancelId && cancelMutation.mutate(cancelId)}
        title={t('sales.cancelTitle')}
        message={t('sales.cancelConfirm')}
        danger
      />
    </div>
  );
}
