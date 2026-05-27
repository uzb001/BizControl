'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { accessLogsApi, companyApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import Pagination from '@/components/ui/Pagination';
import { ShieldAlert, ShieldCheck, Activity, Users, Filter, RefreshCw } from 'lucide-react';

interface AccessLog {
  id: number;
  userId: number;
  action: string;
  module: string;
  result: 'ALLOWED' | 'DENIED';
  reason: string | null;
  ipAddress: string | null;
  createdAt: string;
}

interface CompanyUser {
  userId: number;
  user: { id: number; fullName: string; email: string };
  role: string;
  roleName: string;
}

const MODULE_LABELS: Record<string, string> = {
  dashboard: '📊 Dashboard',
  products: '📦 Products',
  stock: '🏭 Stock',
  sales: '🛒 Sales',
  purchases: '🚚 Purchases',
  customers: '👥 Customers',
  suppliers: '🏢 Suppliers',
  cashbox: '💵 Cashbox',
  bank: '🏦 Bank',
  debts: '💳 Debts',
  reports: '📈 Reports',
  import: '📥 Import',
  export: '📤 Export',
  users: '👤 Users',
  roles: '🔑 Roles',
  settings: '⚙️ Settings',
  audit: '🕵️ Audit',
  approvals: '✅ Approvals',
  daily_close: '📅 Daily Close',
};

