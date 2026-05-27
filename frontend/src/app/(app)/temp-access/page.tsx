'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { tempAccessApi, permissionsApi, companyApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import toast from 'react-hot-toast';
import { Timer, Plus, X, Trash2, CheckCircle2, Clock, User } from 'lucide-react';

// ── Group permissions by module for nicer display ──────────────────────
const MODULE_LABELS: Record<string, string> = {
  dashboard: '🏠 Dashboard', products: '📦 Products', stock: '🏭 Stock',
  sales: '💰 Sales', purchases: '🛒 Purchases', customers: '👥 Customers',
  suppliers: '🚚 Suppliers', cashbox: '💵 Cashbox', bank: '🏦 Bank',
  debts: '📋 Debts', reports: '📊 Reports', import: '📥 Import',
  export: '📤 Export', users: '👤 Users', roles: '🔑 Roles',
  settings: '⚙️ Settings', audit: '🛡️ Audit', daily_close: '🔒 Daily Close',
  approvals: '✅ Approvals',
};

interface PermFlat { id: number; code: string; groupName: string; description: string; }

export default function TempAccessPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [showGrant, setShowGrant] = useState(false);
  const [form, setForm] = useState({
    userId: '',
    permissionCode: '',
    expiresAt: '',
    reason: '',
  });

  // ── Queries ────────────────────────────────────────────────────────
  const { data: grants = [], isLoading } = useQuery<any[]>({
    queryKey: ['temp-access'],
    queryFn: () => tempAccessApi.list().then(r => r.data),
  });

  const { data: companyUsers = [] } = useQuery<any[]>({
    queryKey: ['company-users'],
    queryFn: () => companyApi.users().then(r => r.data),
    enabled: showGrant,
  });

  const { data: permissionsFlat = [] } = useQuery<PermFlat[]>({
    queryKey: ['permissions-flat'],
    queryFn: () => permissionsApi.flat().then(r => r.data),
    enabled: showGrant,
  });

  // Group permissions by module
  const permsByModule: Record<string, PermFlat[]> = {};
  (permissionsFlat as PermFlat[]).forEach(p => {
    const mod = p.groupName || p.code.split('.')[0];
    if (!permsByModule[mod]) permsByModule[mod] = [];
    permsByModule[mod].push(p);
  });

  // ── Mutations ──────────────────────────────────────────────────────
  const grantMutation = useMutation({
    mutationFn: (data: any) => tempAccessApi.grant(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['temp-access'] });
      toast.success(t('tempAccess.granted'));
      setShowGrant(false);
      setForm({ userId: '', permissionCode: '', expiresAt: '', reason: '' });
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || t('errors.serverError')),
  });

  const revokeMutation = useMutation({
    mutationFn: (id: number) => tempAccessApi.revoke(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['temp-access'] });
      toast.success(t('tempAccess.revoked'));
    },
    onError: () => toast.error(t('errors.serverError')),
  });

  const handleGrant = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.userId || !form.permissionCode || !form.expiresAt) {
      toast.error(t('validation.required'));
      return;
    }
    grantMutation.mutate({
      userId: Number(form.userId),
      permissionCode: form.permissionCode,
      expiresAt: form.expiresAt + ':00',
      reason: form.reason,
    });
  };

  const now = new Date();

  // Get display name for userId
  const getUserName = (userId: number) => {
    const cu = (companyUsers as any[]).find(u => u.userId === userId || u.user?.id === userId);
    return cu?.user?.fullName || `#${userId}`;
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('tempAccess.title')}</h1>
          <p className="page-subtitle">{t('tempAccess.subtitle')}</p>
        </div>
        <button className="btn-primary btn-sm" onClick={() => setShowGrant(true)}>
          <Plus className="w-4 h-4" />
          {t('tempAccess.grant')}
        </button>
      </div>

      <div className="card p-0">
        {isLoading ? (
          <div className="p-10 text-center"><LoadingSpinner /></div>
        ) : !grants.length ? (
          <div className="p-10 text-center text-[var(--color-text-muted)]">
            <Timer className="w-10 h-10 mx-auto mb-2 opacity-30" />
            <p>{t('tempAccess.noGrants')}</p>
          </div>
        ) : (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('tempAccess.employee')}</th>
                  <th>{t('tempAccess.permissionCode')}</th>
                  <th>{t('tempAccess.reason')}</th>
                  <th>{t('tempAccess.grantedAt')}</th>
                  <th>{t('tempAccess.expiresAt')}</th>
                  <th>{t('common.status')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {grants.map((g: any) => {
                  const expired = new Date(g.expiresAt) < now;
                  const isActive = g.active && !expired && !g.revokedAt;
                  return (
                    <tr key={g.id}>
                      <td>
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center shrink-0">
                            <User className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
                          </div>
                          <div>
                            <p className="font-medium text-sm">{getUserName(g.userId)}</p>
                            <p className="text-xs text-[var(--color-text-muted)]">ID #{g.userId}</p>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="badge badge-gray font-mono text-xs">{g.permissionCode}</span>
                      </td>
                      <td className="text-sm text-[var(--color-text-muted)] max-w-[200px] truncate">
                        {g.reason || '—'}
                      </td>
                      <td className="text-sm text-[var(--color-text-muted)]">
                        {new Date(g.grantedAt).toLocaleString()}
                      </td>
                      <td className="text-sm text-[var(--color-text-muted)]">
                        {new Date(g.expiresAt).toLocaleString()}
                      </td>
                      <td>
                        {isActive ? (
                          <span className="badge badge-green flex items-center gap-1 w-fit">
                            <CheckCircle2 className="w-3 h-3" /> {t('common.active')}
                          </span>
                        ) : g.revokedAt ? (
                          <span className="badge badge-red">{t('tempAccess.statusRevoked')}</span>
                        ) : (
                          <span className="badge badge-gray flex items-center gap-1 w-fit">
                            <Clock className="w-3 h-3" /> {t('tempAccess.statusExpired')}
                          </span>
                        )}
                      </td>
                      <td>
                        {isActive && (
                          <button
                            className="btn-icon text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                            title={t('tempAccess.revoke')}
                            onClick={() => revokeMutation.mutate(g.id)}
                            disabled={revokeMutation.isPending}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Grant modal ── */}
      {showGrant && (
        <div className="modal-overlay" onClick={() => setShowGrant(false)}>
          <div className="modal-content max-w-lg" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">{t('tempAccess.grantTitle')}</h2>
              <button className="btn-icon" onClick={() => setShowGrant(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleGrant} className="modal-body space-y-4">

              {/* Employee dropdown */}
              <div>
                <label className="label">
                  {t('tempAccess.employee')} <span className="text-red-500">*</span>
                </label>
                <select
                  className="input"
                  value={form.userId}
                  onChange={e => setForm(p => ({ ...p, userId: e.target.value }))}
                  required
                >
                  <option value="">— {t('tempAccess.selectEmployee')} —</option>
                  {(companyUsers as any[])
                    .filter(u => u.role !== 'OWNER' && u.status === 'active')
                    .map(u => (
                      <option key={u.userId} value={u.userId}>
                        {u.user?.fullName} — {u.roleName || u.role}
                      </option>
                    ))}
                </select>
              </div>

              {/* Permission grouped dropdown */}
              <div>
                <label className="label">
                  {t('tempAccess.permissionCode')} <span className="text-red-500">*</span>
                </label>
                <select
                  className="input"
                  value={form.permissionCode}
                  onChange={e => setForm(p => ({ ...p, permissionCode: e.target.value }))}
                  required
                >
                  <option value="">— {t('tempAccess.selectPermission')} —</option>
                  {Object.entries(permsByModule).map(([mod, perms]) => (
                    <optgroup key={mod} label={MODULE_LABELS[mod] ?? mod.toUpperCase()}>
                      {perms.map(perm => (
                        <option key={perm.id} value={perm.code}>
                          {perm.code} — {perm.description}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>
              </div>

              {/* Expiry datetime */}
              <div>
                <label className="label">
                  {t('tempAccess.expiresAt')} <span className="text-red-500">*</span>
                </label>
                <input
                  type="datetime-local"
                  className="input"
                  value={form.expiresAt}
                  onChange={e => setForm(p => ({ ...p, expiresAt: e.target.value }))}
                  min={new Date().toISOString().slice(0, 16)}
                  max={new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString().slice(0, 16)}
                  required
                />
                <p className="text-xs text-[var(--color-text-muted)] mt-1">
                  {t('tempAccess.maxDays')}
                </p>
              </div>

              {/* Reason */}
              <div>
                <label className="label">{t('tempAccess.reason')}</label>
                <textarea
                  className="input h-20 resize-none"
                  placeholder={t('tempAccess.reasonPlaceholder')}
                  value={form.reason}
                  onChange={e => setForm(p => ({ ...p, reason: e.target.value }))}
                />
              </div>

              <div className="modal-footer">
                <button type="button" className="btn-secondary" onClick={() => setShowGrant(false)}>
                  {t('common.cancel')}
                </button>
                <button type="submit" className="btn-primary" disabled={grantMutation.isPending}>
                  {grantMutation.isPending ? t('common.saving') : t('tempAccess.grant')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
