'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { debtsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDate, cn } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import StatCard from '@/components/ui/StatCard';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useTranslation } from 'react-i18next';
import { Users, Truck, ClipboardList, AlertTriangle, RotateCcw } from 'lucide-react';

export default function DebtsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'customer' | 'supplier'>('customer');
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [paymentDebt, setPaymentDebt] = useState<any>(null);
  const [payAmount, setPayAmount] = useState('');
  const [payMethod, setPayMethod] = useState('cash');
  const [payNote, setPayNote] = useState('');

  const { data: debts, isLoading } = useQuery({
    queryKey: ['debts', tab, page, status],
    queryFn: () => debtsApi.list({ debtType: tab, page, size: 20, status: status || undefined }).then(r => r.data),
  });

  const paymentMutation = useMutation({
    mutationFn: ({ id, data }: any) => debtsApi.addPayment(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['debts'] });
      toast.success(t('debts.paymentAdded'));
      setPaymentDebt(null); setPayAmount(''); setPayNote('');
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  const items = useMemo(() => debts?.content ?? [], [debts]);
  const totalDebt = items.reduce((s: number, d: any) => s + parseFloat(d.remainingAmount), 0) || 0;

  const columns: GridColumn<any>[] = [
    { key: 'party', header: tab === 'customer' ? t('common.customer') : t('common.supplier'), width: 200,
      render: (d) => <span className="font-medium truncate">{tab === 'customer' ? d.customer?.name : d.supplier?.name}</span> },
    { key: 'relatedDoc', header: t('debts.relatedDoc'), width: 130,
      render: (d) => <span className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>{d.relatedSaleId ? `Sale #${d.relatedSaleId}` : d.relatedPurchaseId ? `PO #${d.relatedPurchaseId}` : '—'}</span> },
    { key: 'originalAmount', header: t('debts.original'), width: 130, numeric: true, aggregate: 'sum', aggregateValue: (d) => parseFloat(d.originalAmount || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (d) => <span style={{ color: 'rgb(var(--color-text-secondary))' }}>{formatMoney(d.originalAmount)}</span> },
    { key: 'paidAmount', header: t('common.paid'), width: 120, numeric: true, aggregate: 'sum', aggregateValue: (d) => parseFloat(d.paidAmount || 0), aggregateRender: (v) => <span className="num font-bold text-green-600 dark:text-green-400">{formatMoney(v)}</span>, render: (d) => <span className="text-green-600 dark:text-green-400">{formatMoney(d.paidAmount)}</span> },
    { key: 'remainingAmount', header: t('debts.remaining'), width: 130, numeric: true, aggregate: 'sum', aggregateValue: (d) => parseFloat(d.remainingAmount || 0), aggregateRender: (v) => <span className="num font-bold text-red-600 dark:text-red-400">{formatMoney(v)}</span>, render: (d) => <span className={`font-semibold ${parseFloat(d.remainingAmount) > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>{formatMoney(d.remainingAmount)}</span> },
    { key: 'currency', header: t('common.currency'), width: 84, groupable: true, groupValue: (d) => d.currency, render: (d) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{d.currency}</span> },
    { key: 'dueDate', header: t('debts.dueDate'), width: 120, render: (d) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{d.dueDate ? formatDate(d.dueDate) : '—'}</span> },
    { key: 'status', header: t('common.status'), width: 100, groupable: true, groupValue: (d) => d.status, render: (d) => <StatusBadge status={d.status} /> },
    { key: 'actions', header: t('common.actions'), width: 90, render: (d) => (
        d.status !== 'closed'
          ? <button onClick={() => { setPaymentDebt(d); setPayAmount(d.remainingAmount); }} className="btn-primary btn-sm">{t('common.pay')}</button>
          : null
      ) },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">{t('debts.title')}</h1>
        <ExportButton permission="debts.export" filename={`debts_${tab}`} fetcher={() => exportApi.debts({ debtType: tab, status })} />
      </div>

      {/* Tabs */}
      <div className="segment mb-5">
        <button onClick={() => { setTab('customer'); setPage(0); }} className={cn('segment-item gap-1.5', tab === 'customer' && 'active')}>
          <Users size={14} /> {t('debts.customerDebts')}
        </button>
        <button onClick={() => { setTab('supplier'); setPage(0); }} className={cn('segment-item gap-1.5', tab === 'supplier' && 'active')}>
          <Truck size={14} /> {t('debts.supplierDebts')}
        </button>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <StatCard
          label={tab === 'customer' ? t('debts.totalCustomerDebt') : t('debts.totalSupplierDebt')}
          value={formatMoney(totalDebt)}
          icon={tab === 'customer' ? Users : Truck}
          color={tab === 'customer' ? 'yellow' : 'red'}
        />
        <StatCard label={t('debts.openCount')} value={items.filter((d: any) => d.status === 'open').length || 0} icon={ClipboardList} color="blue" />
        <StatCard label={t('debts.partialCount')} value={items.filter((d: any) => d.status === 'partial').length || 0} icon={AlertTriangle} color="yellow" />
      </div>

      <div className="filter-bar">
        <select className="filter-input" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('debts.allStatus')}</option>
          <option value="open">{t('common.open')}</option>
          <option value="partial">{t('common.partial')}</option>
          <option value="overdue">{t('common.overdue')}</option>
          <option value="closed">{t('common.closed')}</option>
        </select>
        <button onClick={() => { setStatus(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="debts"
        columns={columns}
        rows={items}
        getRowId={(d: any) => d.id}
        loading={isLoading}
        groupable
        emptyState={<EmptyState icon={ClipboardList} title={t('debts.noDebts')} description={t('debts.allClear')} />}
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={debts?.totalPages} totalElements={debts?.totalElements} size={20} onChange={setPage} />
      )}

      {/* Payment Modal */}
      <Modal open={!!paymentDebt} onClose={() => setPaymentDebt(null)} title={t('debts.addPayment')} size="sm">
        <div className="space-y-3">
          <div className="rounded-lg p-3 text-sm" style={{ backgroundColor: 'rgb(var(--color-surface-2))' }}>
            <p><strong>{t('debts.remaining')}:</strong> {formatMoney(paymentDebt?.remainingAmount)}</p>
          </div>
          <div>
            <label className="label">{t('common.amount')} *</label>
            <input type="number" min="0.01" max={paymentDebt?.remainingAmount} step="0.01" className="input" value={payAmount} onChange={e => setPayAmount(e.target.value)} />
          </div>
          <div>
            <label className="label">{t('debts.paymentMethod')}</label>
            <select className="input" value={payMethod} onChange={e => setPayMethod(e.target.value)}>
              <option value="cash">{t('common.cash')}</option>
              <option value="bank">{t('common.bank')}</option>
            </select>
          </div>
          <div>
            <label className="label">{t('common.note')}</label>
            <input className="input" value={payNote} onChange={e => setPayNote(e.target.value)} placeholder={t('common.optionalNote')} />
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setPaymentDebt(null)} className="btn-secondary">{t('common.cancel')}</button>
            <button
              onClick={() => paymentMutation.mutate({ id: paymentDebt.id, data: { amount: parseFloat(payAmount), paymentMethod: payMethod, note: payNote } })}
              disabled={paymentMutation.isPending || !payAmount}
              className="btn-primary"
            >
              {paymentMutation.isPending ? t('debts.recording') : t('debts.addPayment')}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