export default function AccessActivityLogPage() {
  const [page, setPage] = useState(0);
  const [resultFilter, setResultFilter] = useState<'' | 'ALLOWED' | 'DENIED'>('');
  const [moduleFilter, setModuleFilter] = useState('');
  const [userFilter, setUserFilter] = useState('');

  const { data: summary } = useQuery({
    queryKey: ['access-logs-summary'],
    queryFn: () => accessLogsApi.summary().then(r => r.data),
    staleTime: 30_000,
  });

  const { data: logs, isLoading, refetch } = useQuery({
    queryKey: ['access-logs', page, resultFilter, moduleFilter, userFilter],
    queryFn: () =>
      accessLogsApi.list({ page, size: 50, result: resultFilter || undefined }).then(r => r.data),
  });

  const { data: companyUsers = [] } = useQuery<CompanyUser[]>({
    queryKey: ['company-users-access'],
    queryFn: () => companyApi.users().then(r => r.data),
    staleTime: 60_000,
  });

  const userMap = Object.fromEntries(
    companyUsers.map(cu => [cu.userId, cu.user?.fullName ?? `#${cu.userId}`])
  );

  // Client-side filter for module + user (server doesn't support these yet)
  const allLogs: AccessLog[] = logs?.content ?? [];
  const filtered = allLogs.filter(log => {
    if (moduleFilter && log.module !== moduleFilter) return false;
    if (userFilter && String(log.userId) !== userFilter) return false;
    return true;
  });

  const uniqueModules = [...new Set(allLogs.map(l => l.module))].sort();

  const resetFilters = () => {
    setResultFilter('');
    setModuleFilter('');
    setUserFilter('');
    setPage(0);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title flex items-center gap-2">
            <Activity size={22} className="text-violet-500" />
            Access Activity Log
          </h1>
          <p className="page-subtitle text-gray-500 text-sm mt-1">
            Real-time record of every permission check — allowed and denied
          </p>
        </div>
        <button onClick={() => refetch()} className="btn-ghost btn-sm flex items-center gap-1">
          <RefreshCw size={14} />
          Refresh
        </button>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="grid grid-cols-3 gap-4">
          <div className="card p-4 flex items-center gap-3">
            <div className="p-2 bg-violet-100 rounded-lg">
              <Activity size={20} className="text-violet-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-gray-900">{summary.total ?? 0}</div>
              <div className="text-xs text-gray-500">Total Events</div>
            </div>
          </div>
          <div className="card p-4 flex items-center gap-3 cursor-pointer hover:border-green-300 transition-colors"
               onClick={() => { setResultFilter('ALLOWED'); setPage(0); }}>
            <div className="p-2 bg-green-100 rounded-lg">
              <ShieldCheck size={20} className="text-green-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-green-600">{summary.allowed ?? 0}</div>
              <div className="text-xs text-gray-500">Allowed</div>
            </div>
          </div>
          <div className="card p-4 flex items-center gap-3 cursor-pointer hover:border-red-300 transition-colors"
               onClick={() => { setResultFilter('DENIED'); setPage(0); }}>
            <div className="p-2 bg-red-100 rounded-lg">
              <ShieldAlert size={20} className="text-red-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-red-600">{summary.denied ?? 0}</div>
              <div className="text-xs text-gray-500">Denied</div>
            </div>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="filter-bar">
        <div className="flex items-center gap-1 text-gray-500">
          <Filter size={14} />
        </div>
        <select
          className="filter-input"
          value={resultFilter}
          onChange={e => { setResultFilter(e.target.value as any); setPage(0); }}
        >
          <option value="">All Results</option>
          <option value="ALLOWED">✅ Allowed</option>
          <option value="DENIED">🚫 Denied</option>
        </select>

        <select
          className="filter-input"
          value={moduleFilter}
          onChange={e => { setModuleFilter(e.target.value); setPage(0); }}
        >
          <option value="">All Modules</option>
          {uniqueModules.map(m => (
            <option key={m} value={m}>{MODULE_LABELS[m] ?? m}</option>
          ))}
        </select>

        <select
          className="filter-input"
          value={userFilter}
          onChange={e => { setUserFilter(e.target.value); setPage(0); }}
        >
          <option value="">All Users</option>
          {companyUsers.map(cu => (
            <option key={cu.userId} value={String(cu.userId)}>
              {cu.user?.fullName ?? `#${cu.userId}`} ({cu.roleName})
            </option>
          ))}
        </select>

        {(resultFilter || moduleFilter || userFilter) && (
          <button onClick={resetFilters} className="btn-ghost btn-sm text-red-500">
            Clear filters
          </button>
        )}
      </div>

      {/* Table */}
      <div className="table-wrapper">
        {isLoading ? (
          <LoadingSpinner />
        ) : filtered.length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <Activity size={40} className="mx-auto mb-3 opacity-30" />
            <p>No access events found</p>
          </div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Time</th>
                <th>User</th>
                <th>Module</th>
                <th>Permission Checked</th>
                <th>Result</th>
                <th>Reason</th>
                <th>IP</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(log => (
                <tr key={log.id} className={log.result === 'DENIED' ? 'bg-red-50/40' : ''}>
                  <td className="text-gray-500 text-xs whitespace-nowrap">
                    {new Date(log.createdAt).toLocaleString('en-GB', {
                      day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit'
                    })}
                  </td>
                  <td>
                    <div className="font-medium text-sm">
                      {userMap[log.userId] ?? `User #${log.userId}`}
                    </div>
                  </td>
                  <td>
                    <span className="text-xs text-gray-600 bg-gray-100 px-2 py-0.5 rounded">
                      {MODULE_LABELS[log.module] ?? log.module}
                    </span>
                  </td>
                  <td className="font-mono text-xs text-gray-700">
                    {log.action}
                  </td>
                  <td>
                    {log.result === 'DENIED' ? (
                      <span className="inline-flex items-center gap-1 badge badge-red text-xs">
                        <ShieldAlert size={10} />
                        DENIED
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 badge badge-green text-xs">
                        <ShieldCheck size={10} />
                        ALLOWED
                      </span>
                    )}
                  </td>
                  <td className="text-xs text-gray-500 max-w-[220px] truncate" title={log.reason ?? ''}>
                    {log.reason ?? '—'}
                  </td>
                  <td className="text-xs text-gray-400 font-mono">
                    {log.ipAddress ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {logs && logs.totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={logs.totalPages}
          totalElements={logs.totalElements}
          size={50}
          onChange={setPage}
        />
      )}
    </div>
  );
}
