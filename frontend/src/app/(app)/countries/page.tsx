'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { countriesApi } from '@/lib/api';
import { usePermission } from '@/hooks/usePermission';
import Modal from '@/components/ui/Modal';
import EmptyState from '@/components/ui/EmptyState';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { Globe2, Plus, Pencil, Archive, RotateCcw, Trash2 } from 'lucide-react';

/** Catalog of countries the company trades with — used as FK on warehouses + suppliers. */
export default function CountriesPage() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const { can } = usePermission();

  const [statusFilter, setStatusFilter] = useState<'' | 'active' | 'archived'>('active');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const blank = { name: '', code: '', currency: 'UZS', timezone: '', language: '', notes: '' };
  const [form, setForm] = useState<any>(blank);

  const { data: countries = [], isLoading } = useQuery({
    queryKey: ['countries', statusFilter],
    queryFn: () => countriesApi.list(statusFilter ? { status: statusFilter } : undefined).then(r => r.data),
    retry: false,
  });

  const refresh = () => qc.invalidateQueries({ queryKey: ['countries'] });
  const onError = (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Something went wrong' }));

  const saveMut = useMutation({
    mutationFn: (data: any) => editing ? countriesApi.update(editing.id, data) : countriesApi.create(data),
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); setForm(blank); toast.success(t('common.saved', { defaultValue: 'Saved' })); },
    onError,
  });
  const archiveMut = useMutation({
    mutationFn: (id: number) => countriesApi.archive(id),
    onSuccess: () => { refresh(); toast.success(t('countries.archived', { defaultValue: 'Country archived' })); }, onError,
  });
  const restoreMut = useMutation({
    mutationFn: (id: number) => countriesApi.restore(id),
    onSuccess: () => { refresh(); toast.success(t('countries.restored', { defaultValue: 'Country restored' })); }, onError,
  });
  const deleteMut = useMutation({
    mutationFn: (id: number) => countriesApi.remove(id),
    onSuccess: () => { refresh(); toast.success(t('countries.deleted', { defaultValue: 'Country deleted' })); }, onError,
  });

  const openCreate = () => { setEditing(null); setForm(blank); setShowForm(true); };
  const openEdit = (c: any) => {
    setEditing(c);
    setForm({
      name: c.name ?? '', code: c.code ?? '', currency: c.currency ?? 'UZS',
      timezone: c.timezone ?? '', language: c.language ?? '', notes: c.notes ?? '',
    });
    setShowForm(true);
  };

  const columns: GridColumn<any>[] = useMemo(() => [
    { key: 'name', header: t('common.name', { defaultValue: 'Name' }), width: 220, render: (c) => (
        <div className="flex items-center gap-2">
          <span className="font-medium">{c.name}</span>
          {c.code && <span className="badge-gray text-[10px] uppercase">{c.code}</span>}
        </div>
      ) },
    { key: 'currency', header: t('common.currency', { defaultValue: 'Currency' }), width: 110,
      render: (c) => <span className="text-sm font-mono">{c.currency || '—'}</span> },
    { key: 'timezone', header: t('countries.timezone', { defaultValue: 'Timezone' }), width: 160,
      render: (c) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{c.timezone || '—'}</span> },
    { key: 'language', header: t('countries.language', { defaultValue: 'Language' }), width: 110,
      render: (c) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{c.language || '—'}</span> },
    { key: 'status', header: t('common.status', { defaultValue: 'Status' }), width: 110, groupable: true, groupValue: (c) => c.status,
      render: (c) => <span className={c.status === 'archived' ? 'badge-gray' : 'badge-green'}>{c.status}</span> },
    { key: 'notes', header: t('common.note', { defaultValue: 'Note' }), width: 260,
      render: (c) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{c.notes || '—'}</span> },
    { key: '__actions', header: '', width: 150, render: (c) => (
        <div className="flex gap-1" onClick={e => e.stopPropagation()}>
          {can('countries.edit')    && c.status !== 'archived' && <button onClick={() => openEdit(c)} className="btn-icon !w-7 !h-7" title={t('common.edit', { defaultValue: 'Edit' })}><Pencil size={13} /></button>}
          {can('countries.archive') && c.status !== 'archived' && <button onClick={() => { if (confirm(t('countries.confirmArchive', { defaultValue: 'Archive this country?' }))) archiveMut.mutate(c.id); }} className="btn-icon !w-7 !h-7" title={t('common.archive', { defaultValue: 'Archive' })}><Archive size={13} /></button>}
          {can('countries.archive') && c.status === 'archived' && <button onClick={() => restoreMut.mutate(c.id)} className="btn-icon !w-7 !h-7 hover:!text-green-600" title={t('common.restore', { defaultValue: 'Restore' })}><RotateCcw size={13} /></button>}
          {can('countries.archive') && c.status === 'archived' && <button onClick={() => { if (confirm(t('countries.confirmDelete', { defaultValue: 'Permanently delete this country? This cannot be undone.' }))) deleteMut.mutate(c.id); }} className="btn-icon !w-7 !h-7 hover:!text-red-600" title={t('common.delete', { defaultValue: 'Delete' })}><Trash2 size={13} /></button>}
        </div>
      ) },
  ], [t, can, archiveMut, restoreMut, deleteMut]);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title flex items-center gap-2"><Globe2 size={22} /> {t('countries.title', { defaultValue: 'Countries' })}</h1>
        <div className="flex gap-2">
          {can('countries.create') && (
            <button onClick={openCreate} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('countries.add', { defaultValue: 'Add Country' })}</button>
          )}
        </div>
      </div>

      <div className="filter-bar">
        <select className="filter-input" value={statusFilter} onChange={e => setStatusFilter(e.target.value as any)}>
          <option value="active">{t('countries.activeOnly', { defaultValue: 'Active only' })}</option>
          <option value="archived">{t('countries.archivedOnly', { defaultValue: 'Archived only' })}</option>
          <option value="">{t('common.all', { defaultValue: 'All' })}</option>
        </select>
      </div>

      <DataGrid
        gridId="countries"
        columns={columns}
        rows={countries}
        getRowId={(c: any) => c.id}
        loading={isLoading}
        groupable
        emptyState={
          <EmptyState icon={Globe2}
            title={t('countries.emptyTitle', { defaultValue: 'No countries yet' })}
            description={t('countries.emptyBody', { defaultValue: 'Add the countries your business imports from or exports to.' })}
            action={can('countries.create') ? <button onClick={openCreate} className="btn-primary btn-sm">{t('countries.add', { defaultValue: 'Add Country' })}</button> : undefined} />
        }
      />

      <Modal open={showForm} onClose={() => { setShowForm(false); setEditing(null); setForm(blank); }}
        title={editing ? t('countries.edit', { defaultValue: 'Edit Country' }) : t('countries.add', { defaultValue: 'Add Country' })} size="sm">
        <div className="space-y-3">
          <div><label className="label">{t('common.name', { defaultValue: 'Name' })} *</label>
            <input className="input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="e.g. China, Russia, Turkey" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">{t('countries.code', { defaultValue: 'ISO Code' })}</label>
              <input className="input" maxLength={8} value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} placeholder="CN" />
            </div>
            <div><label className="label">{t('common.currency', { defaultValue: 'Currency' })}</label>
              <input className="input" maxLength={10} value={form.currency} onChange={e => setForm({ ...form, currency: e.target.value.toUpperCase() })} placeholder="CNY" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label">{t('countries.timezone', { defaultValue: 'Timezone' })}</label>
              <input className="input" value={form.timezone} onChange={e => setForm({ ...form, timezone: e.target.value })} placeholder="Asia/Shanghai" />
            </div>
            <div><label className="label">{t('countries.language', { defaultValue: 'Language' })}</label>
              <input className="input" maxLength={8} value={form.language} onChange={e => setForm({ ...form, language: e.target.value })} placeholder="zh" />
            </div>
          </div>
          <div><label className="label">{t('common.note', { defaultValue: 'Notes' })}</label>
            <textarea className="input" rows={2} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => { setShowForm(false); setEditing(null); setForm(blank); }} className="btn-secondary">{t('common.cancel', { defaultValue: 'Cancel' })}</button>
            <button onClick={() => saveMut.mutate(form)} disabled={saveMut.isPending || !form.name.trim()} className="btn-primary">
              {saveMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('common.save', { defaultValue: 'Save' })}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
