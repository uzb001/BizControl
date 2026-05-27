'use client';

import { useState, useMemo, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { stockApi, categoriesApi, productsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, cn } from '@/lib/utils';
import { StockBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import WarehouseManager from '@/components/warehouse/WarehouseManager';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import { Package, History, RotateCcw, PackagePlus, Plus, Minus, Equal, Boxes, ArrowLeftRight } from 'lucide-react';

type AdjustType = 'IN' | 'OUT' | 'SET';
type TabKey = 'stock' | 'movements' | 'warehouses' | 'transfers';

const REASONS = [
  { v: 'restock', l: 'Restock / Purchase' },
  { v: 'inventory_count', l: 'Inventory count' },
  { v: 'damaged', l: 'Damaged / Written off' },
  { v: 'lost', l: 'Lost / Theft' },
  { v: 'return', l: 'Customer return' },
  { v: 'correction', l: 'Manual correction' },
  { v: 'other', l: 'Other' },
];

export default function StockPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();
  const canAdjust = can('stock.adjust');
  const canViewMovements = can('stock.view_movements');
  const canWarehouses = can('warehouses.view');
  const canTransfers = can('warehouse_stock.view_movements');

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [stockStatus, setStockStatus] = useState('');
  const [activeTab, setActiveTab] = useState<TabKey>('stock');

  // Allow deep-linking to a tab, e.g. /stock?tab=warehouses (used by the /warehouses redirect).
  useEffect(() => {
    const tab = new URLSearchParams(window.location.search).get('tab') as TabKey | null;
    if (tab && ['stock', 'movements', 'warehouses', 'transfers'].includes(tab)) setActiveTab(tab);
  }, []);

  // ── Adjust modal state ────────────────────────────────────────────────
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [adjProductId, setAdjProductId] = useState('');
  const [adjType, setAdjType] = useState<AdjustType>('IN');
  const [adjQty, setAdjQty] = useState('');
  const [adjReason, setAdjReason] = useState('restock');
  const [adjNote, setAdjNote] = useState('');

  const { data: stock, isLoading } = useQuery({
    queryKey: ['stock', page, search, categoryId, stockStatus],
    queryFn: () => stockApi.list({ page, size: 30, search, categoryId, stockStatus }).then(r => r.data),
    enabled: activeTab === 'stock',
  });

  const { data: movements, isLoading: movLoading } = useQuery({
    queryKey: ['movements', page],
    queryFn: () => stockApi.movements({ page, size: 50 }).then(r => r.data),
    enabled: activeTab === 'movements',
  });

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.list().then(r => r.data),
  });

  // All active products for the adjust selector + current-stock lookup
  const { data: allProductsData } = useQuery({
    queryKey: ['products-for-adjust'],
    queryFn: () => productsApi.list({ size: 500, status: 'active' }).then(r => r.data),
    enabled: canAdjust,
  });
  const allProducts: any[] = allProductsData?.content ?? [];
  const selectedProduct = allProducts.find(p => String(p.id) === adjProductId);

  const openAdjust = (productId?: number, type: AdjustType = 'IN') => {
    setAdjProductId(productId ? String(productId) : '');
    setAdjType(type);
    setAdjQty('');
    setAdjReason('restock');
    setAdjNote('');
    setAdjustOpen(true);
  };

  const adjustMutation = useMutation({
    mutationFn: (data: { productId: number; quantity: number; note: string }) => productsApi.adjustStock(data),
    onSuccess: (res: any) => {
      qc.invalidateQueries({ queryKey: ['stock'] });
      qc.invalidateQueries({ queryKey: ['movements'] });
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: ['products-for-adjust'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['alerts'] });
      if (res?.data?.approvalStatus === 'pending_approval') {
        toast(res.data.message || 'Large adjustment submitted for owner approval', { duration: 5000 });
      } else {
        toast.success('Stock updated');
      }
      setAdjustOpen(false);
    },
    onError: (e: any) => {
      const msg = e.response?.data?.error || e.response?.data?.message || '';
      if (e.response?.status === 403 || /permission|access denied/i.test(msg)) {
        toast.error('You do not have permission to adjust stock. Ask the company owner to enable "stock.adjust".');
      } else {
        toast.error(msg || 'Failed to adjust stock');
      }
    },
  });

  const submitAdjust = () => {
    if (!adjProductId) { toast.error('Please select a product'); return; }
    const qty = parseFloat(adjQty);
    if (isNaN(qty) || qty < 0) { toast.error('Enter a valid quantity'); return; }
    const cur = parseFloat(selectedProduct?.currentStock ?? '0');
    let delta: number;
    if (adjType === 'IN') delta = qty;
    else if (adjType === 'OUT') delta = -qty;
    else delta = qty - cur;                       // SET → exact target
    if (delta === 0) { toast.error('No change to apply'); return; }
    if (adjType === 'OUT' && qty > cur) { toast.error(`Cannot remove ${qty} — only ${cur} in stock`); return; }
    const reasonLabel = REASONS.find(r => r.v === adjReason)?.l ?? adjReason;
    const note = `[${reasonLabel}]${adjNote ? ' ' + adjNote : ''}`;
    adjustMutation.mutate({ productId: Number(adjProductId), quantity: delta, note });
  };

  const tabs = [
    { key: 'stock' as TabKey, label: t('stock.currentStock'), icon: Package, show: true },
    { key: 'movements' as TabKey, label: t('stock.movements'), icon: History, show: canViewMovements },
    { key: 'warehouses' as TabKey, label: t('nav.warehouses', { defaultValue: 'Warehouses' }), icon: Boxes, show: canWarehouses },
    { key: 'transfers' as TabKey, label: t('warehouses.transfers', { defaultValue: 'Transfers' }), icon: ArrowLeftRight, show: canTransfers },
  ].filter(tb => tb.show);

  const stockItems = useMemo(() => stock?.content ?? [], [stock]);
  const movementItems = useMemo(() => movements?.content ?? [], [movements]);

  const stockColumns: GridColumn<any>[] = [
    { key: 'name', header: t('stock.product'), width: 200, render: (p) => <span className="font-medium truncate">{p.name}</span> },
    { key: 'sku', header: t('stock.sku'), width: 110, render: (p) => <span className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.sku || '—'}</span> },
    { key: 'category', header: t('stock.category'), width: 140, groupable: true, groupValue: (p) => p.category?.name || '—', render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.category?.name || '—'}</span> },
    { key: 'unit', header: t('stock.unit'), width: 70, groupable: true, groupValue: (p) => p.unit, render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.unit}</span> },
    { key: 'currentStock', header: t('stock.currentStockCol'), width: 120, numeric: true, aggregate: 'sum', aggregateValue: (p) => parseFloat(p.currentStock || 0), aggregateRender: (v) => <span className="num font-bold">{v.toLocaleString()}</span>, render: (p) => <span className="font-semibold num">{p.currentStock} {p.unit}</span> },
    { key: 'minStockLevel', header: t('stock.minStock'), width: 100, numeric: true, render: (p) => <span className="num" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.minStockLevel} {p.unit}</span> },
    { key: 'stockValue', header: t('stock.stockValue'), width: 140, numeric: true, aggregate: 'sum', aggregateValue: (p) => parseFloat(p.currentStock || 0) * parseFloat(p.purchasePrice || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (p) => <span className="num" style={{ color: 'rgb(var(--color-text-secondary))' }}>{formatMoney(parseFloat(p.currentStock) * parseFloat(p.purchasePrice || 0))}</span> },
    { key: 'status', header: t('stock.status'), width: 110, render: (p) => <StockBadge current={p.currentStock} min={p.minStockLevel} /> },
    ...(canAdjust ? [{
      key: 'actions', header: t('common.actions'), width: 130,
      render: (p: any) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => openAdjust(p.id, 'IN')} className="btn-icon !w-7 !h-7 hover:!text-green-600" title="Add stock"><Plus size={14} /></button>
          <button onClick={() => openAdjust(p.id, 'OUT')} className="btn-icon !w-7 !h-7 hover:!text-red-600" title="Remove stock"><Minus size={14} /></button>
          <button onClick={() => openAdjust(p.id, 'SET')} className="btn-icon !w-7 !h-7" title="Set exact quantity"><Equal size={14} /></button>
        </div>
      ),
    }] : []),
  ];

  const movementColumns: GridColumn<any>[] = [
    { key: 'createdAt', header: t('stock.date'), width: 160, render: (m) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{new Date(m.createdAt).toLocaleString('en-GB')}</span> },
    { key: 'product', header: t('stock.product'), width: 180, render: (m) => <span className="font-medium text-sm truncate">{m.product?.name}</span> },
    { key: 'movementType', header: t('stock.type'), width: 110, groupable: true, groupValue: (m) => m.movementType, render: (m) => (
        <span className={m.movementType === 'sale' ? 'badge-red' : m.movementType === 'purchase' ? 'badge-green' : 'badge-yellow'}>{m.movementType}</span>
      ) },
    { key: 'quantity', header: t('common.qty'), width: 90, numeric: true, render: (m) => (
        <span className={`font-medium num ${parseFloat(m.quantity) < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>{parseFloat(m.quantity) > 0 ? '+' : ''}{m.quantity}</span>
      ) },
    { key: 'previousStock', header: t('stock.quantityBefore'), width: 110, numeric: true, render: (m) => <span className="num text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.previousStock}</span> },
    { key: 'newStock', header: t('stock.quantityAfter'), width: 110, numeric: true, render: (m) => <span className="num font-medium">{m.newStock}</span> },
    { key: 'createdBy', header: 'User', width: 80, render: (m) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.createdBy ? `#${m.createdBy}` : '—'}</span> },
    { key: 'note', header: t('stock.note'), width: 240, render: (m) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{m.note}</span> },
  ];

  const cur = parseFloat(selectedProduct?.currentStock ?? '0');
  const qtyNum = parseFloat(adjQty);
  const preview = !isNaN(qtyNum) && qtyNum >= 0
    ? (adjType === 'IN' ? cur + qtyNum : adjType === 'OUT' ? cur - qtyNum : qtyNum)
    : null;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">{t('stock.title')}</h1>
        <div className="flex gap-2">
          {activeTab === 'stock' && (
            <>
              <ExportButton permission="stock.export" filename="stock" fetcher={() => exportApi.stock({ stockStatus })} />
              {canAdjust && (
                <button onClick={() => openAdjust()} className="btn-primary btn-sm gap-1.5">
                  <PackagePlus size={15} /> {t('stock.adjustStock')}
                </button>
              )}
            </>
          )}
          {activeTab === 'movements' && (
            <ExportButton permission="stock.export" filename="stock_movements" fetcher={() => exportApi.stockMovements()} />
          )}
        </div>
      </div>

      <div className="segment mb-5">
        {tabs.map(tab => (
          <button key={tab.key} onClick={() => { setActiveTab(tab.key); setPage(0); }}
            className={cn('segment-item gap-1.5', activeTab === tab.key && 'active')}>
            <tab.icon size={14} /> {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'stock' && (
        <>
          <div className="filter-bar">
            <input className="filter-input" placeholder={t('stock.searchPlaceholder')} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
            <select className="filter-input" value={categoryId} onChange={e => { setCategoryId(e.target.value); setPage(0); }}>
              <option value="">{t('stock.allCategories')}</option>
              {categories?.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <select className="filter-input" value={stockStatus} onChange={e => { setStockStatus(e.target.value); setPage(0); }}>
              <option value="">{t('stock.allStock')}</option>
              <option value="in_stock">{t('stock.inStock')}</option>
              <option value="low_stock">{t('stock.lowStock')}</option>
              <option value="out_of_stock">{t('stock.outOfStock')}</option>
            </select>
            <button onClick={() => { setSearch(''); setCategoryId(''); setStockStatus(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
          </div>

          <DataGrid
            gridId="stock"
            columns={stockColumns}
            rows={stockItems}
            getRowId={(p: any) => p.id}
            loading={isLoading}
            groupable
            onRowOpen={canAdjust ? (p: any) => openAdjust(p.id, 'IN') : undefined}
            rowClassName={(p: any) => p.currentStock === 0 ? 'dgrid-row-danger' : (p.currentStock <= p.minStockLevel ? 'dgrid-row-warn' : '')}
            emptyState={<EmptyState icon={Package} title={t('stock.noProducts')}
              action={canAdjust ? <button onClick={() => openAdjust()} className="btn-primary btn-sm">{t('stock.adjustStock')}</button> : undefined} />}
          />

          {stockItems.length > 0 && (
            <Pagination page={page} totalPages={stock?.totalPages} totalElements={stock?.totalElements} size={30} onChange={setPage} />
          )}
        </>
      )}

      {activeTab === 'movements' && (
        <>
          <DataGrid
            gridId="stock-movements"
            columns={movementColumns}
            rows={movementItems}
            getRowId={(m: any) => m.id}
            loading={movLoading}
            groupable
            emptyState={<EmptyState icon={History} title={t('stock.movements')} />}
          />
          {movementItems.length > 0 && (
            <Pagination page={page} totalPages={movements?.totalPages} totalElements={movements?.totalElements} size={50} onChange={setPage} />
          )}
        </>
      )}

      {activeTab === 'warehouses' && <WarehouseManager mode="warehouses" />}
      {activeTab === 'transfers' && <WarehouseManager mode="transfers" />}

      {/* ── Adjust Stock modal ── */}
      <Modal open={adjustOpen} onClose={() => setAdjustOpen(false)} title={t('stock.adjustStock')} size="sm">
        <div className="space-y-3.5">
          <div>
            <label className="label">Product *</label>
            <select className="input" value={adjProductId} onChange={e => setAdjProductId(e.target.value)}>
              <option value="">Select a product…</option>
              {allProducts.map(p => (
                <option key={p.id} value={p.id}>{p.name} ({p.currentStock} {p.unit})</option>
              ))}
            </select>
          </div>

          {selectedProduct && (
            <div className="rounded-lg px-3 py-2 text-sm flex items-center justify-between" style={{ backgroundColor: 'rgb(var(--color-surface-2))' }}>
              <span style={{ color: 'rgb(var(--color-text-muted))' }}>Current stock</span>
              <span className="font-semibold num">{selectedProduct.currentStock} {selectedProduct.unit}</span>
            </div>
          )}

          <div>
            <label className="label">Adjustment type</label>
            <div className="segment w-full">
              {([['IN', 'Add (IN)'], ['OUT', 'Remove (OUT)'], ['SET', 'Set exact']] as [AdjustType, string][]).map(([v, l]) => (
                <button key={v} type="button" onClick={() => setAdjType(v)}
                  className={cn('segment-item flex-1', adjType === v && 'active')}>{l}</button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">{adjType === 'SET' ? 'New quantity' : 'Quantity'} *</label>
              <input type="number" min="0" step="0.001" className="input num" value={adjQty} onChange={e => setAdjQty(e.target.value)} placeholder="0" />
            </div>
            <div>
              <label className="label">Unit</label>
              <input className="input" value={selectedProduct?.unit ?? ''} readOnly placeholder="—" />
            </div>
          </div>

          {preview !== null && selectedProduct && (
            <div className="text-xs" style={{ color: preview < 0 ? 'rgb(220 38 38)' : 'rgb(var(--color-text-muted))' }}>
              Result: <span className="num font-semibold">{selectedProduct.currentStock} → {preview} {selectedProduct.unit}</span>
              {preview < 0 && ' (negative not allowed)'}
            </div>
          )}

          <div>
            <label className="label">Reason</label>
            <select className="input" value={adjReason} onChange={e => setAdjReason(e.target.value)}>
              {REASONS.map(r => <option key={r.v} value={r.v}>{r.l}</option>)}
            </select>
          </div>

          <div>
            <label className="label">Note</label>
            <input className="input" value={adjNote} onChange={e => setAdjNote(e.target.value)} placeholder="Optional details" />
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={() => setAdjustOpen(false)} className="btn-secondary">{t('common.cancel')}</button>
            <button type="button" onClick={submitAdjust} disabled={adjustMutation.isPending} className="btn-primary gap-1.5">
              <PackagePlus size={14} /> {adjustMutation.isPending ? t('common.saving') : t('stock.adjustStock')}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
