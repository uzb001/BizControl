'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { bankApi, companyApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDateTime } from '@/lib/utils';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import StatCard from '@/components/ui/StatCard';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Landmark, TrendingUp, TrendingDown, RotateCcw, Plus, ArrowUpRight, ArrowDownRight } from 'lucide-react';

export default function BankPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [transactionType, setTransactionType] = useState('');
  const [showModal, setShowModal] = useState(false);
  const { register, handleSubmit, reset } = useForm();

  // Per-currency bank balances (kept separate — never auto-converted)
  const { data: balances } = useQuery({
    queryKey: ['balances'],
    queryFn: () => companyApi.balances().then(r => r.data),
    retry: false,
  });
  const bankByCurrency: any[] = balances?.bank ?? [];

  const { data: transactions, isLoading } = useQuery({
    queryKey: ['bank', page, transactionType],
    queryFn: () => bankApi.list({ page, size: 30, transactionType }).then(r => r.data),
  });

  const createMutation = useMutation({
    mutationFn: (data: any) => bankApi.create({ ...data, transactionDate: new Date().toISOString() }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bank'] });
      qc.invalidateQueries({ queryKey: ['balances'] });
      qc.invalidateQueries({ queryKey: ['company'] });
      toast.success(t('bank.createSuccess'));
      setShowModal(false); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  const items = useMemo(() => transactions?.content ?? [], [transactions]);

  const columns: GridColumn<any>[] = [
    { key: 'transactionDate', header: t('common.date'), width: 170, render: (tx) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(tx.transactionDate)}</span> },
    { key: 'transactionType', header: t('common.type'), width: 120, groupable: true, groupValue: (tx) => tx.transactionType === 'income' ? t('common.income') : t('common.expense'), render: (tx) => (
        <span className={tx.transactionType === 'income' ? 'badge-green' : 'badge-red'}>
          {tx.transactionType === 'income' ? <ArrowUpRight size={11} /> : <ArrowDownRight size={11} />}
          {tx.transactionType === 'income' ? t('common.income') : t('common.expense')}
        </span>
      ) },
    { key: 'category', header: t('cashbox.category'), width: 180, groupable: true, groupValue: (tx) => tx.category?.replace(/_/g, ' ') || '—', render: (tx) => <span className="text-sm capitalize" style={{ color: 'rgb(var(--color-text-secondary))' }}>{tx.category?.replace(/_/g, ' ')}</span> },
    { key: 'amount', header: t('common.amount'), width: 160, numeric: true, aggregate: 'sum', aggregateValue: (tx) => parseFloat(tx.amount || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (tx) => (
        <span className={`font-semibold num ${tx.transactionType === 'income' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
          {tx.transactionType === 'income' ? '+' : '-'}{formatMoney(tx.amount)}
        </span>
      ) },
    { key: 'note', header: t('common.note'), width: 260, render: (tx) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{tx.note}</span> },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">{t('bank.title')}</h1>
        <div className="flex gap-2">
          <ExportButton permission="bank.export" filename="bank" fetcher={() => exportApi.bank({ transactionType })} />
          <button onClick={() => { reset(); setShowModal(true); }} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('bank.addTransaction')}</button>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {bankByCurrency.length > 0 ? (
          bankByCurrency.map((b: any) => (
            <StatCard key={b.currency} label={`${t('bank.bankBalance')} (${b.currency})`} value={formatMoney(b.balance, b.currency)} icon={Landmark} color="blue" />
          ))
        ) : (
          <StatCard label={`${t('bank.bankBalance')} (${balances?.base || 'UZS'})`} value={formatMoney(0, balances?.base || 'UZS')} icon={Landmark} color="blue" />
        )}
        <StatCard
          label={t('cashbox.pageIncome')}
          value={formatMoney(items.filter((tx: any) => tx.transactionType === 'income').reduce((s: number, tx: any) => s + parseFloat(tx.amount), 0))}
          icon={TrendingUp} color="green"
        />
        <StatCard
          label={t('cashbox.pageExpense')}
          value={formatMoney(items.filter((tx: any) => tx.transactionType === 'expense').reduce((s: number, tx: any) => s + parseFloat(tx.amount), 0))}
          icon={TrendingDown} color="red"
        />
      </div>

      <div className="filter-bar">
        <select className="filter-input" value={transactionType} onChange={e => { setTransactionType(e.target.value); setPage(0); }}>
          <option value="">{t('common.allTypes')}</option>
          <option value="income">{t('common.income')}</option>
          <option value="expense">{t('common.expense')}</option>
        </select>
        <button onClick={() => { setTransactionType(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="bank"
        columns={columns}
        rows={items}
        getRowId={(tx: any) => tx.id}
        loading={isLoading}
        groupable
        emptyState={
          <EmptyState icon={Landmark} title={t('bank.noTransactions')}
            action={<button onClick={() => setShowModal(true)} className="btn-primary btn-sm">{t('bank.addTransaction')}</button>} />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={transactions?.totalPages} totalElements={transactions?.totalElements} size={30} onChange={setPage} />
      )}

      <Modal open={showModal} onClose={() => { setShowModal(false); reset(); }} title={t('bank.addTransaction')} size="sm">
        <form onSubmit={handleSubmit(data => createMutation.mutate(data))} className="space-y-3">
          <div>
            <label className="label">{t('common.type')}</label>
            <select {...register('transactionType')} className="input">
              <option value="income">{t('common.income')}</option>
              <option value="expense">{t('common.expense')}</option>
            </select>
          </div>
          <div>
            <label className="label">{t('cashbox.category')}</label>
            <input {...register('category')} className="input" placeholder="e.g. supplier_payment, sale_payment" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">{t('common.amount')} *</label>
              <input {...register('amount', { required: true })} type="number" min="0.01" step="0.01" className="input" />
            </div>
            <div>
              <label className="label">{t('common.currency')}</label>
              <select {...register('currency')} className="input" defaultValue="UZS">
                <option value="UZS">UZS</option>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
              </select>
            </div>
          </div>
          <div>
            <label className="label">{t('common.note')}</label>
            <textarea {...register('note')} className="input" rows={2} />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setShowModal(false); reset(); }} className="btn-secondary">{t('common.cancel')}</button>
            <button type="submit" disabled={createMutation.isPending} className="btn-primary">
              {createMutation.isPending ? t('common.saving') : t('common.save')}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
