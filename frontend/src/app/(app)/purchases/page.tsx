'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { purchasesApi, exportApi } from '@/lib/api';
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
import { PackageSearch, Eye, Ban, RotateCcw, Plus } from 'lucide-react';

export default function PurchasesPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [sortBy, setSortBy] = useState('purchaseDate');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [cancelId, setCancelId] = useState<number | null>(null);

  const { data: purchases, isLoading } = useQuery({
    queryKey: ['purchases', page, search, paymentStatus, fromDate, toDate, sortBy, sortDir],
    queryFn: () => purchasesApi.list({ page, size: 20, search, paymentStatus, fromDate, toDate, sortBy, sortDir }).then(r => r.data),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => purchasesApi.cancel(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchases'] });
      toast.success('Purchase cancelled');
      setCancelId(null);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  const { presets, save: savePreset, remove: deletePreset } = useFilterPresets('purchases');
  const currentFilters = () => ({ search, paymentStatus, fromDate, toDate, sortBy, sortDir });
  const applyView = (f: Record<string, string>) => {
    setSearch(f.search || ''); setPaymentStatus(f.paymentStatus || '');
    setFromDate(f.fromDate || ''); setToDate(f.toDate || '');
    setSortBy(f.sortBy || 'purchaseDate'); setSortDir((f.sortDir as 'asc' | 'desc') || 'desc'); setPage(0);
  };

  const items = useMemo(() => purchases?.content ?? [], [purchases]);
  const sums = useMemo(() => items.reduce((acc: any, r: any) => ({
    total: acc.total + parseFloat(r.totalAmount || 0),
    paid: acc.paid + parseFloat(r.paidAmount || 0),
    unpaid: acc.unpaid + parseFloat(r.unpaidAmount || 0),
  }), { total: 0, paid: 0, unpaid: 0 }), [items]);

  const columns: GridColumn<any>[] = [
    { key: 'purchaseNumber', header: 'PO #', width: 150, render: (p) => <span className="font-mono text-xs" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.purchaseNumber}</span> },
    { key: 'supplier', header: 'Supplier', width: 190, groupable: true, groupValue: (p) => p.supplier?.name || '—', render: (p) => p.supplier?.name
        ? <span className="text-sm font-medium truncate">{p.supplier.name}</span>
        : <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>—</span> },
    { key: 'purchaseDate', header: 'Date', width: 150, sortable: true, sortKey: 'purchaseDate', render: (p) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(p.purchaseDate)}</span> },
    { key: 'totalAmount', header: 'Total', width: 130, numeric: true, sortable: true, sortKey: 'totalAmount', aggregate: 'sum', aggregateValue: (p) => parseFloat(p.totalAmount || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (p) => <span className="font-semibold">{formatMoney(p.totalAmount)}</span> },
    { key: 'paidAmount', header: 'Paid', width: 120, numeric: true, aggregate: 'sum', aggregateValue: (p) => parseFloat(p.paidAmount || 0), aggregateRender: (v) => <span className="num font-bold text-green-600 dark:text-green-400">{formatMoney(v)}</span>, render: (p) => <span className="text-sm text-green-600 dark:text-green-400">{formatMoney(p.paidAmount)}</span> },
    { key: 'unpaidAmount', header: 'Unpaid', width: 120, numeric: true, aggregate: 'sum', aggregateValue: (p) => parseFloat(p.unpaidAmount || 0), aggregateRender: (v) => <span className="num font-bold text-red-600 dark:text-red-400">{formatMoney(v)}</span>, render: (p) => <span className={`text-sm font-medium ${p.unpaidAmount > 0 ? 'text-red-600 dark:text-red-400' : ''}`} style={p.unpaidAmount > 0 ? undefined : { color: 'rgb(var(--color-text-muted))' }}>{p.unpaidAmount > 0 ? formatMoney(p.unpaidAmount) : '—'}</span> },
    { key: 'paymentMethod', header: 'Method', width: 92, groupable: true, groupValue: (p) => p.paymentMethod, render: (p) => <span className="text-xs capitalize" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.paymentMethod}</span> },
    { key: 'payment', header: 'Payment', width: 110, render: (p) => <PaymentBadge paid={p.paidAmount} total={p.totalAmount} /> },
    { key: 'status', header: 'Status', width: 100, groupable: true, groupValue: (p) => p.status, render: (p) => <StatusBadge status={p.status} /> },
    {
      key: 'actions', header: 'Actions', width: 110,
      render: (p) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => router.push(`/purchases/${p.id}`)} className="btn-icon !w-7 !h-7" title="View"><Eye size={14} /></button>
          {p.status !== 'cancelled' && (
            <button onClick={() => setCancelId(p.id)} className="btn-icon !w-7 !h-7 hover:!text-red-600" title="Cancel"><Ban size={14} /></button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">
          Purchases
          {purchases?.totalElements != null && <span className="ml-2 text-sm font-normal" style={{ color: 'rgb(var(--color-text-muted))' }}>({purchases.totalElements})</span>}
        </h1>
        <div className="flex gap-2">
          <FilterPresets presets={presets} onApply={applyView} onSave={(n) => savePreset(n, currentFilters() as Record<string, string>)} onDelete={deletePreset} />
          <ExportButton permission="purchases.export" filename="purchases" fetcher={() => exportApi.purchases({ search, paymentStatus, fromDate, toDate })} />
          <button onClick={() => router.push('/purchases/new')} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> New Purchase</button>
        </div>
      </div>

      <div className="filter-bar">
        <input className="filter-input" placeholder="Purchase number..." value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        <select className="filter-input" value={paymentStatus} onChange={e => { setPaymentStatus(e.target.value); setPage(0); }}>
          <option value="">All Payments</option>
          <option value="paid">Paid</option>
          <option value="partial">Partial</option>
          <option value="unpaid">Unpaid</option>
        </select>
        <div className="flex items-center gap-2">
          <input type="date" className="filter-input" value={fromDate} onChange={e => { setFromDate(e.target.value); setPage(0); }} />
          <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>—</span>
          <input type="date" className="filter-input" value={toDate} onChange={e => { setToDate(e.target.value); setPage(0); }} />
        </div>
        <button onClick={() => { setSearch(''); setPaymentStatus(''); setFromDate(''); setToDate(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> Reset</button>
      </div>

      <DataGrid
        gridId="purchases"
        columns={columns}
        rows={items}
        getRowId={(p: any) => p.id}
        loading={isLoading}
        groupable
        sortBy={sortBy}
        sortDir={sortDir}
        onSortChange={(key, dir) => { setSortBy(key); setSortDir(dir); setPage(0); }}
        onRowOpen={(p: any) => router.push(`/purchases/${p.id}`)}
        rowClassName={(p: any) => p.status === 'cancelled' ? 'opacity-55' : ''}
        emptyState={
          <EmptyState icon={PackageSearch} title="No purchases found" description="Create your first purchase"
            action={<button onClick={() => router.push('/purchases/new')} className="btn-primary btn-sm">New Purchase</button>} />
        }
      />

      {items.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-x-8 gap-y-2 px-4 py-3 rounded-xl text-sm"
             style={{ backgroundColor: 'rgb(var(--color-surface-2))', border: '1px solid rgb(var(--color-border))' }}>
          <span style={{ color: 'rgb(var(--color-text-muted))' }}>Total ({items.length})</span>
          <span className="font-semibold num">{formatMoney(sums.total)}</span>
          <span className="num text-green-600 dark:text-green-400">Paid: {formatMoney(sums.paid)}</span>
          <span className="num text-red-600 dark:text-red-400">Unpaid: {formatMoney(sums.unpaid)}</span>
        </div>
      )}

      {items.length > 0 && (
        <Pagination page={page} totalPages={purchases?.totalPages} totalElements={purchases?.totalElements} size={20} onChange={setPage} />
      )}

      <ConfirmModal
        open={!!cancelId}
        onClose={() => setCancelId(null)}
        onConfirm={() => cancelId && cancelMutation.mutate(cancelId)}
        title="Cancel Purchase"
        message="This will reverse all stock movements for this purchase. Are you sure?"
        danger
      />
    </div>
  );
}
