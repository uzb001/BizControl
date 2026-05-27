'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { categoriesApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { StatusBadge } from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import Modal from '@/components/ui/Modal';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';

export default function CategoriesPage() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [showModal, setShowModal] = useState(false);
  const [editCat, setEditCat] = useState<any>(null);
  const { register, handleSubmit, reset, setValue } = useForm();

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.list().then(r => r.data),
  });

  const saveMutation = useMutation({
    mutationFn: (data: any) => editCat ? categoriesApi.update(editCat.id, data) : categoriesApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      toast.success(editCat ? 'Updated' : 'Category created');
      setShowModal(false); setEditCat(null); reset();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => categoriesApi.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categories'] }); toast.success('Deactivated'); },
  });

  const openEdit = (c: any) => {
    setEditCat(c);
    setValue('name', c.name);
    setValue('description', c.description);
    setShowModal(true);
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Categories</h1>
        <div className="flex gap-2">
          <ExportButton permission="products.export" filename="categories" fetcher={() => exportApi.categories()} />
          <button onClick={() => { setEditCat(null); reset(); setShowModal(true); }} className="btn-primary btn-sm">+ Add Category</button>
        </div>
      </div>

      <div className="table-wrapper">
        {isLoading ? <LoadingSpinner /> : categories?.length === 0 ? (
          <EmptyState icon="🗂️" title="No categories" description="Create your first category" action={<button onClick={() => setShowModal(true)} className="btn-primary btn-sm">Add Category</button>} />
        ) : (
          <table className="table">
            <thead><tr><th>Name</th><th>Parent</th><th>Description</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>
              {categories?.map((c: any) => (
                <tr key={c.id}>
                  <td className="font-medium">{c.name}</td>
                  <td className="text-gray-500 text-sm">{c.parent?.name || '—'}</td>
                  <td className="text-gray-500 text-sm">{c.description || '—'}</td>
                  <td><StatusBadge status={c.status} /></td>
                  <td>
                    <div className="flex gap-1">
                      <button onClick={() => openEdit(c)} className="btn-ghost btn-sm text-xs">Edit</button>
                      <button onClick={() => deleteMutation.mutate(c.id)} className="btn-ghost btn-sm text-xs text-red-500">Del</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal open={showModal} onClose={() => { setShowModal(false); setEditCat(null); reset(); }} title={editCat ? 'Edit Category' : 'Add Category'} size="sm">
        <form onSubmit={handleSubmit(data => saveMutation.mutate(data))} className="space-y-3">
          <div>
            <label className="label">Name *</label>
            <input {...register('name', { required: true })} className="input" placeholder="Category name" />
          </div>
          <div>
            <label className="label">Parent Category</label>
            <select {...register('parentId')} className="input">
              <option value="">No parent</option>
              {categories?.filter((c: any) => c.id !== editCat?.id).map((c: any) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Description</label>
            <textarea {...register('description')} className="input" rows={2} />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setShowModal(false); reset(); }} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saveMutation.isPending} className="btn-primary">{saveMutation.isPending ? 'Saving...' : 'Save'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
