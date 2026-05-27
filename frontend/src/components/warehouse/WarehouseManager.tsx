'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { warehousesApi, productsApi, exportApi, countriesApi } from '@/lib/api';
import { formatMoney, formatDateTime, cn } from '@/lib/utils';
import Modal from '@/components/ui/Modal';
import EmptyState from '@/components/ui/EmptyState';
import ExportButton from '@/components/ui/ExportButton';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import {
  Warehouse as WarehouseIcon, Plus, Pencil, Archive, ArrowLeftRight,
  PackagePlus, PackageMinus, ClipboardCheck, Boxes, AlertTriangle, User, MapPin,
  RotateCcw, Trash2,
} from 'lucide-react';

const TYPES = ['main', 'retail', 'transit', 'damaged', 'customs', 'temporary'];
const TYPE_COLORS: Record<string, string> = {
  main: 'badge-blue', retail: 'badge-green', transit: 'badge-yellow',
  damaged: 'badge-red', customs: 'badge-gray', temporary: 'badge-gray',
};

/**
 * Warehouse management surface, embedded as tabs inside the Stock page.
 * mode="warehouses" → cards + per-warehouse inventory + adjust/transfer.
 * mode="transfers"  → transfer history.
 */
export default function WarehouseManager({ mode = 'warehouses' }: { mode?: 'warehouses' | 'transfers' }) {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();

  const [selected, setSelected] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [form, setForm] = useState<any>({ name: '', code: '', type: 'main', location: '', countryId: '', responsiblePerson: '', phone: '', note: '' });
  const [adjust, setAdjust] = useState<{ mode: 'in' | 'out' | 'set' } | null>(null);
  const [adjustForm, setAdjustForm] = useState<any>({ productId: '', warehouseId: '', quantity: '', note: '' });
  const [showTransfer, setShowTransfer] = useState(false);
  const [transferForm, setTransferForm] = useState<any>({ productId: '', fromWarehouseId: '', toWarehouseId: '', quantity: '', note: '' });

  const canAdjust = can('warehouse_stock.adjust');
  const canTransfer = can('warehouse_stock.transfer');

  const { data: summary = [], isLoading } = useQuery({
    queryKey: ['wh-summary'], queryFn: () => warehousesApi.summary().then(r => r.data), retry: false,
  });
  const { data: warehouses = [] } = useQuery({
    queryKey: ['wh-list'], queryFn: () => warehousesApi.list().then(r => r.data), retry: false,
  });
  const { data: stockRows = [], isLoading: stockLoading } = useQuery({
    queryKey: ['wh-stock', selected], queryFn: () => warehousesApi.stock(selected!).then(r => r.data),
    enabled: selected != null && mode === 'warehouses', retry: false,
  });
  const { data: products = [] } = useQuery({
    queryKey: ['wh-products'], queryFn: () => productsApi.list({ size: 500 }).then(r => r.data.content ?? r.data ?? []),
    enabled: (adjust != null || showTransfer), retry: false,
  });
  const { data: transfers } = useQuery({
    queryKey: ['wh-transfers'], queryFn: () => warehousesApi.transfers({ size: 100 }).then(r => r.data),
    enabled: mode === 'transfers', retry: false,
  });
  // Active countries (for the warehouse country selector in the form modal).
  // Loaded lazily — only when the form is open and the user has permission.
  const { data: countries = [] } = useQuery({
    queryKey: ['countries', 'active', 'for-warehouse-form'],
    queryFn: () => countriesApi.list({ status: 'active' }).then(r => r.data),
    enabled: showForm && can('countries.view'),
    retry: false,
  });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['wh-summary'] });
    qc.invalidateQueries({ queryKey: ['wh-list'] });
    qc.invalidateQueries({ queryKey: ['wh-stock'] });
    qc.invalidateQueries({ queryKey: ['wh-transfers'] });
    qc.invalidateQueries({ queryKey: ['stock'] });
    qc.invalidateQueries({ queryKey: ['products'] });
  };
  const onError = (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' }));

  const saveMut = useMutation({
    mutationFn: (data: any) => editing ? warehousesApi.update(editing.id, data) : warehousesApi.create(data),
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); toast.success(t('common.saved', { defaultValue: 'Saved' })); }, onError,
  });
  const archiveMut = useMutation({
    mutationFn: (id: number) => warehousesApi.archive(id),
    onSuccess: () => { refresh(); toast.success(t('warehouses.archived', { defaultValue: 'Warehouse archived' })); }, onError,
  });
  const adjustMut = useMutation({
    mutationFn: ({ mode: m, data }: any) => m === 'in' ? warehousesApi.stockIn(data) : m === 'out' ? warehousesApi.stockOut(data) : warehousesApi.stockSet(data),
    onSuccess: (res: any) => {
      refresh(); setAdjust(null);
      if (res?.status === 202) toast(t('warehouses.adjustPending', { defaultValue: 'Large adjustment sent for approval' }), { icon: '⏳' });
      else toast.success(t('warehouses.stockUpdated', { defaultValue: 'Stock updated' }));
    }, onError,
  });
  const transferMut = useMutation({
    mutationFn: (data: any) => warehousesApi.transfer(data),
    onSuccess: () => { refresh(); setShowTransfer(false); toast.success(t('warehouses.transferred', { defaultValue: 'Stock transferred' })); }, onError,
  });
  const restoreMut = useMutation({
    mutationFn: (id: number) => warehousesApi.restore(id),
    onSuccess: () => { refresh(); toast.success(t('warehouses.restored', { defaultValue: 'Warehouse restored' })); }, onError,
  });
  const deleteMut = useMutation({
    mutationFn: (id: number) => warehousesApi.remove(id),
    onSuccess: () => { refresh(); if (selected != null) setSelected(null); toast.success(t('warehouses.deleted', { defaultValue: 'Warehouse deleted' })); }, onError,
  });

  const openCreate = () => { setEditing(null); setForm({ name: '', code: '', type: 'main', location: '', countryId: '', responsiblePerson: '', phone: '', note: '' }); setShowForm(true); };
  const openEdit = (w: any) => { setEditing(w); setForm({ name: w.name ?? '', code: w.code ?? '', type: w.type ?? 'main', location: w.location ?? '', countryId: w.countryId ?? '', responsiblePerson: w.responsiblePerson ?? '', phone: w.phone ?? '', note: w.note ?? '' }); setShowForm(true); };
  const openAdjust = (m: 'in' | 'out' | 'set') => { setAdjustForm({ productId: '', warehouseId: selected ?? (summary[0]?.id ?? ''), quantity: '', note: '' }); setAdjust({ mode: m }); };
  const openTransfer = () => { setTransferForm({ productId: '', fromWarehouseId: selected ?? (summary[0]?.id ?? ''), toWarehouseId: '', quantity: '', note: '' }); setShowTransfer(true); };

  const selectedName = useMemo(() => summary.find((w: any) => w.id === selected)?.name, [summary, selected]);
  const activeWarehouses = warehouses.filter((w: any) => w.status !== 'archived');

  // ── Transfers mode ──────────────────────────────────────────────────────────
  if (mode === 'transfers') {
    return (
      <div className="card overflow-hidden">
        <div className="px-4 py-2.5 border-b flex justify-end gap-2" style={{ borderColor: 'rgb(var(--color-border))' }}>
          <ExportButton permission="warehouse_stock.export" filename="stock_transfers" fetcher={() => exportApi.stockTransfers()} />
          {canTransfer && (
            <button onClick={openTransfer} className="btn-primary btn-sm gap-1.5"><ArrowLeftRight size={14} /> {t('warehouses.transfer', { defaultValue: 'Transfer' })}</button>
          )}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr style={{ color: 'rgb(var(--color-text-muted))' }} className="text-left text-xs uppercase tracking-wide">
                <th className="px-4 py-2 font-medium">{t('common.date', { defaultValue: 'Date' })}</th>
                <th className="px-4 py-2 font-medium">{t('warehouses.from', { defaultValue: 'From' })}</th>
                <th className="px-4 py-2 font-medium">{t('warehouses.to', { defaultValue: 'To' })}</th>
                <th className="px-4 py-2 font-medium text-right">{t('warehouses.qty', { defaultValue: 'Qty' })}</th>
                <th className="px-4 py-2 font-medium">{t('common.status', { defaultValue: 'Status' })}</th>
                <th className="px-4 py-2 font-medium">{t('common.note', { defaultValue: 'Note' })}</th>
              </tr>
            </thead>
            <tbody>
              {(transfers?.content ?? []).map((tr: any) => {
                const fromW = warehouses.find((w: any) => w.id === tr.fromWarehouseId)?.name ?? `#${tr.fromWarehouseId}`;
                const toW = warehouses.find((w: any) => w.id === tr.toWarehouseId)?.name ?? `#${tr.toWarehouseId}`;
                return (
                  <tr key={tr.id} className="border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
                    <td className="px-4 py-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(tr.createdAt)}</td>
                    <td className="px-4 py-2">{fromW}</td>
                    <td className="px-4 py-2">{toW}</td>
                    <td className="px-4 py-2 text-right num font-semibold">{tr.quantity}</td>
                    <td className="px-4 py-2"><span className="badge-green text-[10px]">{tr.status}</span></td>
                    <td className="px-4 py-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{tr.note}</td>
                  </tr>
                );
              })}
              {(transfers?.content ?? []).length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('warehouses.noTransfers', { defaultValue: 'No transfers yet.' })}</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {renderTransferModal()}
      </div>
    );
  }

  // ── Warehouses mode ─────────────────────────────────────────────────────────
  return (
    <div>
      {/* Toolbar */}
      <div className="flex flex-wrap gap-2 justify-end mb-4">
        <ExportButton permission="warehouse_stock.export" filename="warehouse_stock"
          fetcher={() => exportApi.warehouseStock(selected != null ? { warehouseId: selected } : undefined)} />
        {canTransfer && <button onClick={openTransfer} className="btn-secondary btn-sm gap-1.5"><ArrowLeftRight size={14} /> {t('warehouses.transfer', { defaultValue: 'Transfer' })}</button>}
        {canAdjust && <>
          <button onClick={() => openAdjust('in')} className="btn-secondary btn-sm gap-1.5"><PackagePlus size={14} /> {t('warehouses.in', { defaultValue: 'Stock In' })}</button>
          <button onClick={() => openAdjust('out')} className="btn-secondary btn-sm gap-1.5"><PackageMinus size={14} /> {t('warehouses.out', { defaultValue: 'Stock Out' })}</button>
          <button onClick={() => openAdjust('set')} className="btn-secondary btn-sm gap-1.5"><ClipboardCheck size={14} /> {t('warehouses.set', { defaultValue: 'Set Qty' })}</button>
        </>}
        {can('warehouses.create') && <button onClick={openCreate} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('warehouses.add', { defaultValue: 'Add Warehouse' })}</button>}
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
          {Array.from({ length: 3 }).map((_, i) => <div key={i} className="card p-5"><div className="skeleton h-5 w-32 rounded mb-3" /><div className="skeleton h-4 w-full rounded" /></div>)}
        </div>
      ) : summary.length === 0 ? (
        <EmptyState icon={WarehouseIcon}
          title={t('warehouses.emptyTitle', { defaultValue: 'No warehouse created yet' })}
          description={t('warehouses.emptyBody', { defaultValue: 'Create your first warehouse to track stock by location.' })}
          action={can('warehouses.create') ? <button onClick={openCreate} className="btn-primary btn-sm">{t('warehouses.add', { defaultValue: 'Add Warehouse' })}</button> : undefined} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
          <button onClick={() => setSelected(null)} className={cn('card p-4 text-left transition-all', selected == null && 'ring-2 ring-blue-500')}>
            <div className="flex items-center gap-2 mb-2">
              <div className="w-9 h-9 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center"><Boxes size={18} className="text-blue-600 dark:text-blue-400" /></div>
              <div className="font-semibold">{t('warehouses.allWarehouses', { defaultValue: 'All Warehouses' })}</div>
            </div>
            <div className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{summary.length} {t('warehouses.locations', { defaultValue: 'locations' })}</div>
          </button>
          {summary.map((w: any) => (
            <div key={w.id} className={cn('card p-4 cursor-pointer transition-all', selected === w.id && 'ring-2 ring-blue-500', w.status === 'archived' && 'opacity-60')} onClick={() => setSelected(w.id)}>
              <div className="flex items-start justify-between gap-2 mb-2">
                <div className="flex items-center gap-2 min-w-0">
                  <div className="w-9 h-9 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center shrink-0"><WarehouseIcon size={18} style={{ color: 'rgb(var(--color-text-secondary))' }} /></div>
                  <div className="min-w-0">
                    <div className="font-semibold truncate">{w.name}</div>
                    <span className={cn('text-[10px]', TYPE_COLORS[w.type] || 'badge-gray')}>{w.type}{w.status === 'archived' ? ' · archived' : ''}</span>
                  </div>
                </div>
                <div className="flex gap-1 shrink-0" onClick={e => e.stopPropagation()}>
                  {can('warehouses.edit') && w.status !== 'archived' && <button onClick={() => openEdit(w)} className="btn-icon !w-7 !h-7" title={t('common.edit', { defaultValue: 'Edit' })}><Pencil size={13} /></button>}
                  {can('warehouses.archive') && w.status !== 'archived' && <button onClick={() => { if (confirm(t('warehouses.confirmArchive', { defaultValue: 'Archive this warehouse?' }))) archiveMut.mutate(w.id); }} className="btn-icon !w-7 !h-7" title={t('warehouses.archiveAction', { defaultValue: 'Archive' })}><Archive size={13} /></button>}
                  {can('warehouses.archive') && w.status === 'archived' && <button onClick={() => restoreMut.mutate(w.id)} className="btn-icon !w-7 !h-7 hover:!text-green-600" title={t('warehouses.restore', { defaultValue: 'Restore' })}><RotateCcw size={13} /></button>}
                  {can('warehouses.archive') && w.status === 'archived' && <button onClick={() => { if (confirm(t('warehouses.confirmDelete', { defaultValue: 'Permanently delete this warehouse? This cannot be undone.' }))) deleteMut.mutate(w.id); }} className="btn-icon !w-7 !h-7 hover:!text-red-600" title={t('common.delete', { defaultValue: 'Delete' })}><Trash2 size={13} /></button>}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs mt-3">
                <div><span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('warehouses.products', { defaultValue: 'Products' })}: </span><span className="font-semibold num">{w.productCount}</span></div>
                <div><span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('warehouses.qty', { defaultValue: 'Qty' })}: </span><span className="font-semibold num">{w.totalQuantity}</span></div>
                {w.stockValue !== undefined && <div className="col-span-2"><span style={{ color: 'rgb(var(--color-text-muted))' }}>{t('warehouses.value', { defaultValue: 'Value' })}: </span><span className="font-semibold num">{formatMoney(w.stockValue)}</span></div>}
                {w.lowStockCount > 0 && <div className="col-span-2 flex items-center gap-1 text-yellow-600 dark:text-yellow-400"><AlertTriangle size={12} /> {w.lowStockCount} {t('warehouses.lowStock', { defaultValue: 'low stock' })}</div>}
              </div>
              {(w.responsiblePerson || w.location) && (
                <div className="mt-3 pt-2 border-t flex flex-col gap-1 text-xs" style={{ borderColor: 'rgb(var(--color-border))', color: 'rgb(var(--color-text-muted))' }}>
                  {w.responsiblePerson && <span className="flex items-center gap-1.5"><User size={11} /> {w.responsiblePerson}</span>}
                  {w.location && <span className="flex items-center gap-1.5"><MapPin size={11} /> {w.location}</span>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Inventory for selected warehouse */}
      {selected != null && (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 border-b font-semibold text-sm" style={{ borderColor: 'rgb(var(--color-border))' }}>
            {selectedName} — {t('warehouses.inventory', { defaultValue: 'Inventory' })}
          </div>
          {stockLoading ? (
            <div className="p-6 text-center text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.loading', { defaultValue: 'Loading…' })}</div>
          ) : stockRows.length === 0 ? (
            <div className="p-8 text-center text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('warehouses.noStock', { defaultValue: 'No stock in this warehouse yet.' })}</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr style={{ color: 'rgb(var(--color-text-muted))' }} className="text-left text-xs uppercase tracking-wide">
                    <th className="px-4 py-2 font-medium">{t('common.product', { defaultValue: 'Product' })}</th>
                    <th className="px-4 py-2 font-medium">SKU</th>
                    <th className="px-4 py-2 font-medium text-right">{t('warehouses.qty', { defaultValue: 'Qty' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('warehouses.available', { defaultValue: 'Available' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('warehouses.value', { defaultValue: 'Value' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {stockRows.map((r: any) => (
                    <tr key={r.productId} className="border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
                      <td className="px-4 py-2 font-medium">{r.productName}</td>
                      <td className="px-4 py-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{r.sku || '—'}</td>
                      <td className="px-4 py-2 text-right num">{r.quantity} {r.unit}</td>
                      <td className="px-4 py-2 text-right num font-semibold">{r.availableQuantity}</td>
                      <td className="px-4 py-2 text-right num">{r.stockValue !== undefined ? formatMoney(r.stockValue) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Create / Edit warehouse modal */}
      <Modal open={showForm} onClose={() => { setShowForm(false); setEditing(null); }} title={editing ? t('warehouses.edit', { defaultValue: 'Edit Warehouse' }) : t('warehouses.add', { defaultValue: 'Add Warehouse' })} size="sm">
        <div className="space-y-3">
          <div><label className="label">{t('common.name', { defaultValue: 'Name' })} *</label><input className="input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">{t('warehouses.code', { defaultValue: 'Code' })}</label><input className="input" value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} disabled={!!editing} /></div>
            <div><label className="label">{t('common.type', { defaultValue: 'Type' })}</label>
              <select className="input" value={form.type} onChange={e => setForm({ ...form, type: e.target.value })}>{TYPES.map(tp => <option key={tp} value={tp}>{tp}</option>)}</select>
            </div>
          </div>
          <div><label className="label">{t('warehouses.location', { defaultValue: 'Location / Address' })}</label><input className="input" value={form.location} onChange={e => setForm({ ...form, location: e.target.value })} /></div>
          {can('countries.view') && (
            <div><label className="label">{t('warehouses.country', { defaultValue: 'Country' })}</label>
              <select className="input" value={form.countryId} onChange={e => setForm({ ...form, countryId: e.target.value })}>
                <option value="">{t('common.none', { defaultValue: '— None —' })}</option>
                {countries.map((c: any) => <option key={c.id} value={c.id}>{c.name}{c.code ? ` (${c.code})` : ''}</option>)}
              </select>
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">{t('warehouses.responsible', { defaultValue: 'Responsible' })}</label><input className="input" value={form.responsiblePerson} onChange={e => setForm({ ...form, responsiblePerson: e.target.value })} /></div>
            <div><label className="label">{t('common.phone', { defaultValue: 'Phone' })}</label><input className="input" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></div>
          </div>
          <div><label className="label">{t('common.note', { defaultValue: 'Note' })}</label><input className="input" value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} /></div>
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => { setShowForm(false); setEditing(null); }} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
            <button onClick={() => saveMut.mutate(form)} disabled={saveMut.isPending || !form.name.trim()} className="btn-primary">{saveMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('common.save', { defaultValue: 'Save' })}</button>
          </div>
        </div>
      </Modal>

      {/* Adjust modal */}
      <Modal open={adjust != null} onClose={() => setAdjust(null)}
        title={adjust?.mode === 'in' ? t('warehouses.in', { defaultValue: 'Stock In' }) : adjust?.mode === 'out' ? t('warehouses.out', { defaultValue: 'Stock Out' }) : t('warehouses.set', { defaultValue: 'Set Quantity' })} size="sm">
        <div className="space-y-3">
          <div><label className="label">{t('warehouses.warehouse', { defaultValue: 'Warehouse' })} *</label>
            <select className="input" value={adjustForm.warehouseId} onChange={e => setAdjustForm({ ...adjustForm, warehouseId: e.target.value })}>
              <option value="">—</option>{activeWarehouses.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
          <div><label className="label">{t('common.product', { defaultValue: 'Product' })} *</label>
            <select className="input" value={adjustForm.productId} onChange={e => setAdjustForm({ ...adjustForm, productId: e.target.value })}>
              <option value="">—</option>{products.map((p: any) => <option key={p.id} value={p.id}>{p.name}{p.sku ? ` (${p.sku})` : ''}</option>)}
            </select>
          </div>
          <div><label className="label">{adjust?.mode === 'set' ? t('warehouses.targetQty', { defaultValue: 'Target quantity' }) : t('common.quantity', { defaultValue: 'Quantity' })} *</label>
            <input type="number" min="0" step="0.01" className="input" value={adjustForm.quantity} onChange={e => setAdjustForm({ ...adjustForm, quantity: e.target.value })} />
          </div>
          <div><label className="label">{t('common.note', { defaultValue: 'Note' })}</label><input className="input" value={adjustForm.note} onChange={e => setAdjustForm({ ...adjustForm, note: e.target.value })} /></div>
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => setAdjust(null)} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
            <button onClick={() => adjustMut.mutate({ mode: adjust!.mode, data: { productId: Number(adjustForm.productId), warehouseId: Number(adjustForm.warehouseId), quantity: parseFloat(adjustForm.quantity), note: adjustForm.note } })}
              disabled={adjustMut.isPending || !adjustForm.productId || !adjustForm.warehouseId || !adjustForm.quantity} className="btn-primary">
              {adjustMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('common.confirm', { defaultValue: 'Confirm' })}
            </button>
          </div>
        </div>
      </Modal>

      {renderTransferModal()}
    </div>
  );

  function renderTransferModal() {
    return (
      <Modal open={showTransfer} onClose={() => setShowTransfer(false)} title={t('warehouses.transferStock', { defaultValue: 'Transfer Stock' })} size="sm">
        <div className="space-y-3">
          <div><label className="label">{t('common.product', { defaultValue: 'Product' })} *</label>
            <select className="input" value={transferForm.productId} onChange={e => setTransferForm({ ...transferForm, productId: e.target.value })}>
              <option value="">—</option>{products.map((p: any) => <option key={p.id} value={p.id}>{p.name}{p.sku ? ` (${p.sku})` : ''}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">{t('warehouses.from', { defaultValue: 'From' })} *</label>
              <select className="input" value={transferForm.fromWarehouseId} onChange={e => setTransferForm({ ...transferForm, fromWarehouseId: e.target.value })}>
                <option value="">—</option>{activeWarehouses.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
            <div><label className="label">{t('warehouses.to', { defaultValue: 'To' })} *</label>
              <select className="input" value={transferForm.toWarehouseId} onChange={e => setTransferForm({ ...transferForm, toWarehouseId: e.target.value })}>
                <option value="">—</option>{activeWarehouses.filter((w: any) => String(w.id) !== String(transferForm.fromWarehouseId)).map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
          </div>
          <div><label className="label">{t('common.quantity', { defaultValue: 'Quantity' })} *</label>
            <input type="number" min="0.01" step="0.01" className="input" value={transferForm.quantity} onChange={e => setTransferForm({ ...transferForm, quantity: e.target.value })} />
          </div>
          <div><label className="label">{t('common.note', { defaultValue: 'Note' })}</label><input className="input" value={transferForm.note} onChange={e => setTransferForm({ ...transferForm, note: e.target.value })} /></div>
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => setShowTransfer(false)} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
            <button onClick={() => transferMut.mutate({ productId: Number(transferForm.productId), fromWarehouseId: Number(transferForm.fromWarehouseId), toWarehouseId: Number(transferForm.toWarehouseId), quantity: parseFloat(transferForm.quantity), note: transferForm.note })}
              disabled={transferMut.isPending || !transferForm.productId || !transferForm.fromWarehouseId || !transferForm.toWarehouseId || !transferForm.quantity} className="btn-primary">
              {transferMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('warehouses.transfer', { defaultValue: 'Transfer' })}
            </button>
          </div>
        </div>
      </Modal>
    );
  }
}
