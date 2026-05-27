'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { salesApi, customersApi, productsApi, warehousesApi } from '@/lib/api';
import { formatMoney, cn } from '@/lib/utils';
import { usePermission } from '@/hooks/usePermission';
import { ShieldAlert, Plus, X, Search } from 'lucide-react';

interface SaleItem {
  productId: number;
  productName: string;
  quantity: number;
  sellingPrice: number;
  discountAmount: number;
  stock: number;
  unit: string;
}

export default function NewSalePage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();

  // ── Permission guard ─────────────────────────────────────────────────────
  if (!can('sales.create')) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-4">
        <ShieldAlert size={48} className="text-red-400" />
        <p className="text-lg font-semibold text-gray-700">{t('errors.accessDenied')}</p>
        <p className="text-sm text-gray-500">{t('errors.permissionRequired', { perm: 'sales.create' })}</p>
        <button onClick={() => router.back()} className="btn-secondary btn-sm mt-2">{t('common.back')}</button>
      </div>
    );
  }

  return <NewSaleForm />;
}

function NewSaleForm() {
  const router = useRouter();
  const { t } = useTranslation();

  const [customerId, setCustomerId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [items, setItems] = useState<SaleItem[]>([]);
  const [discountType, setDiscountType] = useState<'amount' | 'percent'>('amount');
  const [discountValue, setDiscountValue] = useState(0);
  const [paidAmount, setPaidAmount] = useState(0);
  const [paymentMethod, setPaymentMethod] = useState('cash');
  const [currency, setCurrency] = useState('UZS');
  const [note, setNote] = useState('');
  const [saleDate, setSaleDate] = useState(new Date().toISOString().slice(0, 16));
  const [productSearch, setProductSearch] = useState('');
  const [showProductPicker, setShowProductPicker] = useState(false);

  const { data: customers } = useQuery({
    queryKey: ['customers-simple'],
    queryFn: () => customersApi.list({ size: 100, status: 'active' }).then(r => r.data?.content || []),
  });

  const { data: warehouses } = useQuery({
    queryKey: ['warehouses-simple'],
    queryFn: () => warehousesApi.list().then(r => (r.data || []).filter((w: any) => w.status !== 'archived')),
  });

  const { data: products } = useQuery({
    queryKey: ['products-search', productSearch],
    queryFn: () => productsApi.list({ search: productSearch, status: 'active', size: 20 }).then(r => r.data?.content || []),
    enabled: showProductPicker,
  });

  const subtotalRaw = items.reduce((sum, i) => sum + (i.quantity * i.sellingPrice) - i.discountAmount, 0);
  // Header discount: percent is computed off the subtotal; amount is capped to it.
  const discountPct = Math.min(100, Math.max(0, discountValue));
  const discountAmount = discountType === 'percent'
    ? Math.round(subtotalRaw * discountPct) / 100
    : Math.min(subtotalRaw, Math.max(0, discountValue));
  const total = Math.max(0, subtotalRaw - discountAmount);
  const unpaid = Math.max(0, total - paidAmount);

  const addItem = (product: any) => {
    const existing = items.find(i => i.productId === product.id);
    if (existing) {
      setItems(items.map(i => i.productId === product.id ? { ...i, quantity: i.quantity + 1 } : i));
    } else {
      setItems([...items, {
        productId:      product.id,
        productName:    product.name,
        quantity:       1,
        sellingPrice:   product.sellingPrice,
        discountAmount: 0,
        stock:          product.currentStock,
        unit:           product.unit,
      }]);
    }
    setShowProductPicker(false);
    setProductSearch('');
  };

  const removeItem = (productId: number) => setItems(items.filter(i => i.productId !== productId));

  const updateItem = (productId: number, field: keyof SaleItem, value: number) => {
    setItems(items.map(i => i.productId === productId ? { ...i, [field]: Math.max(0, value) } : i));
  };

  const createMutation = useMutation({
    mutationFn: (data: any) => salesApi.create(data),
    onSuccess: (res) => {
      toast.success(t('sales.createdSuccess'));
      router.push(`/sales/${res.data.id}`);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  const handleSubmit = () => {
    if (items.length === 0) { toast.error(t('validation.addAtLeastOneProduct')); return; }

    // Client-side guard: customer required for debt
    if (unpaid > 0 && !customerId) {
      toast.error(t('sales.customerRequiredForDebt'));
      return;
    }

    // Validate quantities
    const badItem = items.find(i => i.quantity <= 0 || i.quantity > i.stock);
    if (badItem) {
      toast.error(badItem.quantity <= 0
        ? t('validation.quantityMustBePositive')
        : t('sales.insufficientStock', { name: badItem.productName, stock: badItem.stock }));
      return;
    }

    if (paidAmount < 0) { toast.error(t('validation.paidAmountNegative')); return; }

    createMutation.mutate({
      customerId: customerId || null,
      warehouseId: warehouseId ? Number(warehouseId) : null,
      saleDate,
      items: items.map(i => ({
        productId:      i.productId,
        quantity:       i.quantity,
        sellingPrice:   i.sellingPrice,
        discountAmount: i.discountAmount,
      })),
      discountAmount,
      paidAmount,
      paymentMethod,
      currency,
      note,
    });
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="page-header">
        <h1 className="page-title">{t('sales.newSale')}</h1>
        <button onClick={() => router.back()} className="btn-secondary btn-sm">← {t('common.back')}</button>
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Left: Products */}
        <div className="lg:col-span-2 space-y-4">
          <div className="card p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="section-title">{t('common.products')}</h3>
              <button onClick={() => setShowProductPicker(true)} className="btn-primary btn-sm flex items-center gap-1">
                <Plus size={13} /> {t('sales.addProduct')}
              </button>
            </div>

            {showProductPicker && (
              <div className="mb-3">
                <div className="relative mb-2">
                  <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    className="input pl-8"
                    placeholder={t('common.searchProducts')}
                    value={productSearch}
                    onChange={e => setProductSearch(e.target.value)}
                    autoFocus
                  />
                </div>
                <div className="border border-gray-200 rounded-lg max-h-48 overflow-y-auto">
                  {products?.map((p: any) => (
                    <button
                      key={p.id}
                      onClick={() => addItem(p)}
                      className="w-full flex items-center justify-between px-3 py-2 hover:bg-blue-50 border-b border-gray-100 last:border-0 text-left"
                    >
                      <div>
                        <div className="text-sm font-medium">{p.name}</div>
                        <div className="text-xs text-gray-500">
                          {p.sku} · {t('stock.stock')}: {p.currentStock} {p.unit}
                        </div>
                      </div>
                      <span className="text-sm font-medium text-blue-600">{formatMoney(p.sellingPrice)}</span>
                    </button>
                  ))}
                  {products?.length === 0 && (
                    <p className="px-3 py-4 text-sm text-gray-400 text-center">{t('common.noResults')}</p>
                  )}
                </div>
                <button onClick={() => setShowProductPicker(false)} className="text-xs text-gray-400 mt-1 hover:text-gray-600">
                  {t('common.close')}
                </button>
              </div>
            )}

            {items.length === 0 ? (
              <div className="text-center py-8 text-gray-400 text-sm">{t('sales.noProductsAdded')}</div>
            ) : (
              <div className="overflow-x-auto -mx-4 px-4">
                <table className="table min-w-[540px]">
                  <thead>
                    <tr>
                      <th className="text-left">{t('common.product')}</th>
                      <th className="text-center w-24">{t('common.qty')}</th>
                      <th className="text-right w-32">{t('common.price')}</th>
                      <th className="text-right w-28">{t('common.discount')}</th>
                      <th className="text-right w-32">{t('common.total')}</th>
                      <th className="w-10"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(item => (
                      <tr key={item.productId}>
                        <td className="min-w-[140px]">
                          <div className="text-sm font-medium truncate max-w-[220px]">{item.productName}</div>
                          <div className="text-xs text-gray-400">{t('stock.stock')}: {item.stock} {item.unit}</div>
                        </td>
                        <td className="text-center">
                          <input
                            type="number"
                            min="0.001"
                            max={item.stock}
                            step="0.001"
                            value={item.quantity}
                            onChange={e => {
                              const v = parseFloat(e.target.value) || 0;
                              updateItem(item.productId, 'quantity', Math.min(v, item.stock));
                            }}
                            className={`input w-20 text-center num ${item.quantity > item.stock ? 'border-red-400' : ''}`}
                          />
                        </td>
                        <td className="text-right">
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={item.sellingPrice}
                            onChange={e => updateItem(item.productId, 'sellingPrice', parseFloat(e.target.value) || 0)}
                            className="input w-28 text-right num"
                          />
                        </td>
                        <td className="text-right">
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={item.discountAmount}
                            onChange={e => updateItem(item.productId, 'discountAmount', parseFloat(e.target.value) || 0)}
                            className="input w-24 text-right num"
                          />
                        </td>
                        <td className="font-medium text-right num whitespace-nowrap">{formatMoney((item.quantity * item.sellingPrice) - item.discountAmount)}</td>
                        <td className="text-center">
                          <button onClick={() => removeItem(item.productId)} className="text-red-400 hover:text-red-600" aria-label="Remove">
                            <X size={14} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Right: Summary */}
        <div className="space-y-4">
          <div className="card p-4 space-y-3">
            <h3 className="section-title">{t('sales.saleDetails')}</h3>
            <div>
              <label className="label">{t('common.customer')}</label>
              <select className="input" value={customerId} onChange={e => setCustomerId(e.target.value)}>
                <option value="">{t('sales.walkInCustomer')}</option>
                {customers?.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('warehouses.sourceWarehouse', { defaultValue: 'Source warehouse' })}</label>
              <select className="input" value={warehouseId} onChange={e => setWarehouseId(e.target.value)}>
                <option value="">{t('warehouses.mainDefault', { defaultValue: 'Main warehouse (default)' })}</option>
                {warehouses?.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('sales.saleDate')}</label>
              <input type="datetime-local" className="input" value={saleDate} onChange={e => setSaleDate(e.target.value)} />
            </div>
            <div>
              <label className="label">{t('common.paymentMethod')}</label>
              <select className="input" value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)}>
                <option value="cash">{t('common.cash')}</option>
                <option value="bank">{t('common.bankTransfer')}</option>
                <option value="debt">{t('common.debt')}</option>
              </select>
            </div>
            <div>
              <label className="label">{t('common.currency')}</label>
              <select className="input" value={currency} onChange={e => setCurrency(e.target.value)}>
                <option value="UZS">UZS</option>
                <option value="USD">USD</option>
              </select>
            </div>
          </div>

          <div className="card p-4 space-y-3">
            <h3 className="section-title">{t('common.payment')}</h3>
            <div>
              <label className="label">{t('common.discount')}</label>
              <div className="flex gap-2">
                <div className="segment shrink-0">
                  <button type="button" onClick={() => setDiscountType('amount')} className={cn('segment-item', discountType === 'amount' && 'active')}>{currency}</button>
                  <button type="button" onClick={() => setDiscountType('percent')} className={cn('segment-item', discountType === 'percent' && 'active')}>%</button>
                </div>
                <input type="number" min="0" max={discountType === 'percent' ? 100 : undefined} step="0.01" className="input num flex-1"
                  value={discountValue}
                  onChange={e => setDiscountValue(Math.max(0, parseFloat(e.target.value) || 0))} />
              </div>
              {discountType === 'percent' && discountValue > 0 && (
                <p className="hint mt-1">{discountPct}% = {formatMoney(discountAmount)}</p>
              )}
            </div>
            <div className="pt-2 border-t border-gray-100 space-y-1">
              <div className="flex justify-between items-baseline gap-2 text-sm">
                <span className="text-gray-500 shrink-0">{t('common.subtotal')}:</span>
                <span className="num text-right break-all">{formatMoney(subtotalRaw)}</span>
              </div>
              <div className="flex justify-between items-baseline gap-2 text-sm">
                <span className="text-gray-500 shrink-0">{t('common.discount')}:</span>
                <span className="text-red-500 num text-right break-all">-{formatMoney(discountAmount)}</span>
              </div>
              <div className="flex justify-between items-baseline gap-2 font-bold text-base border-t pt-2">
                <span className="shrink-0">{t('common.total')}:</span>
                <span className="num text-right break-all">{formatMoney(total)}</span>
              </div>
            </div>
            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
                <label className="label mb-0">{t('sales.paidAmount')}</label>
                <div className="flex flex-wrap gap-1.5">
                  <button type="button" onClick={() => setPaidAmount(total)}
                    className="text-xs px-2 py-0.5 rounded-md border whitespace-nowrap" style={{ borderColor: 'rgb(var(--color-border))', color: 'rgb(var(--color-text-secondary))' }}>
                    {t('sales.payFull', { defaultValue: 'Full' })}
                  </button>
                  <button type="button" onClick={() => setPaidAmount(Math.round((total / 2) * 100) / 100)}
                    className="text-xs px-2 py-0.5 rounded-md border whitespace-nowrap" style={{ borderColor: 'rgb(var(--color-border))', color: 'rgb(var(--color-text-secondary))' }}>
                    {t('sales.payHalf', { defaultValue: 'Half' })}
                  </button>
                  <button type="button" onClick={() => setPaidAmount(0)}
                    className="text-xs px-2 py-0.5 rounded-md border whitespace-nowrap" style={{ borderColor: 'rgb(var(--color-border))', color: 'rgb(var(--color-text-secondary))' }}>
                    {t('sales.payCredit', { defaultValue: 'Credit (0)' })}
                  </button>
                </div>
              </div>
              <input type="number" min="0" max={total} step="0.01" className="input num" value={paidAmount}
                onChange={e => setPaidAmount(Math.min(total, Math.max(0, parseFloat(e.target.value) || 0)))} />
            </div>
            {unpaid > 0 && (
              <div className={`border rounded-lg p-3 ${customerId ? 'bg-amber-50 border-amber-200' : 'bg-red-50 border-red-200'}`}>
                <p className={`text-sm font-medium ${customerId ? 'text-amber-700' : 'text-red-700'}`}>
                  {t('sales.unpaidAmount')}: <strong>{formatMoney(unpaid)}</strong>
                </p>
                {!customerId && (
                  <p className="text-xs text-red-500 mt-1">{t('sales.customerRequiredForDebtHint')}</p>
                )}
              </div>
            )}
            <div>
              <label className="label">{t('common.note')}</label>
              <textarea className="input" rows={2} value={note} onChange={e => setNote(e.target.value)}
                placeholder={t('common.optionalNote')} />
            </div>
            <button
              onClick={handleSubmit}
              disabled={createMutation.isPending || items.length === 0 || (unpaid > 0 && !customerId)}
              className="btn-primary w-full"
            >
              {createMutation.isPending
                ? t('common.saving')
                : (unpaid > 0 && !customerId)
                  ? t('sales.selectCustomerForCredit', { defaultValue: 'Select a customer for credit sale' })
                  : `${t('sales.createSale')} (${formatMoney(total)})`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
