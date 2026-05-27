'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { customersApi, salesApi, debtsApi } from '@/lib/api';
import { formatMoney, formatDate, formatDateTime, cn } from '@/lib/utils';
import { StatusBadge, PaymentBadge } from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection } from '@/components/ui/DetailShell';
import { useForm } from 'react-hook-form';
import { User, ShoppingCart, ClipboardList, History, Eye, CircleDollarSign, Wallet, ReceiptText, CalendarClock } from 'lucide-react';

type Tab = 'overview' | 'sales' | 'debts' | 'payments';

export default function CustomerProfilePage() {
  const { id } = useParams();
  const router = useRouter();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('overview');
  const [payDebt, setPayDebt] = useState<any>(null);
  const { register, handleSubmit, reset } = useForm();

  const { data: customer, isLoading } = useQuery({
    queryKey: ['customer', id],
    queryFn: () => customersApi.get(Number(id)).then(r => r.data),
  });

  const { data: sales } = useQuery({
    queryKey: ['customer-sales', id],
    queryFn: () => salesApi.list({ customerId: id, size: 50 }).then(r => r.data),
    enabled: tab === 'sales',
  });

  const { data: debts } = useQuery({
    queryKey: ['customer-debts', id],
    queryFn: () => debtsApi.list({ customerId: id, debtType: 'customer' }).then(r => r.data),
    enabled: tab === 'debts' || tab === 'payments',
  });

  const payMutation = useMutation({
    mutationFn: (data: any) => debtsApi.addPayment(payDebt.id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customer-debts', id] });
      qc.invalidateQueries({ queryKey: ['customer', id] });
      toast.success('Payment recorded');
      setPayDebt(null); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  if (isLoading) return <PageLoading />;
  if (!customer) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>Customer not found.</div>;

  const tabs: { key: Tab; label: string; icon: typeof User }[] = [
    { key: 'overview', label: 'Overview', icon: User },
    { key: 'sales', label: 'Sales History', icon: ShoppingCart },
    { key: 'debts', label: 'Debts', icon: ClipboardList },
    { key: 'payments', label: 'Payment History', icon: History },
  ];

  return (
    <div className="max-w-6xl mx-auto">
      <DetailHeader
        backHref="/customers"
        title={customer.name}
        subtitle={<span className="inline-flex items-center gap-1.5">{customer.phone || '—'} · {customer.city || '—'} <StatusBadge status={customer.status} /></span>}
        badge={<span className="badge-blue text-xs capitalize">{customer.customerType}</span>}
      />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <DetailStat label="Total Sales" value={formatMoney(customer.totalSalesAmount || 0)} icon={CircleDollarSign} />
        <DetailStat label="Total Paid" value={formatMoney(customer.totalPaid || 0)} icon={Wallet} accent="green" />
        <DetailStat label="Current Debt" value={formatMoney(customer.currentDebt || 0)} icon={ReceiptText} accent={customer.currentDebt > 0 ? 'red' : 'default'} />
        <DetailStat label="Last Sale" value={customer.lastSaleDate ? formatDate(customer.lastSaleDate) : '—'} icon={CalendarClock} />
      </div>

      <div className="segment mb-5">
        {tabs.map(tt => (
          <button key={tt.key} onClick={() => setTab(tt.key)} className={cn('segment-item gap-1.5', tab === tt.key && 'active')}>
            <tt.icon size={14} /> {tt.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="grid lg:grid-cols-2 gap-6">
          <DetailSection title="Contact Info">
            <dl className="space-y-2.5 text-sm">
              {[['Phone', customer.phone], ['Alt. Phone', customer.phone2], ['City', customer.city], ['Address', customer.address]].map(([label, val]) => (
                <div key={label as string} className="flex justify-between gap-3">
                  <dt style={{ color: 'rgb(var(--color-text-muted))' }}>{label as string}</dt>
                  <dd className="font-medium" style={{ color: 'rgb(var(--color-text-primary))' }}>{(val as string) || '—'}</dd>
                </div>
              ))}
            </dl>
          </DetailSection>
          <DetailSection title="Notes">
            <p className="text-sm whitespace-pre-line" style={{ color: 'rgb(var(--color-text-secondary))' }}>{customer.notes || 'No notes.'}</p>
          </DetailSection>
        </div>
      )}

      {tab === 'sales' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>#</th><th>Date</th><th>Items</th><th>Total</th><th>Paid</th><th>Unpaid</th><th>Payment</th><th>Status</th><th></th></tr>
              </thead>
              <tbody>
                {sales?.content?.map((s: any) => (
                  <tr key={s.id}>
                    <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>#{s.id}</td>
                    <td className="text-sm">{formatDate(s.saleDate)}</td>
                    <td className="num text-sm">{s.items?.length ?? '—'}</td>
                    <td className="num font-medium">{formatMoney(s.totalAmount)}</td>
                    <td className="num text-green-600 dark:text-green-400">{formatMoney(s.paidAmount)}</td>
                    <td className={cn('num', s.unpaidAmount > 0 ? 'text-red-600 dark:text-red-400 font-medium' : '')} style={s.unpaidAmount > 0 ? undefined : { color: 'rgb(var(--color-text-muted))' }}>{formatMoney(s.unpaidAmount)}</td>
                    <td><span className="badge-blue text-xs capitalize">{s.paymentMethod}</span></td>
                    <td><PaymentBadge status={s.paymentStatus} /></td>
                    <td><button onClick={() => router.push(`/sales/${s.id}`)} className="btn-icon !w-7 !h-7" title="View"><Eye size={14} /></button></td>
                  </tr>
                ))}
                {!sales?.content?.length && <tr><td colSpan={9} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No sales found</td></tr>}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {tab === 'debts' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>#</th><th>Date</th><th>Original</th><th>Paid</th><th>Remaining</th><th>Due Date</th><th>Status</th><th></th></tr>
              </thead>
              <tbody>
                {debts?.content?.map((d: any) => (
                  <tr key={d.id}>
                    <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>#{d.id}</td>
                    <td className="text-sm">{formatDate(d.createdAt)}</td>
                    <td className="num">{formatMoney(d.originalAmount)}</td>
                    <td className="num text-green-600 dark:text-green-400">{formatMoney(d.paidAmount)}</td>
                    <td className={cn('num', d.remainingAmount > 0 ? 'text-red-600 dark:text-red-400 font-medium' : '')} style={d.remainingAmount > 0 ? undefined : { color: 'rgb(var(--color-text-muted))' }}>{formatMoney(d.remainingAmount)}</td>
                    <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{d.dueDate ? formatDate(d.dueDate) : '—'}</td>
                    <td><span className={`text-xs ${d.status === 'closed' ? 'badge-green' : d.status === 'overdue' ? 'badge-red' : 'badge-yellow'}`}>{d.status}</span></td>
                    <td>{d.status !== 'closed' && <button onClick={() => setPayDebt(d)} className="btn-primary btn-sm">Pay</button>}</td>
                  </tr>
                ))}
                {!debts?.content?.length && <tr><td colSpan={8} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No debts found</td></tr>}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {tab === 'payments' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>#</th><th>Date</th><th>Amount</th><th>Method</th><th>Note</th></tr>
              </thead>
              <tbody>
                {debts?.content?.flatMap((d: any) => d.payments || []).map((p: any) => (
                  <tr key={p.id}>
                    <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>#{p.id}</td>
                    <td className="text-sm">{formatDateTime(p.paymentDate)}</td>
                    <td className="num font-medium text-green-600 dark:text-green-400">{formatMoney(p.amount)}</td>
                    <td><span className="badge-blue text-xs capitalize">{p.paymentMethod}</span></td>
                    <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.note || '—'}</td>
                  </tr>
                ))}
                {!debts?.content?.flatMap((d: any) => d.payments || []).length && <tr><td colSpan={5} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No payments found</td></tr>}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      <Modal open={!!payDebt} onClose={() => { setPayDebt(null); reset(); }} title="Record Payment" size="sm">
        <form onSubmit={handleSubmit(data => payMutation.mutate(data))} className="space-y-3">
          <div>
            <label className="label">Remaining: {payDebt && formatMoney(payDebt.remainingAmount)}</label>
            <input type="number" step="0.01" {...register('amount', { required: true, min: 0.01 })} className="input" placeholder="Amount" />
          </div>
          <div>
            <label className="label">Payment Method</label>
            <select {...register('paymentMethod')} className="input">
              <option value="cash">Cash</option>
              <option value="card">Card</option>
              <option value="bank_transfer">Bank Transfer</option>
            </select>
          </div>
          <div>
            <label className="label">Note</label>
            <input {...register('note')} className="input" placeholder="Optional note" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setPayDebt(null); reset(); }} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={payMutation.isPending} className="btn-primary">{payMutation.isPending ? 'Saving...' : 'Record Payment'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
