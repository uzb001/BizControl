'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { productionApi } from '@/lib/api';
import { formatMoney } from '@/lib/utils';
import StatCard from '@/components/ui/StatCard';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import { Factory, Loader2, ClipboardCheck, CheckCircle2, DollarSign, Trash2, FlaskConical, ListChecks, Plus } from 'lucide-react';

export default function ProductionDashboardPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const canCost = can('production.view_cost');

  const { data, isLoading } = useQuery({
    queryKey: ['production-summary'],
    queryFn: () => productionApi.summary().then(r => r.data),
    retry: false,
  });

  const d = data ?? {};
  const wastePct = canCost && d.totalProductionCost && Number(d.totalProductionCost) > 0
    ? ((Number(d.totalWasteCost || 0) / Number(d.totalProductionCost)) * 100).toFixed(1)
    : null;

  return (
    <ErrorBoundary area="production-dashboard">
      <div className="max-w-7xl mx-auto">
        <div className="page-header">
          <h1 className="page-title">{t('production.dashboardTitle', { defaultValue: 'Production Dashboard' })}</h1>
          <div className="flex gap-2">
            <Link href="/production/bom" className="btn-secondary btn-sm gap-1.5"><FlaskConical size={14} /> {t('nav.bom', { defaultValue: 'BOM / Recipes' })}</Link>
            <Link href="/production/orders" className="btn-secondary btn-sm gap-1.5"><ListChecks size={14} /> {t('nav.productionOrders', { defaultValue: 'Orders' })}</Link>
            {can('production.create') && (
              <Link href="/production/orders" className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('production.newOrder', { defaultValue: 'New Order' })}</Link>
            )}
          </div>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => <StatCard key={i} label="" value="" loading />)}
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              <StatCard label={t('production.totalOrders', { defaultValue: 'Total Orders' })} value={d.totalOrders ?? 0} icon={Factory} color="blue" />
              <StatCard label={t('production.inProgress', { defaultValue: 'In Progress' })} value={d.inProgress ?? 0} icon={Loader2} color="yellow" />
              <StatCard label={t('production.qualityCheck', { defaultValue: 'Quality Check' })} value={d.qualityCheck ?? 0} icon={ClipboardCheck} color="purple" />
              <StatCard label={t('production.completed', { defaultValue: 'Completed' })} value={d.completed ?? 0} icon={CheckCircle2} color="green" />
            </div>

            {canCost && (
              <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
                <StatCard label={t('production.totalCost', { defaultValue: 'Total Production Cost' })} value={formatMoney(d.totalProductionCost)} icon={DollarSign} color="blue" />
                <StatCard label={t('production.wasteCost', { defaultValue: 'Total Waste Cost' })} value={formatMoney(d.totalWasteCost)} icon={Trash2} color="red" />
                {wastePct !== null && <StatCard label={t('production.wastePct', { defaultValue: 'Waste %' })} value={`${wastePct}%`} icon={Trash2} color="yellow" />}
              </div>
            )}

            <div className="grid lg:grid-cols-2 gap-5">
              {/* Status breakdown */}
              <div className="card p-5">
                <h3 className="section-title mb-4">{t('production.byStatus', { defaultValue: 'Orders by Status' })}</h3>
                <div className="space-y-2">
                  {Object.entries(d.byStatus ?? {}).map(([status, count]) => (
                    <div key={status} className="flex items-center justify-between text-sm">
                      <span style={{ color: 'rgb(var(--color-text-secondary))' }}>{t('production.status.' + status, { defaultValue: status.replace(/_/g, ' ') })}</span>
                      <span className="font-semibold num">{count as number}</span>
                    </div>
                  ))}
                  {Object.keys(d.byStatus ?? {}).length === 0 && (
                    <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noData', { defaultValue: 'No production data yet.' })}</p>
                  )}
                </div>
              </div>

              {/* Top produced products */}
              <div className="card p-5">
                <h3 className="section-title mb-4">{t('production.topProducts', { defaultValue: 'Top Produced Products' })}</h3>
                {(d.topProducts ?? []).length === 0 ? (
                  <p className="text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('production.noProduced', { defaultValue: 'Nothing produced yet.' })}</p>
                ) : (
                  <div className="space-y-2">
                    {(d.topProducts ?? []).map((p: any) => (
                      <div key={p.productId} className="flex items-center justify-between text-sm">
                        <span className="font-medium truncate">{p.productName}</span>
                        <span className="num" style={{ color: 'rgb(var(--color-text-secondary))' }}>{p.quantity}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </ErrorBoundary>
  );
}
