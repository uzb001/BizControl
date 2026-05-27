'use client';

import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { purchasesApi, companyApi } from '@/lib/api';
import { formatMoney, formatDateTime } from '@/lib/utils';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { useTranslation } from 'react-i18next';
import { Printer, ArrowLeft } from 'lucide-react';

/** Printable purchase invoice. Uses the browser print dialog (paper or Save-as-PDF). */
export default function PurchaseInvoicePage() {
  const { id } = useParams();
  const router = useRouter();
  const { t } = useTranslation();

  const { data: purchase, isLoading } = useQuery({
    queryKey: ['purchase', id],
    queryFn: () => purchasesApi.get(Number(id)).then(r => r.data),
  });
  const { data: company } = useQuery({
    queryKey: ['company'],
    queryFn: () => companyApi.get().then(r => r.data),
  });

  if (isLoading) return <PageLoading />;
  if (!purchase) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('errors.notFound', { defaultValue: 'Not found' })}</div>;

  const cur = purchase.currency || 'UZS';
  const items: any[] = purchase.items ?? [];

  return (
    <div className="max-w-3xl mx-auto">
      <div className="no-print flex items-center justify-between mb-4">
        <button onClick={() => router.back()} className="btn-secondary btn-sm gap-1.5"><ArrowLeft size={14} /> {t('common.back', { defaultValue: 'Back' })}</button>
        <button onClick={() => window.print()} className="btn-primary btn-sm gap-1.5"><Printer size={14} /> {t('sales.printOrPdf', { defaultValue: 'Print / Save PDF' })}</button>
      </div>

      <div className="print-sheet card p-8 bg-white" style={{ color: '#0f172a' }}>
        <div className="flex items-start justify-between gap-4 pb-5 border-b" style={{ borderColor: '#e2e8f0' }}>
          <div>
            <h1 className="text-2xl font-bold">{company?.name || 'Company'}</h1>
            <div className="text-xs text-slate-500 mt-1 space-y-0.5">
              {company?.address && <div>{company.address}</div>}
              {company?.phone && <div>{t('common.phone', { defaultValue: 'Phone' })}: {company.phone}</div>}
              {company?.taxId && <div>{t('invoice.taxId', { defaultValue: 'Tax ID' })}: {company.taxId}</div>}
            </div>
          </div>
          <div className="text-right">
            <div className="text-lg font-bold uppercase tracking-wide">{t('invoice.purchaseTitle', { defaultValue: 'Purchase Invoice' })}</div>
            <div className="text-sm font-mono mt-1">{purchase.purchaseNumber}</div>
            <div className="text-xs text-slate-500 mt-1">{formatDateTime(purchase.purchaseDate)}</div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6 py-5 text-sm">
          <div>
            <div className="text-xs font-semibold uppercase text-slate-400 mb-1">{t('invoice.supplier', { defaultValue: 'Supplier' })}</div>
            <div className="font-medium">{purchase.supplier?.name || '—'}</div>
            {purchase.supplier?.phone && <div className="text-xs text-slate-500">{purchase.supplier.phone}</div>}
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-500">{t('invoice.paymentStatus', { defaultValue: 'Payment status' })}: <span className="font-semibold uppercase">{purchase.paymentStatus}</span></div>
            <div className="text-xs text-slate-500">{t('common.currency', { defaultValue: 'Currency' })}: {cur}</div>
            {purchase.createdBy && <div className="text-xs text-slate-500">{t('invoice.cashier', { defaultValue: 'User' })}: #{purchase.createdBy}</div>}
          </div>
        </div>

        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="text-left" style={{ borderBottom: '2px solid #e2e8f0' }}>
              <th className="py-2 pr-2">#</th>
              <th className="py-2 pr-2">{t('common.product', { defaultValue: 'Product' })}</th>
              <th className="py-2 px-2 text-right">{t('common.qty', { defaultValue: 'Qty' })}</th>
              <th className="py-2 px-2 text-right">{t('purchases.unitCost', { defaultValue: 'Unit cost' })}</th>
              <th className="py-2 px-2 text-right">{t('common.discount', { defaultValue: 'Discount' })}</th>
              <th className="py-2 pl-2 text-right">{t('common.total', { defaultValue: 'Total' })}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it: any, i: number) => (
              <tr key={it.id ?? i} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td className="py-2 pr-2 text-slate-400">{i + 1}</td>
                <td className="py-2 pr-2">{it.productName ?? it.product?.name ?? `#${it.productId ?? ''}`}</td>
                <td className="py-2 px-2 text-right num">{it.quantity}</td>
                <td className="py-2 px-2 text-right num">{formatMoney(it.purchasePrice, cur)}</td>
                <td className="py-2 px-2 text-right num">{formatMoney(it.discountAmount, cur)}</td>
                <td className="py-2 pl-2 text-right num font-medium">{formatMoney(it.totalAmount, cur)}</td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr><td colSpan={6} className="py-4 text-center text-slate-400">{t('purchases.noProductsAdded', { defaultValue: 'No items' })}</td></tr>
            )}
          </tbody>
        </table>

        <div className="flex justify-end pt-5">
          <div className="w-64 space-y-1.5 text-sm">
            {Number(purchase.additionalCost) > 0 && (
              <div className="flex justify-between"><span className="text-slate-500">{t('purchases.additionalCosts', { defaultValue: 'Additional costs' })}</span><span className="num">{formatMoney(purchase.additionalCost, cur)}</span></div>
            )}
            {Number(purchase.discountAmount) > 0 && (
              <div className="flex justify-between"><span className="text-slate-500">{t('common.discount', { defaultValue: 'Discount' })}</span><span className="num text-red-600">-{formatMoney(purchase.discountAmount, cur)}</span></div>
            )}
            <div className="flex justify-between font-bold text-base border-t pt-1.5" style={{ borderColor: '#e2e8f0' }}>
              <span>{t('common.total', { defaultValue: 'Total' })}</span><span className="num">{formatMoney(purchase.totalAmount, cur)}</span>
            </div>
            <div className="flex justify-between"><span className="text-slate-500">{t('purchases.paidAmount', { defaultValue: 'Paid' })}</span><span className="num text-green-600">{formatMoney(purchase.paidAmount, cur)}</span></div>
            {Number(purchase.unpaidAmount) > 0 && (
              <div className="flex justify-between"><span className="text-slate-500">{t('purchases.unpaidAmount', { defaultValue: 'Remaining (we owe)' })}</span><span className="num text-red-600 font-semibold">{formatMoney(purchase.unpaidAmount, cur)}</span></div>
            )}
          </div>
        </div>

        <div className="mt-8 pt-4 border-t text-xs text-slate-400 flex justify-between" style={{ borderColor: '#e2e8f0' }}>
          <span>{t('invoice.generated', { defaultValue: 'Generated' })}: {formatDateTime(new Date().toISOString())}</span>
          <span>{purchase.purchaseNumber}</span>
        </div>
      </div>
    </div>
  );
}
