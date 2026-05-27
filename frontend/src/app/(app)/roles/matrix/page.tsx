'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { rolesApi, permissionsApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import { ArrowLeft, CheckCircle2, XCircle, Eye } from 'lucide-react';

// ── Key action columns to show in the matrix ───────────────────────────
const ACTION_KEYS = [
  { key: 'view',              label: 'View',         color: 'text-blue-600' },
  { key: 'create',            label: 'Create',       color: 'text-green-600' },
  { key: 'edit',              label: 'Edit',         color: 'text-amber-600' },
  { key: 'delete',            label: 'Delete',       color: 'text-red-600' },
  { key: 'export',            label: 'Export',       color: 'text-purple-600' },
  { key: 'view_profit',       label: 'Profit',       color: 'text-emerald-600' },
  { key: 'view_purchase_price', label: 'Cost Price', color: 'text-orange-600' },
  { key: 'cancel',            label: 'Cancel',       color: 'text-rose-600' },
  { key: 'adjust',            label: 'Adjust',       color: 'text-cyan-600' },
];

const MODULES = [
  'dashboard', 'products', 'stock', 'sales', 'purchases',
  'customers', 'suppliers', 'cashbox', 'bank', 'debts',
  'reports', 'users', 'roles', 'settings', 'audit', 'daily_close',
];

const MODULE_LABELS: Record<string, string> = {
  dashboard: 'Dashboard', products: 'Products', stock: 'Stock',
  sales: 'Sales', purchases: 'Purchases', customers: 'Customers',
  suppliers: 'Suppliers', cashbox: 'Cashbox', bank: 'Bank',
  debts: 'Debts', reports: 'Reports', users: 'Users',
  roles: 'Roles', settings: 'Settings', audit: 'Audit Log',
  daily_close: 'Daily Close',
};

interface Role {
  id: number; name: string; code: string; color: string;
  description: string;
  permissionCodes: string[];
  permissions: { id: number; code: string; groupName: string }[];
}

// ── Risk detection ─────────────────────────────────────────────────────
const RISK_COMBOS = [
  { perms: ['cashbox.view', 'cashbox.create_expense', 'export.all'], label: 'High risk: cash + export' },
  { perms: ['sales.view_profit', 'products.view_purchase_price', 'reports.view_money_leak'], label: 'Sensitive data over-exposure' },
  { perms: ['users.change_role', 'roles.assign_permissions'], label: 'Role escalation risk' },
];

function getRiskWarning(permCodes: string[]): string | null {
  for (const combo of RISK_COMBOS) {
    if (combo.perms.every(p => permCodes.includes(p))) return combo.label;
  }
  return null;
}

// ── Cell ──────────────────────────────────────────────────────────────
function MatrixCell({ has }: { has: boolean }) {
  return has
    ? <CheckCircle2 className="w-4 h-4 text-green-500 mx-auto" />
    : <XCircle     className="w-4 h-4 text-slate-200 dark:text-slate-700 mx-auto" />;
}

