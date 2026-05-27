'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { logisticsApi, countriesApi, warehousesApi, productsApi, suppliersApi } from '@/lib/api';
import { usePermission } from '@/hooks/usePermission';
import { formatMoney } from '@/lib/utils';
import { Ship, Plus, X, ArrowLeft } from 'lucide-react';

type ItemRow   = { productId: string; quantity: string; unitCost: string };
type ExpenseRow = {
  expenseType: string; amount: string; currency: string; exchangeRate: string;
  paymentSource: 'cash' | 'bank'; paidAmount: string; note: string;
};

const EXPENSE_TYPES = ['shipping', 'customs', 'broker', 'insurance', 'other'] as const;
const CURRENCIES = ['USD', 'UZS', 'CNY', 'EUR', 'RUB'] as const;

/**
 * Create-logistics-order wizard. Single-page form: source/destination,
 * supplier (optional), items, expenses, and an inline landed-cost preview
 * computed client-side. Submits the entire payload as one POST; backend
 * persists it as a draft, then the user clicks Confirm.
 */
export default function NewLogisticsOrderPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();

  const [sourceCountryId, setSourceCountryId] = useState('');
  const [sourceWarehouseId, setSourceWarehouseId] = useState('');
  const [destinationCountryId, setDestinationCountryId] = useState('');
  const [destinationWarehouseId, setDestinationWarehouseId] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [exchangeRate, setExchangeRate] = useState('1');
  const [note, setNote] = useState('');
  const [confirmAfterCreate, setConfirmAfterCreate] = useState(true);

  const [items, setItems] = useState<ItemRow[]>([{ productId: '', quantity: '', unitCost: '' }]);
  const [expenses, setExpenses] = useState<ExpenseRow[]>([{
    expenseType: 'shipping', amount: '', currency: 'USD', exchangeRate: '1',
    paymentSource: 'cash', paidAmount: '', note: '',
  }]);

  // Catalogs
  const { data: countries = [] } = useQuery({
    queryKey: ['countries', 'active', 'for-logistics-form'],
    queryFn: () => countriesApi.list({ status: 'active' }).then(r => r.data),
    enabled: can('countries.view'), retry: false,
  });
  const { data: warehouses = [] } = useQuery({
    queryKey: ['wh-list-logistics'], queryFn: () => warehousesApi.list().then(r => r.data), retry: false,
  });
  const { data: products = [] } = useQuery({
    queryKey: ['products-for-logistics'],
    queryFn: () => productsApi.list({ size: 500 }).then(r => r.data.content ?? r.data ?? []),
    retry: false,
  });
  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers-for-logistics'],
    queryFn: () => suppliersApi.list({ size: 200 }).then(r => r.data.content ?? r.data ?? []),
    retry: false,
  });

  const activeWarehouses = warehouses.filter((w: any) => w.status !== 'archived');
  // When a country is picked, narrow the warehouse list (still keep unassigned visible)
  const filterWh = (countryId: string) => activeWarehouses.filter((w: any) => !countryId || !w.countryId || String(w.countryId) === countryId);

  // ── Inline calculations (mirror backend math for preview) ────────────────
  const itemsValue = useMemo(() =>
    items.reduce((s, i) => s + (parseFloat(i.quantity) || 0) * (parseFloat(i.unitCost) || 0), 0),
  [items]);
  // Total reported in the ORDER currency: amount × exchangeRate per row.
  const expensesTotal = useMemo(() =>
    expenses.reduce((s, e) => s + (parseFloat(e.amount) || 0) * (parseFloat(e.exchangeRate) || 0), 0),
  [expenses]);
  const landedTotal = itemsValue + expensesTotal;

  const preview = useMemo(() => {
    if (itemsValue <= 0 || expensesTotal <= 0) return [];
    return items.map(it => {
      const q = parseFloat(it.quantity) || 0;
      const v = q * (parseFloat(it.unitCost) || 0);
      const alloc = expensesTotal * (v / itemsValue);
      const landed = q > 0 ? (v + alloc) / q : 0;
      return { name: products.find((p: any) => String(p.id) === it.productId)?.name || '—',
               value: v, allocated: alloc, unit: landed };
    });
  }, [items, itemsValue, expensesTotal, products]);

  // Default unitCost when product selected
  const onPickProduct = (idx: number, productId: string) => {
    const p = products.find((x: any) => String(x.id) === productId);
    setItems(arr => arr.map((row, i) =>
      i === idx ? { ...row, productId, unitCost: row.unitCost || String(p?.purchasePrice ?? '') } : row));
  };

  // ── Submit ───────────────────────────────────────────────────────────────
  const createMut = useMutation({
    mutationFn: async () => {
      const body: any = {
        sourceCountryId: sourceCountryId || null,
        sourceWarehouseId: Number(sourceWarehouseId),
        destinationCountryId: destinationCountryId || null,
        destinationWarehouseId: Number(destinationWarehouseId),
        supplierId: supplierId ? Number(supplierId) : null,
        currency,
        exchangeRate: parseFloat(exchangeRate) || 1,
        note,
        items: items
          .filter(i => i.productId && parseFloat(i.quantity) > 0)
          .map(i => ({ productId: Number(i.productId), quantity: parseFloat(i.quantity),
                       unitCost: parseFloat(i.unitCost) || 0 })),
        expenses: expenses
          .filter(e => parseFloat(e.amount) > 0)
          .map(e => ({
            expenseType: e.expenseType,
            amount: parseFloat(e.amount),
            currency: e.currency,
            exchangeRate: parseFloat(e.exchangeRate) || 1,
            paymentSource: e.paymentSource,
            // Empty paidAmount → backend defaults to full (paid). Set to 0 to leave unpaid.
            ...(e.paidAmount !== '' && !isNaN(parseFloat(e.paidAmount))
                ? { paidAmount: parseFloat(e.paidAmount) } : {}),
            note: e.note,
          })),
      };
      const created = await logisticsApi.create(body).then(r => r.data);
      if (confirmAfterCreate) await logisticsApi.confirm(created.id);
      return created;
    },
    onSuccess: (created: any) => {
      toast.success(confirmAfterCreate
        ? t('logistics.confirmedToast', { defaultValue: 'Order confirmed — stock + ledger updated' })
        : t('logistics.draftToast', { defaultValue: 'Draft saved' }));
      router.push(`/logistics/${created.id}`);
    },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' })),
  });

  const canSubmit = sourceWarehouseId && destinationWarehouseId
    && sourceWarehouseId !== destinationWarehouseId
    && items.some(i => i.productId && parseFloat(i.quantity) > 0);

  return (
    <div className="max-w-5xl mx-auto">
      <div className="page-header">
        <div className="flex items-center gap-2">
          <button onClick={() => router.back()} className="btn-icon !w-8 !h-8"><ArrowLeft size={16} /></button>
          <h1 className="page-title flex items-center gap-2"><Ship size={22} /> {t('logistics.newOrder', { defaultValue: 'New logistics order' })}</h1>
        </div>
      </div>

      {/* Source / destination */}
      <div className="card p-5 mb-4">
        <div className="text-sm font-semibold mb-3">{t('logistics.routeSection', { defaultValue: 'Route' })}</div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div>
            <label className="label">{t('logistics.sourceCountry', { defaultValue: 'Source country' })}</label>
            <select className="input" value={sourceCountryId} onChange={e => setSourceCountryId(e.target.value)}>
              <option value="">—</option>
              {countries.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('logistics.sourceWarehouse', { defaultValue: 'Source warehouse' })} *</label>
            <select className="input" value={sourceWarehouseId} onChange={e => setSourceWarehouseId(e.target.value)}>
              <option value="">—</option>
              {filterWh(sourceCountryId).map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('logistics.destinationCountry', { defaultValue: 'Destination country' })}</label>
            <select className="input" value={destinationCountryId} onChange={e => setDestinationCountryId(e.target.value)}>
              <option value="">—</option>
              {countries.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('logistics.destinationWarehouse', { defaultValue: 'Destination warehouse' })} *</label>
            <select className="input" value={destinationWarehouseId} onChange={e => setDestinationWarehouseId(e.target.value)}>
              <option value="">—</option>
              {filterWh(destinationCountryId).filter((w: any) => String(w.id) !== sourceWarehouseId).map((w: any) =>
                <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mt-3">
          <div>
            <label className="label">{t('logistics.supplier', { defaultValue: 'Supplier (optional)' })}</label>
            <select className="input" value={supplierId} onChange={e => setSupplierId(e.target.value)}>
              <option value="">—</option>
              {suppliers.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('common.currency', { defaultValue: 'Currency' })}</label>
            <input className="input uppercase" maxLength={10} value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} />
          </div>
          <div>
            <label className="label">{t('logistics.exchangeRate', { defaultValue: 'Exchange rate' })}</label>
            <input className="input" type="number" min="0" step="0.000001" value={exchangeRate} onChange={e => setExchangeRate(e.target.value)} />
          </div>
          <div>
            <label className="label">{t('common.note', { defaultValue: 'Note' })}</label>
            <input className="input" value={note} onChange={e => setNote(e.target.value)} />
          </div>
        </div>
      </div>

      {/* Items */}
      <div className="card p-5 mb-4">
        <div className="flex justify-between items-center mb-3">
          <div className="text-sm font-semibold">{t('logistics.itemsSection', { defaultValue: 'Items' })}</div>
          <button onClick={() => setItems(a => [...a, { productId: '', quantity: '', unitCost: '' }])} className="btn-secondary btn-sm gap-1.5"><Plus size={13} /> {t('logistics.addItem', { defaultValue: 'Add item' })}</button>
        </div>
        <div className="space-y-2">
          {items.map((it, idx) => (
            <div key={idx} className="grid grid-cols-12 gap-2 items-end">
              <div className="col-span-5">
                {idx === 0 && <label className="label">{t('common.product', { defaultValue: 'Product' })}</label>}
                <select className="input" value={it.productId} onChange={e => onPickProduct(idx, e.target.value)}>
                  <option value="">—</option>
                  {products.map((p: any) => <option key={p.id} value={p.id}>{p.name}{p.sku ? ` (${p.sku})` : ''}</option>)}
                </select>
              </div>
              <div className="col-span-3">
                {idx === 0 && <label className="label">{t('common.quantity', { defaultValue: 'Qty' })}</label>}
                <input type="number" min="0" step="0.0001" className="input" value={it.quantity} onChange={e => setItems(a => a.map((r, i) => i === idx ? { ...r, quantity: e.target.value } : r))} />
              </div>
              <div className="col-span-3">
                {idx === 0 && <label className="label">{t('logistics.unitCost', { defaultValue: 'Unit cost' })}</label>}
                <input type="number" min="0" step="0.01" className="input" value={it.unitCost} onChange={e => setItems(a => a.map((r, i) => i === idx ? { ...r, unitCost: e.target.value } : r))} />
              </div>
              <div className="col-span-1 flex justify-end pb-0.5">
                {items.length > 1 && (
                  <button onClick={() => setItems(a => a.filter((_, i) => i !== idx))} className="btn-icon !w-8 !h-8 hover:!text-red-600" title={t('common.remove', { defaultValue: 'Remove' })}><X size={14} /></button>
                )}
              </div>
            </div>
          ))}
        </div>
        <div className="text-right text-sm mt-3 pt-3 border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
          <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.itemsValue', { defaultValue: 'Items value' })}: </span>
          <span className="num font-semibold">{formatMoney(itemsValue, currency)}</span>
        </div>
      </div>

      {/* Expenses */}
      <div className="card p-5 mb-4">
        <div className="flex justify-between items-center mb-3">
          <div className="text-sm font-semibold">{t('logistics.expensesSection', { defaultValue: 'Expenses' })}</div>
          <button onClick={() => setExpenses(a => [...a, { expenseType: 'shipping', amount: '', currency, exchangeRate: '1', paymentSource: 'cash', paidAmount: '', note: '' }])} className="btn-secondary btn-sm gap-1.5"><Plus size={13} /> {t('logistics.addExpense', { defaultValue: 'Add expense' })}</button>
        </div>
        <div className="space-y-2">
          {expenses.map((e, idx) => {
            const isDiffCurrency = e.currency !== currency;
            return (
              <div key={idx} className="grid grid-cols-12 gap-2 items-end pb-2 border-b" style={{ borderColor: 'rgb(var(--color-border))' }}>
                <div className="col-span-3">
                  {idx === 0 && <label className="label">{t('logistics.expenseType', { defaultValue: 'Type' })}</label>}
                  <select className="input" value={e.expenseType} onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, expenseType: ev.target.value } : r))}>
                    {EXPENSE_TYPES.map(et => <option key={et} value={et}>{t(`logistics.expense.${et}`, { defaultValue: et })}</option>)}
                  </select>
                </div>
                <div className="col-span-2">
                  {idx === 0 && <label className="label">{t('common.amount', { defaultValue: 'Amount' })}</label>}
                  <input type="number" min="0" step="0.01" className="input" value={e.amount} onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, amount: ev.target.value } : r))} />
                </div>
                <div className="col-span-1">
                  {idx === 0 && <label className="label">{t('common.currency', { defaultValue: 'Currency' })}</label>}
                  <select className="input !px-1.5" value={e.currency} onChange={ev => {
                    const newCur = ev.target.value;
                    setExpenses(a => a.map((r, i) => i === idx
                        ? { ...r, currency: newCur, exchangeRate: newCur === currency ? '1' : r.exchangeRate }
                        : r));
                  }}>
                    {CURRENCIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="col-span-1">
                  {idx === 0 && <label className="label" title={t('logistics.fxRateHint', { defaultValue: `Rate to ${currency}` })}>FX</label>}
                  <input type="number" min="0" step="0.000001"
                    className={`input ${isDiffCurrency ? 'ring-2 ring-yellow-400' : ''}`}
                    disabled={!isDiffCurrency}
                    value={e.exchangeRate}
                    onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, exchangeRate: ev.target.value } : r))} />
                </div>
                <div className="col-span-1">
                  {idx === 0 && <label className="label">{t('logistics.paymentSource', { defaultValue: 'Pay' })}</label>}
                  <select className="input !px-1.5" value={e.paymentSource} onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, paymentSource: ev.target.value as any } : r))}>
                    <option value="cash">{t('common.cash', { defaultValue: 'Cash' })}</option>
                    <option value="bank">{t('common.bank', { defaultValue: 'Bank' })}</option>
                  </select>
                </div>
                <div className="col-span-1">
                  {idx === 0 && <label className="label" title={t('logistics.paidAmountHint', { defaultValue: 'Blank = fully paid; 0 = unpaid (creates payable)' })}>{t('logistics.paid', { defaultValue: 'Paid' })}</label>}
                  <input type="number" min="0" step="0.01" placeholder={t('logistics.paidHint', { defaultValue: 'full' })}
                    className="input" value={e.paidAmount}
                    onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, paidAmount: ev.target.value } : r))} />
                </div>
                <div className="col-span-2">
                  {idx === 0 && <label className="label">{t('common.note', { defaultValue: 'Note' })}</label>}
                  <input className="input" value={e.note} onChange={ev => setExpenses(a => a.map((r, i) => i === idx ? { ...r, note: ev.target.value } : r))} />
                </div>
                <div className="col-span-1 flex justify-end pb-0.5">
                  {expenses.length > 1 && (
                    <button onClick={() => setExpenses(a => a.filter((_, i) => i !== idx))} className="btn-icon !w-8 !h-8 hover:!text-red-600"><X size={14} /></button>
                  )}
                </div>
                {isDiffCurrency && parseFloat(e.amount) > 0 && parseFloat(e.exchangeRate) > 0 && (
                  <div className="col-span-12 text-xs text-right" style={{ color: 'rgb(var(--color-text-muted))' }}>
                    {formatMoney(parseFloat(e.amount), e.currency)} × {e.exchangeRate} = <span className="font-semibold">{formatMoney(parseFloat(e.amount) * parseFloat(e.exchangeRate), currency)}</span> ({t('logistics.inOrderCurrency', { defaultValue: 'in order currency' })})
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <div className="text-right text-sm mt-3 pt-3 border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
          <span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('logistics.expensesTotal', { defaultValue: 'Expenses total' })}: </span>
          <span className="num font-semibold text-orange-600 dark:text-orange-400">{formatMoney(expensesTotal, currency)}</span>
        </div>
      </div>

      {/* Landed cost preview */}
      {preview.length > 0 && (
        <div className="card p-5 mb-4">
          <div className="text-sm font-semibold mb-3">{t('logistics.preview', { defaultValue: 'Landed cost preview' })}</div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr style={{ color: 'rgb(var(--color-text-muted))' }} className="text-left text-xs uppercase tracking-wide">
                <th className="py-1.5">{t('common.product', { defaultValue: 'Product' })}</th>
                <th className="py-1.5 text-right">{t('logistics.itemValue', { defaultValue: 'Item value' })}</th>
                <th className="py-1.5 text-right">{t('logistics.allocated', { defaultValue: 'Allocated' })}</th>
                <th className="py-1.5 text-right">{t('logistics.unitLandedCost', { defaultValue: 'Unit landed cost' })}</th>
              </tr></thead>
              <tbody>
                {preview.map((row, i) => (
                  <tr key={i} className="border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
                    <td className="py-1.5">{row.name}</td>
                    <td className="py-1.5 text-right num">{formatMoney(row.value, currency)}</td>
                    <td className="py-1.5 text-right num text-orange-600 dark:text-orange-400">{formatMoney(row.allocated, currency)}</td>
                    <td className="py-1.5 text-right num font-semibold">{formatMoney(row.unit, currency)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t-2 font-semibold" style={{ borderColor: 'rgb(var(--color-border))' }}>
                  <td className="py-2">{t('logistics.landedTotal', { defaultValue: 'Landed total' })}</td>
                  <td className="py-2 text-right num">{formatMoney(itemsValue, currency)}</td>
                  <td className="py-2 text-right num text-orange-600 dark:text-orange-400">{formatMoney(expensesTotal, currency)}</td>
                  <td className="py-2 text-right num">{formatMoney(landedTotal, currency)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}

      {/* Submit */}
      <div className="flex justify-between items-center gap-3 mb-8">
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={confirmAfterCreate} onChange={e => setConfirmAfterCreate(e.target.checked)} />
          {t('logistics.confirmAfterCreate', { defaultValue: 'Confirm immediately (transfer stock + book expenses)' })}
        </label>
        <div className="flex gap-2">
          <button onClick={() => router.back()} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
          <button onClick={() => createMut.mutate()} disabled={!canSubmit || createMut.isPending} className="btn-primary">
            {createMut.isPending ? t('common.saving', { defaultValue: 'Saving…' })
              : confirmAfterCreate ? t('logistics.createAndConfirm', { defaultValue: 'Create & confirm' })
                                   : t('logistics.saveDraft', { defaultValue: 'Save draft' })}
          </button>
        </div>
      </div>
    </div>
  );
}
