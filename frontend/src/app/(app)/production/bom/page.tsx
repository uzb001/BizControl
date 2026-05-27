'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { bomApi, productsApi, exportApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import Modal from '@/components/ui/Modal';
import EmptyState from '@/components/ui/EmptyState';
import ExportButton from '@/components/ui/ExportButton';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import { FlaskConical, Plus, Pencil, Trash2, X } from 'lucide-react';

interface CompRow { componentProductId: string; quantity: string; unit: string; wastePercent: string; }

export default function BomPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [form, setForm] = useState<any>({ productId: '', name: '', version: 'v1', outputQuantity: '1', unit: 'piece', note: '' });
  const [rows, setRows] = useState<CompRow[]>([{ componentProductId: '', quantity: '', unit: 'piece', wastePercent: '0' }]);

  const { data: boms = [], isLoading } = useQuery({
    queryKey: ['boms'], queryFn: () => bomApi.list().then(r => r.data), retry: false,
  });
  const { data: products = [] } = useQuery({
    queryKey: ['bom-products'], queryFn: () => productsApi.list({ size: 500 }).then(r => r.data.content ?? r.data ?? []),
    retry: false,
  });
  const pName = (id: number) => products.find((p: any) => p.id === id)?.name ?? `#${id}`;

  const refresh = () => qc.invalidateQueries({ queryKey: ['boms'] });
  const onError = (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' }));

  const saveMut = useMutation({
    mutationFn: (data: any) => editing ? bomApi.update(editing.id, data) : bomApi.create(data),
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); toast.success(t('common.saved', { defaultValue: 'Saved' })); },
    onError,
  });
  const delMut = useMutation({
    mutationFn: (id: number) => bomApi.delete(id),
    onSuccess: () => { refresh(); toast.success(t('bom.deleted', { defaultValue: 'BOM removed' })); },
    onError,
  });

  const openCreate = () => {
    setEditing(null);
    setForm({ productId: '', name: '', version: 'v1', outputQuantity: '1', unit: 'piece', note: '' });
    setRows([{ componentProductId: '', quantity: '', unit: 'piece', wastePercent: '0' }]);
    setShowForm(true);
  };
  const openEdit = async (b: any) => {
    const res = await bomApi.get(b.id);
    const tpl = res.data.template; const comps = res.data.components ?? [];
    setEditing(tpl);
    setForm({ productId: String(tpl.productId), name: tpl.name, version: tpl.version, outputQuantity: String(tpl.outputQuantity), unit: tpl.unit, note: tpl.note ?? '' });
    setRows(comps.length ? comps.map((c: any) => ({ componentProductId: String(c.componentProductId), quantity: String(c.quantity), unit: c.unit, wastePercent: String(c.wastePercent ?? 0) }))
                         : [{ componentProductId: '', quantity: '', unit: 'piece', wastePercent: '0' }]);
    setShowForm(true);
  };

  const submit = () => {
    const components = rows.filter(r => r.componentProductId && r.quantity).map(r => ({
      componentProductId: Number(r.componentProductId), quantity: parseFloat(r.quantity),
      unit: r.unit, wastePercent: parseFloat(r.wastePercent) || 0,
    }));
    if (!form.productId) { toast.error(t('bom.productRequired', { defaultValue: 'Select a finished product' })); return; }
    if (components.length === 0) { toast.error(t('bom.componentsRequired', { defaultValue: 'Add at least one component' })); return; }
    saveMut.mutate({ ...form, productId: Number(form.productId), outputQuantity: parseFloat(form.outputQuantity) || 1, components });
  };

  return (
    <ErrorBoundary area="bom">
      <div>
        <div className="page-header">
          <h1 className="page-title">{t('bom.title', { defaultValue: 'BOM / Recipes' })}</h1>
          <div className="flex gap-2">
            <ExportButton permission="bom.export" filename="bom" fetcher={() => exportApi.bom()} />
            {can('bom.create') && <button onClick={openCreate} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('bom.add', { defaultValue: 'New BOM' })}</button>}
          </div>
        </div>

        {isLoading ? (
          <div className="card p-8 text-center text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.loading', { defaultValue: 'Loading…' })}</div>
        ) : boms.length === 0 ? (
          <EmptyState icon={FlaskConical}
            title={t('bom.emptyTitle', { defaultValue: 'No recipes yet' })}
            description={t('bom.emptyBody', { defaultValue: 'Create a BOM to define how a finished product is manufactured.' })}
            action={can('bom.create') ? <button onClick={openCreate} className="btn-primary btn-sm">{t('bom.add', { defaultValue: 'New BOM' })}</button> : undefined}
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr style={{ color: 'rgb(var(--color-text-muted))' }} className="text-left text-xs uppercase tracking-wide">
                    <th className="px-4 py-2 font-medium">{t('bom.name', { defaultValue: 'Name' })}</th>
                    <th className="px-4 py-2 font-medium">{t('common.product', { defaultValue: 'Product' })}</th>
                    <th className="px-4 py-2 font-medium">{t('bom.version', { defaultValue: 'Version' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('bom.output', { defaultValue: 'Output' })}</th>
                    <th className="px-4 py-2 font-medium">{t('common.status', { defaultValue: 'Status' })}</th>
                    <th className="px-4 py-2 font-medium text-right">{t('common.actions', { defaultValue: 'Actions' })}</th>
                  </tr>
                </thead>
                <tbody>
                  {boms.map((b: any) => (
                    <tr key={b.id} className={cn('border-t', b.status !== 'active' && 'opacity-50')} style={{ borderColor: 'rgb(var(--color-border))' }}>
                      <td className="px-4 py-2 font-medium">{b.name}</td>
                      <td className="px-4 py-2">{pName(b.productId)}</td>
                      <td className="px-4 py-2 text-xs">{b.version}</td>
                      <td className="px-4 py-2 text-right num">{b.outputQuantity} {b.unit}</td>
                      <td className="px-4 py-2"><span className={b.status === 'active' ? 'badge-green' : 'badge-gray'}>{b.status}</span></td>
                      <td className="px-4 py-2">
                        <div className="flex justify-end gap-1">
                          {can('bom.edit') && <button onClick={() => openEdit(b)} className="btn-icon !w-7 !h-7" title={t('common.edit', { defaultValue: 'Edit' })}><Pencil size={13} /></button>}
                          {can('bom.delete') && b.status === 'active' && <button onClick={() => { if (confirm(t('bom.confirmDelete', { defaultValue: 'Deactivate this BOM?' }))) delMut.mutate(b.id); }} className="btn-icon !w-7 !h-7" title={t('common.delete', { defaultValue: 'Delete' })}><Trash2 size={13} /></button>}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <Modal open={showForm} onClose={() => { setShowForm(false); setEditing(null); }} title={editing ? t('bom.edit', { defaultValue: 'Edit BOM' }) : t('bom.add', { defaultValue: 'New BOM' })} size="lg">
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label">{t('bom.finishedProduct', { defaultValue: 'Finished product' })} *</label>
                <select className="input" value={form.productId} onChange={e => setForm({ ...form, productId: e.target.value })} disabled={!!editing}>
                  <option value="">—</option>
                  {products.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>
              <div><label className="label">{t('bom.name', { defaultValue: 'Name' })} *</label><input className="input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><label className="label">{t('bom.version', { defaultValue: 'Version' })}</label><input className="input" value={form.version} onChange={e => setForm({ ...form, version: e.target.value })} disabled={!!editing} /></div>
              <div><label className="label">{t('bom.outputQty', { defaultValue: 'Output qty' })}</label><input type="number" min="0.001" step="0.001" className="input" value={form.outputQuantity} onChange={e => setForm({ ...form, outputQuantity: e.target.value })} /></div>
              <div><label className="label">{t('common.unit', { defaultValue: 'Unit' })}</label><input className="input" value={form.unit} onChange={e => setForm({ ...form, unit: e.target.value })} /></div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="label mb-0">{t('bom.components', { defaultValue: 'Components' })} *</label>
                <button onClick={() => setRows([...rows, { componentProductId: '', quantity: '', unit: 'piece', wastePercent: '0' }])} className="btn-secondary btn-sm gap-1"><Plus size={12} /> {t('bom.addComponent', { defaultValue: 'Add' })}</button>
              </div>
              <div className="space-y-2">
                {rows.map((r, i) => (
                  <div key={i} className="grid grid-cols-12 gap-2 items-center">
                    <select className="input col-span-6" value={r.componentProductId} onChange={e => setRows(rows.map((x, j) => j === i ? { ...x, componentProductId: e.target.value } : x))}>
                      <option value="">{t('common.product', { defaultValue: 'Product' })}…</option>
                      {products.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
                    </select>
                    <input type="number" min="0" step="0.001" placeholder={t('common.qty', { defaultValue: 'Qty' })} className="input col-span-2" value={r.quantity} onChange={e => setRows(rows.map((x, j) => j === i ? { ...x, quantity: e.target.value } : x))} />
                    <input placeholder={t('common.unit', { defaultValue: 'Unit' })} className="input col-span-2" value={r.unit} onChange={e => setRows(rows.map((x, j) => j === i ? { ...x, unit: e.target.value } : x))} />
                    <input type="number" min="0" step="0.1" placeholder="%" title={t('bom.wastePercent', { defaultValue: 'Waste %' })} className="input col-span-1" value={r.wastePercent} onChange={e => setRows(rows.map((x, j) => j === i ? { ...x, wastePercent: e.target.value } : x))} />
                    <button onClick={() => setRows(rows.filter((_, j) => j !== i))} className="col-span-1 text-red-400 hover:text-red-600 flex justify-center"><X size={14} /></button>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-1">
              <button onClick={() => { setShowForm(false); setEditing(null); }} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
              <button onClick={submit} disabled={saveMut.isPending} className="btn-primary">{saveMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('common.save', { defaultValue: 'Save' })}</button>
            </div>
          </div>
        </Modal>
      </div>
    </ErrorBoundary>
  );
}