export default function AccessMatrixPage() {
  const { t } = useTranslation();
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);

  const { data: roles = [], isLoading } = useQuery<Role[]>({
    queryKey: ['roles'],
    queryFn:  () => rolesApi.list().then(r => r.data),
  });

  if (isLoading) return <div className="p-10 text-center"><LoadingSpinner /></div>;

  // Build matrix data: for each role × module × action → has perm?
  function hasPerm(role: Role, module: string, action: string): boolean {
    const code = `${module}.${action}`;
    const codes = role.permissionCodes ?? role.permissions?.map(p => p.code) ?? [];
    // Special: OWNER has everything
    if (role.code === 'OWNER') return true;
    return codes.includes(code);
  }

  // Does a role have AT LEAST ONE permission in a module?
  function hasAnyInModule(role: Role, module: string): boolean {
    const codes = role.permissionCodes ?? role.permissions?.map(p => p.code) ?? [];
    if (role.code === 'OWNER') return true;
    return codes.some(c => c.startsWith(module + '.'));
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <div className="flex items-center gap-3">
            <Link href="/roles" className="btn-icon">
              <ArrowLeft className="w-4 h-4" />
            </Link>
            <div>
              <h1 className="page-title">{t('roles.accessMatrix')}</h1>
              <p className="page-subtitle">{t('roles.accessMatrixSubtitle')}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Risk Warnings */}
      <div className="space-y-2 mb-4">
        {roles.filter(r => r.code !== 'OWNER').map(role => {
          const codes = role.permissionCodes ?? role.permissions?.map(p => p.code) ?? [];
          const risk = getRiskWarning(codes);
          if (!risk) return null;
          return (
            <div
              key={role.id}
              className="flex items-center gap-3 p-3 bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800 rounded-lg text-sm"
            >
              <span className="text-amber-600 font-bold shrink-0">⚠️</span>
              <span className="text-amber-800 dark:text-amber-300">
                <strong>{role.name}</strong>: {risk}
              </span>
            </div>
          );
        })}
      </div>

      {/* Matrix table */}
      <div className="card p-0 overflow-x-auto">
        <table className="text-xs border-collapse w-full">
          <thead>
            <tr className="bg-[var(--color-surface-alt)]">
              <th className="text-left px-3 py-3 font-semibold text-[var(--color-text-muted)] w-28 border-b border-[var(--color-border)] sticky left-0 bg-[var(--color-surface-alt)] z-10">
                Module
              </th>
              {roles.map(role => (
                <th
                  key={role.id}
                  colSpan={ACTION_KEYS.length}
                  className="px-2 py-3 border-b border-[var(--color-border)] border-l text-center"
                >
                  <button
                    className="flex items-center gap-1.5 mx-auto hover:opacity-80 transition-opacity"
                    onClick={() => setSelectedRole(role)}
                  >
                    <span
                      className="w-2 h-2 rounded-full shrink-0"
                      style={{ backgroundColor: role.color }}
                    />
                    <span
                      className="font-semibold truncate max-w-[80px]"
                      style={{ color: role.color }}
                    >
                      {role.code}
                    </span>
                    <Eye className="w-3 h-3 text-[var(--color-text-muted)]" />
                  </button>
                </th>
              ))}
            </tr>
            {/* Action sub-headers */}
            <tr className="bg-[var(--color-surface-alt)]">
              <th className="sticky left-0 bg-[var(--color-surface-alt)] z-10 border-b border-[var(--color-border)]" />
              {roles.map(role => (
                ACTION_KEYS.map(ak => (
                  <th
                    key={`${role.id}-${ak.key}`}
                    className={`px-1 py-1.5 font-medium border-b border-[var(--color-border)] text-center first:border-l ${ak.color}`}
                    title={ak.label}
                  >
                    {ak.label.slice(0, 3)}
                  </th>
                ))
              ))}
            </tr>
          </thead>
          <tbody>
            {MODULES.map((mod, mi) => (
              <tr
                key={mod}
                className={mi % 2 === 0 ? 'bg-[var(--color-surface)]' : 'bg-[var(--color-surface-alt)]'}
              >
                <td className="sticky left-0 px-3 py-2 font-semibold text-[var(--color-text)] border-r border-[var(--color-border)] z-10"
                    style={{ backgroundColor: 'inherit' }}>
                  {MODULE_LABELS[mod] ?? mod}
                </td>
                {roles.map(role => (
                  ACTION_KEYS.map(ak => (
                    <td
                      key={`${role.id}-${mod}-${ak.key}`}
                      className="px-1 py-2 text-center border-l border-[var(--color-border)] first:border-l"
                    >
                      <MatrixCell has={hasPerm(role, mod, ak.key)} />
                    </td>
                  ))
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ── Preview as Role Modal ── */}
      {selectedRole && (
        <div className="modal-overlay" onClick={() => setSelectedRole(null)}>
          <div className="modal-content max-w-xl" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: selectedRole.color }}
                />
                {t('roles.previewAs')} {selectedRole.name}
              </h2>
              <button className="btn-icon" onClick={() => setSelectedRole(null)}>
                <XCircle className="w-5 h-5" />
              </button>
            </div>
            <div className="modal-body">
              <p className="text-sm text-[var(--color-text-muted)] mb-4">
                {selectedRole.description}
              </p>

              {/* Risk warning */}
              {selectedRole.code !== 'OWNER' && (() => {
                const codes = selectedRole.permissionCodes ?? selectedRole.permissions?.map(p => p.code) ?? [];
                const risk = getRiskWarning(codes);
                return risk ? (
                  <div className="p-3 mb-4 bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800 rounded-lg text-sm text-amber-800 dark:text-amber-300">
                    ⚠️ <strong>Risk Warning:</strong> {risk}
                  </div>
                ) : null;
              })()}

              {/* Permission groups */}
              <div className="space-y-3">
                {MODULES.map(mod => {
                  const hasAny = hasAnyInModule(selectedRole, mod);
                  const codes = selectedRole.permissionCodes ?? selectedRole.permissions?.map(p => p.code) ?? [];
                  const modPerms = codes.filter(c => c.startsWith(mod + '.'));
                  if (!hasAny) return (
                    <div key={mod} className="flex items-center gap-2 text-sm text-[var(--color-text-muted)] opacity-50">
                      <XCircle className="w-3.5 h-3.5 shrink-0" />
                      <span>{MODULE_LABELS[mod]}</span>
                      <span className="text-xs">— no access</span>
                    </div>
                  );
                  return (
                    <div key={mod} className="flex items-start gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5 text-green-500 shrink-0 mt-0.5" />
                      <div>
                        <p className="text-sm font-medium text-[var(--color-text)]">{MODULE_LABELS[mod]}</p>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {(selectedRole.code === 'OWNER' ? ['ALL PERMISSIONS'] : modPerms).map(c => (
                            <span key={c} className="badge badge-gray text-xs font-mono">
                              {c.replace(mod + '.', '')}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setSelectedRole(null)}>
                {t('common.close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
