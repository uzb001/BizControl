'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { suppliersApi, exportApi, countriesApi } from '@/lib/api';
import { usePermission } from '@/hooks/usePermission';
import { formatMoney } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import ExportButton from '@/components/ui/ExportButton';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Truck, Eye, Pencil, RotateCcw, Plus } from 'lucide-react';

export default function SuppliersPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { t } = useTranslation();
  const { can } = usePermission();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('active');
  const [country, setCountry] = useState('');
  const [countryId, setCountryId] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editSupplier, setEditSupplier] = useState<any>(null);

  const { data: suppliers, isLoading } = useQuery({
    queryKey: ['suppliers', page, search, status, country, countryId],
    queryFn: () => suppliersApi.list({ page, size: 20, search, status, country, countryId: countryId || undefined }).then(r => r.data),
  });

  // Active-country catalog used by the filter dropdown and the create/edit modal.
  // Only loaded when the user can see countries — view-only roles still get the page.
  const { data: countries = [] } = useQuery({
    queryKey: ['countries', 'active', 'for-suppliers'],
    queryFn: () => countriesApi.list({ status: 'active' }).then(r => r.data),
    enabled: can('countries.view'),
    retry: false,
  });
  const countryNameById = useMemo(() => {
    const m: Record<number, string> = {};
    for (const c of countries) m[c.id] = c.name;
    return m;
  }, [countries]);

  const { register, handleSubmit, reset, setValue } = useForm();

  const saveMutation = useMutation({
    mutationFn: (data: any) => {
      // Coerce the form's string countryId into Long/null for the Java backend.
      const payload = { ...data, countryId: data.countryId ? Number(data.countryId) : null };
      return editSupplier ? suppliersApi.update(editSupplier.id, payload) : suppliersApi.create(payload);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suppliers'] });
      toast.success(editSupplier ? t('suppliers.updated') : t('suppliers.created'));
      setShowModal(false); setEditSupplier(null); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.generic')),
  });

  const openEdit = (s: any) => {
    setEditSupplier(s);
    ['name', 'phone', 'country', 'city', 'address', 'currency', 'notes'].forEach(k => setValue(k, s[k]));
    setValue('countryId', s.countryId ?? '');
    setShowModal(true);
  };

  const items = useMemo(() => suppliers?.content ?? [], [suppliers]);

  const columns: GridColumn<any>[] = [
    { key: 'name', header: t('suppliers.name'), width: 200, render: (s) => (
        <button onClick={() => router.push(`/suppliers/${s.id}`)} className="font-medium text-left truncate hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>{s.name}</button>
      ) },
    { key: 'phone', header: t('suppliers.phone'), width: 150, render: (s) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{s.phone || '—'}</span> },
    { key: 'country', header: t('suppliers.country'), width: 150, groupable: true, groupValue: (s) => countryNameById[s.countryId] || s.country || '—', render: (s) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{countryNameById[s.countryId] || s.country || '—'}</span> },
    { key: 'city', header: t('suppliers.city'), width: 120, render: (s) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{s.city || '—'}</span> },
    { key: 'currency', header: t('suppliers.currency'), width: 90, groupable: true, groupValue: (s) => s.currency, render: (s) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{s.currency}</span> },
    { key: 'currentDebt', header: t('suppliers.debt'), width: 150, numeric: true, aggregate: 'sum', aggregateValue: (s) => parseFloat(s.currentDebt || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (s) => <span className={`text-sm font-medium ${s.currentDebt > 0 ? 'text-red-600 dark:text-red-400' : ''}`} style={s.currentDebt > 0 ? undefined : { color: 'rgb(var(--color-text-secondary))' }}>{formatMoney(s.currentDebt)}</span> },
    { key: 'status', header: t('common.status'), width: 100, groupable: true, groupValue: (s) => s.status, render: (s) => <StatusBadge status={s.status} /> },
    {
      key: 'actions', header: t('common.actions'), width: 100,
      render: (s) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => router.push(`/suppliers/${s.id}`)} className="btn-icon !w-7 !h-7" title={t('common.view')}><Eye size={14} /></button>
          <button onClick={() => openEdit(s)} className="btn-icon !w-7 !h-7" title={t('common.edit')}><Pencil size={14} /></button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">
          {t('nav.suppliers')}
          {suppliers?.totalElements != null && <span className="ml-2 text-sm font-normal" style={{ color: 'rgb(var(--color-text-muted))' }}>({suppliers.totalElements})</span>}
        </h1>
        <div className="flex gap-2">
          <ExportButton permission="suppliers.export" filename="suppliers" fetcher={() => exportApi.suppliers()} />
          <button onClick={() => { setEditSupplier(null); reset(); setShowModal(true); }} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('suppliers.addSupplier')}</button>
        </div>
      </div>

      <div className="filter-bar">
        <input className="filter-input" placeholder={t('suppliers.searchPlaceholder')} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        <select className="filter-input" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('common.allStatuses')}</option>
          <option value="active">{t('common.active')}</option>
          <option value="inactive">{t('common.inactive')}</option>
        </select>
        {can('countries.view') && countries.length > 0 ? (
          <select className="filter-input" value={countryId} onChange={e => { setCountryId(e.target.value); setPage(0); }}>
            <option value="">{t('suppliers.filterCountry', { defaultValue: 'Country' })}: {t('common.all', { defaultValue: 'All' })}</option>
            {countries.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        ) : (
          <input className="filter-input" placeholder={t('suppliers.filterCountry')} value={country} onChange={e => { setCountry(e.target.value); setPage(0); }} />
        )}
        <button onClick={() => { setSearch(''); setStatus('active'); setCountry(''); setCountryId(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="suppliers"
        columns={columns}
        rows={items}
        getRowId={(s: any) => s.id}
        loading={isLoading}
        groupable
        onRowOpen={(s: any) => router.push(`/suppliers/${s.id}`)}
        emptyState={
          <EmptyState icon={Truck} title={t('suppliers.emptyTitle')} description={t('suppliers.emptyDesc')}
            action={<button onClick={() => setShowModal(true)} className="btn-primary btn-sm">{t('suppliers.addSupplier')}</button>} />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={suppliers?.totalPages} totalElements={suppliers?.totalElements} size={20} onChange={setPage} />
      )}

      <Modal open={showModal} onClose={() => { setShowModal(false); setEditSupplier(null); reset(); }} title={editSupplier ? t('suppliers.editSupplier') : t('suppliers.addSupplier')}>
        <form onSubmit={handleSubmit(data => saveMutation.mutate(data))} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <label className="label">{t('suppliers.name')} *</label>
              <input {...register('name', { required: true })} className="input" />
            </div>
            <div><label className="label">{t('suppliers.phone')}</label><input {...register('phone')} className="input" /></div>
            {can('countries.view') && (
              <div><label className="label">{t('suppliers.countryStructured', { defaultValue: 'Country' })}</label>
                <select {...register('countryId')} className="input">
                  <option value="">{t('common.none', { defaultValue: '— None —' })}</option>
                  {countries.map((c: any) => <option key={c.id} value={c.id}>{c.name}{c.code ? ` (${c.code})` : ''}</option>)}
                </select>
              </div>
            )}
            <div><label className="label">{t('suppliers.country')}</label><input {...register('country')} className="input" placeholder={t('suppliers.countryLegacyHint', { defaultValue: 'Free-text (legacy)' })} /></div>
            <div><label className="label">{t('suppliers.city')}</label><input {...register('city')} className="input" /></div>
            <div>
              <label className="label">{t('suppliers.currency')}</label>
              <select {...register('currency')} className="input">
                <option value="UZS">UZS</option><option value="USD">USD</option><option value="EUR">EUR</option>
              </select>
            </div>
            <div className="col-span-2"><label className="label">{t('suppliers.notes')}</label><textarea {...register('notes')} className="input" rows={2} /></div>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setShowModal(false); reset(); }} className="btn-secondary">{t('common.cancel')}</button>
            <button type="submit" disabled={saveMutation.isPending} className="btn-primary">{saveMutation.isPending ? t('common.saving') : t('common.save')}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
