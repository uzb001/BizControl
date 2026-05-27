'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reportsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney } from '@/lib/utils';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import EmptyState from '@/components/ui/EmptyState';
import StatCard from '@/components/ui/StatCard';
import { useTranslation } from 'react-i18next';
import { PackageX, Lock, CheckCircle2, AlertTriangle, AlertOctagon } from 'lucide-react';

const THRESHOLD_OPTIONS = [
  { value: 30, label: '30 days' },
  { value: 60, label: '60 days' },
  { value: 90, label: '90 days' },
  { value: 180, label: '6 months' },
];

export default function DeadStockPage() {
  const { t } = useTranslation();
  const [days, setDays] = useState(60);

  const { data, isLoading } = useQuery({
    queryKey: ['dead-stock', days],
    queryFn: () => reportsApi.deadStock(days).then(r => r.data),
  });

  const products: any[] = data?.products || [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Dead Stock Detector</h1>
          <p className="text-sm text-gray-500">Products with stock but no sales in the selected period</p>
        </div>
        <div className="flex gap-2 items-center">
          <div className="flex items-center gap-1 bg-white border border-gray-200 rounded-lg p-1">
            {THRESHOLD_OPTIONS.map(t => (
              <button
                key={t.value}
                onClick={() => setDays(t.value)}
                className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                  days === t.value ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
          <ExportButton permission="reports.export" filename="dead_stock" fetcher={() => exportApi.deadStock(days)} />
        </div>
      </div>

      {isLoading ? <LoadingSpinner /> : (
        <>
          <div className="grid grid-cols-3 gap-4 mb-6">
            <StatCard
              label="Dead Stock Items"
              value={data?.totalProducts ?? 0}
              icon={PackageX}
              color="red"
              sub={`No sale in ${days} days`}
            />
            <StatCard
              label="Locked Capital"
              value={formatMoney(data?.totalValue)}
              icon={Lock}
              color="yellow"
              sub="Purchase cost of dead stock"
            />
            <div className="stat-card">
              <p className="stat-label">Recommendation</p>
              <div className="text-sm mt-1.5 flex items-start gap-2" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                {data?.totalProducts === 0 ? (
                  <><CheckCircle2 size={16} className="text-green-600 shrink-0 mt-0.5" /><span>No dead stock — great turnover!</span></>
                ) : data?.totalProducts < 5 ? (
                  <><AlertTriangle size={16} className="text-yellow-600 shrink-0 mt-0.5" /><span>A few slow items. Consider a discount or return.</span></>
                ) : (
                  <><AlertOctagon size={16} className="text-red-600 shrink-0 mt-0.5" /><span>Significant dead stock. Run a clearance sale or negotiate returns.</span></>
                )}
              </div>
            </div>
          </div>

          {products.length === 0 ? (
            <EmptyState
              icon={CheckCircle2}
              title="No dead stock found"
              description={`All products with stock had at least one sale in the last ${days} days`}
            />
          ) : (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Product</th>
                    <th>SKU</th>
                    <th>Category</th>
                    <th>Stock Qty</th>
                    <th>Unit</th>
                    <th>Buy Price</th>
                    <th>Locked Value</th>
                    <th>No Sale Since</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p: any, i: number) => (
                    <tr key={p.id}>
                      <td className="text-gray-400 text-xs">{i + 1}</td>
                      <td className="font-medium text-gray-900">{p.name}</td>
                      <td className="text-gray-400 font-mono text-xs">{p.sku || '—'}</td>
                      <td className="text-gray-500 text-sm">{p.category || '—'}</td>
                      <td className="font-semibold text-red-600">{p.currentStock?.toLocaleString()}</td>
                      <td className="text-gray-500 text-sm">{p.unit}</td>
                      <td className="text-gray-700">{formatMoney(p.purchasePrice)}</td>
                      <td className="font-semibold text-orange-600">{formatMoney(p.stockValue)}</td>
                      <td>
                        <span className="badge-red text-xs">{days}+ days</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
