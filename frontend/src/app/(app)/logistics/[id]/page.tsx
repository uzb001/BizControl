'use client';

import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { logisticsApi } from '@/lib/api';
import { usePermission } from '@/hooks/usePermission';
import { formatMoney, formatDateTime } from '@/lib/utils';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection } from '@/components/ui/DetailShell';
import Modal, { ConfirmModal } from '@/components/ui/Modal';
import { useState } from 'react';
import { Ship, CheckCircle2, Ban, FileDown, Package, Receipt, Calculator, Undo2, Wallet } from 'lucide-react';

/** Logistics order detail with landed-cost summary and confirm / cancel actions. */
export default function LogisticsDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();

  const [showConfirm, setShowConfirm] = useState(false);
  const [showCancel, setShowCancel] = useState(false);
  const [showReverse, setShowReverse] = useState(false);
  const [reverseReason, setReverseReason] = useState('');
  const [payExpenseId, setPayExpenseId] = useState<number | null>(null);
  const [payAmount, setPayAmount] = useState('');
  const [paySource, setPaySource] = useState<'cash' | 'bank'>('cash');
  const [payNote, setPayNote] = useState('');
  const [payCurrency, setPayCurrency] = useState('');
  const [payRate, setPayRate] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['logistics', id],
    queryFn: () => logisticsApi.get(Number(id)).then(r => r.data),
    retry: false,
  });

  const refresh = () => qc.invalidateQueries({ queryKey: ['logistics'] });

  const confirmMut = useMutation({
    mutationFn: () => logisticsApi.confirm(Number(id)),
    onSuccess: () => { refresh(); toast.success(t('logistics.confirmedToast', { defaultValue: 'Order confirmed — stock + ledger updated' })); setShowConfirm(false); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });
  const cancelMut = useMutation({
    mutationFn: () => logisticsApi.cancel(Number(id)),
    onSuccess: () => { refresh(); toast.success(t('logistics.cancelledToast', { defaultValue: 'Draft cancelled' })); setShowCancel(false); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });
  const reverseMut = useMutation({
    mutationFn: () => logisticsApi.reverse(Number(id), reverseReason || undefined),
    onSuccess: () => { refresh(); setShowReverse(false); setReverseReason('');
      toast.success(t('logistics.reversedToast', { defaultValue: 'Order reversed — stock + cash + ledger restored' })); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });
  const payMut = useMutation({
    mutationFn: () => {
      const expenseRow = expenses.find((e: any) => e.id === payExpenseId);
      const expenseCurrency = expenseRow?.currency || cur;
      const body: any = {
        amount: parseFloat(payAmount),
        paymentSource: paySource,
        note: payNote,
        currency: payCurrency || expenseCurrency,
      };
      // Provide an exchange rate when the user overrode the currency OR the rate
      if (payRate) body.exchangeRate = parseFloat(payRate);
      return (logisticsApi as any).payExpense(Number(id), payExpenseId!, body);
    },
    onSuccess: () => { refresh(); setPayExpenseId(null); setPayAmount(''); setPayNote(''); setPayCurrency(''); setPayRate('');
      toast.success(t('logistics.paidToast', { defaultValue: 'Payment recorded' })); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });

  if (isLoading) return <PageLoading />;
  if (!data?.order) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('errors.notFound', { defaultValue: 'Not found' })}</div>;

  const order = data.order;
  const itemDetails: any[] = data.itemDetails ?? [];
  const expenses: any[] = data.expenses ?? [];
  const allocations: any[] = data.allocations ?? [];
  const allocsByItemId = Object.fromEntries(allocations.map(a => [a.logisticsOrderItemId, a]));
  const cur = order.currency || 'UZS';

  const isDraft = order.status === 'draft';
  const isConfirmed = order.status === 'confirmed';
  const canSeeLanded = can('logistics.view_landed_cost');

  const onExport = async () => {
    const blob = await logisticsApi.exportOne(Number(id)).then(r => r.data);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `logistics_${order.orderNumber}.xlsx`; a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="max-w-6xl mx-auto">
      <DetailHeader
        backHref="/logistics"
        title={<span className="flex items-center gap-2"><Ship size={20} /> {order.orderNumber}</span> as any}
        subtitle={formatDateTime(order.createdAt)}
        badge={<span className={order.status === 'confirmed' ? 'badge-green' : order.status === 'cancelled' ? 'badge-red' : 'badge-gray'}>{order.status.toUpperCase()}</span>}
        actions={
          <>
            {can('logistics.export') && (
              <button onClick={onExport} className="btn-secondary btn-sm gap-1.5"><FileDown size={14} /> {t('common.export', { defaultValue: 'Export' })}</button>
            )}
            {isDraft && can('logistics.confirm') && (
              <button onClick={() => setShowConfirm(true)} className="btn-primary btn-sm gap-1.5"><CheckCircle2 size={14} /> {t('logistics.confirm', { defaultValue: 'Confirm' })}</button>
            )}
            {isDraft && can('logistics.cancel') && (
              <button onClick={() => setShowCancel(true)} className="btn-danger btn-sm gap-1.5"><Ban size={14} /> {t('common.cancel', { defaultValue: 'Cancel' })}</button>
            )}
            {isConfirmed && can('logistics.reverse') && (
              <button onClick={() => setShowReverse(true)} className="btn-danger btn-sm gap-1.5"><Undo2 size={14} /> {t('logistics.reverseAction', { defaultValue: 'Reverse logistics order' })}</button>
            )}
          </>
        }
      />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <DetailStat label={t('logistics.itemsValue', { defaultValue: 'Items value' })} value={formatMoney(order.itemsValue, cur)} icon={Package} />
        <DetailStat label={t('logistics.expensesTotal', { defaultValue: 'Expenses' })} value={formatMoney(order.expensesTotal, cur)} icon={Receipt} accent="yellow" />
        {canSeeLanded && (
          <DetailStat label={t('logistics.landedTotal', { defaultValue: 'Landed total' })} value={formatMoney(order.landedTotal, cur)} icon={Calculator} accent="green" />
        )}
        <DetailStat label={t('common.currency', { defaultValue: 'Currency' })} value={cur} icon={Calculator} />
      </div>

      {/* Items + landed cost */}
      <DetailSection title={t('logistics.itemsSection', { defaultValue: 'Items' })} noPadding>
        <div className="overflow-x-auto">
          <table className="table">
            <thead>
              <tr>
                <th>{t('common.product', { defaultValue: 'Product' })}</th>
                <th>SKU</th>
                <th>{t('common.qty', { defaultValue: 'Qty' })}</th>
                <th>{t('logistics.unitCost', { defaultValue: 'Unit cost' })}</th>
                <th>{t('logistics.itemValue', { defaultValue: 'Item value' })}</th>
                {canSeeLanded && <th>{t('logistics.allocated', { defaultValue: 'Allocated' })}</th>}
                {canSeeLanded && <th>{t('logistics.unitLandedCost', { defaultValue: 'Unit landed' })}</th>}
              </tr>
            </thead>
            <tbody>
              {itemDetails.map((it: any) => {
                const a = allocsByItemId[it.id];
                return (
                  <tr key={it.id}>
                    <td className="font-medium">{it.productName}</td>
                    <td className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>{it.productSku || '—'}</td>
                    <td className="num">{it.quantity}</td>
                    <td className="num">{formatMoney(it.unitCost, cur)}</td>
                    <td className="num font-medium">{formatMoney(it.itemValue, cur)}</td>
                    {canSeeLanded && <td className="num text-orange-600 dark:text-orange-400">{a ? formatMoney(a.allocatedAmount, cur) : '—'}</td>}
                    {canSeeLanded && <td className="num font-semibold text-green-700 dark:text-green-400">{a ? formatMoney(a.unitLandedCost, cur) : '—'}</td>}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </DetailSection>

      {/* Expenses */}
      <div className="mt-4">
        <DetailSection title={t('logistics.expensesSection', { defaultValue: 'Expenses' })} noPadding>
          {/* Desktop / tablet: full table */}
          <div className="hidden md:block overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('logistics.expenseType', { defaultValue: 'Type' })}</th>
                  <th>{t('common.amount', { defaultValue: 'Amount' })}</th>
                  <th>{t('logistics.convertedAmount', { defaultValue: `Converted (${cur})` })}</th>
                  <th>{t('logistics.paymentSource', { defaultValue: 'Source' })}</th>
                  <th>{t('logistics.paymentStatus', { defaultValue: 'Status' })}</th>
                  <th>{t('common.note', { defaultValue: 'Note' })}</th>
                  {isConfirmed && can('logistics.pay_payable') && <th>{t('common.actions', { defaultValue: 'Actions' })}</th>}
                </tr>
              </thead>
              <tbody>
                {expenses.length === 0 ? (
                  <tr><td colSpan={isConfirmed ? 7 : 6} className="text-center py-4" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.noExpenses', { defaultValue: 'No expenses yet' })}</td></tr>
                ) : expenses.map((e: any) => {
                  const outstanding = Number(e.amount) - Number(e.paidAmount ?? 0);
                  const isUnpaid = e.paymentStatus === 'unpaid' || e.paymentStatus === 'partial';
                  return (
                    <tr key={e.id}>
                      <td className="font-medium capitalize">{e.expenseType}</td>
                      <td className="num">
                        {formatMoney(e.amount, e.currency || cur)}
                        {e.currency !== cur && (
                          <div className="text-[10px]" style={{ color: 'rgb(var(--color-text-muted))' }}>@ {e.exchangeRate}</div>
                        )}
                      </td>
                      <td className="num text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
                        {e.convertedAmount != null ? formatMoney(e.convertedAmount, cur) : '—'}
                      </td>
                      <td><span className="badge-blue text-xs capitalize">{e.paymentSource}</span></td>
                      <td>
                        <span className={e.paymentStatus === 'paid' ? 'badge-green' : e.paymentStatus === 'partial' ? 'badge-yellow' : 'badge-red'}>
                          {e.paymentStatus}
                        </span>
                        {isUnpaid && outstanding > 0 && (
                          <div className="text-[10px]" style={{ color: 'rgb(var(--color-text-muted))' }}>
                            {t('logistics.outstanding', { defaultValue: 'Outstanding' })}: {formatMoney(outstanding, e.currency || cur)}
                          </div>
                        )}
                      </td>
                      <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{e.note || '—'}</td>
                      {isConfirmed && can('logistics.pay_payable') && (
                        <td>
                          {isUnpaid && outstanding > 0 && (
                            <button onClick={() => { setPayExpenseId(e.id); setPayAmount(String(outstanding)); setPaySource((e.paymentSource as 'cash' | 'bank') ?? 'cash'); }}
                              className="btn-secondary btn-sm gap-1"><Wallet size={12} /> {t('logistics.payAction', { defaultValue: 'Pay' })}</button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile: cards (≤ 768px). One card per expense; action stays in-card. */}
          <div className="md:hidden divide-y" style={{ borderColor: 'rgb(var(--color-border))' }}>
            {expenses.length === 0 ? (
              <div className="text-center py-6 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
                {t('logistics.noExpenses', { defaultValue: 'No expenses yet' })}
              </div>
            ) : expenses.map((e: any) => {
              const outstanding = Number(e.amount) - Number(e.paidAmount ?? 0);
              const isUnpaid = e.paymentStatus === 'unpaid' || e.paymentStatus === 'partial';
              return (
                <div key={e.id} className="p-3 space-y-1.5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-semibold capitalize truncate">{e.expenseType}</div>
                    <span className={(e.paymentStatus === 'paid' ? 'badge-green' : e.paymentStatus === 'partial' ? 'badge-yellow' : 'badge-red') + ' shrink-0'}>{e.paymentStatus}</span>
                  </div>
                  <div className="text-sm flex flex-wrap gap-x-3 gap-y-0.5">
                    <span className="num">{formatMoney(e.amount, e.currency || cur)}</span>
                    {e.currency !== cur && e.convertedAmount != null && (
                      <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                        ≈ {formatMoney(e.convertedAmount, cur)} ({e.exchangeRate})
                      </span>
                    )}
                    <span className="badge-blue text-xs capitalize">{e.paymentSource}</span>
                  </div>
                  {isUnpaid && outstanding > 0 && (
                    <div className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                      {t('logistics.outstanding', { defaultValue: 'Outstanding' })}: <span className="num font-semibold">{formatMoney(outstanding, e.currency || cur)}</span>
                    </div>
                  )}
                  {e.note && <div className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{e.note}</div>}
                  {isConfirmed && can('logistics.pay_payable') && isUnpaid && outstanding > 0 && (
                    <button onClick={() => { setPayExpenseId(e.id); setPayAmount(String(outstanding)); setPaySource((e.paymentSource as 'cash' | 'bank') ?? 'cash'); }}
                      className="btn-secondary btn-sm gap-1.5 w-full justify-center mt-1"><Wallet size={13} /> {t('logistics.payAction', { defaultValue: 'Pay' })}</button>
                  )}
                </div>
              );
            })}
          </div>
        </DetailSection>
      </div>

      {/* Payment history */}
      {Array.isArray(data?.payments) && data.payments.length > 0 && (
        <div className="mt-4">
          <DetailSection title={t('logistics.paymentsSection', { defaultValue: 'Payable settlements' })} noPadding>
            {/* Desktop */}
            <div className="hidden md:block overflow-x-auto">
              <table className="table">
                <thead><tr>
                  <th>{t('common.date', { defaultValue: 'Date' })}</th>
                  <th>{t('common.amount', { defaultValue: 'Amount' })}</th>
                  <th>{t('logistics.convertedAmount', { defaultValue: `Converted (${cur})` })}</th>
                  <th>{t('logistics.fxDelta', { defaultValue: 'FX delta' })}</th>
                  <th>{t('logistics.paymentSource', { defaultValue: 'Source' })}</th>
                  <th>{t('common.note', { defaultValue: 'Note' })}</th>
                </tr></thead>
                <tbody>
                  {data.payments.map((p: any) => {
                    const fx = Number(p.fxDelta || 0);
                    return (
                      <tr key={p.id}>
                        <td className="text-xs">{formatDateTime(p.paidAt)}</td>
                        <td className="num">{formatMoney(p.amount, p.currency)}</td>
                        <td className="num text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatMoney(p.convertedAmount, cur)}</td>
                        <td className={`num text-sm ${fx > 0 ? 'text-red-600 dark:text-red-400' : fx < 0 ? 'text-green-600 dark:text-green-400' : ''}`}
                          style={fx === 0 ? { color: 'rgb(var(--color-text-muted))' } : undefined}>
                          {fx === 0 ? '—' : `${fx > 0 ? t('logistics.fxLoss', { defaultValue: 'loss' }) : t('logistics.fxGain', { defaultValue: 'gain' })} ${formatMoney(Math.abs(fx), cur)}`}
                        </td>
                        <td><span className="badge-blue text-xs capitalize">{p.paymentSource}</span></td>
                        <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.note || '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {/* Mobile cards */}
            <div className="md:hidden divide-y" style={{ borderColor: 'rgb(var(--color-border))' }}>
              {data.payments.map((p: any) => {
                const fx = Number(p.fxDelta || 0);
                return (
                  <div key={p.id} className="p-3 space-y-1">
                    <div className="flex justify-between items-start gap-2">
                      <div className="font-semibold num">{formatMoney(p.amount, p.currency)}</div>
                      <span className="badge-blue text-xs capitalize shrink-0">{p.paymentSource}</span>
                    </div>
                    <div className="text-xs flex flex-wrap gap-x-3" style={{ color: 'rgb(var(--color-text-muted))' }}>
                      <span>{formatDateTime(p.paidAt)}</span>
                      <span>≈ {formatMoney(p.convertedAmount, cur)}</span>
                    </div>
                    {fx !== 0 && (
                      <div className={`text-xs ${fx > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>
                        {t('logistics.fxDelta', { defaultValue: 'FX' })}: {fx > 0 ? t('logistics.fxLoss', { defaultValue: 'loss' }) : t('logistics.fxGain', { defaultValue: 'gain' })} {formatMoney(Math.abs(fx), cur)}
                      </div>
                    )}
                    {p.note && <div className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.note}</div>}
                  </div>
                );
              })}
            </div>
          </DetailSection>
        </div>
      )}

      {/* Footer info */}
      <div className="mt-4 grid lg:grid-cols-2 gap-4">
        <DetailSection title={t('logistics.routeSection', { defaultValue: 'Route' })}>
          <div className="text-sm space-y-1">
            <div>
              <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.source', { defaultValue: 'Source' })}: </span>
              <span className="font-medium">#{order.sourceWarehouseId}</span>
            </div>
            <div>
              <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.destination', { defaultValue: 'Destination' })}: </span>
              <span className="font-medium">#{order.destinationWarehouseId}</span>
            </div>
            {order.supplierId && (
              <div>
                <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.supplier', { defaultValue: 'Supplier' })}: </span>
                <span className="font-medium">#{order.supplierId}</span>
              </div>
            )}
            {order.exchangeRate && (
              <div>
                <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.exchangeRate', { defaultValue: 'Exchange rate' })}: </span>
                <span className="num">{order.exchangeRate}</span>
              </div>
            )}
          </div>
        </DetailSection>
        {(order.confirmedAt || order.cancelledAt) && (
          <DetailSection title={t('logistics.lifecycleSection', { defaultValue: 'Lifecycle' })}>
            <div className="text-sm space-y-1">
              <div>{t('common.created', { defaultValue: 'Created' })}: {formatDateTime(order.createdAt)}</div>
              {order.confirmedAt && <div>{t('logistics.confirmed', { defaultValue: 'Confirmed' })}: {formatDateTime(order.confirmedAt)}</div>}
              {order.cancelledAt && <div>{t('common.cancelled', { defaultValue: 'Cancelled' })}: {formatDateTime(order.cancelledAt)}</div>}
              {order.note && <div className="mt-2"><span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.note', { defaultValue: 'Note' })}: </span>{order.note}</div>}
            </div>
          </DetailSection>
        )}
      </div>

      <ConfirmModal
        open={showConfirm}
        onClose={() => setShowConfirm(false)}
        onConfirm={() => confirmMut.mutate()}
        title={t('logistics.confirmTitle', { defaultValue: 'Confirm logistics order?' })}
        message={t('logistics.confirmMessage', { defaultValue: 'This will transfer stock to the destination warehouse, debit cash/bank for each expense, and post a balanced journal entry. This action cannot be undone.' })}
      />
      <ConfirmModal
        open={showCancel}
        onClose={() => setShowCancel(false)}
        onConfirm={() => cancelMut.mutate()}
        title={t('logistics.cancelTitle', { defaultValue: 'Cancel draft?' })}
        message={t('logistics.cancelMessage', { defaultValue: 'This draft will be marked cancelled. No stock movement or accounting impact.' })}
        danger
      />

      <Modal open={payExpenseId != null} onClose={() => { setPayExpenseId(null); setPayAmount(''); setPayNote(''); setPayCurrency(''); setPayRate(''); }}
        title={t('logistics.payTitle', { defaultValue: 'Settle logistics payable' })} size="sm">
        {(() => {
          const expenseRow = expenses.find((e: any) => e.id === payExpenseId);
          const expenseCurrency = expenseRow?.currency || cur;
          const effectiveCurrency = payCurrency || expenseCurrency;
          const isCrossCurrency = effectiveCurrency !== expenseCurrency;
          const showRate = isCrossCurrency || (effectiveCurrency !== cur);
          return (
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">{t('common.amount', { defaultValue: 'Amount' })} *</label>
                  <input className="input" type="number" min="0.01" step="0.01" value={payAmount} onChange={e => setPayAmount(e.target.value)} />
                </div>
                <div>
                  <label className="label">{t('common.currency', { defaultValue: 'Currency' })}</label>
                  <select className="input" value={effectiveCurrency} onChange={e => setPayCurrency(e.target.value)}>
                    {/* Default = expense currency; add base/order if different */}
                    <option value={expenseCurrency}>{expenseCurrency}</option>
                    {cur !== expenseCurrency && <option value={cur}>{cur} ({t('logistics.orderCurrency', { defaultValue: 'order' })})</option>}
                    {['USD', 'UZS', 'CNY', 'EUR', 'RUB'].filter(c => c !== expenseCurrency && c !== cur).map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
              </div>
              {showRate && (
                <div>
                  <label className="label">{t('logistics.payRate', { defaultValue: `Exchange rate ${effectiveCurrency} → ${cur}` })} {isCrossCurrency && '*'}</label>
                  <input className="input" type="number" min="0" step="0.000001"
                    placeholder={isCrossCurrency ? t('logistics.requiredField', { defaultValue: 'required' })
                                                  : (expenseRow?.exchangeRate ? String(expenseRow.exchangeRate) : '1')}
                    value={payRate} onChange={e => setPayRate(e.target.value)} />
                  {!isCrossCurrency && expenseRow?.exchangeRate && (
                    <p className="text-xs mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>
                      {t('logistics.payRateHint', { defaultValue: 'Booked at' })}: {expenseRow.exchangeRate}.
                      {t('logistics.payRateFxHint', { defaultValue: ' Different rate → FX gain/loss posted automatically.' })}
                    </p>
                  )}
                </div>
              )}
              <div>
                <label className="label">{t('logistics.paymentSource', { defaultValue: 'Pay from' })}</label>
                <select className="input" value={paySource} onChange={e => setPaySource(e.target.value as any)}>
                  <option value="cash">{t('common.cash', { defaultValue: 'Cash' })}</option>
                  <option value="bank">{t('common.bank', { defaultValue: 'Bank' })}</option>
                </select>
              </div>
              <div>
                <label className="label">{t('common.note', { defaultValue: 'Note' })}</label>
                <input className="input" value={payNote} onChange={e => setPayNote(e.target.value)} />
              </div>
              <div className="flex justify-end gap-2 pt-1">
                <button onClick={() => { setPayExpenseId(null); setPayAmount(''); setPayNote(''); setPayCurrency(''); setPayRate(''); }} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
                <button onClick={() => payMut.mutate()}
                  disabled={payMut.isPending || !payAmount || parseFloat(payAmount) <= 0
                            || (isCrossCurrency && (!payRate || parseFloat(payRate) <= 0))}
                  className="btn-primary gap-1.5">
                  <Wallet size={14} /> {payMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('logistics.payAction', { defaultValue: 'Pay' })}
                </button>
              </div>
            </div>
          );
        })()}
      </Modal>

      <Modal open={showReverse} onClose={() => { setShowReverse(false); setReverseReason(''); }}
        title={t('logistics.reverseTitle', { defaultValue: 'Reverse confirmed logistics order?' })} size="sm">
        <div className="space-y-3">
          <div className="card p-3" style={{ background: 'rgb(254, 226, 226)', borderColor: 'rgb(239, 68, 68)', color: '#7f1d1d' }}>
            <div className="font-semibold text-sm mb-1">{t('logistics.reverseImpactTitle', { defaultValue: 'This will permanently:' })}</div>
            <ul className="text-xs list-disc pl-4 space-y-0.5">
              <li>{t('logistics.reverseImpact1', { defaultValue: 'Transfer stock back from destination → source' })}</li>
              <li>{t('logistics.reverseImpact2', { defaultValue: 'Mark expense cash/bank transactions as reversed and refund balances' })}</li>
              <li>{t('logistics.reverseImpact3', { defaultValue: 'Post a mirror journal entry (REVERSAL/LOGISTICS)' })}</li>
              <li>{t('logistics.reverseImpact4', { defaultValue: 'Fail if destination stock has already been sold or transferred away' })}</li>
            </ul>
          </div>
          <div>
            <label className="label">{t('logistics.reverseReason', { defaultValue: 'Reason (recommended for audit)' })}</label>
            <textarea className="input" rows={2} value={reverseReason} onChange={e => setReverseReason(e.target.value)}
              placeholder={t('logistics.reverseReasonPlaceholder', { defaultValue: 'e.g. wrong shipment received, customs rejected, …' })} />
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => { setShowReverse(false); setReverseReason(''); }} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
            <button onClick={() => reverseMut.mutate()} disabled={reverseMut.isPending} className="btn-danger gap-1.5">
              <Undo2 size={14} /> {reverseMut.isPending ? t('common.saving', { defaultValue: 'Reversing…' }) : t('logistics.reverseConfirmButton', { defaultValue: 'Reverse order' })}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
