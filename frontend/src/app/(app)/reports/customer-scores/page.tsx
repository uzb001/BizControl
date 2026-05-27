'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { reportsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney } from '@/lib/utils';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import EmptyState from '@/components/ui/EmptyState';
import { useTranslation } from 'react-i18next';

export default function CustomerScoresPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const { data: scores, isLoading } = useQuery({
    queryKey: ['customer-scores'],
    queryFn: () => reportsApi.customerScores().then(r => r.data),
  });

  const high = scores?.filter((s: any) => s.trustLevel === 'HIGH').length || 0;
  const medium = scores?.filter((s: any) => s.trustLevel === 'MEDIUM').length || 0;
  const low = scores?.filter((s: any) => s.trustLevel === 'LOW').length || 0;

  const levelBadge = (level: string) => {
    if (level === 'HIGH') return <span className="badge-green text-xs">HIGH TRUST</span>;
    if (level === 'MEDIUM') return <span className="badge-yellow text-xs">MEDIUM</span>;
    return <span className="badge-red text-xs">LOW TRUST</span>;
  };

  const scoreBar = (score: number) => (
    <div className="flex items-center gap-2">
      <div className="h-2 w-24 bg-gray-100 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${
            score >= 80 ? 'bg-green-500' : score >= 50 ? 'bg-yellow-500' : 'bg-red-500'
          }`}
          style={{ width: `${score}%` }}
        />
      </div>
      <span className={`text-sm font-bold ${
        score >= 80 ? 'text-green-600' : score >= 50 ? 'text-yellow-600' : 'text-red-600'
      }`}>{score}</span>
    </div>
  );

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Customer Trust Scores</h1>
          <p className="text-sm text-gray-500">Risk assessment based on debt, payment history and customer type</p>
        </div>
        <ExportButton permission="reports.export" filename="customer_rating" fetcher={() => exportApi.customerRating()} />
      </div>

      {isLoading ? <LoadingSpinner /> : (
        <>
          <div className="grid grid-cols-3 gap-4 mb-6">
            <div className="stat-card border-l-4 border-green-500">
              <p className="stat-label">High Trust</p>
              <p className="stat-value text-green-600">{high}</p>
              <p className="text-xs text-gray-400">Score ≥ 80</p>
            </div>
            <div className="stat-card border-l-4 border-yellow-500">
              <p className="stat-label">Medium Risk</p>
              <p className="stat-value text-yellow-600">{medium}</p>
              <p className="text-xs text-gray-400">Score 50–79</p>
            </div>
            <div className="stat-card border-l-4 border-red-500">
              <p className="stat-label">High Risk</p>
              <p className="stat-value text-red-600">{low}</p>
              <p className="text-xs text-gray-400">Score &lt; 50</p>
            </div>
          </div>

          {!scores?.length ? (
            <EmptyState icon="👥" title="No customers found" description="Add customers to see trust scores" />
          ) : (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Customer</th>
                    <th>Type</th>
                    <th>Trust Score</th>
                    <th>Level</th>
                    <th>Current Debt</th>
                    <th>Debt Limit</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {scores.map((s: any, i: number) => (
                    <tr key={s.id}>
                      <td className="text-gray-400 text-xs">{i + 1}</td>
                      <td>
                        <div className="font-medium text-gray-900">{s.name}</div>
                        <div className="text-xs text-gray-400">{s.phone}</div>
                      </td>
                      <td><span className="badge-blue text-xs capitalize">{s.customerType}</span></td>
                      <td>{scoreBar(s.trustScore)}</td>
                      <td>{levelBadge(s.trustLevel)}</td>
                      <td className={`font-medium ${s.currentDebt > 0 ? 'text-red-600' : 'text-gray-500'}`}>
                        {formatMoney(s.currentDebt)}
                      </td>
                      <td className="text-gray-500 text-sm">
                        {s.debtLimit > 0 ? formatMoney(s.debtLimit) : '—'}
                      </td>
                      <td>
                        <button onClick={() => router.push(`/customers/${s.id}`)} className="btn-ghost btn-sm text-xs">
                          View
                        </button>
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
