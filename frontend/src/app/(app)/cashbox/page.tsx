'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { cashApi, companyApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDateTime } from '@/lib/utils';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import StatCard from '@/components/ui/StatCard';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import { Wallet, TrendingUp, TrendingDown, RotateCcw, Plus, ArrowUpRight, ArrowDownRight } from 'lucide-react';

const INCOME_CATS = ['sale_payment', 'customer_debt_payment', 'owner_investment', 'bank_withdrawal', 'other_income'];
const EXPENSE_CATS = ['supplier_payment', 'purchase_payment', 'rent', 'salary', 'transport', 'advertising', 'personal_withdrawal', 'other_expense'];

export default function CashboxPage() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const { can } = usePermission();
  const [page, setPage] = useState(0);
  const [transactionType, setTransactionType] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [showModal, setShowModal] = useState(false);
  // The cashbox is the cash ledger; bank movements live on the Bank page,
  // so every transaction created/listed here is a cash transaction.
  const paymentSource = 'cash';

  // Per-currency balances (UZS, USD, … kept separate — never auto-converted)
  const { data: balances } = useQuery({
    queryKey: ['balances'],
    queryFn: () => companyApi.balances().then(r => r.data),
    retry: false,
  });
  const cashByCurrency: any[] = balances?.cash ?? [];

  const { data: transactions, isLoading } = useQuery({
    queryKey: ['cash', page, transactionType, paymentSource, fromDate, toDate],
    queryFn: () => cashApi.list({ page, size: 30, transactionType, paymentSource, fromDate, toDate }).then(r => r.data),
  });

  const { register, handleSubmit, watch, reset } = useForm<{
    transactionType: string; category: string;
    amount: string; currency: string; note: string;
  }>({
    defaultValues: { transactionType: 'income', category: '', amount: '', currency: 'UZS', note: '' }
  });
  const watchType = watch('transactionType');

  const createMutation = useMutation({
    mutationFn: (data: any) => cashApi.create({ ...data, paymentSource: 'cash', transactionDate: new Date().toISOString() }),
    onSuccess: (res: any) => {
      qc.invalidateQueries({ queryKey: ['cash'] });
      qc.invalidateQueries({ queryKey: ['balances'] });
      qc.invalidateQueries({ queryKey: ['company'] });
      qc.invalidateQueries({ queryKey: ['approvals'] });
      // Large expenses return HTTP 202 with a pending-approval body
      if (res?.data?.approvalStatus === 'pending_approval') {
        toast(res.data.message || t('cashbox.createSuccess'), { duration: 5000 });
      } else {
        toast.success(t('cashbox.createSuccess'));
      }
      setShowModal(false); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || e.response?.data?.message || t('errors.serverError')),
  });

  const items = useMemo(() => transactions?.content ?? [], [transactions]);

  const columns: GridColumn<any>[] = [
    { key: 'transactionDate', header: t('common.date'), width: 165, render: (tx) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(tx.transactionDate)}</span> },
    { key: 'transactionType', header: t('common.type'), width: 120, groupable: true, groupValue: (tx) => tx.transactionType === 'income' ? t('common.income') : t('common.expense'), render: (tx) => (
        <span className={tx.transactionType === 'income' ? 'badge-green' : 'badge-red'}>
          {tx.transactionType === 'income' ? <ArrowUpRight size={11} /> : <ArrowDownRight size={11} />}
          {tx.transactionType === 'income' ? t('common.income') : t('common.expense')}
        </span>
      ) },
    { key: 'paymentSource', header: t('common.source'), width: 100, groupable: true, groupValue: (tx) => tx.paymentSource, render: (tx) => <span className="badge-blue text-xs capitalize">{tx.paymentSource}</span> },
    { key: 'category', header: t('cashbox.category'), width: 170, groupable: true, groupValue: (tx) => tx.category?.replace(/_/g, ' ') || '—', render: (tx) => <span className="text-sm capitalize" style={{ color: 'rgb(var(--color-text-secondary))' }}>{tx.category?.replace(/_/g, ' ')}</span> },
    { key: 'amount', header: t('common.amount'), width: 150, numeric: true, aggregate: 'sum', aggregateValue: (tx) => parseFloat(tx.amount || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (tx) => (
        <span className={`font-semibold num ${tx.transactionType === 'income' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
          {tx.transactionType === 'income' ? '+' : '-'}{formatMoney(tx.amount)}
        </span>
      ) },
    { key: 'note', header: t('common.note'), width: 240, render: (tx) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{tx.note}</span> },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">{t('cashbox.cashAndBank')}</h1>
        <div className="flex gap-2">
          <ExportButton permission="cashbox.export" filename="cashbox" fetcher={() => exportApi.cashbox({ transactionType, paymentSource, fromDate, toDate })} />
          <button onClick={() => { reset(); setShowModal(true); }} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('cashbox.addTransaction')}</button>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {can('cashbox.view') && cashByCurrency.length > 0 ? (
          cashByCurrency.map((b: any) => (
            <StatCard key={b.currency} label={`${t('cashbox.cashBalance')} (${b.currency})`} value={formatMoney(b.balance, b.currency)} icon={Wallet} color="green" />
          ))
        ) : can('cashbox.view') ? (
          <StatCard label={`${t('cashbox.cashBalance')} (${balances?.base || 'UZS'})`} value={formatMoney(0, balances?.base || 'UZS')} icon={Wallet} color="green" />
        ) : null}
        <StatCard
          label={t('cashbox.pageIncome')}
          value={formatMoney(items.filter((tx: any) => tx.transactionType === 'income').reduce((s: number, tx: any) => s + parseFloat(tx.amount), 0))}
          icon={TrendingUp}
          color="green"
        />
        <StatCard
          label={t('cashbox.pageExpense')}
          value={formatMoney(items.filter((tx: any) => tx.transactionType === 'expense').reduce((s: number, tx: any) => s + parseFloat(tx.amount), 0))}
          icon={TrendingDown}
          color="red"
        />
      </div>

      <div className="filter-bar">
        <select className="filter-input" value={transactionType} onChange={e => { setTransactionType(e.target.value); setPage(0); }}>
          <option value="">{t('common.allTypes')}</option>
          <option value="income">{t('common.income')}</option>
          <option value="expense">{t('common.expense')}</option>
        </select>
        <input type="date" className="filter-input" value={fromDate} onChange={e => { setFromDate(e.target.value); setPage(0); }} />
        <input type="date" className="filter-input" value={toDate} onChange={e => { setToDate(e.target.value); setPage(0); }} />
        <button onClick={() => { setTransactionType(''); setFromDate(''); setToDate(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="cashbox"
        columns={columns}
        rows={items}
        getRowId={(tx: any) => tx.id}
        loading={isLoading}
        groupable
        emptyState={
          <EmptyState icon={Wallet} title={t('cashbox.noTransactions')} description={t('cashbox.addTransaction')}
            action={<button onClick={() => setShowModal(true)} className="btn-primary btn-sm">{t('cashbox.addTransaction')}</button>} />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={transactions?.totalPages} totalElements={transactions?.totalElements} size={30} onChange={setPage} />
      )}

      <Modal open={showModal} onClose={() => { setShowModal(false); reset(); }} title={t('cashbox.addTransaction')}>
        <form onSubmit={handleSubmit(data => createMutation.mutate(data))} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">{t('common.type')} *</label>
              <select {...register('transactionType')} className="input">
                <option value="income">{t('common.income')}</option>
                <option value="expense">{t('common.expense')}</option>
              </select>
            </div>
            <div>
              <label className="label">{t('common.currency')}</label>
              <select {...register('currency')} className="input">
                <option value="UZS">UZS</option>
                <option value="USD">USD</option>
              </select>
            </div>
            <div className="col-span-2">
              <label className="label">{t('cashbox.category')} *</label>
              <select {...register('category')} className="input">
                {(watchType === 'income' ? INCOME_CATS : EXPENSE_CATS).map(c => (
                  <option key={c} value={c}>{c.replace(/_/g, ' ').toUpperCase()}</option>
                ))}
              </select>
            </div>
            <div className="col-span-2">
              <label className="label">{t('common.amount')} *</label>
              <input {...register('amount', { required: true })} type="number" min="0.01" step="0.01" className="input" placeholder="0.00" />
            </div>
            <div className="col-span-2">
              <label className="label">{t('common.note')}</label>
              <textarea {...register('note')} className="input" rows={2} />
            </div>
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
