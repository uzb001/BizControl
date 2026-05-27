'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { salesApi } from '@/lib/api';
import { formatMoney, formatDateTime } from '@/lib/utils';
import { PaymentBadge } from '@/components/ui/Badge';
import Modal, { ConfirmModal } from '@/components/ui/Modal';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection, DetailRow } from '@/components/ui/DetailShell';
import { useForm } from 'react-hook-form';
import Link from 'next/link';
import { Plus, Ban, Wallet, TrendingUp, CircleDollarSign, ReceiptText, Printer } from 'lucide-react';

interface PaymentForm { amount: string; paymentMethod: string; note: string; }

export default function SaleDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [showPayModal, setShowPayModal] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const { register, handleSubmit, reset } = useForm<PaymentForm>({
    defaultValues: { paymentMethod: 'cash', note: '', amount: '' },
  });

  const { data: sale, isLoading } = useQuery({
    queryKey: ['sale', id],
    queryFn: () => salesApi.get(Number(id)).then(r => r.data),
  });

  const payMutation = useMutation({
    mutationFn: (data: PaymentForm) => salesApi.addPayment(Number(id), {
      amount: parseFloat(data.amount), paymentMethod: data.paymentMethod, note: data.note,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sale', id] });
      toast.success(t('sales.paymentAdded'));
      setShowPayModal(false); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  const cancelMutation = useMutation({
    mutationFn: () => salesApi.cancel(Number(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sale', id] });
      toast.success(t('sales.cancelSuccess'));
      setShowCancelModal(false);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  if (isLoading) return <PageLoading />;
  if (!sale) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('errors.notFound')}</div>;

  const isPaid = sale.paymentStatus === 'paid';
  const isCancelled = sale.status === 'cancelled';

  return (
    <div className="max-w-6xl mx-auto">
      <DetailHeader
        backHref="/sales"
        title={sale.saleNumber}
        subtitle={formatDateTime(sale.saleDate)}
        badge={
          <div className="flex items-center gap-2">
            <PaymentBadge status={sale.paymentStatus} />
            {isCancelled && <span className="badge-red">{t('common.cancelled').toUpperCase()}</span>}
          </div>
        }
        actions={
          <>
            <Link href={`/sales/${id}/invoice`} className="btn-secondary btn-sm gap-1.5"><Printer size={14} /> {t('sales.printInvoice', { defaultValue: 'Invoice' })}</Link>
            {!isCancelled && !isPaid && (
              <button onClick={() => setShowPayModal(true)} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('sales.addPayment')}</button>
            )}
            {!isCancelled && (
              <button onClick={() => setShowCancelModal(true)} className="btn-danger btn-sm gap-1.5"><Ban size={14} /> {t('sales.cancelSale')}</button>
            )}
          </>
        }
      />

      {/* Summary strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <DetailStat label={t('sales.totalAmount')} value={formatMoney(sale.totalAmount)} icon={CircleDollarSign} />
        <DetailStat label={t('sales.paidAmount')} value={formatMoney(sale.paidAmount)} icon={Wallet} accent="green" />
        <DetailStat label={t('sales.unpaidAmount')} value={formatMoney(sale.unpaidAmount)} icon={ReceiptText} accent={sale.unpaidAmount > 0 ? 'red' : 'default'} />
        {sale.totalProfit != null && (
          <DetailStat label={t('common.profit')} value={formatMoney(sale.totalProfit)} icon={TrendingUp} accent="green" />
        )}
      </div>

      <div className="grid lg:grid-cols-3 gap-6 mb-6">
        {/* Items */}
        <div className="lg:col-span-2">
          <DetailSection title={t('common.items')} noPadding>
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>{t('common.product')}</th>
                    <th>{t('common.sku')}</th>
                    <th>{t('common.qty')}</th>
                    <th>{t('sales.unitPrice')}</th>
                    <th>{t('common.discount')}</th>
                    <th>{t('common.total')}</th>
                    <th>{t('common.profit')}</th>
                  </tr>
                </thead>
                <tbody>
                  {sale.items?.map((item: any) => (
                    <tr key={item.id}>
                      <td className="font-medium">{item.productName}</td>
                      <td className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>{item.productSku || '—'}</td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatMoney(item.sellingPrice)}</td>
                      <td className="num text-orange-600 dark:text-orange-400 text-sm">{item.discountAmount > 0 ? formatMoney(item.discountAmount) : '—'}</td>
                      <td className="num font-medium">{formatMoney(item.totalAmount)}</td>
                      <td className={`num text-sm font-medium ${item.profitAmount >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                        {item.profitAmount != null ? formatMoney(item.profitAmount) : '•••'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </DetailSection>
        </div>

        {/* Side panel */}
        <div className="space-y-4">
          <DetailSection title={t('sales.paymentSummary')}>
            <dl className="space-y-2.5 text-sm">
              {[
                [t('sales.totalAmount'), formatMoney(sale.totalAmount), false],
                [t('common.discount'), formatMoney(sale.discountAmount), false],
                [t('sales.paidAmount'), formatMoney(sale.paidAmount), false],
                [t('sales.unpaidAmount'), formatMoney(sale.unpaidAmount), sale.unpaidAmount > 0],
                [t('common.paymentMethod'), sale.paymentMethod, false],
              ].map(([label, val, danger]) => (
                <div key={label as string} className="flex justify-between gap-3">
                  <dt style={{ color: 'rgb(var(--color-text-muted))' }}>{label as string}</dt>
                  <dd className={`font-medium num ${danger ? 'text-red-600 dark:text-red-400' : ''}`} style={danger ? undefined : { color: 'rgb(var(--color-text-primary))' }}>{val as string}</dd>
                </div>
              ))}
            </dl>
          </DetailSection>

          <DetailSection title={t('common.customer')}>
            {sale.customer ? (
              <div className="space-y-1">
                <button onClick={() => router.push(`/customers/${sale.customer.id}`)} className="font-medium text-sm hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>
                  {sale.customer.name}
                </button>
                <p className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{sale.customer.phone}</p>
                {sale.customer.currentDebt > 0 && (
                  <p className="text-xs text-red-500 dark:text-red-400">{t('sales.debtLabel')}: {formatMoney(sale.customer.currentDebt)}</p>
                )}
              </div>
            ) : (
              <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('sales.walkInCustomer')}</p>
            )}
          </DetailSection>

          {sale.note && (
            <DetailSection title={t('common.note')}>
              <p className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{sale.note}</p>
            </DetailSection>
          )}
        </div>
      </div>

      {/* Payment history */}
      {sale.payments?.length > 0 && (
        <DetailSection title={t('sales.paymentHistory')} noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('common.date')}</th>
                  <th>{t('common.amount')}</th>
                  <th>{t('common.source')}</th>
                  <th>{t('common.status')}</th>
                  <th>{t('common.note')}</th>
                </tr>
              </thead>
              <tbody>
                {sale.payments.map((p: any) => (
                  <tr key={p.id} className={p.status === 'reversed' ? 'opacity-50' : ''}>
                    <td className="text-sm">{formatDateTime(p.createdAt)}</td>
                    <td className={`num font-medium ${p.status === 'reversed' ? 'line-through' : 'text-green-600 dark:text-green-400'}`} style={p.status === 'reversed' ? { color: 'rgb(var(--color-text-muted))' } : undefined}>
                      {formatMoney(p.amount)}
                    </td>
                    <td><span className="badge-blue text-xs capitalize">{p.paymentSource}</span></td>
                    <td>{p.status === 'reversed' ? <span className="badge-red text-xs">{t('common.reversed')}</span> : <span className="badge-green text-xs">{t('common.active')}</span>}</td>
                    <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.note || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {/* Record payment modal */}
      <Modal open={showPayModal} onClose={() => { setShowPayModal(false); reset(); }} title={t('sales.addPayment')} size="sm">
        <form onSubmit={handleSubmit(data => payMutation.mutate(data))} className="space-y-3">
          <div>
            <label className="label">{t('sales.unpaidAmount')}: {formatMoney(sale.unpaidAmount)}</label>
            <input type="number" step="0.01" max={sale.unpaidAmount} {...register('amount', { required: true })} className="input" placeholder={t('common.amount')} defaultValue={sale.unpaidAmount} />
          </div>
          <div>
            <label className="label">{t('common.paymentMethod')}</label>
            <select {...register('paymentMethod')} className="input">
              <option value="cash">{t('common.cash')}</option>
              <option value="card">{t('common.card')}</option>
              <option value="bank_transfer">{t('common.bankTransfer')}</option>
            </select>
          </div>
          <div>
            <label className="label">{t('common.note')}</label>
            <input {...register('note')} className="input" placeholder={t('common.optionalNote')} />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setShowPayModal(false); reset(); }} className="btn-secondary">{t('common.cancel')}</button>
            <button type="submit" disabled={payMutation.isPending} className="btn-primary">{payMutation.isPending ? t('common.saving') : t('sales.addPayment')}</button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={showCancelModal}
        onClose={() => setShowCancelModal(false)}
        onConfirm={() => cancelMutation.mutate()}
        title={t('sales.cancelSale')}
        message={t('sales.cancelConfirm')}
        danger
      />
    </div>
  );
}
