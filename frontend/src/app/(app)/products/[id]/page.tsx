'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { productsApi, salesApi, purchasesApi, stockApi, warehousesApi } from '@/lib/api';
import { formatMoney, formatDate, formatDateTime, cn } from '@/lib/utils';
import { StockBadge } from '@/components/ui/Badge';
import Modal from '@/components/ui/Modal';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection } from '@/components/ui/DetailShell';
import { usePermission } from '@/hooks/usePermission';
import { useForm } from 'react-hook-form';
import { Package, Boxes, Tag, ShoppingCart, PackageSearch, History, Sliders } from 'lucide-react';

type Tab = 'overview' | 'stock' | 'sales' | 'purchases';

export default function ProductProfilePage() {
  const { id } = useParams();
  const router = useRouter();
  const qc = useQueryClient();
  const { can } = usePermission();
  const [tab, setTab] = useState<Tab>('overview');
  const [showAdjustModal, setShowAdjustModal] = useState(false);
  const { register, handleSubmit, reset } = useForm();

  const { data: product, isLoading } = useQuery({
    queryKey: ['product', id],
    queryFn: () => productsApi.get(Number(id)).then(r => r.data),
  });

  const canWarehouse = can('warehouse_stock.view');
  const { data: whBreakdown = [] } = useQuery({
    queryKey: ['product-wh-breakdown', id],
    queryFn: () => warehousesApi.productBreakdown(Number(id)).then(r => r.data),
    enabled: tab === 'overview' && canWarehouse,
    retry: false,
  });
  const { data: warehousesList = [] } = useQuery({
    queryKey: ['warehouses-simple'],
    queryFn: () => warehousesApi.list().then(r => r.data),
    enabled: tab === 'overview' && canWarehouse,
    retry: false,
  });
  const whName = (wid: number) => warehousesList.find((w: any) => w.id === wid)?.name ?? `#${wid}`;

  const { data: movements } = useQuery({
    queryKey: ['product-movements', id],
    queryFn: () => stockApi.movements({ productId: id, size: 50 }).then(r => r.data),
    enabled: tab === 'stock',
  });

  const { data: sales } = useQuery({
    queryKey: ['product-sales', id],
    queryFn: () => salesApi.list({ productId: id, size: 50 }).then(r => r.data),
    enabled: tab === 'sales',
  });

  const { data: purchases } = useQuery({
    queryKey: ['product-purchases', id],
    queryFn: () => purchasesApi.list({ productId: id, size: 50 }).then(r => r.data),
    enabled: tab === 'purchases',
  });

  const adjustMutation = useMutation({
    mutationFn: (data: any) => productsApi.adjustStock({ productId: Number(id), ...data }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', id] });
      qc.invalidateQueries({ queryKey: ['product-movements', id] });
      toast.success('Stock adjusted');
      setShowAdjustModal(false); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  if (isLoading) return <PageLoading />;
  if (!product) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>Product not found.</div>;

  const masked = product.purchasePrice == null;
  const margin = product.sellingPrice && product.purchasePrice
    ? (((product.sellingPrice - product.purchasePrice) / product.sellingPrice) * 100).toFixed(1)
    : null;

  const tabs: { key: Tab; label: string; icon: typeof Package }[] = [
    { key: 'overview', label: 'Overview', icon: Package },
    { key: 'stock', label: 'Stock Movements', icon: History },
    { key: 'sales', label: 'Sales History', icon: ShoppingCart },
    { key: 'purchases', label: 'Purchase History', icon: PackageSearch },
  ];

  const movementTypeColor: Record<string, string> = {
    purchase: 'badge-green', sale: 'badge-red', adjustment: 'badge-blue', return: 'badge-yellow',
  };

  const stockAccent = product.currentStock === 0 ? 'red' : product.currentStock <= (product.minStockLevel || 0) ? 'yellow' : 'default';

  return (
    <div className="max-w-6xl mx-auto">
      <DetailHeader
        backHref="/products"
        title={product.name}
        subtitle={<>{product.sku && <span>SKU: {product.sku} · </span>}{product.category?.name || '—'}</>}
        badge={<StockBadge currentStock={product.currentStock} minStock={product.minStockLevel} />}
        actions={<button onClick={() => setShowAdjustModal(true)} className="btn-secondary btn-sm gap-1.5"><Sliders size={14} /> Adjust Stock</button>}
      />

      {/* KPI row */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
        <DetailStat label="Current Stock" value={`${product.currentStock} ${product.unit}`} icon={Boxes} accent={stockAccent as any} />
        <DetailStat label="Selling Price" value={formatMoney(product.sellingPrice)} icon={Tag} />
        <DetailStat label="Purchase Price" value={masked ? '•••' : formatMoney(product.purchasePrice)} icon={Tag} />
        <DetailStat label="Margin" value={margin ? `${margin}%` : '—'} accent={margin && Number(margin) >= 20 ? 'green' : 'yellow'} />
        <DetailStat label="Stock Value" value={masked ? '•••' : formatMoney((product.currentStock || 0) * (product.purchasePrice || 0))} />
      </div>

      {/* Tabs */}
      <div className="segment mb-5">
        {tabs.map(tt => (
          <button key={tt.key} onClick={() => setTab(tt.key)} className={cn('segment-item gap-1.5', tab === tt.key && 'active')}>
            <tt.icon size={14} /> {tt.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="grid lg:grid-cols-2 gap-6">
          <DetailSection title="Product Details">
            <dl className="space-y-2.5 text-sm">
              {[
                ['Barcode', product.barcode],
                ['Unit', product.unit],
                ['Category', product.category?.name],
                ['Supplier', product.supplier?.name],
                ['Min Stock Level', product.minStockLevel],
                ['Status', product.status],
                ['Created', formatDate(product.createdAt)],
              ].map(([label, val]) => (
                <div key={label as string} className="flex justify-between gap-3">
                  <dt style={{ color: 'rgb(var(--color-text-muted))' }}>{label as string}</dt>
                  <dd className="font-medium" style={{ color: 'rgb(var(--color-text-primary))' }}>{(val as string) || '—'}</dd>
                </div>
              ))}
            </dl>
          </DetailSection>
          {canWarehouse && (
            <DetailSection title="Stock by Warehouse">
              {whBreakdown.length === 0 ? (
                <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>No warehouse stock recorded.</p>
              ) : (
                <dl className="space-y-2.5 text-sm">
                  {whBreakdown.map((r: any) => (
                    <div key={r.id ?? r.warehouseId} className="flex justify-between gap-3">
                      <dt style={{ color: 'rgb(var(--color-text-muted))' }}>{whName(r.warehouseId)}</dt>
                      <dd className="font-medium num" style={{ color: 'rgb(var(--color-text-primary))' }}>
                        {r.quantity} {product.unit}
                        {Number(r.reservedQuantity) > 0 && (
                          <span className="ml-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>({r.reservedQuantity} reserved)</span>
                        )}
                      </dd>
                    </div>
                  ))}
                  <div className="flex justify-between gap-3 pt-2 border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
                    <dt className="font-semibold">Total</dt>
                    <dd className="font-bold num">{product.currentStock} {product.unit}</dd>
                  </div>
                </dl>
              )}
            </DetailSection>
          )}
          {product.description && (
            <DetailSection title="Description">
              <p className="text-sm whitespace-pre-line" style={{ color: 'rgb(var(--color-text-secondary))' }}>{product.description}</p>
            </DetailSection>
          )}
        </div>
      )}

      {tab === 'stock' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>Date</th><th>Type</th><th>Change</th><th>Before</th><th>After</th><th>Reference</th><th>Notes</th></tr>
              </thead>
              <tbody>
                {movements?.content?.map((m: any) => (
                  <tr key={m.id}>
                    <td className="text-sm">{formatDateTime(m.createdAt)}</td>
                    <td><span className={`text-xs ${movementTypeColor[m.movementType] || 'badge-blue'}`}>{m.movementType}</span></td>
                    <td className={`num font-medium ${m.quantity >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>{m.quantity >= 0 ? '+' : ''}{m.quantity}</td>
                    <td className="num text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.previousStock}</td>
                    <td className="num font-medium">{m.newStock}</td>
                    <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.referenceType ? `${m.referenceType} #${m.referenceId}` : '—'}</td>
                    <td className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.notes || m.note || '—'}</td>
                  </tr>
                ))}
                {!movements?.content?.length && (
                  <tr><td colSpan={7} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No movements recorded</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {tab === 'sales' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>Sale #</th><th>Date</th><th>Customer</th><th>Qty</th><th>Unit Price</th><th>Total</th><th>Profit</th></tr>
              </thead>
              <tbody>
                {sales?.content?.flatMap((s: any) =>
                  (s.items || []).filter((item: any) => item.product?.id === Number(id) || item.productId === Number(id)).map((item: any) => (
                    <tr key={`${s.id}-${item.id}`}>
                      <td><button onClick={() => router.push(`/sales/${s.id}`)} className="text-sm hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>#{s.id}</button></td>
                      <td className="text-sm">{formatDate(s.saleDate)}</td>
                      <td className="text-sm">{s.customer?.name || 'Walk-in'}</td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatMoney(item.unitPrice)}</td>
                      <td className="num font-medium">{formatMoney(item.lineTotal)}</td>
                      <td className="num text-green-600 dark:text-green-400 text-sm">{item.profitAmount != null ? formatMoney(item.profitAmount) : '•••'}</td>
                    </tr>
                  ))
                )}
                {!sales?.content?.length && (
                  <tr><td colSpan={7} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No sales found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {tab === 'purchases' && (
        <DetailSection noPadding>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr><th>Purchase #</th><th>Date</th><th>Supplier</th><th>Qty</th><th>Unit Cost</th><th>Total</th></tr>
              </thead>
              <tbody>
                {purchases?.content?.flatMap((p: any) =>
                  (p.items || []).filter((item: any) => item.product?.id === Number(id) || item.productId === Number(id)).map((item: any) => (
                    <tr key={`${p.id}-${item.id}`}>
                      <td><button onClick={() => router.push(`/purchases/${p.id}`)} className="text-sm hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>#{p.id}</button></td>
                      <td className="text-sm">{formatDate(p.purchaseDate)}</td>
                      <td className="text-sm">{p.supplier?.name || '—'}</td>
                      <td className="num">{item.quantity}</td>
                      <td className="num">{formatMoney(item.unitCost)}</td>
                      <td className="num font-medium">{formatMoney(item.lineTotal)}</td>
                    </tr>
                  ))
                )}
                {!purchases?.content?.length && (
                  <tr><td colSpan={6} className="text-center py-8" style={{ color: 'rgb(var(--color-text-muted))' }}>No purchases found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </DetailSection>
      )}

      {/* Adjust stock modal */}
      <Modal open={showAdjustModal} onClose={() => { setShowAdjustModal(false); reset(); }} title="Adjust Stock" size="sm">
        <form onSubmit={handleSubmit(data => adjustMutation.mutate(data))} className="space-y-3">
          <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>Current stock: <strong style={{ color: 'rgb(var(--color-text-primary))' }}>{product.currentStock} {product.unit}</strong></p>
          <div>
            <label className="label">Quantity Change *</label>
            <input type="number" {...register('quantity', { required: true })} className="input" placeholder="e.g. 10 to add, -5 to remove" />
          </div>
          <div>
            <label className="label">Reason</label>
            <select {...register('reason')} className="input">
              <option value="manual_adjustment">Manual Adjustment</option>
              <option value="damaged">Damaged / Written Off</option>
              <option value="return_from_customer">Return from Customer</option>
              <option value="return_to_supplier">Return to Supplier</option>
              <option value="inventory_count">Inventory Count</option>
            </select>
          </div>
          <div>
            <label className="label">Notes</label>
            <input {...register('note')} className="input" placeholder="Optional notes" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setShowAdjustModal(false); reset(); }} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={adjustMutation.isPending} className="btn-primary">{adjustMutation.isPending ? 'Saving...' : 'Adjust Stock'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
