'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { productionApi, bomApi, warehousesApi, productsApi, exportApi } from '@/lib/api';
import { formatMoney, formatDate, cn } from '@/lib/utils';
import Modal from '@/components/ui/Modal';
import EmptyState from '@/components/ui/EmptyState';
import ExportButton from '@/components/ui/ExportButton';
import Pagination from '@/components/ui/Pagination';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import { Factory, Plus, Eye, Play, ClipboardCheck, CheckCircle2, XCircle, CalendarClock } from 'lucide-react';

const STATUS_BADGE: Record<string, string> = {
  draft: 'badge-gray', planned: 'badge-blue', in_progress: 'badge-yellow',
  quality_check: 'badge-purple', completed: 'badge-green', cancelled: 'badge-red',
};

export default function ProductionOrdersPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<any>({ bomTemplateId: '', productId: '', plannedQuantity: '', sourceWarehouseId: '', finishedGoodsWarehouseId: '', note: '' });
  const canCost = can('production.view_cost');

  const { data: orders, isLoading } = useQuery({
    queryKey: ['production-orders', page],
    queryFn: () => productionApi.list({ page, size: 20 }).then(r => r.data), retry: false,
  });
  const { data: boms = [] } = useQuery({ queryKey: ['boms-active'], queryFn: () => bomApi.list().then(r => (r.data || []).filter((b: any) => b.status === 'active')), retry: false });
  const { data: warehouses = [] } = useQuery({ queryKey: ['warehouses-simple'], queryFn: () => warehousesApi.list().then(r => (r.data || []).filter((w: any) => w.status !== 'archived')), retry: false });
  const { data: products = [] } = useQuery({ queryKey: ['prod-products'], queryFn: () => productsApi.list({ size: 500 }).then(r => r.data.content ?? r.data ?? []), retry: false });

  const pName = (id: number) => products.find((p: any) => p.id === id)?.name ?? `#${id}`;
  const items = orders?.content ?? [];
  const refresh = () => qc.invalidateQueries({ queryKey: ['production-orders'] });
  const onError = (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' }));

  const createMut = useMutation({
    mutationFn: (data: any) => productionApi.create(data),
    onSuccess: () => { refresh(); setShowCreate(false); toast.success(t('production.created', { defaultValue: 'Production order created' })); },
    onError,
  });
  const action = (fn: (id: number) => Promise<any>, okMsg: string) => useMutation({
    mutationFn: fn, onSuccess: () => { refresh(); toast.success(okMsg); }, onError,
  });
  const planMut = action(productionApi.plan, t('production.planned', { defaultValue: 'Planned' }));
  const startMut = action(productionApi.start, t('production.started', { defaultValue: 'Started' }));
  const qcMut = action(productionApi.qualityCheck, t('production.qcStarted', { defaultValue: 'Moved to quality check' }));
  const completeMut = action(productionApi.complete, t('production.completed', { defaultValue: 'Production completed' }));
  const cancelMut = action(productionApi.cancel, t('production.cancelled', { defaultValue: 'Order cancelled' }));

  const selectedBom = boms.find((b: any) => String(b.id) === String(form.bomTemplateId));
  const derivedProductId = selectedBom ? selectedBom.productId : (form.productId ? Number(form.productId) : null);

  const openCreate = () => {
    setForm({ bomTemplateId: '', productId: '', plannedQuantity: '', sourceWarehouseId: '', finishedGoodsWarehouseId: '', note: '' });
    setShowCreate(true);
  };
  const submit = () => {
    if (!derivedProductId) { toast.error(t('production.productRequired', { defaultValue: 'Select a BOM or product' })); return; }
    if (!form.plannedQuantity || parseFloat(form.plannedQuantity) <= 0) { toast.error(t('production.qtyRequired', { defaultValue: 'Enter a quantity' })); return; }
    createMut.mutate({
      bomTemplateId: form.bomTemplateId ? Number(form.bomTemplateId) : null,
      productId: derivedProductId,
      plannedQuantity: parseFloat(form.plannedQuantity),
      sourceWarehouseId: form.sourceWarehouseId ? Number(form.sourceWarehouseId) : null,
      finishedGoodsWarehouseId: form.finishedGoodsWarehouseId ? Number(form.finishedGoodsWarehouseId) : null,
      note: form.note,
    });
  };

  const ActBtn = ({ show, onClick, pending, icon: Icon, label, danger }: any) => show ? (
    <button onClick={onClick} disabled={pending} className={cn('btn-sm gap-1', danger ? 'btn-ghost text-red-600' : 'btn-secondary')} title={label}>
      <Icon size={13} /> <span className="hidden xl:inline">{label}</span>
    </button>
  ) : null;

  return (
    <ErrorBoundary area="production-orders">
      <div>
        <div className="page-header">
          <h1 className="page-title">{t('production.ordersTitle', { defaultValue: 'Production Orders' })}</h1>
          <div className="flex gap-2">
            <ExportButton permission="production.export" filename="production_orders" fetcher={() => exportApi.productionOrders()} />
            {can('production.create') && <button onClick={openCreate} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('production.newOrder', { defaultValue: 'New Order' })}</button>}
          </div>
        </div>

        {isLoading ? (
          <div className="card p-8 text-center text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.loading', { defaultValue: 'Loading…' })}</div>
        ) : items.length === 0 ? (
          <EmptyState icon={Factory}
            title={t('production.emptyTitle', { defaultValue: 'No production orders yet' })}
            description={t('production.emptyBody', { defaultValue: 'Create a production order from a BOM to manufacture finished goods.' })}
            action={can('production.create') ? <button onClick={openCreate} className="btn-primary btn-sm">{t('production.newOrder', { defaultValue: 'New Order' })}</button> : undefined}
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr style={{ color: 'rgb(var(--color-text-muted))' }} className="text-left text-xs uppercase tracking-wide">
                    <th className="px-4 py-2 font-medium">{t('production.orderNo', { defaultValue: 'Order #' })}</th>
                    <th className="px-4 py-2 font-medium">{t('common.product', { defaultValue: 'Product' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('production.planned', { defaultValue: 'Planned' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('production.done', { defaultValue: 'Done' })}</th>
                    <th className="px-4 py-2 font-medium">{t('common.status', { defaultValue: 'Status' })}</th>
                    {canCost && <th className="px-4 py-2 font-medium text-right">{t('production.cost', { defaultValue: 'Cost' })}</th>}
                    <th className="px-4 py-2 font-medium text-right">{t('common.actions', { defaultValue: 'Actions' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((o: any) => (
                    <tr key={o.id} className="border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
                      <td className="px-4 py-2 font-mono text-xs">{o.orderNumber}</td>
                      <td className="px-4 py-2 font-medium">{pName(o.productId)}</td>
                      <td className="px-4 py-2 text-right num">{o.plannedQuantity} {o.unit}</td>
                      <td className="px-4 py-2 text-right num">{o.completedQuantity}</td>
                      <td className="px-4 py-2"><span className={STATUS_BADGE[o.status] || 'badge-gray'}>{t('production.status.' + o.status, { defaultValue: o.status })}</span></td>
                      {canCost && <td className="px-4 py-2 text-right num">{formatMoney(o.totalCost)}</td>}
                      <td className="px-4 py-2">
                        <div className="flex justify-end gap-1 flex-wrap">
                          <Link href={`/production/orders/${o.id}`} className="btn-secondary btn-sm gap-1" title={t('common.view', { defaultValue: 'View' })}><Eye size={13} /></Link>
                          <ActBtn show={o.status === 'draft' && can('production.edit')} onClick={() => planMut.mutate(o.id)} pending={planMut.isPending} icon={CalendarClock} label={t('production.plan', { defaultValue: 'Plan' })} />
                          <ActBtn show={['draft', 'planned'].includes(o.status) && can('production.start')} onClick={() => startMut.mutate(o.id)} pending={startMut.isPending} icon={Play} label={t('production.start', { defaultValue: 'Start' })} />
                          <ActBtn show={o.status === 'in_progress' && can('production.quality_check')} onClick={() => qcMut.mutate(o.id)} pending={qcMut.isPending} icon={ClipboardCheck} label={t('production.qc', { defaultValue: 'QC' })} />
                          <ActBtn show={['in_progress', 'quality_check'].includes(o.status) && can('production.complete')} onClick={() => completeMut.mutate(o.id)} pending={completeMut.isPending} icon={CheckCircle2} label={t('production.complete', { defaultValue: 'Complete' })} />
                          <ActBtn show={!['cancelled'].includes(o.status) && can('production.cancel')} onClick={() => { if (confirm(t('production.confirmCancel', { defaultValue: 'Cancel this order? Completed orders will be reversed.' }))) cancelMut.mutate(o.id); }} pending={cancelMut.isPending} icon={XCircle} label={t('common.cancel', { defaultValue: 'Cancel' })} danger />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {items.length > 0 && <Pagination page={page} totalPages={orders?.totalPages} totalElements={orders?.totalElements} size={20} onChange={setPage} />}
          </div>
        )}

        {/* Create wizard */}
        <Modal open={showCreate} onClose={() => setShowCreate(false)} title={t('production.newOrder', { defaultValue: 'New Production Order' })} size="md">
          <div className="space-y-3">
            <div><label className="label">{t('production.bom', { defaultValue: 'BOM / Recipe' })}</label>
              <select className="input" value={form.bomTemplateId} onChange={e => setForm({ ...form, bomTemplateId: e.target.value, productId: '' })}>
                <option value="">{t('production.noBom', { defaultValue: '— No BOM (manual product) —' })}</option>
                {boms.map((b: any) => <option key={b.id} value={b.id}>{b.name} ({pName(b.productId)})</option>)}
              </select>
            </div>
            {!form.bomTemplateId && (
              <div><label className="label">{t('bom.finishedProduct', { defaultValue: 'Finished product' })} *</label>
                <select className="input" value={form.productId} onChange={e => setForm({ ...form, productId: e.target.value })}>
                  <option value="">—</option>
                  {products.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>
            )}
            {derivedProductId && (
              <div className="rounded-lg p-2.5 text-sm" style={{ backgroundColor: 'rgb(var(--color-surface-2))' }}>
                {t('bom.finishedProduct', { defaultValue: 'Finished product' })}: <strong>{pName(derivedProductId)}</strong>
              </div>
            )}
            <div><label className="label">{t('production.quantity', { defaultValue: 'Quantity to produce' })} *</label>
              <input type="number" min="0.001" step="0.001" className="input" value={form.plannedQuantity} onChange={e => setForm({ ...form, plannedQuantity: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label">{t('production.sourceWarehouse', { defaultValue: 'Raw materials from' })}</label>
                <select className="input" value={form.sourceWarehouseId} onChange={e => setForm({ ...form, sourceWarehouseId: e.target.value })}>
                  <option value="">{t('warehouses.mainDefault', { defaultValue: 'Main warehouse (default)' })}</option>
                  {warehouses.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
                </select>
              </div>
              <div><label className="label">{t('production.finishedWarehouse', { defaultValue: 'Finished goods to' })}</label>
                <select className="input" value={form.finishedGoodsWarehouseId} onChange={e => setForm({ ...form, finishedGoodsWarehouseId: e.target.value })}>
                  <option value="">{t('warehouses.mainDefault', { defaultValue: 'Main warehouse (default)' })}</option>
                  {warehouses.map((w: any) => <option key={w.id} value={w.id}>{w.name}</option>)}
                </select>
              </div>
            </div>
            <div><label className="label">{t('common.note', { defaultValue: 'Note' })}</label><input className="input" value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} /></div>
            <div className="flex justify-end gap-2 pt-1">
              <button onClick={() => setShowCreate(false)} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
              <button onClick={submit} disabled={createMut.isPending} className="btn-primary">{createMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('production.create', { defaultValue: 'Create Order' })}</button>
            </div>
          </div>
        </Modal>
      </div>
    </ErrorBoundary>
  );
}
