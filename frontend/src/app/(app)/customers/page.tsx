'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { customersApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDate } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal from '@/components/ui/Modal';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Users, Eye, Pencil, RotateCcw, Plus } from 'lucide-react';

const CUSTOMER_TYPES = ['retail', 'wholesale', 'vip', 'risky'];

export default function CustomersPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('active');
  const [customerType, setCustomerType] = useState('');
  const [city, setCity] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editCustomer, setEditCustomer] = useState<any>(null);

  const { data: customers, isLoading } = useQuery({
    queryKey: ['customers', page, search, status, customerType, city],
    queryFn: () => customersApi.list({ page, size: 20, search, status, customerType, city }).then(r => r.data),
  });

  const { register, handleSubmit, reset, setValue } = useForm();

  const saveMutation = useMutation({
    mutationFn: (data: any) => editCustomer
      ? customersApi.update(editCustomer.id, data)
      : customersApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] });
      toast.success(editCustomer ? t('customers.updated') : t('customers.created'));
      setShowModal(false); setEditCustomer(null); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.generic')),
  });

  const openEdit = (c: any) => {
    setEditCustomer(c);
    ['name', 'phone', 'phone2', 'city', 'address', 'customerType', 'notes'].forEach(k => setValue(k, c[k]));
    setShowModal(true);
  };

  const items = useMemo(() => customers?.content ?? [], [customers]);

  const columns: GridColumn<any>[] = [
    { key: 'name', header: t('customers.name'), width: 200, render: (c) => (
        <button onClick={() => router.push(`/customers/${c.id}`)} className="font-medium text-left truncate hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>{c.name}</button>
      ) },
    { key: 'phone', header: t('customers.phone'), width: 150, render: (c) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{c.phone || '—'}</span> },
    { key: 'city', header: t('customers.city'), width: 120, groupable: true, groupValue: (c) => c.city || '—', render: (c) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{c.city || '—'}</span> },
    { key: 'customerType', header: t('customers.type'), width: 110, groupable: true, groupValue: (c) => c.customerType, render: (c) => <span className="badge-blue text-xs capitalize">{c.customerType}</span> },
    { key: 'currentDebt', header: t('customers.debt'), width: 150, numeric: true, aggregate: 'sum', aggregateValue: (c) => parseFloat(c.currentDebt || 0), aggregateRender: (v) => <span className="num font-bold">{formatMoney(v)}</span>, render: (c) => <span className={`text-sm font-medium ${c.currentDebt > 0 ? 'text-red-600 dark:text-red-400' : ''}`} style={c.currentDebt > 0 ? undefined : { color: 'rgb(var(--color-text-secondary))' }}>{formatMoney(c.currentDebt)}</span> },
    { key: 'status', header: t('common.status'), width: 100, groupable: true, groupValue: (c) => c.status, render: (c) => <StatusBadge status={c.status} /> },
    { key: 'createdAt', header: t('common.createdAt'), width: 130, render: (c) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDate(c.createdAt)}</span> },
    {
      key: 'actions', header: t('common.actions'), width: 100,
      render: (c) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => router.push(`/customers/${c.id}`)} className="btn-icon !w-7 !h-7" title={t('common.view')}><Eye size={14} /></button>
          <button onClick={() => openEdit(c)} className="btn-icon !w-7 !h-7" title={t('common.edit')}><Pencil size={14} /></button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">
          {t('nav.customers')}
          {customers?.totalElements != null && <span className="ml-2 text-sm font-normal" style={{ color: 'rgb(var(--color-text-muted))' }}>({customers.totalElements})</span>}
        </h1>
        <div className="flex gap-2">
          <ExportButton permission="customers.export" filename="customers" fetcher={() => exportApi.customers()} />
          <button onClick={() => { setEditCustomer(null); reset(); setShowModal(true); }} className="btn-primary btn-sm gap-1.5">
            <Plus size={14} /> {t('customers.addCustomer')}
          </button>
        </div>
      </div>

      <div className="filter-bar">
        <input className="filter-input" placeholder={t('customers.searchPlaceholder')} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
        <select className="filter-input" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('common.allStatuses')}</option>
          <option value="active">{t('common.active')}</option>
          <option value="blocked">{t('common.blocked')}</option>
          <option value="risky">{t('customers.risky')}</option>
        </select>
        <select className="filter-input" value={customerType} onChange={e => { setCustomerType(e.target.value); setPage(0); }}>
          <option value="">{t('customers.allTypes')}</option>
          {CUSTOMER_TYPES.map(ct => <option key={ct} value={ct}>{ct.toUpperCase()}</option>)}
        </select>
        <input className="filter-input" placeholder={t('customers.filterCity')} value={city} onChange={e => { setCity(e.target.value); setPage(0); }} />
        <button onClick={() => { setSearch(''); setStatus('active'); setCustomerType(''); setCity(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="customers"
        columns={columns}
        rows={items}
        getRowId={(c: any) => c.id}
        loading={isLoading}
        groupable
        onRowOpen={(c: any) => router.push(`/customers/${c.id}`)}
        emptyState={
          <EmptyState icon={Users} title={t('customers.emptyTitle')} description={t('customers.emptyDesc')}
            action={<button onClick={() => setShowModal(true)} className="btn-primary btn-sm">{t('customers.addCustomer')}</button>} />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={customers?.totalPages} totalElements={customers?.totalElements} size={20} onChange={setPage} />
      )}

      <Modal open={showModal} onClose={() => { setShowModal(false); setEditCustomer(null); reset(); }} title={editCustomer ? t('customers.editCustomer') : t('customers.addCustomer')}>
        <form onSubmit={handleSubmit(data => saveMutation.mutate(data))} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <label className="label">{t('customers.name')} *</label>
              <input {...register('name', { required: true })} className="input" placeholder={t('customers.namePlaceholder')} />
            </div>
            <div>
              <label className="label">{t('customers.phone')}</label>
              <input {...register('phone')} className="input" placeholder="+998..." />
            </div>
            <div>
              <label className="label">{t('customers.city')}</label>
              <input {...register('city')} className="input" />
            </div>
            <div className="col-span-2">
              <label className="label">{t('customers.address')}</label>
              <input {...register('address')} className="input" />
            </div>
            <div>
              <label className="label">{t('customers.type')}</label>
              <select {...register('customerType')} className="input">
                {CUSTOMER_TYPES.map(ct => <option key={ct} value={ct}>{ct.toUpperCase()}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('common.status')}</label>
              <select {...register('status')} className="input">
                <option value="active">{t('common.active')}</option>
                <option value="blocked">{t('common.blocked')}</option>
                <option value="risky">{t('customers.risky')}</option>
              </select>
            </div>
            <div className="col-span-2">
              <label className="label">{t('customers.notes')}</label>
              <textarea {...register('notes')} className="input" rows={2} />
            </div>
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
