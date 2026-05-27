'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { companyApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import { useTranslation } from 'react-i18next';
import Link from 'next/link';
import { Bell } from 'lucide-react';

export default function SettingsPage() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const { data: company, isLoading } = useQuery({
    queryKey: ['company'],
    queryFn: () => companyApi.get().then(r => r.data),
  });

  const { register, handleSubmit } = useForm({ values: company });

  const updateMutation = useMutation({
    mutationFn: (data: any) => companyApi.update(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['company'] });
      toast.success(t('settings.savedSuccess'));
    },
    onError: (e: any) => toast.error(e.response?.data?.error || t('errors.serverError')),
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <div className="max-w-2xl">
      <div className="page-header">
        <h1 className="page-title">{t('settings.title')}</h1>
      </div>

      {/* Settings sections — Notifications lives on a dedicated page (V20+) */}
      <div className="card p-3 mb-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Bell size={18} style={{ color: 'rgb(var(--color-text-muted))' }} />
          <div>
            <div className="font-semibold text-sm">{t('notifications.title', { defaultValue: 'Notifications' })}</div>
            <div className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
              {t('notifications.settingsBlurb', { defaultValue: 'Daily monitor, timezone, language and Telegram opt-in.' })}
            </div>
          </div>
        </div>
        <Link href="/settings/notifications" className="btn-secondary btn-sm">
          {t('common.configure', { defaultValue: 'Configure' })} →
        </Link>
      </div>

      <div className="card p-6">
        <form onSubmit={handleSubmit(data => updateMutation.mutate(data))} className="space-y-4">
          <div>
            <label className="label">{t('settings.companyName')}</label>
            <input {...register('name')} className="input" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">{t('settings.phone')}</label>
              <input {...register('phone')} className="input" />
            </div>
            <div>
              <label className="label">{t('settings.taxId')}</label>
              <input {...register('taxId')} className="input" />
            </div>
          </div>
          <div>
            <label className="label">{t('settings.address')}</label>
            <textarea {...register('address')} className="input" rows={2} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">{t('settings.businessType')}</label>
              <select {...register('businessType')} className="input">
                {['retail_store', 'wholesale', 'import', 'warehouse', 'online_shop', 'mixed'].map(bt => (
                  <option key={bt} value={bt}>{bt.replace('_', ' ').toUpperCase()}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label">{t('settings.mainCurrency')}</label>
              <select {...register('mainCurrency')} className="input">
                <option value="UZS">UZS</option>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">{t('settings.defaultStockUnit')}</label>
              <select {...register('defaultStockUnit')} className="input">
                {['piece', 'kg', 'box', 'carton', 'meter', 'liter'].map(u => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="pt-4 border-t border-gray-100">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">{t('settings.currentBalances')}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-green-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">{t('settings.cashBalance')}</p>
                <p className="text-lg font-bold text-green-700">{company?.cashBalance?.toLocaleString()} {company?.mainCurrency}</p>
              </div>
              <div className="bg-blue-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">{t('settings.bankBalance')}</p>
                <p className="text-lg font-bold text-blue-700">{company?.bankBalance?.toLocaleString()} {company?.mainCurrency}</p>
              </div>
            </div>
          </div>

          <div className="flex justify-end">
            <button type="submit" disabled={updateMutation.isPending} className="btn-primary">
              {updateMutation.isPending ? t('common.saving') : t('settings.saveSettings')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
