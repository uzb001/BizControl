'use client';

import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { productsApi, categoriesApi, suppliersApi, exportApi } from '@/lib/api';
import { formatMoney, calcMargin, cn } from '@/lib/utils';
import { StatusBadge, StockBadge } from '@/components/ui/Badge';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import Modal, { ConfirmModal } from '@/components/ui/Modal';
import BulkBar from '@/components/ui/BulkBar';
import FilterPresets from '@/components/ui/FilterPresets';
import { useFilterPresets } from '@/lib/useTableFilters';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import ExportButton from '@/components/ui/ExportButton';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import {
  Package, Plus, RotateCcw, Eye, Pencil, Trash2, Ban,
} from 'lucide-react';

export default function ProductsPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('active');
  const [categoryId, setCategoryId] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [stockStatus, setStockStatus] = useState('');
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [showModal, setShowModal] = useState(false);
  const [editProduct, setEditProduct] = useState<any>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number | string>>(new Set());

  const { presets, save: savePreset, remove: deletePreset } = useFilterPresets('products');

  const { data: products, isLoading } = useQuery({
    queryKey: ['products', page, search, status, categoryId, supplierId, stockStatus, sortBy, sortDir],
    queryFn: () => productsApi.list({ page, size: 20, search, status, categoryId, supplierId, stockStatus, sortBy, sortDir }).then(r => r.data),
  });

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.list().then(r => r.data),
  });

  const { data: suppliers } = useQuery({
    queryKey: ['suppliers-simple'],
    queryFn: () => suppliersApi.list({ size: 200 }).then(r => r.data?.content || []),
  });

  const { register, handleSubmit, reset, setValue } = useForm();

  const saveMutation = useMutation({
    mutationFn: (data: any) => editProduct ? productsApi.update(editProduct.id, data) : productsApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      toast.success(editProduct ? t('products.updated') : t('products.created'));
      setShowModal(false); setEditProduct(null); reset();
    },
    onError: (err: any) => {
      const msg = err.response?.data?.error || err.response?.data?.message || '';
      if (err.response?.status === 403 || /permission|access denied/i.test(msg)) {
        toast.error('You do not have permission to create products. Ask the company owner to enable "products.create".');
      } else {
        toast.error(msg || t('errors.generic'));
      }
    },
  });

  const inlineEdit = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) => productsApi.update(id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['products'] }); toast.success(t('products.updated')); },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.generic')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => productsApi.deactivate(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['products'] }); toast.success(t('products.deactivated')); setDeleteId(null); },
  });

  const bulkDeactivateMutation = useMutation({
    mutationFn: () => productsApi.bulkDeactivate([...selected] as number[]),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      toast.success(t('products.bulkDeactivated', { count: selected.size }));
      setSelected(new Set());
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.generic')),
  });

  const handleEdit = (p: any) => {
    setEditProduct(p);
    ['name', 'sku', 'barcode', 'purchasePrice', 'sellingPrice', 'wholesalePrice', 'unit', 'minStockLevel', 'description']
      .forEach(k => setValue(k, p[k]));
    setValue('categoryId', p.category?.id || '');
    setValue('supplierId', p.supplier?.id || '');
    setShowModal(true);
  };


  const resetFilters = () => {
    setSearch(''); setStatus('active'); setCategoryId('');
    setSupplierId(''); setStockStatus(''); setPage(0);
    setSortBy('createdAt'); setSortDir('desc');
  };

  const currentFilters = () => ({ search, status, categoryId, supplierId, stockStatus, sortBy, sortDir });

  const applyPreset = (filters: Record<string, string>) => {
    setSearch(filters.search || '');
    setStatus(filters.status || '');
    setCategoryId(filters.categoryId || '');
    setSupplierId(filters.supplierId || '');
    setStockStatus(filters.stockStatus || '');
    setSortBy(filters.sortBy || 'createdAt');
    setSortDir((filters.sortDir as 'asc' | 'desc') || 'desc');
    setPage(0);
  };

  const items = useMemo(() => products?.content ?? [], [products]);

  // Inline edit: send a full ProductRequest payload (prices required by backend)
  const handleCellEdit = (row: any, key: string, value: string) => {
    if (row.purchasePrice == null || row.sellingPrice == null) {
      toast.error(t('errors.generic'));
      return;
    }
    const num = parseFloat(value);
    if (isNaN(num) || num < 0) { toast.error('Enter a valid number'); return; }
    if (String(num) === String(row[key])) return; // no change
    const payload = {
      name: row.name, sku: row.sku, barcode: row.barcode,
      categoryId: row.category?.id, supplierId: row.supplier?.id,
      unit: row.unit, currency: row.currency,
      purchasePrice: row.purchasePrice, sellingPrice: row.sellingPrice,
      wholesalePrice: row.wholesalePrice, minStockLevel: row.minStockLevel,
      description: row.description,
      [key]: num,
    };
    inlineEdit.mutate({ id: row.id, data: payload });
  };

  const columns: GridColumn<any>[] = [
    {
      key: 'rn', header: '#', width: 50, align: 'right', hideable: true,
      render: (_p, i) => <span className="text-xs num" style={{ color: 'rgb(var(--color-text-muted))' }}>{page * 20 + i + 1}</span>,
    },
    {
      key: 'name', header: t('products.product'), width: 240, sortable: true, sortKey: 'name',
      render: (p) => (
        <button onClick={() => router.push(`/products/${p.id}`)} className="text-left block min-w-0 w-full">
          <div className="font-medium truncate hover:text-[rgb(var(--color-primary))] transition-colors" style={{ color: 'rgb(var(--color-text-primary))' }}>{p.name}</div>
          {p.supplier && <div className="text-xs truncate" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.supplier.name}</div>}
        </button>
      ),
    },
    { key: 'sku', header: 'SKU', width: 120, render: (p) => <span className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.sku || '—'}</span> },
    { key: 'category', header: t('products.category'), width: 140, groupable: true, groupValue: (p) => p.category?.name || '—', render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.category?.name || '—'}</span> },
    { key: 'unit', header: t('products.unit'), width: 80, render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{p.unit}</span> },
    {
      key: 'purchasePrice', header: t('products.buyPrice'), width: 120, numeric: true, sortable: true, sortKey: 'purchasePrice',
      render: (p) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.purchasePrice == null ? '•••' : formatMoney(p.purchasePrice)}</span>,
    },
    {
      key: 'sellingPrice', header: t('products.sellPrice'), width: 120, numeric: true, sortable: true, sortKey: 'sellingPrice',
      editable: true, editType: 'number', accessor: (p) => p.sellingPrice,
      render: (p) => <span className="font-medium" style={{ color: 'rgb(var(--color-text-primary))' }}>{formatMoney(p.sellingPrice)}</span>,
    },
    {
      key: 'margin', header: t('products.margin'), width: 92, align: 'right',
      render: (p) => {
        const margin = parseFloat(calcMargin(p.purchasePrice, p.sellingPrice));
        return (
          <span className={cn('text-xs font-semibold px-1.5 py-0.5 rounded num',
            margin >= 30 ? 'bg-green-500/10 text-green-700 dark:text-green-400' :
            margin >= 15 ? 'bg-yellow-500/10 text-yellow-700 dark:text-yellow-400' :
            'bg-red-500/10 text-red-700 dark:text-red-400')}>
            {calcMargin(p.purchasePrice, p.sellingPrice)}
          </span>
        );
      },
    },
    {
      key: 'currentStock', header: t('products.stock'), width: 120, sortable: true, sortKey: 'currentStock',
      aggregate: 'sum', aggregateValue: (p) => parseFloat(p.currentStock || 0), aggregateRender: (v) => <span className="num font-bold">{v.toLocaleString()}</span>,
      render: (p) => (
        <div className="min-w-0">
          <div className="text-sm font-semibold num">{p.currentStock} {p.unit}</div>
          <StockBadge current={p.currentStock} min={p.minStockLevel} />
        </div>
      ),
    },
    {
      key: 'minStockLevel', header: t('products.minStock'), width: 84, numeric: true,
      editable: true, editType: 'number', accessor: (p) => p.minStockLevel,
      render: (p) => <span className="text-sm num" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.minStockLevel}</span>,
    },
    { key: 'status', header: t('common.status'), width: 104, groupable: true, groupValue: (p) => p.status, render: (p) => <StatusBadge status={p.status} /> },
    {
      key: 'actions', header: t('common.actions'), width: 150,
      render: (p) => (
        <div className="flex items-center gap-0.5">
          <button onClick={() => router.push(`/products/${p.id}`)} className="btn-icon !w-7 !h-7" title={t('common.view')}><Eye size={14} /></button>
          <button onClick={() => handleEdit(p)} className="btn-icon !w-7 !h-7" title={t('common.edit')}><Pencil size={14} /></button>
          <button onClick={() => setDeleteId(p.id)} className="btn-icon !w-7 !h-7 hover:!text-red-600" title={t('common.delete')}><Trash2 size={14} /></button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">
          {t('nav.products')}
          {products?.totalElements != null && (
            <span className="ml-2 text-sm font-normal" style={{ color: 'rgb(var(--color-text-muted))' }}>({products.totalElements})</span>
          )}
        </h1>
        <div className="flex gap-2 flex-wrap">
          <FilterPresets
            presets={presets}
            onApply={applyPreset}
            onSave={name => savePreset(name, currentFilters() as Record<string, string>)}
            onDelete={deletePreset}
          />
          <ExportButton permission="products.export" filename="products" fetcher={() => exportApi.products({ status, categoryId, stockStatus })} />
          {can('products.create') && (
            <button onClick={() => { setEditProduct(null); reset(); setShowModal(true); }} className="btn-primary btn-sm gap-1.5">
              <Plus size={14} /> {t('products.addProduct')}
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="filter-bar">
        <input
          className="filter-input"
          placeholder={t('products.searchPlaceholder')}
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(0); }}
        />
        <select className="filter-input" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">{t('common.allStatuses')}</option>
          <option value="active">{t('common.active')}</option>
          <option value="inactive">{t('common.inactive')}</option>
        </select>
        <select className="filter-input" value={categoryId} onChange={e => { setCategoryId(e.target.value); setPage(0); }}>
          <option value="">{t('products.allCategories')}</option>
          {categories?.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="filter-input" value={supplierId} onChange={e => { setSupplierId(e.target.value); setPage(0); }}>
          <option value="">{t('products.allSuppliers')}</option>
          {suppliers?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <select className="filter-input" value={stockStatus} onChange={e => { setStockStatus(e.target.value); setPage(0); }}>
          <option value="">{t('products.allStock')}</option>
          <option value="in_stock">{t('products.inStock')}</option>
          <option value="low_stock">{t('products.lowStock')}</option>
          <option value="out_of_stock">{t('products.outOfStock')}</option>
        </select>
        <button onClick={resetFilters} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      {/* Data grid */}
      <DataGrid
        gridId="products"
        columns={columns}
        rows={items}
        getRowId={(p: any) => p.id}
        loading={isLoading}
        groupable
        selectable
        selectedIds={selected}
        onSelectionChange={setSelected}
        sortBy={sortBy}
        sortDir={sortDir}
        onSortChange={(key, dir) => { setSortBy(key); setSortDir(dir); setPage(0); }}
        onCellEdit={handleCellEdit}
        onRowOpen={(p: any) => router.push(`/products/${p.id}`)}
        rowClassName={(p: any) =>
          p.currentStock === 0 ? 'dgrid-row-danger'
          : (p.currentStock > 0 && p.currentStock <= p.minStockLevel) ? 'dgrid-row-warn'
          : ''
        }
        emptyState={
          <EmptyState
            icon={Package}
            title={t('products.emptyTitle')}
            description={t('products.emptyDesc')}
            action={can('products.create')
              ? <button onClick={() => setShowModal(true)} className="btn-primary btn-sm">{t('products.addProduct')}</button>
              : undefined}
          />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={products?.totalPages} totalElements={products?.totalElements} size={20} onChange={setPage} />
      )}

      {/* Bulk actions bar */}
      <BulkBar
        count={selected.size}
        onClear={() => setSelected(new Set())}
        actions={[
          { label: t('common.deactivate'), icon: <Ban size={14} />, danger: true, onClick: () => bulkDeactivateMutation.mutate() },
        ]}
      />

      {/* Create / Edit Modal */}
      <Modal
        open={showModal}
        onClose={() => { setShowModal(false); setEditProduct(null); reset(); }}
        title={editProduct ? t('products.editProduct') : t('products.addProduct')}
        size="lg"
      >
        <form onSubmit={handleSubmit(data => saveMutation.mutate(data))} className="grid grid-cols-2 gap-4">
          <div className="col-span-2">
            <label className="label">{t('products.name')} *</label>
            <input {...register('name', { required: true })} className="input" placeholder={t('products.namePlaceholder')} />
          </div>
          <div>
            <label className="label">{t('products.sku')}</label>
            <input {...register('sku')} className="input" placeholder="SKU001" />
          </div>
          <div>
            <label className="label">{t('products.barcode')}</label>
            <input {...register('barcode')} className="input" placeholder="123456789" />
          </div>
          <div>
            <label className="label">{t('products.category')}</label>
            <select {...register('categoryId')} className="input">
              <option value="">{t('products.selectCategory')}</option>
              {categories?.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('nav.suppliers')}</label>
            <select {...register('supplierId')} className="input">
              <option value="">{t('products.selectSupplier')}</option>
              {suppliers?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div>
            <label className="label">{t('products.unit')} *</label>
            <select {...register('unit')} className="input">
              {['piece', 'kg', 'g', 'box', 'carton', 'meter', 'cm', 'liter', 'ml', 'set', 'pair'].map(u => (
                <option key={u} value={u}>{u}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">{t('products.currency')}</label>
            <select {...register('currency')} className="input">
              <option value="UZS">UZS</option>
              <option value="USD">USD</option>
            </select>
          </div>
          <div>
            <label className="label">{t('products.purchasePrice')} *</label>
            <input {...register('purchasePrice', { required: true })} type="number" step="0.01" className="input" placeholder="0.00" />
          </div>
          <div>
            <label className="label">{t('products.sellingPrice')} *</label>
            <input {...register('sellingPrice', { required: true })} type="number" step="0.01" className="input" placeholder="0.00" />
          </div>
          <div>
            <label className="label">{t('products.wholesalePrice')}</label>
            <input {...register('wholesalePrice')} type="number" step="0.01" className="input" placeholder="0.00" />
          </div>
          <div>
            <label className="label">{t('products.minStock')}</label>
            <input {...register('minStockLevel')} type="number" step="0.01" className="input" placeholder="0" />
          </div>
          <div className="col-span-2">
            <label className="label">{t('products.description')}</label>
            <textarea {...register('description')} className="input" rows={2} />
          </div>
          <div className="col-span-2 flex justify-end gap-2">
            <button type="button" onClick={() => { setShowModal(false); reset(); }} className="btn-secondary">{t('common.cancel')}</button>
            <button type="submit" disabled={saveMutation.isPending} className="btn-primary">
              {saveMutation.isPending ? t('common.saving') : (editProduct ? t('common.update') : t('common.create'))}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => deleteId && deleteMutation.mutate(deleteId)}
        title={t('products.deactivateTitle')}
        message={t('products.deactivateConfirm')}
        danger
      />
    </div>
  );
}
