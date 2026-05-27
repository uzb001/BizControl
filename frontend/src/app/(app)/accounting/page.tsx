'use client';

import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { accountingApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDateTime, cn } from '@/lib/utils';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { DetailStat } from '@/components/ui/DetailShell';
import EmptyState from '@/components/ui/EmptyState';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import { Scale, BookOpen, TrendingUp, Wallet, Landmark, CircleDollarSign } from 'lucide-react';

type Tab = 'trial' | 'pnl' | 'balance' | 'journal';

export default function AccountingPage() {
  const [tab, setTab] = useState<Tab>('trial');

  const { data: trial, isLoading: trialLoading } = useQuery({
    queryKey: ['acc-trial'], queryFn: () => accountingApi.trialBalance().then(r => r.data),
    enabled: tab === 'trial',
  });
  const { data: pnl } = useQuery({
    queryKey: ['acc-pnl'], queryFn: () => accountingApi.profitLoss().then(r => r.data),
    enabled: tab === 'pnl',
  });
  const { data: bs } = useQuery({
    queryKey: ['acc-bs'], queryFn: () => accountingApi.balanceSheet().then(r => r.data),
    enabled: tab === 'balance',
  });
  const { data: journal, isLoading: journalLoading } = useQuery({
    queryKey: ['acc-journal'], queryFn: () => accountingApi.journal({ size: 100 }).then(r => r.data),
    enabled: tab === 'journal',
  });

  const tabs: { key: Tab; label: string; icon: typeof Scale }[] = [
    { key: 'trial', label: 'Trial Balance', icon: Scale },
    { key: 'pnl', label: 'Profit & Loss', icon: TrendingUp },
    { key: 'balance', label: 'Balance Sheet', icon: Landmark },
    { key: 'journal', label: 'General Journal', icon: BookOpen },
  ];

  const trialRows = useMemo(() => trial?.rows ?? [], [trial]);
  const journalRows = useMemo(() => journal?.content ?? [], [journal]);

  const trialColumns: GridColumn<any>[] = [
    { key: 'code', header: 'Code', width: 90, render: (r) => <span className="font-mono text-xs">{r.code}</span> },
    { key: 'name', header: 'Account', width: 220, render: (r) => <span className="font-medium">{r.name}</span> },
    { key: 'type', header: 'Type', width: 120, groupable: true, groupValue: (r) => r.type, render: (r) => <span className="badge-gray text-xs">{r.type}</span> },
    { key: 'debit', header: 'Debit', width: 150, numeric: true, aggregate: 'sum', aggregateValue: (r) => parseFloat(r.debit || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (r) => <span className="num">{formatMoney(r.debit)}</span> },
    { key: 'credit', header: 'Credit', width: 150, numeric: true, aggregate: 'sum', aggregateValue: (r) => parseFloat(r.credit || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (r) => <span className="num">{formatMoney(r.credit)}</span> },
    { key: 'balance', header: 'Balance', width: 160, numeric: true, render: (r) => <span className="num font-semibold">{formatMoney(r.balance)}</span> },
  ];

  const journalColumns: GridColumn<any>[] = [
    { key: 'id', header: 'Entry #', width: 90, render: (e) => <span className="font-mono text-xs">#{e.id}</span> },
    { key: 'entryDate', header: 'Date', width: 160, render: (e) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(e.entryDate)}</span> },
    { key: 'sourceType', header: 'Source', width: 110, groupable: true, groupValue: (e) => e.sourceType || 'MANUAL', render: (e) => <span className="badge-blue text-xs">{e.sourceType}{e.sourceId ? ` #${e.sourceId}` : ''}</span> },
    { key: 'memo', header: 'Memo', width: 240, render: (e) => <span className="text-sm truncate">{e.memo}</span> },
    { key: 'lines', header: 'Debit / Credit', width: 280, render: (e) => (
        <div className="text-xs space-y-0.5 py-1">
          {(e.lines || []).map((l: any) => (
            <div key={l.id} className="flex justify-between gap-3">
              <span style={{ color: 'rgb(var(--color-text-muted))' }}>acct {l.accountId}</span>
              <span className="num">{parseFloat(l.debit) > 0 ? `Dr ${formatMoney(l.debit)}` : `Cr ${formatMoney(l.credit)}`}</span>
            </div>
          ))}
        </div>
      ) },
    { key: 'status', header: 'Status', width: 100, render: (e) => <span className={e.status === 'reversed' ? 'badge-red text-xs' : 'badge-green text-xs'}>{e.status}</span> },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Accounting</h1>
          <p className="page-subtitle">Double-entry ledger &amp; financial statements</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <ExportButton permission="accounting.export" filename="accounting_journal" label="Journal" fetcher={() => exportApi.accountingJournal()} />
          <ExportButton permission="accounting.export" filename="general_ledger" label="Ledger" fetcher={() => exportApi.accountingLedger()} />
          <ExportButton permission="accounting.export" filename="trial_balance" label="Trial Balance" fetcher={() => exportApi.trialBalance()} />
          <ExportButton permission="accounting.export" filename="profit_loss" label="P&L" fetcher={() => exportApi.profitLoss()} />
          <ExportButton permission="accounting.export" filename="balance_sheet" label="Balance Sheet" fetcher={() => exportApi.balanceSheet()} />
          <ExportButton permission="accounting.export" filename="cashflow" label="Cashflow" fetcher={() => exportApi.cashflow()} />
        </div>
      </div>

      <div className="segment mb-5">
        {tabs.map(tt => (
          <button key={tt.key} onClick={() => setTab(tt.key)} className={cn('segment-item gap-1.5', tab === tt.key && 'active')}>
            <tt.icon size={14} /> {tt.label}
          </button>
        ))}
      </div>

      {tab === 'trial' && (
        <>
          {trial && (
            <div className={cn('mb-4 px-4 py-2.5 rounded-xl text-sm flex items-center gap-3',
              trial.balanced ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400')}
              style={{ backgroundColor: trial.balanced ? 'rgb(16 185 129 / 0.1)' : 'rgb(239 68 68 / 0.1)', border: `1px solid ${trial.balanced ? 'rgb(16 185 129 / 0.25)' : 'rgb(239 68 68 / 0.25)'}` }}>
              <Scale size={15} />
              {trial.balanced ? 'Ledger is balanced' : 'Ledger is OUT OF BALANCE'} · Σ Debit {formatMoney(trial.totalDebit)} · Σ Credit {formatMoney(trial.totalCredit)}
            </div>
          )}
          <DataGrid gridId="acc-trial" columns={trialColumns} rows={trialRows} getRowId={(r: any) => r.accountId}
            loading={trialLoading} groupable
            emptyState={<EmptyState icon={Scale} title="No ledger activity yet" description="Post a sale to generate journal entries." />} />
        </>
      )}

      {tab === 'pnl' && (
        pnl ? (
          <div className="space-y-5 max-w-3xl">
            <div className="grid grid-cols-3 gap-4">
              <DetailStat label="Revenue" value={formatMoney(pnl.revenue)} icon={CircleDollarSign} accent="green" />
              <DetailStat label="Expenses" value={formatMoney(pnl.expenses)} icon={Wallet} accent="red" />
              <DetailStat label="Net Profit" value={formatMoney(pnl.netProfit)} icon={TrendingUp} accent={pnl.netProfit >= 0 ? 'green' : 'red'} />
            </div>
            <div className="card p-0 overflow-hidden">
              <table className="table">
                <thead><tr><th>Code</th><th>Account</th><th>Type</th><th>Amount</th></tr></thead>
                <tbody>
                  {(pnl.lines || []).map((l: any) => (
                    <tr key={l.accountId}>
                      <td className="font-mono text-xs">{l.code}</td>
                      <td className="font-medium">{l.name}</td>
                      <td><span className="badge-gray text-xs">{l.type}</span></td>
                      <td className="num font-semibold">{formatMoney(l.balance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : <LoadingSpinner />
      )}

      {tab === 'balance' && (
        bs ? (
          <div className="space-y-5 max-w-3xl">
            <div className="grid grid-cols-3 gap-4">
              <DetailStat label="Assets" value={formatMoney(bs.assets)} icon={Wallet} />
              <DetailStat label="Liabilities" value={formatMoney(bs.liabilities)} icon={Landmark} accent="red" />
              <DetailStat label="Equity + Retained" value={formatMoney((bs.equity || 0) + (bs.retainedEarnings || 0))} icon={CircleDollarSign} accent="green" />
            </div>
            <div className={cn('px-4 py-2.5 rounded-xl text-sm', bs.balanced ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400')}
              style={{ backgroundColor: bs.balanced ? 'rgb(16 185 129 / 0.1)' : 'rgb(239 68 68 / 0.1)', border: `1px solid ${bs.balanced ? 'rgb(16 185 129 / 0.25)' : 'rgb(239 68 68 / 0.25)'}` }}>
              {bs.balanced ? 'Assets = Liabilities + Equity + Retained Earnings ✓' : 'Balance sheet does not balance'}
            </div>
            <div className="card p-0 overflow-hidden">
              <table className="table">
                <thead><tr><th>Code</th><th>Account</th><th>Type</th><th>Balance</th></tr></thead>
                <tbody>
                  {(bs.lines || []).map((l: any) => (
                    <tr key={l.accountId}>
                      <td className="font-mono text-xs">{l.code}</td>
                      <td className="font-medium">{l.name}</td>
                      <td><span className="badge-gray text-xs">{l.type}</span></td>
                      <td className="num font-semibold">{formatMoney(l.balance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : <LoadingSpinner />
      )}

      {tab === 'journal' && (
        <DataGrid gridId="acc-journal" columns={journalColumns} rows={journalRows} getRowId={(e: any) => e.id}
          loading={journalLoading} groupable
          emptyState={<EmptyState icon={BookOpen} title="No journal entries yet" description="Entries are created automatically when you post sales." />} />
      )}
    </div>
  );
}
