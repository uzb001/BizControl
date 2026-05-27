'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { productionApi, productsApi, warehousesApi } from '@/lib/api';
import { formatMoney, formatDateTime, cn } from '@/lib/utils';
import Modal from '@/components/ui/Modal';
import { PageLoading } from '@/components/ui/LoadingSpinner';
import { DetailHeader, DetailStat, DetailSection } from '@/components/ui/DetailShell';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import { Factory, Boxes, DollarSign, Play, ClipboardCheck, CheckCircle2, XCircle, CalendarClock, Plus } from 'lucide-react';

const STATUS_BADGE: Record<string, string> = {
  draft: 'badge-gray', planned: 'badge-blue', in_progress: 'badge-yellow',
  quality_check: 'badge-purple', completed: 'badge-green', cancelled: 'badge-red',
};

export default function ProductionOrderDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const qc = useQueryClient();
  const { can } = usePermission();
  const { t } = useTranslation();
  const canCost = can('production.view_cost');
  const [costModal, setCostModal] = useState(false);
  const [costForm, setCostForm] = useState<any>({ costType: 'labor', amount: '', note: '' });
  const [wasteModal, setWasteModal] = useState(false);
  const [wasteForm, setWasteForm] = useState<any>({ productId: '', quantity: '', reason: '' });

  const { data, isLoading } = useQuery({
    queryKey: ['production-order', id],
    queryFn: () => productionApi.get(Number(id)).then(r => r.data),
    retry: false,
  });
  const { data: products = [] } = useQuery({ queryKey: ['prod-products'], queryFn: () => productsApi.list({ size: 500 }).then(r => r.data.content ?? r.data ?? []), retry: false });
  const { data: warehouses = [] } = useQuery({ queryKey: ['wh-list'], queryFn: () => warehousesApi.list().then(r => r.data), retry: false });
  const pName = (pid: number) => products.find((p: any) => p.id === pid)?.name ?? `#${pid}`;
  const wName = (wid: number) => warehouses.find((w: any) => w.id === wid)?.name ?? (wid ? `#${wid}` : '—');

  const refresh = () => { qc.invalidateQueries({ queryKey: ['production-order', id] }); qc.invalidateQueries({ queryKey: ['production-orders'] }); };
  const onError = (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' }));
  const act = (fn: () => Promise<any>, ok: string) => useMutation({ mutationFn: fn, onSuccess: () => { refresh(); toast.success(ok); }, onError });

  const oid = Number(id);
  const planMut = act(() => productionApi.plan(oid), t('production.planned', { defaultValue: 'Planned' }));
  const startMut = act(() => productionApi.start(oid), t('production.started', { defaultValue: 'Started' }));
  const qcMut = act(() => productionApi.qualityCheck(oid), t('production.qcStarted', { defaultValue: 'Moved to quality check' }));
  const completeMut = act(() => productionApi.complete(oid), t('production.completed', { defaultValue: 'Production completed' }));
  const cancelMut = act(() => productionApi.cancel(oid), t('production.cancelled', { defaultValue: 'Order cancelled' }));
  const addCostMut = useMutation({
    mutationFn: (body: any) => productionApi.addCost(oid, body),
    onSuccess: () => { refresh(); setCostModal(false); toast.success(t('production.costAdded', { defaultValue: 'Cost added' })); }, onError,
  });
  const addWasteMut = useMutation({
    mutationFn: (body: any) => productionApi.addWaste(oid, body),
    onSuccess: () => { refresh(); setWasteModal(false); toast.success(t('production.wasteAdded', { defaultValue: 'Waste recorded' })); }, onError,
  });

  if (isLoading) return <PageLoading />;
  if (!data?.order) return <div className="p-6" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.notFound', { defaultValue: 'Production order not found.' })}</div>;

  const o = data.order;
  const components = data.components ?? [];
  const steps = data.steps ?? [];
  const costs = data.costs ?? [];
  const waste = data.waste ?? [];
  const status = o.status;

  return (
    <ErrorBoundary area="production-order-detail">
      <div className="max-w-6xl mx-auto">
        <DetailHeader
          backHref="/production/orders"
          title={`${o.orderNumber} — ${pName(o.productId)}`}
          subtitle={<>{t('production.planned', { defaultValue: 'Planned' })}: {o.plannedQuantity} {o.unit} · {t('production.done', { defaultValue: 'Done' })}: {o.completedQuantity}</>}
          badge={<span className={STATUS_BADGE[status] || 'badge-gray'}>{t('production.status.' + status, { defaultValue: status })}</span>}
          actions={
            <div className="flex gap-2 flex-wrap">
              {status === 'draft' && can('production.edit') && <button onClick={() => planMut.mutate()} className="btn-secondary btn-sm gap-1"><CalendarClock size={13} /> {t('production.plan', { defaultValue: 'Plan' })}</button>}
              {['draft', 'planned'].includes(status) && can('production.start') && <button onClick={() => startMut.mutate()} className="btn-secondary btn-sm gap-1"><Play size={13} /> {t('production.start', { defaultValue: 'Start' })}</button>}
              {status === 'in_progress' && can('production.quality_check') && <button onClick={() => qcMut.mutate()} className="btn-secondary btn-sm gap-1"><ClipboardCheck size={13} /> {t('production.qc', { defaultValue: 'Quality Check' })}</button>}
              {['in_progress', 'quality_check'].includes(status) && can('production.complete') && <button onClick={() => completeMut.mutate()} className="btn-primary btn-sm gap-1"><CheckCircle2 size={13} /> {t('production.complete', { defaultValue: 'Complete' })}</button>}
              {status !== 'cancelled' && can('production.cancel') && <button onClick={() => { if (confirm(t('production.confirmCancel', { defaultValue: 'Cancel this order? Completed orders will be reversed.' }))) cancelMut.mutate(); }} className="btn-ghost btn-sm gap-1 text-red-600"><XCircle size={13} /> {t('common.cancel', { defaultValue: 'Cancel' })}</button>}
            </div>
          }
        />

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <DetailStat label={t('production.planned', { defaultValue: 'Planned' })} value={`${o.plannedQuantity} ${o.unit}`} icon={Factory} />
          <DetailStat label={t('production.done', { defaultValue: 'Completed' })} value={`${o.completedQuantity} ${o.unit}`} icon={Boxes} accent={status === 'completed' ? 'green' : 'default' as any} />
          {canCost && <DetailStat label={t('production.totalCost', { defaultValue: 'Total Cost' })} value={formatMoney(o.totalCost)} icon={DollarSign} />}
          {canCost && <DetailStat label={t('production.costPerUnit', { defaultValue: 'Cost / Unit' })} value={formatMoney(o.costPerUnit)} icon={DollarSign} />}
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          {/* Components */}
          <DetailSection title={t('production.components', { defaultValue: 'Raw Materials' })} noPadding>
            <div className="overflow-x-auto">
              <table className="table">
                <thead><tr><th>{t('common.product', { defaultValue: 'Product' })}</th><th>{t('warehouses.warehouse', { defaultValue: 'Warehouse' })}</th><th className="text-right">{t('production.required', { defaultValue: 'Required' })}</th><th className="text-right">{t('production.consumed', { defaultValue: 'Consumed' })}</th>{canCost && <th className="text-right">{t('production.cost', { defaultValue: 'Cost' })}</th>}</tr></thead>
                <tbody>
                  {components.map((c: any) => (
                    <tr key={c.id}>
                      <td className="font-medium">{pName(c.productId)}</td>
                      <td className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{wName(c.warehouseId)}</td>
                      <td className="text-right num">{c.requiredQuantity} {c.unit}</td>
                      <td className="text-right num">{c.consumedQuantity}</td>
                      {canCost && <td className="text-right num">{formatMoney(c.totalCost)}</td>}
                    </tr>
                  ))}
                  {components.length === 0 && <tr><td colSpan={canCost ? 5 : 4} className="text-center py-6" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noComponents', { defaultValue: 'No components (no BOM).' })}</td></tr>}
                </tbody>
              </table>
            </div>
          </DetailSection>

          {/* Steps timeline */}
          <DetailSection title={t('production.steps', { defaultValue: 'Workflow Steps' })}>
            <div className="space-y-3">
              {steps.map((s: any) => (
                <div key={s.id} className="flex items-center gap-3">
                  <div className={cn('w-2.5 h-2.5 rounded-full shrink-0', s.status === 'completed' ? 'bg-green-500' : s.status === 'in_progress' ? 'bg-yellow-500' : 'bg-slate-300')} />
                  <div className="flex-1 flex items-center justify-between">
                    <span className="text-sm font-medium">{s.stepName}</span>
                    <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.status.' + s.status, { defaultValue: s.status })}</span>
                  </div>
                </div>
              ))}
              {steps.length === 0 && <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noSteps', { defaultValue: 'No steps.' })}</p>}
            </div>
          </DetailSection>

          {/* Costs */}
          {canCost && (
            <DetailSection title={t('production.additionalCosts', { defaultValue: 'Additional Costs' })}
              actions={['draft', 'planned', 'in_progress', 'quality_check'].includes(status) && can('production.edit')
                ? <button onClick={() => { setCostForm({ costType: 'labor', amount: '', note: '' }); setCostModal(true); }} className="btn-secondary btn-sm gap-1"><Plus size={12} /> {t('production.addCost', { defaultValue: 'Add Cost' })}</button>
                : undefined}>
              <div className="space-y-1.5">
                {costs.map((c: any) => (
                  <div key={c.id} className="flex items-center justify-between text-sm">
                    <span className="capitalize" style={{ color: 'rgb(var(--color-text-secondary))' }}>{c.costType?.replace(/_/g, ' ')}</span>
                    <span className="num font-medium">{formatMoney(c.amount)}</span>
                  </div>
                ))}
                {costs.length === 0 && <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noCosts', { defaultValue: 'No extra costs added.' })}</p>}
              </div>
            </DetailSection>
          )}

          {/* Waste */}
          <DetailSection title={t('production.waste', { defaultValue: 'Waste' })}
            actions={can('production_waste.create') && status !== 'cancelled'
              ? <button onClick={() => { setWasteForm({ productId: components[0]?.productId ? String(components[0].productId) : '', quantity: '', reason: '' }); setWasteModal(true); }} className="btn-secondary btn-sm gap-1"><Plus size={12} /> {t('production.addWaste', { defaultValue: 'Record Waste' })}</button>
              : undefined}>
            <div className="space-y-1.5">
              {waste.map((w: any) => (
                <div key={w.id} className="flex items-center justify-between text-sm">
                  <span style={{ color: 'rgb(var(--color-text-secondary))' }}>{pName(w.productId)} · {w.quantity} {w.unit}{w.reason ? ` (${w.reason})` : ''}</span>
                  {canCost && <span className="num">{formatMoney(w.costImpact)}</span>}
                </div>
              ))}
              {waste.length === 0 && <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noWaste', { defaultValue: 'No waste recorded.' })}</p>}
            </div>
          </DetailSection>
        </div>

        {o.note && <DetailSection title={t('common.note', { defaultValue: 'Note' })}><p className="text-sm whitespace-pre-line" style={{ color: 'rgb(var(--color-text-secondary))' }}>{o.note}</p></DetailSection>}

        {/* Add cost modal */}
        <Modal open={costModal} onClose={() => setCostModal(false)} title={t('production.addCost', { defaultValue: 'Add Cost' })} size="sm">
          <div className="space-y-3">
            <div><label className="label">{t('production.costType', { defaultValue: 'Cost type' })}</label>
              <select className="input" value={costForm.costType} onChange={e => setCostForm({ ...costForm, costType: e.target.value })}>
                {['labor', 'packaging', 'transport', 'overhead', 'other'].map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div><label className="label">{t('common.amount', { defaultValue: 'Amount' })} *</label><input type="number" min="0.01" step="0.01" className="input" value={costForm.amount} onChange={e => setCostForm({ ...costForm, amount: e.target.value })} /></div>
            <div><label className="label">{t('common.note', { defaultValue: 'Note' })}</label><input className="input" value={costForm.note} onChange={e => setCostForm({ ...costForm, note: e.target.value })} /></div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setCostModal(false)} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
              <button onClick={() => addCostMut.mutate({ costType: costForm.costType, amount: parseFloat(costForm.amount), note: costForm.note })} disabled={addCostMut.isPending || !costForm.amount} className="btn-primary">{t('common.save', { defaultValue: 'Save' })}</button>
            </div>
          </div>
        </Modal>

        {/* Add waste modal */}
        <Modal open={wasteModal} onClose={() => setWasteModal(false)} title={t('production.addWaste', { defaultValue: 'Record Waste' })} size="sm">
          <div className="space-y-3">
            <div><label className="label">{t('common.product', { defaultValue: 'Product' })} *</label>
              <select className="input" value={wasteForm.productId} onChange={e => setWasteForm({ ...wasteForm, productId: e.target.value })}>
                <option value="">—</option>{products.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
            <div><label className="label">{t('common.quantity', { defaultValue: 'Quantity' })} *</label><input type="number" min="0.001" step="0.001" className="input" value={wasteForm.quantity} onChange={e => setWasteForm({ ...wasteForm, quantity: e.target.value })} /></div>
            <div><label className="label">{t('production.reason', { defaultValue: 'Reason' })}</label><input className="input" value={wasteForm.reason} onChange={e => setWasteForm({ ...wasteForm, reason: e.target.value })} /></div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setWasteModal(false)} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
              <button onClick={() => addWasteMut.mutate({ productId: Number(wasteForm.productId), quantity: parseFloat(wasteForm.quantity), reason: wasteForm.reason })} disabled={addWasteMut.isPending || !wasteForm.productId || !wasteForm.quantity} className="btn-primary">{t('common.save', { defaultValue: 'Save' })}</button>
            </div>
          </div>
        </Modal>
      </div>
    </ErrorBoundary>
  );
}
