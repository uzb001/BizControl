'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { advisorApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import {
  Lightbulb, TrendingDown, PackageX, AlertCircle,
  BarChart3, Zap, ArrowRight, RefreshCw,
} from 'lucide-react';

const SEVERITY_COLORS: Record<string, string> = {
  HIGH:   'border-l-red-500 bg-red-50 dark:bg-red-900/10',
  MEDIUM: 'border-l-orange-400 bg-orange-50 dark:bg-orange-900/10',
  LOW:    'border-l-blue-400 bg-blue-50 dark:bg-blue-900/10',
};

const SEVERITY_BADGE: Record<string, string> = {
  HIGH:   'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  MEDIUM: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  LOW:    'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
};

type Tab = 'insights' | 'forecasts' | 'anomalies';

export default function AdvisorPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('insights');

  const insightsQ = useQuery({
    queryKey: ['advisor-insights'],
    queryFn: () => advisorApi.insights().then(r => r.data),
    enabled: tab === 'insights',
  });

  const forecastsQ = useQuery({
    queryKey: ['advisor-forecasts'],
    queryFn: () => advisorApi.forecasts().then(r => r.data),
    enabled: tab === 'forecasts',
  });

  const anomaliesQ = useQuery({
    queryKey: ['advisor-anomalies'],
    queryFn: () => advisorApi.anomalies().then(r => r.data),
    enabled: tab === 'anomalies',
  });

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'insights',  label: t('advisor.insights'),  icon: <Lightbulb className="w-4 h-4" /> },
    { key: 'forecasts', label: t('advisor.forecasts'), icon: <BarChart3 className="w-4 h-4" /> },
    { key: 'anomalies', label: t('advisor.anomalies'), icon: <Zap className="w-4 h-4" /> },
  ];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('advisor.title')}</h1>
          <p className="page-subtitle">{t('advisor.subtitle')}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b border-[var(--color-border)]">
        {tabs.map(({ key, label, icon }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === key
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text)]'
            }`}
          >
            {icon}
            {label}
          </button>
        ))}
      </div>

      {/* Insights tab */}
      {tab === 'insights' && (
        <div>
          {insightsQ.isLoading ? (
            <div className="p-10 text-center"><LoadingSpinner /></div>
          ) : !insightsQ.data?.length ? (
            <div className="card p-10 text-center text-[var(--color-text-muted)]">
              <Lightbulb className="w-10 h-10 mx-auto mb-2 opacity-30" />
              <p>{t('advisor.noInsights')}</p>
            </div>
          ) : (
            <div className="space-y-3">
              {(insightsQ.data as any[]).map((insight: any, i: number) => (
                <div
                  key={i}
                  className={`card p-4 border-l-4 ${SEVERITY_COLORS[insight.severity] ?? SEVERITY_COLORS.LOW}`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className={`badge text-xs ${SEVERITY_BADGE[insight.severity]}`}>
                          {insight.severity}
                        </span>
                        <span className="font-semibold text-[var(--color-text)]">{insight.title}</span>
                      </div>
                      <p className="text-sm text-[var(--color-text-muted)]">{insight.body}</p>
                      {insight.action && (
                        <p className="text-xs text-[var(--color-primary)] mt-2 flex items-center gap-1 font-medium">
                          <ArrowRight className="w-3 h-3" />
                          {insight.action}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Forecasts tab */}
      {tab === 'forecasts' && (
        <div className="space-y-6">
          {forecastsQ.isLoading ? (
            <div className="p-10 text-center"><LoadingSpinner /></div>
          ) : (
            <>
              {/* Stock runout */}
              <div>
                <h2 className="text-base font-semibold text-[var(--color-text)] mb-3">
                  {t('advisor.stockRunout')} ({(forecastsQ.data as any)?.stockRunoutCount ?? 0})
                </h2>
                {!((forecastsQ.data as any)?.stockRunout?.length) ? (
                  <div className="card p-6 text-center text-[var(--color-text-muted)]">
                    <PackageX className="w-8 h-8 mx-auto mb-2 opacity-30" />
                    <p>{t('advisor.noStockRunout')}</p>
                  </div>
                ) : (
                  <div className="card p-0">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>{t('products.name')}</th>
                          <th>{t('products.sku')}</th>
                          <th>{t('products.currentStock')}</th>
                          <th>{t('products.minStock')}</th>
                          <th>{t('common.urgency')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(forecastsQ.data as any)?.stockRunout?.map((item: any) => (
                          <tr key={item.productId}>
                            <td className="font-medium">{item.productName}</td>
                            <td className="text-sm text-[var(--color-text-muted)]">{item.sku}</td>
                            <td>{item.currentStock} {item.unit}</td>
                            <td>{item.minStockLevel}</td>
                            <td>
                              <span className={`badge ${
                                item.urgency === 'OUT' ? 'badge-red' :
                                item.urgency === 'CRITICAL' ? 'bg-red-100 text-red-700' :
                                item.urgency === 'HIGH' ? 'badge-yellow' : 'badge-blue'
                              }`}>
                                {item.urgency}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Cashflow forecast */}
              <div>
                <h2 className="text-base font-semibold text-[var(--color-text)] mb-3">
                  {t('advisor.cashflowForecast')}
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  {(forecastsQ.data as any)?.cashflowForecast?.map((fc: any, i: number) => (
                    <div key={i} className="card p-4">
                      <p className="text-sm font-medium text-[var(--color-text-muted)] mb-3">{fc.month}</p>
                      <div className="space-y-2">
                        <div className="flex justify-between text-sm">
                          <span className="text-[var(--color-text-muted)]">{t('common.sales')}</span>
                          <span className="font-medium text-green-600">
                            {Number(fc.forecastedSales).toLocaleString()} UZS
                          </span>
                        </div>
                        <div className="flex justify-between text-sm">
                          <span className="text-[var(--color-text-muted)]">{t('common.expenses')}</span>
                          <span className="font-medium text-red-500">
                            {Number(fc.forecastedExpenses).toLocaleString()} UZS
                          </span>
                        </div>
                        <div className="flex justify-between text-sm border-t pt-2 mt-2 border-[var(--color-border)]">
                          <span className="font-semibold">{t('common.profit')}</span>
                          <span className={`font-bold ${Number(fc.forecastedProfit) >= 0 ? 'text-green-600' : 'text-red-500'}`}>
                            {Number(fc.forecastedProfit).toLocaleString()} UZS
                          </span>
                        </div>
                      </div>
                      <div className="mt-2 text-xs text-[var(--color-text-muted)]">
                        {t('advisor.confidence')}: {fc.confidence}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* Anomalies tab */}
      {tab === 'anomalies' && (
        <div>
          {anomaliesQ.isLoading ? (
            <div className="p-10 text-center"><LoadingSpinner /></div>
          ) : !anomaliesQ.data?.length ? (
            <div className="card p-10 text-center text-[var(--color-text-muted)]">
              <Zap className="w-10 h-10 mx-auto mb-2 opacity-30" />
              <p>{t('advisor.noAnomalies')}</p>
            </div>
          ) : (
            <div className="space-y-3">
              {(anomaliesQ.data as any[]).map((anomaly: any, i: number) => (
                <div
                  key={i}
                  className={`card p-4 border-l-4 ${SEVERITY_COLORS[anomaly.severity] ?? SEVERITY_COLORS.LOW}`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <AlertCircle className="w-4 h-4 text-orange-500" />
                    <span className={`badge text-xs ${SEVERITY_BADGE[anomaly.severity]}`}>
                      {anomaly.severity}
                    </span>
                    <span className="font-semibold text-[var(--color-text)]">{anomaly.title}</span>
                  </div>
                  <p className="text-sm text-[var(--color-text-muted)]">{anomaly.detail}</p>
                  <p className="text-xs text-[var(--color-text-muted)] mt-1">
                    {t('advisor.detectedAt')}: {new Date(anomaly.detectedAt).toLocaleString()}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
