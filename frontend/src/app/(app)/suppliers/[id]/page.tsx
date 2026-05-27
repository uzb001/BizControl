'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { suppliersApi, purchasesApi, debtsApi } from '@/lib/api';
import { formatMoney, formatDate, cn } from '@/lib/utils';
import { StatusBadge, PaymentBadge } from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection } from '@/components/ui/DetailShell';
import { useForm } from 'react-hook-form';
import { Truck, PackageSearch, ClipboardList, Eye, CircleDollarSign, Wallet, ReceiptText, CalendarClock } from 'lucide-react';

type Tab = 'overview' | 'purchases' | 'debts';

export default function SupplierProfilePage() {
  const { id } = useParams();
  const router = useRouter();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('overview');
  const [payDebt, setPayDebt] = useState<any>(null);
  const { register, handleSubmit, reset } = useForm();

  const { data: supplier, isLoading } = useQuery({
    queryKey: ['supplier', id],
    queryFn: () => suppliersApi.get(Number(id)).then(r => r.data),
  });

  const { data: purchases } = useQuery({
    queryKey: ['supplier-purchases', id],
    queryFn: () => purchasesApi.list({ supplierId: id, size: 50 }).then(r => r.data),
    enabled: tab === 'purchases',
  });

  const { data: debts } = useQuery({
    queryKey: ['supplier-debts', id],
    queryFn: () => debtsApi.list({ supplierId: id, debtType: 'supplier' }).then(r => r.data),
    enabled: tab === 'debts',
  });

  const payMutation = useMutation({
    mutationFn: (data: any) => debtsApi.addPayment(payDebt.id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['supplier-debts', id] });
      qc.invalidateQueries({ queryKey: ['supplier', id] });
      toast.success('Payment recorded');
      setPayDebt(null); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  if (isLoading) return <PageLoading />;
  if (!supplier) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>Supplier not found.</div>;

  const tabs: { key: Tab; label: string; icon: typeof Truck }[] = [
    { key: 'overview', label: 'Overview', icon: Truck },
    { key: 'purchases', label: 'Purchase History', icon: PackageSearch },
    { key: 'debts', label: 'Debts', icon: ClipboardList },
  ];

  return (
    <div className="max-w-6xl mx-auto">
      <DetailHeader
        backHref="/suppliers"
        title={supplier.name}
        subtitle={<span className="inline-flex items-center gap-1.5">{supplier.contactPerson && <>{supplier.contactPerson} · </>}{supplier.phone || '—'} <StatusBadge status={supplier.status} /></span>}
      />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <DetailStat label="Total Purchases" value={formatMoney(supplier.totalPurchasesAmount || 0)} icon={CircleDollarSign} />
        <DetailStat label="Total Paid" value={formatMoney(supplier.totalPaid || 0)} icon={Wallet} accent="green" />
        <DetailStat label="Current Debt" value={formatMoney(supplier.currentDebt || 0)} icon={ReceiptText} accent={supplier.currentDebt > 0 ? 'red' : 'default'} />
        <DetailStat label="Last Purchase" value={supplier.lastPurchaseDate ? formatDate(supplier.lastPurchaseDate) : '—'} icon={CalendarClock} />
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
              {[['Contact Person', supplier.contactPerson], ['Phone', supplier.phone], ['Email', supplier.email], ['Address', supplier.address], ['Tax ID', supplier.taxId], ['Bank Account', supplier.bankAccount]].map(([label, val]) => (
                <div key={label as string} className="flex justify-between gap-3">
                  <dt style={{ color: 'rgb(var(--color-text-muted))' }}>{label as string}</dt>
                  <dd className="font-medium" style={{ color: 'rgb(var(--color-text-primary))' }}>{(val as string) || '—'}</dd>
                </div>
              ))}
            </dl>
          </DetailSection>
          <DetailSection title="Notes">
            <p className="text-sm whitespace-pre-line" style={{ color: 'rgb(var(--color-text-secondary))' }}>{supplier.notes || 'No notes.'}</p>
          </DetailSection>
        </div>
      )}

      {tab === 'purchases' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>#</th><th>Date</th><th>Items</th><th>Total</th><th>Paid</th><th>Unpaid</th><th>Payment</th><th>Status</th><th></th></tr>
              </thead>
              <tbody>
                {purchases?.content?.map((p: any) => (
                  <tr key={p.id}>
                    <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>#{p.id}</td>
                    <td className="text-sm">{formatDate(p.purchaseDate)}</td>
                    <td className="num text-sm">{p.items?.length ?? '—'}</td>
                    <td className="num font-medium">{formatMoney(p.totalAmount)}</td>
                    <td className="num text-green-600 dark:text-green-400">{formatMoney(p.paidAmount)}</td>
                    <td className={cn('num', p.unpaidAmount > 0 ? 'text-red-600 dark:text-red-400 font-medium' : '')} style={p.unpaidAmount > 0 ? undefined : { color: 'rgb(var(--color-text-muted))' }}>{formatMoney(p.unpaidAmount)}</td>
                    <td><span className="badge-blue text-xs capitalize">{p.paymentMethod}</span></td>
                    <td><PaymentBadge status={p.paymentStatus} /></td>
                    <td><button onClick={() => router.push(`/purchases/${p.id}`)} className="btn-icon !w-7 !h-7" title="View"><Eye size={14} /></button></td>
                  </tr>
                ))}
                {!purchases?.content?.length && <tr><td colSpan={9} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No purchases found</td></tr>}
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
