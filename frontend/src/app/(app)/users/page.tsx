'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { companyApi, rolesApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { StatusBadge } from '@/components/ui/Badge';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import toast from 'react-hot-toast';
import {
  UserPlus, Users, ChevronDown, X, Check,
  UserX, UserCheck, Trash2, AlertTriangle, Copy, Link2,
} from 'lucide-react';

interface BackendRole {
  id: number;
  name: string;
  code: string;
  color: string;
  description: string;
  isSystem: boolean;
}

export default function UsersPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();

  const [showInvite, setShowInvite]       = useState(false);
  const [roleMenuOpen, setRoleMenuOpen]   = useState<number | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<number | null>(null);
  const [inviteResult, setInviteResult]   = useState<{ acceptUrl: string; inviteToken: string } | null>(null);
  const [inviteForm, setInviteForm]       = useState({ email: '', phone: '', roleId: '', fullName: '' });
  const [inviteError, setInviteError]     = useState('');

  // ── Data queries ──────────────────────────────────────────────────
  const { data: users = [], isLoading } = useQuery<any[]>({
    queryKey: ['company-users'],
    queryFn:  () => companyApi.users().then(r => r.data),
  });

  const { data: roles = [] } = useQuery<BackendRole[]>({
    queryKey: ['roles'],
    queryFn:  () => rolesApi.list().then(r => r.data),
  });

  // Roles available for assignment (not OWNER)
  const assignableRoles = roles.filter(r => r.code !== 'OWNER');

  // ── Mutations ─────────────────────────────────────────────────────
  const inviteMutation = useMutation({
    mutationFn: (data: any) => companyApi.inviteUser(data).then(r => r.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['company-users'] });
      setInviteResult({ acceptUrl: data.acceptUrl, inviteToken: data.inviteToken });
      setInviteForm({ email: '', phone: '', roleId: '', fullName: '' });
      setInviteError('');
      toast.success(t('users.invited'));
    },
    onError: (e: any) => {
      setInviteError(e?.response?.data?.message || t('errors.serverError'));
    },
  });

  const roleMutation = useMutation({
    mutationFn: ({ userId, roleId }: { userId: number; roleId: number }) =>
      companyApi.changeRole(userId, roleId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['company-users'] });
      setRoleMenuOpen(null);
      toast.success(t('users.roleChanged'));
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || t('errors.serverError')),
  });

  const deactivateMutation = useMutation({
    mutationFn: (userId: number) => companyApi.deactivateUser(userId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['company-users'] }); toast.success(t('users.deactivated')); },
    onError:   (e: any) => toast.error(e?.response?.data?.message || t('errors.serverError')),
  });

  const activateMutation = useMutation({
    mutationFn: (userId: number) => companyApi.activateUser(userId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['company-users'] }); toast.success(t('users.activated')); },
    onError:   (e: any) => toast.error(e?.response?.data?.message || t('errors.serverError')),
  });

  const removeMutation = useMutation({
    mutationFn: (userId: number) => companyApi.removeUser(userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['company-users'] });
      setConfirmRemove(null);
      toast.success(t('users.removed'));
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || t('errors.serverError')),
  });

  // ── Invite submit ─────────────────────────────────────────────────
  const handleInviteSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setInviteError('');
    if (!inviteForm.email && !inviteForm.phone) {
      setInviteError(t('validation.emailOrPhone'));
      return;
    }
    if (!inviteForm.roleId) {
      setInviteError(t('users.roleRequired'));
      return;
    }
    inviteMutation.mutate({
      email:    inviteForm.email   || undefined,
      phone:    inviteForm.phone   || undefined,
      fullName: inviteForm.fullName || undefined,
      roleId:   Number(inviteForm.roleId),
    });
  };

  // ── Role badge color ──────────────────────────────────────────────
  const getRoleColor = (roleCode: string) => {
    const role = roles.find(r => r.code === roleCode);
    return role?.color ?? '#64748b';
  };

  const getRoleName = (u: any) => u.roleName || u.role || '—';

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('users.title')}</h1>
          <p className="page-subtitle">{t('users.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <ExportButton permission="users.export" filename="users" fetcher={() => exportApi.users()} />
          <button className="btn-primary btn-sm" onClick={() => setShowInvite(true)}>
            <UserPlus className="w-4 h-4" />
            {t('users.invite')}
          </button>
        </div>
      </div>

      {/* Role legend — dynamic from backend */}
      {roles.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3 mb-6">
          {roles.filter(r => r.code !== 'OWNER').map(role => (
            <div key={role.id} className="card p-3">
              <span
                className="badge mb-1.5 text-white text-xs font-semibold"
                style={{ backgroundColor: role.color }}
              >
                {role.code}
              </span>
              <p className="text-xs text-[var(--color-text-muted)] leading-snug line-clamp-2">
                {role.description}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Users table */}
      <div className="card p-0">
        {isLoading ? (
          <div className="p-10 text-center"><LoadingSpinner /></div>
        ) : (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('users.name')}</th>
                  <th>{t('users.contact')}</th>
                  <th>{t('users.role')}</th>
                  <th>{t('users.status')}</th>
                  <th>{t('users.joined')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="text-center py-10 text-[var(--color-text-muted)]">
                      <Users className="w-8 h-8 mx-auto mb-2 opacity-40" />
                      {t('users.noUsers')}
                    </td>
                  </tr>
                ) : users.map((u: any) => (
                  <tr key={u.id}>
                    <td className="font-medium">{u.user?.fullName}</td>
                    <td className="text-sm text-[var(--color-text-muted)]">
                      {u.user?.email || u.user?.phone || '—'}
                    </td>
                    <td>
                      {u.role === 'OWNER' ? (
                        <span
                          className="badge text-white text-xs font-semibold"
                          style={{ backgroundColor: getRoleColor('OWNER') }}
                        >
                          {t('users.roleOwner')}
                        </span>
                      ) : (
                        <div className="relative inline-block">
                          <button
                            className="badge text-white text-xs font-semibold flex items-center gap-1 cursor-pointer"
                            style={{ backgroundColor: getRoleColor(u.role) }}
                            onClick={() => setRoleMenuOpen(roleMenuOpen === u.userId ? null : u.userId)}
                          >
                            {getRoleName(u)}
                            <ChevronDown className="w-3 h-3" />
                          </button>
                          {roleMenuOpen === u.userId && (
                            <div className="absolute top-full left-0 mt-1 z-20 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-lg shadow-lg py-1 min-w-[160px]">
                              {assignableRoles.map(r => (
                                <button
                                  key={r.id}
                                  className="flex items-center gap-2 w-full text-left px-3 py-1.5 text-sm hover:bg-[var(--color-border)] transition-colors"
                                  onClick={() => roleMutation.mutate({ userId: u.userId, roleId: r.id })}
                                  disabled={roleMutation.isPending}
                                >
                                  <span
                                    className="w-2 h-2 rounded-full shrink-0"
                                    style={{ backgroundColor: r.color }}
                                  />
                                  <span className="flex-1">{r.name}</span>
                                  {u.roleId === r.id && <Check className="w-3 h-3 text-green-500 shrink-0" />}
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </td>
                    <td><StatusBadge status={u.status} /></td>
                    <td className="text-sm text-[var(--color-text-muted)]">
                      {u.joinedAt ? new Date(u.joinedAt).toLocaleDateString() : '—'}
                    </td>
                    <td>
                      {u.role !== 'OWNER' && (
                        <div className="flex gap-1">
                          {u.status === 'active' ? (
                            <button
                              className="btn-icon text-orange-500 hover:bg-orange-50 dark:hover:bg-orange-900/20"
                              title={t('users.deactivate')}
                              onClick={() => deactivateMutation.mutate(u.userId)}
                              disabled={deactivateMutation.isPending}
                            >
                              <UserX className="w-4 h-4" />
                            </button>
                          ) : (
                            <button
                              className="btn-icon text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20"
                              title={t('users.activate')}
                              onClick={() => activateMutation.mutate(u.userId)}
                              disabled={activateMutation.isPending}
                            >
                              <UserCheck className="w-4 h-4" />
                            </button>
                          )}
                          <button
                            className="btn-icon text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                            title={t('users.remove')}
                            onClick={() => setConfirmRemove(u.userId)}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Invite Modal ── */}
      {showInvite && (
        <div className="modal-overlay" onClick={() => { setShowInvite(false); setInviteResult(null); setInviteError(''); }}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">{t('users.inviteTitle')}</h2>
              <button className="btn-icon" onClick={() => { setShowInvite(false); setInviteResult(null); setInviteError(''); }}>
                <X className="w-5 h-5" />
              </button>
            </div>

            {inviteResult ? (
              <div className="modal-body space-y-4">
                <div className="flex flex-col items-center text-center py-2">
                  <div className="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center mb-3">
                    <Link2 className="w-5 h-5 text-green-600" />
                  </div>
                  <h3 className="font-semibold text-[var(--color-text)]">{t('users.invited')}</h3>
                  <p className="text-sm text-[var(--color-text-muted)] mt-1">{t('users.copyInviteLink')}</p>
                </div>
                <div className="flex gap-2">
                  <input
                    readOnly
                    className="input font-mono text-xs flex-1"
                    value={`${window.location.origin}${inviteResult.acceptUrl}`}
                  />
                  <button
                    className="btn-secondary shrink-0"
                    onClick={() => {
                      navigator.clipboard.writeText(`${window.location.origin}${inviteResult.acceptUrl}`);
                      toast.success(t('invitations.copied'));
                    }}
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                </div>
                <div className="modal-footer">
                  <button className="btn-primary w-full" onClick={() => { setShowInvite(false); setInviteResult(null); }}>
                    {t('common.close')}
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleInviteSubmit} className="modal-body space-y-4">
                <div>
                  <label className="label">{t('users.fullName')}</label>
                  <input
                    className="input"
                    placeholder={t('users.fullNamePlaceholder')}
                    value={inviteForm.fullName}
                    onChange={e => setInviteForm(p => ({ ...p, fullName: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="label">{t('users.email')}</label>
                  <input
                    className="input"
                    type="email"
                    placeholder="email@example.com"
                    value={inviteForm.email}
                    onChange={e => setInviteForm(p => ({ ...p, email: e.target.value }))}
                  />
                </div>
                <div>
                  <label className="label">{t('users.phone')}</label>
                  <input
                    className="input"
                    type="tel"
                    placeholder="+998 90 123 45 67"
                    value={inviteForm.phone}
                    onChange={e => setInviteForm(p => ({ ...p, phone: e.target.value }))}
                  />
                </div>
                <p className="text-xs text-[var(--color-text-muted)]">{t('users.emailOrPhoneHint')}</p>
                <div>
                  <label className="label">{t('users.role')} <span className="text-red-500">*</span></label>
                  <select
                    className="input"
                    value={inviteForm.roleId}
                    onChange={e => setInviteForm(p => ({ ...p, roleId: e.target.value }))}
                    required
                  >
                    <option value="">— {t('users.selectRole')} —</option>
                    {assignableRoles.map(r => (
                      <option key={r.id} value={r.id}>
                        {r.name} ({r.code})
                      </option>
                    ))}
                  </select>
                  {inviteForm.roleId && (
                    <p className="text-xs text-[var(--color-text-muted)] mt-1">
                      {assignableRoles.find(r => r.id === Number(inviteForm.roleId))?.description}
                    </p>
                  )}
                </div>

                {inviteError && (
                  <div className="p-3 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-600 dark:text-red-400 flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 shrink-0" />
                    {inviteError}
                  </div>
                )}

                <div className="modal-footer">
                  <button type="button" className="btn-secondary" onClick={() => { setShowInvite(false); setInviteError(''); }}>
                    {t('common.cancel')}
                  </button>
                  <button type="submit" className="btn-primary" disabled={inviteMutation.isPending}>
                    <UserPlus className="w-4 h-4" />
                    {inviteMutation.isPending ? t('common.saving') : t('users.sendInvite')}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* ── Confirm Remove Modal ── */}
      {confirmRemove !== null && (
        <div className="modal-overlay" onClick={() => setConfirmRemove(null)}>
          <div className="modal-content max-w-sm" onClick={e => e.stopPropagation()}>
            <div className="modal-body text-center py-6">
              <AlertTriangle className="w-12 h-12 text-red-500 mx-auto mb-3" />
              <h3 className="font-semibold text-[var(--color-text)] text-lg mb-2">{t('users.removeConfirmTitle')}</h3>
              <p className="text-sm text-[var(--color-text-muted)]">{t('users.removeConfirmBody')}</p>
            </div>
            <div className="modal-footer">
              <button className="btn-secondary flex-1" onClick={() => setConfirmRemove(null)}>
                {t('common.cancel')}
              </button>
              <button
                className="btn-primary flex-1 bg-red-600 hover:bg-red-700"
                onClick={() => removeMutation.mutate(confirmRemove)}
                disabled={removeMutation.isPending}
              >
                <Trash2 className="w-4 h-4" />
                {t('users.removeConfirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
