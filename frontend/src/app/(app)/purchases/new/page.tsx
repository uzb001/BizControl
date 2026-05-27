'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { purchasesApi, suppliersApi, productsApi, warehousesApi } from '@/lib/api';
import { formatMoney, cn } from '@/lib/utils';
import { usePermission } from '@/hooks/usePermission';
import { ShieldAlert, Plus, X, Search } from 'lucide-react';

interface PurchaseItem {
  productId: number;
  productName: string;
  quantity: number;
  purchasePrice: number;
  discountAmount: number;
  unit: string;
}

export default function NewPurchasePage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();

  // ── Permission guard ─────────────────────────────────────────────────────
  if (!can('purchases.create')) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-4">
        <ShieldAlert size={48} className="text-red-400" />
        <p className="text-lg font-semibold text-gray-700">{t('errors.accessDenied')}</p>
        <p className="text-sm text-gray-500">{t('errors.permissionRequired', { perm: 'purchases.create' })}</p>
        <button onClick={() => router.back()} className="btn-secondary btn-sm mt-2">{t('common.back')}</button>
      </div>
    );
  }

  return <NewPurchaseForm />;
}

function NewPurchaseForm() {
  const router = useRouter();
  const { t } = useTranslation();

  const [supplierId, setSupplierId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [items, setItems] = useState<PurchaseItem[]>([]);
  const [discountType, setDiscountType] = useState<'amount' | 'percent'>('amount');
  const [discountValue, setDiscountValue] = useState(0);
  const [additionalCost, setAdditionalCost] = useState(0);
  const [paidAmount, setPaidAmount] = useState(0);
  const [paymentMethod, setPaymentMethod] = useState('cash');
  const [currency, setCurrency] = useState('UZS');
  const [note, setNote] = useState('');
  const [purchaseDate, setPurchaseDate] = useState(new Date().toISOString().slice(0, 16));
  const [productSearch, setProductSearch] = useState('');
  const [showPicker, setShowPicker] = useState(false);

  const { data: suppliers } = useQuery({
    queryKey: ['suppliers-simple'],
    queryFn: () => suppliersApi.list({ size: 100 }).then(r => r.data?.content || []),
  });

  const { data: warehouses } = useQuery({
    queryKey: ['warehouses-simple'],
    queryFn: () => warehousesApi.list().then(r => (r.data || []).filter((w: any) => w.status !== 'archived')),
  });

  const { data: products } = useQuery({
    queryKey: ['products-search', productSearch],
    queryFn: () => productsApi.list({ search: productSearch, status: 'active', size: 20 }).then(r => r.data?.content || []),
    enabled: showPicker,
  });

  const subtotal = items.reduce((sum, i) => sum + (i.quantity * i.purchasePrice) - i.discountAmount, 0);
  const discountPct = Math.min(100, Math.max(0, discountValue));
  const discountAmount = discountType === 'percent'
    ? Math.round(subtotal * discountPct) / 100
    : Math.min(subtotal, Math.max(0, discountValue));
  const total    = Math.max(0, subtotal + additionalCost - discountAmount);
  const unpaid   = Math.max(0, total - paidAmount);

  const addItem = (product: any) => {
    const existing = items.find(i => i.productId === product.id);
    if (existing) {
      setItems(items.map(i => i.productId === product.id ? { ...i, quantity: i.quantity + 1 } : i));
    } else {
      setItems([...items, {
        productId:     product.id,
        productName:   product.name,
        quantity:      1,
        purchasePrice: product.purchasePrice,
        discountAmount: 0,
        unit:          product.unit,
      }]);
    }
    setShowPicker(false);
    setProductSearch('');
  };

  const updateItem = (productId: number, field: keyof PurchaseItem, value: number) => {
    setItems(items.map(i => i.productId === productId ? { ...i, [field]: Math.max(0, value) } : i));
  };

  const createMutation = useMutation({
    mutationFn: (data: any) => purchasesApi.create(data),
    onSuccess: (res) => {
      toast.success(t('purchases.createdSuccess'));
      router.push(`/purchases/${res.data.id}`);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  const handleSubmit = () => {
    if (items.length === 0) { toast.error(t('validation.addAtLeastOneProduct')); return; }

    // Client-side guard: supplier required when unpaid
    if (unpaid > 0 && !supplierId) {
      toast.error(t('purchases.supplierRequiredForDebt'));
      return;
    }

    // Validate all quantities > 0
    const badItem = items.find(i => i.quantity <= 0);
    if (badItem) { toast.error(t('validation.quantityMustBePositive')); return; }

    if (paidAmount < 0) { toast.error(t('validation.paidAmountNegative')); return; }

    createMutation.mutate({
      supplierId: supplierId || null,
      warehouseId: warehouseId ? Number(warehouseId) : null,
      purchaseDate,
      items: items.map(i => ({
        productId:     i.productId,
        quantity:      i.quantity,
        purchasePrice: i.purchasePrice,
        discountAmount: i.discountAmount,
      })),
      discountAmount, additionalCost, paidAmount, paymentMethod, currency, note,
    });
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="page-header">
        <h1 className="page-title">{t('purchases.newPurchase')}</h1>
        <button onClick={() => router.back()} className="btn-secondary btn-sm">← {t('common.back')}</button>
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Left: Products */}
        <div className="lg:col-span-2 space-y-4">
          <div className="card p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="section-title">{t('common.products')}</h3>
              <button onClick={() => setShowPicker(true)} className="btn-primary btn-sm flex items-center gap-1">
                <Plus size={13} /> {t('sales.addProduct')}
              </button>
            </div>

            {showPicker && (
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
                    <button key={p.id} onClick={() => addItem(p)}
                      className="w-full flex items-center justify-between px-3 py-2 hover:bg-blue-50 border-b border-gray-100 last:border-0 text-left">
                      <div>
                        <div className="text-sm font-medium">{p.name}</div>
                        <div className="text-xs text-gray-500">{p.sku} · {t('stock.stock')}: {p.currentStock} {p.unit}</div>
                      </div>
                      <span className="text-sm text-gray-600">{formatMoney(p.purchasePrice)}</span>
                    </button>
                  ))}
                  {products?.length === 0 && (
                    <p className="px-3 py-4 text-sm text-gray-400 text-center">{t('common.noResults')}</p>
                  )}
                </div>
                <button onClick={() => setShowPicker(false)} className="text-xs text-gray-400 mt-1">
                  {t('common.close')}
                </button>
              </div>
            )}

            {items.length === 0 ? (
              <div className="text-center py-8 text-gray-400 text-sm">{t('purchases.noProductsAdded')}</div>
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
                        <td className="min-w-[140px]"><div className="text-sm font-medium truncate max-w-[220px]">{item.productName}</div></td>
                        <td className="text-center">
                          <input type="number" min="0.001" step="0.001" value={item.quantity}
                            onChange={e => updateItem(item.productId, 'quantity', parseFloat(e.target.value) || 0)}
                            className="input w-20 text-center num" />
                        </td>
                        <td className="text-right">
                          <input type="number" min="0.01" step="0.01" value={item.purchasePrice}
                            onChange={e => updateItem(item.productId, 'purchasePrice', parseFloat(e.target.value) || 0)}
                            className="input w-28 text-right num" />
                        </td>
                        <td className="text-right">
                          <input type="number" min="0" step="0.01" value={item.discountAmount}
                            onChange={e => updateItem(item.productId, 'discountAmount', parseFloat(e.target.value) || 0)}
                            className="input w-24 text-right num" />
                        </td>
                        <td className="font-medium text-right num whitespace-nowrap">{formatMoney((item.quantity * item.purchasePrice) - item.discountAmount)}</td>
                        <td className="text-center">
                          <button onClick={() => setItems(items.filter(i => i.productId !== item.productId))} className="text-red-400 hover:text-red-600" aria-label="Remove">
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
            <h3 className="section-title">{t('purchases.purchaseDetails')}</h3>
            <div>
              <label className="label">{t('common.supplier')}</label>
              <select className="input" value={supplierId} onChange={e => setSupplierId(e.target.value)}>
                <option value="">{t('purchases.selectSupplier')}</option>
                {suppliers?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('warehouses.receivingWarehouse', { defaultValue: 'Receiving warehouse' })}</label>
              <select className="input" value={warehouseId} onChange={e => setWarehouseId(e.target.value)}>
                <option value="">{t('warehouses.mainDefault', { defaultValue: 'Main warehouse (default)' })}</option>
                {warehouses?.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('purchases.purchaseDate')}</label>
              <input type="datetime-local" className="input" value={purchaseDate} onChange={e => setPurchaseDate(e.target.value)} />
            </div>
            <div>
              <label className="label">{t('common.paymentMethod')}</label>
              <select className="input" value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)}>
                <option value="cash">{t('common.cash')}</option>
                <option value="bank">{t('common.bankTransfer')}</option>
                <option value="debt">{t('common.debt')}</option>
              </select>
            </div>
          </div>

          <div className="card p-4 space-y-3">
            <h3 className="section-title">{t('common.payment')}</h3>
            <div>
              <label className="label">{t('purchases.additionalCost')}</label>
              <input type="number" min="0" className="input" value={additionalCost}
                onChange={e => setAdditionalCost(Math.max(0, parseFloat(e.target.value) || 0))} />
            </div>
            <div>
              <label className="label">{t('common.discount')}</label>
              <div className="flex gap-2">
                <div className="segment shrink-0">
                  <button type="button" onClick={() => setDiscountType('amount')} className={cn('segment-item', discountType === 'amount' && 'active')}>{currency}</button>
                  <button type="button" onClick={() => setDiscountType('percent')} className={cn('segment-item', discountType === 'percent' && 'active')}>%</button>
                </div>
                <input type="number" min="0" max={discountType === 'percent' ? 100 : undefined} className="input num flex-1"
                  value={discountValue}
                  onChange={e => setDiscountValue(Math.max(0, parseFloat(e.target.value) || 0))} />
              </div>
              {discountType === 'percent' && discountValue > 0 && (
                <p className="hint mt-1">{discountPct}% = {formatMoney(discountAmount)}</p>
              )}
            </div>
            <div className="pt-2 border-t border-gray-100 space-y-1">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">{t('common.subtotal')}:</span>
                <span>{formatMoney(subtotal)}</span>
              </div>
              <div className="flex justify-between font-bold text-base border-t pt-2">
                <span>{t('common.total')}:</span>
                <span>{formatMoney(total)}</span>
              </div>
            </div>
            <div>
              <label className="label">{t('sales.paidAmount')}</label>
              <input type="number" min="0" className="input" value={paidAmount}
                onChange={e => setPaidAmount(Math.max(0, parseFloat(e.target.value) || 0))} />
            </div>
            {unpaid > 0 && (
              <div className={`border rounded-lg p-3 ${supplierId ? 'bg-amber-50 border-amber-200' : 'bg-red-50 border-red-200'}`}>
                <p className={`text-sm font-medium ${supplierId ? 'text-amber-700' : 'text-red-700'}`}>
                  {t('purchases.supplierDebt')}: <strong>{formatMoney(unpaid)}</strong>
                </p>
                {!supplierId && (
                  <p className="text-xs text-red-500 mt-1">{t('purchases.supplierRequiredForDebtHint')}</p>
                )}
              </div>
            )}
            <div>
              <label className="label">{t('common.note')}</label>
              <textarea className="input" rows={2} value={note} onChange={e => setNote(e.target.value)} />
            </div>
            <button
              onClick={handleSubmit}
              disabled={createMutation.isPending || items.length === 0 || (unpaid > 0 && !supplierId)}
              className="btn-primary w-full"
            >
              {createMutation.isPending ? t('common.saving') : `${t('purchases.createPurchase')} (${formatMoney(total)})`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
