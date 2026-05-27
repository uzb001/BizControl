'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reportsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney } from '@/lib/utils';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import StatCard from '@/components/ui/StatCard';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { useTranslation } from 'react-i18next';
import { TrendingDown, TrendingUp, DollarSign } from 'lucide-react';

const PERIODS = [
  { value: 'today', label: 'Today' },
  { value: 'this_week', label: 'This Week' },
  { value: 'this_month', label: 'This Month' },
  { value: 'last_month', label: 'Last Month' },
];

const CATEGORY_ICONS: Record<string, string> = {
  supplier_payment: '🚚',
  purchase_payment: '📦',
  rent: '🏢',
  salary: '👥',
  transport: '🚗',
  advertising: '📢',
  personal_withdrawal: '💸',
  other_expense: '📋',
};

const COLORS = ['#ef4444', '#f97316', '#eab308', '#84cc16', '#22c55e', '#06b6d4', '#6366f1', '#a855f7'];

export default function MoneyLeakPage() {
  const { t } = useTranslation();
  const [period, setPeriod] = useState('this_month');

  const { data, isLoading } = useQuery({
    queryKey: ['money-leak', period],
    queryFn: () => reportsApi.moneyLeak(period).then(r => r.data),
  });

  const categories: any[] = data?.categories || [];

  const chartData = categories.map(c => ({
    name: c.category.replace(/_/g, ' ').replace(/\b\w/g, (l: string) => l.toUpperCase()),
    amount: parseFloat(c.amount),
    pct: parseFloat(c.percentage),
  }));

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Money Leak Report</h1>
          <p className="text-sm text-gray-500">Understand where your money is going</p>
        </div>
        <div className="flex items-center gap-2">
        <ExportButton permission="reports.export" filename="money_leak" fetcher={() => exportApi.moneyLeak()} />
        <div className="flex items-center gap-1 bg-white border border-gray-200 rounded-lg p-1">
          {PERIODS.map(p => (
            <button
              key={p.value}
              onClick={() => setPeriod(p.value)}
              className={`px-3 py-1 rounded text-xs font-medium transition-colors ${
                period === p.value ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
        </div>
      </div>

      {isLoading ? <LoadingSpinner /> : (
        <>
          <div className="grid grid-cols-4 gap-4 mb-6">
            <StatCard label="Total Expenses" value={formatMoney(data?.totalExpense)} icon={TrendingDown} color="red" />
            <StatCard label="Total Sales" value={formatMoney(data?.totalSales)} icon={TrendingUp} color="green" />
            <StatCard label="Gross Profit" value={formatMoney(data?.totalProfit)} icon={DollarSign} color="blue" />
            <div className="stat-card">
              <p className="stat-label">Expense Ratio</p>
              <p className={`stat-value ${parseFloat(data?.expenseToSalesRatio) > 50 ? 'text-red-600' : 'text-green-600'}`}>
                {data?.expenseToSalesRatio ?? 0}%
              </p>
              <p className="text-xs text-gray-400 mt-0.5">of sales</p>
            </div>
          </div>

          <div className="grid lg:grid-cols-2 gap-6">
            {/* Bar Chart */}
            <div className="card p-5">
              <h3 className="font-semibold text-gray-900 mb-4">Expense by Category</h3>
              {chartData.length === 0 ? (
                <p className="text-gray-400 text-sm text-center py-8">No expense data for this period</p>
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={chartData} layout="vertical" margin={{ left: 20 }}>
                    <XAxis type="number" tickFormatter={v => `${(v / 1000000).toFixed(1)}M`} />
                    <YAxis dataKey="name" type="category" width={130} tick={{ fontSize: 11 }} />
                    <Tooltip
                      formatter={(v: any) => new Intl.NumberFormat('uz-UZ').format(v) + ' UZS'}
                    />
                    <Bar dataKey="amount" radius={[0, 4, 4, 0]}>
                      {chartData.map((_, i) => (
                        <Cell key={i} fill={COLORS[i % COLORS.length]} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Table breakdown */}
            <div className="card p-5">
              <h3 className="font-semibold text-gray-900 mb-4">Detailed Breakdown</h3>
              <div className="space-y-2">
                {categories.length === 0 ? (
                  <p className="text-gray-400 text-sm text-center py-8">No expenses recorded</p>
                ) : categories.map((c: any, i: number) => (
                  <div key={c.category} className="flex items-center gap-3">
                    <span className="text-lg w-7 text-center">{CATEGORY_ICONS[c.category] || '📋'}</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-center mb-0.5">
                        <span className="text-sm font-medium text-gray-800 capitalize">
                          {c.category.replace(/_/g, ' ')}
                        </span>
                        <span className="text-sm font-semibold text-gray-900">{formatMoney(c.amount)}</span>
                      </div>
                      <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all"
                          style={{
                            width: `${c.percentage}%`,
                            backgroundColor: COLORS[i % COLORS.length]
                          }}
                        />
                      </div>
                    </div>
                    <span className="text-xs text-gray-400 w-10 text-right">{c.percentage}%</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
