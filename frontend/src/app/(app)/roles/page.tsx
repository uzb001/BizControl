'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { rolesApi, permissionsApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import {
  Plus, Trash2, Edit2, KeyRound, Check, ChevronDown, ChevronRight,
  Shield, ShieldCheck, ShieldOff, Users, Save, X, AlertTriangle,
} from 'lucide-react';
import { cn } from '@/lib/utils';

// ── Types ────────────────────────────────────────────────────────────
interface Permission {
  id: number;
  code: string;
  groupName: string;
  description: string;
}

interface Role {
  id: number;
  name: string;
  code: string;
  description: string;
  isSystem: boolean;
  color: string;
  permissionCodes: string[];
  permissions: Permission[];
}

// ── Risk detection ────────────────────────────────────────────────────
const RISK_COMBOS: { perms: string[]; label: string; severity: 'high' | 'medium' }[] = [
  {
    perms: ['users.change_role', 'roles.assign_permissions'],
    label: '🔺 Privilege Escalation — can assign themselves any permission',
    severity: 'high',
  },
  {
    perms: ['cashbox.create_expense', 'cashbox.export', 'reports.view_profit'],
    label: '💸 Unaudited Cash Control — can drain cash & hide it in exports',
    severity: 'high',
  },
  {
    perms: ['sales.cancel', 'sales.edit', 'sales.view_profit'],
    label: '📊 Sales Manipulation — can edit, cancel and track profit unsupervised',
    severity: 'medium',
  },
  {
    perms: ['purchases.create', 'purchases.cancel', 'bank.create'],
    label: '🏦 Fake Purchase Risk — can create and cancel purchases with bank payments',
    severity: 'medium',
  },
];

function getRiskWarnings(permCodes: string[]): typeof RISK_COMBOS {
  return RISK_COMBOS.filter(combo =>
    combo.perms.every(p => permCodes.includes(p))
  );
}

// ── Role color presets ───────────────────────────────────────────────
const COLOR_PRESETS = [
  '#7c3aed','#2563eb','#0891b2','#16a34a',
  '#d97706','#9333ea','#db2777','#64748b','#ef4444',
];

// ── Helpers ──────────────────────────────────────────────────────────
const GROUP_LABELS: Record<string, string> = {
  dashboard: 'Dashboard', products: 'Products', stock: 'Stock',
  sales: 'Sales', purchases: 'Purchases', customers: 'Customers',
  suppliers: 'Suppliers', cashbox: 'Cashbox', bank: 'Bank',
  debts: 'Debts', reports: 'Reports', import: 'Import',
  export: 'Export', users: 'Users', roles: 'Roles',
  settings: 'Settings', audit: 'Audit Log', daily_close: 'Daily Close',
};

// ═══════════════════════════════════════════════════════════════════
export default function RolesPage() {
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();

  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [editingRole,    setEditingRole]    = useState<Role | null>(null);
  const [showCreate,     setShowCreate]     = useState(false);
  const [deleteTarget,   setDeleteTarget]   = useState<Role | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({});

  // New role form
  const [newName,        setNewName]        = useState('');
  const [newCode,        setNewCode]        = useState('');
  const [newDesc,        setNewDesc]        = useState('');
  const [newColor,       setNewColor]       = useState('#2563eb');
  const [newPermIds,     setNewPermIds]     = useState<Set<number>>(new Set());

  // Edit mode permissions (live set while editing)
  const [editPermIds,    setEditPermIds]    = useState<Set<number>>(new Set());

  // ── Queries ──
  const { data: roles = [], isLoading: loadingRoles } = useQuery<Role[]>({
    queryKey: ['roles'],
    queryFn: () => rolesApi.list().then(r => r.data),
  });

  const { data: grouped = {} } = useQuery<Record<string, Permission[]>>({
    queryKey: ['permissions-grouped'],
    queryFn: () => permissionsApi.grouped().then(r => r.data),
  });

  const selectedRole = roles.find(r => r.id === selectedRoleId) ?? null;

  // ── Mutations ──
  const createMut = useMutation({
    mutationFn: () => rolesApi.create({
      name: newName, code: newCode, description: newDesc,
      color: newColor, permissionIds: [...newPermIds],
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] });
      toast.success(t('roles.created'));
      setShowCreate(false);
      setNewName(''); setNewCode(''); setNewDesc('');
      setNewColor('#2563eb'); setNewPermIds(new Set());
    },
    onError: (e: any) => toast.error(e.response?.data?.message ?? t('errors.serverError')),
  });

  const updateMut = useMutation({
    mutationFn: () => rolesApi.update(editingRole!.id, {
      name: editingRole!.name,
      description: editingRole!.description,
      color: editingRole!.color,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] });
      toast.success(t('roles.updated'));
      setEditingRole(null);
    },
    onError: (e: any) => toast.error(e.response?.data?.message ?? t('errors.serverError')),
  });

  const setPermMut = useMutation({
    mutationFn: (roleId: number) => rolesApi.setPermissions(roleId, [...editPermIds]),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] });
      toast.success(t('roles.permissionsUpdated'));
    },
    onError: (e: any) => toast.error(e.response?.data?.message ?? t('errors.serverError')),
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => rolesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['roles'] });
      toast.success(t('roles.deleted'));
      setDeleteTarget(null);
      if (selectedRoleId === deleteTarget?.id) setSelectedRoleId(null);
    },
    onError: (e: any) => toast.error(e.response?.data?.message ?? t('errors.serverError')),
  });

  // ── Group helpers ──
  const toggleGroup = (g: string) =>
    setExpandedGroups(prev => ({ ...prev, [g]: !prev[g] }));

  const groupExpanded = (g: string) => expandedGroups[g] !== false; // default expanded

  // Toggle a permission in a set (immutable)
  const togglePerm = (
    permId: number,
    set: Set<number>,
    setter: (s: Set<number>) => void
  ) => {
    const next = new Set(set);
    next.has(permId) ? next.delete(permId) : next.add(permId);
    setter(next);
  };

  // Select/deselect all in a group
  const toggleGroupAll = (
    perms: Permission[],
    set: Set<number>,
    setter: (s: Set<number>) => void
  ) => {
    const allIn = perms.every(p => set.has(p.id));
    const next = new Set(set);
    if (allIn) { perms.forEach(p => next.delete(p.id)); }
    else        { perms.forEach(p => next.add(p.id));    }
    setter(next);
  };

  // Open a role for permission editing
  const openRole = (role: Role) => {
    setSelectedRoleId(role.id);
    setEditPermIds(new Set(role.permissions.map(p => p.id)));
    setEditingRole(null);
  };

  // ── Render ──
  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold" style={{ color: 'rgb(var(--color-text-primary))' }}>
            {t('roles.title')}
          </h1>
          <p className="text-sm mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>
            {t('roles.subtitle')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <ExportButton permission="users.export" filename="roles" fetcher={() => exportApi.roles()} />
          {can('roles.create') && (
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
            >
              <Plus size={15} />
              {t('roles.createRole')}
            </button>
          )}
        </div>
      </div>

      <div className="flex gap-5">
        {/* ── Left panel: Role list ─────────────────────────────── */}
        <div className="w-72 shrink-0 space-y-2">
          {loadingRoles && (
            <div className="text-sm text-center py-10" style={{ color: 'rgb(var(--color-text-muted))' }}>
              {t('common.loading')}
            </div>
          )}
          {roles.map(role => {
            const risks = role.code === 'OWNER' ? [] : getRiskWarnings(role.permissions?.map(p => p.code) ?? []);
            const hasHighRisk = risks.some(r => r.severity === 'high');
            const hasMediumRisk = risks.some(r => r.severity === 'medium');
            return (
              <button
                key={role.id}
                onClick={() => openRole(role)}
                className={cn(
                  'w-full text-left px-4 py-3 rounded-xl border transition-all',
                  selectedRoleId === role.id
                    ? 'border-blue-500 bg-blue-50 shadow-sm'
                    : hasHighRisk
                    ? 'border-red-300 bg-red-50/30 hover:border-red-400'
                    : hasMediumRisk
                    ? 'border-amber-300 bg-amber-50/30 hover:border-amber-400'
                    : 'border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))] hover:border-blue-300'
                )}
              >
                <div className="flex items-center gap-2.5">
                  <div
                    className="w-3 h-3 rounded-full shrink-0"
                    style={{ backgroundColor: role.color || '#6366f1' }}
                  />
                  <span className="text-sm font-semibold truncate" style={{ color: 'rgb(var(--color-text-primary))' }}>
                    {role.name}
                  </span>
                  <div className="ml-auto flex items-center gap-1 shrink-0">
                    {hasHighRisk && (
                      <span title="High-risk permission combination" className="text-[10px] px-1.5 py-0.5 rounded bg-red-100 text-red-600 font-bold">
                        ⚠ HIGH
                      </span>
                    )}
                    {!hasHighRisk && hasMediumRisk && (
                      <span title="Medium-risk permission combination" className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 font-bold">
                        ⚡ MED
                      </span>
                    )}
                    {role.isSystem && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 font-medium">
                        {t('roles.system')}
                      </span>
                    )}
                  </div>
                </div>
                <p className="text-xs mt-1 truncate" style={{ color: 'rgb(var(--color-text-muted))' }}>
                  {role.description || role.code}
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>
                  {role.permissions.length} {t('roles.permissions')}
                </p>
              </button>
            );
          })}
        </div>

        {/* ── Right panel: Permission matrix ───────────────────── */}
        <div className="flex-1 min-w-0">
          {!selectedRole ? (
            <div className="flex flex-col items-center justify-center h-64 rounded-2xl border-2 border-dashed"
                 style={{ borderColor: 'rgb(var(--color-border))', color: 'rgb(var(--color-text-muted))' }}>
              <KeyRound size={32} className="mb-3 opacity-30" />
              <p className="text-sm">{t('roles.selectRole')}</p>
            </div>
          ) : (
            <div className="card-elevated rounded-2xl overflow-hidden">
              {/* Role header */}
              <div className="px-5 py-4 border-b flex items-center justify-between"
                   style={{ borderColor: 'rgb(var(--color-border))' }}>
                {editingRole?.id === selectedRole.id ? (
                  <div className="flex-1 flex flex-col gap-2 mr-4">
                    <input
                      value={editingRole.name}
                      onChange={e => setEditingRole({ ...editingRole, name: e.target.value })}
                      className="w-full text-sm px-3 py-1.5 rounded-lg border border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))]"
                      placeholder={t('roles.roleName')}
                    />
                    <input
                      value={editingRole.description ?? ''}
                      onChange={e => setEditingRole({ ...editingRole, description: e.target.value })}
                      className="w-full text-xs px-3 py-1.5 rounded-lg border border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))]"
                      placeholder={t('common.description')}
                    />
                    <div className="flex gap-1.5 mt-1">
                      {COLOR_PRESETS.map(c => (
                        <button
                          key={c}
                          onClick={() => setEditingRole({ ...editingRole, color: c })}
                          className="w-5 h-5 rounded-full border-2 transition-transform hover:scale-110"
                          style={{
                            backgroundColor: c,
                            borderColor: editingRole.color === c ? '#fff' : 'transparent',
                            outline: editingRole.color === c ? `2px solid ${c}` : 'none',
                          }}
                        />
                      ))}
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                         style={{ backgroundColor: selectedRole.color + '22' }}>
                      <Shield size={18} style={{ color: selectedRole.color }} />
                    </div>
                    <div>
                      <h2 className="text-sm font-bold" style={{ color: 'rgb(var(--color-text-primary))' }}>
                        {selectedRole.name}
                      </h2>
                      <p className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                        {selectedRole.description || selectedRole.code}
                      </p>
                    </div>
                  </div>
                )}

                <div className="flex items-center gap-2 shrink-0">
                  {can('roles.assign_permissions') && selectedRole.code !== 'OWNER' && (
                    <button
                      onClick={() => setPermMut.mutate(selectedRole.id)}
                      disabled={setPermMut.isPending}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-blue-600 text-white hover:bg-blue-700 transition-colors disabled:opacity-50"
                    >
                      <Save size={13} />
                      {setPermMut.isPending ? t('common.saving') : t('roles.savePermissions')}
                    </button>
                  )}
                  {can('roles.edit') && !selectedRole.isSystem && (
                    editingRole?.id === selectedRole.id ? (
                      <div className="flex gap-1">
                        <button
                          onClick={() => updateMut.mutate()}
                          disabled={updateMut.isPending}
                          className="p-1.5 rounded-lg bg-green-600 text-white hover:bg-green-700 transition-colors"
                        >
                          <Check size={13} />
                        </button>
                        <button
                          onClick={() => setEditingRole(null)}
                          className="p-1.5 rounded-lg text-[rgb(var(--color-text-muted))] hover:bg-[rgb(var(--color-border-subtle))] transition-colors"
                        >
                          <X size={13} />
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setEditingRole({ ...selectedRole })}
                        className="p-1.5 rounded-lg text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))] transition-colors"
                        title={t('common.edit')}
                      >
                        <Edit2 size={14} />
                      </button>
                    )
                  )}
                  {can('roles.delete') && !selectedRole.isSystem && (
                    <button
                      onClick={() => setDeleteTarget(selectedRole)}
                      className="p-1.5 rounded-lg text-red-500 hover:bg-red-50 transition-colors"
                      title={t('common.delete')}
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              </div>

              {/* Risk warning banner — live as permissions are edited */}
              {selectedRole.code !== 'OWNER' && (() => {
                // Use live editPermIds if editing, otherwise fall back to saved permissions
                const activeCodes = editPermIds.size > 0
                  ? Object.values(grouped).flat().filter(p => editPermIds.has(p.id)).map(p => p.code)
                  : selectedRole.permissions?.map(p => p.code) ?? [];
                const risks = getRiskWarnings(activeCodes);
                if (risks.length === 0) return null;
                return (
                  <div className="mx-5 mt-4 mb-0 space-y-2">
                    {risks.map((risk, i) => (
                      <div
                        key={i}
                        className={cn(
                          'px-4 py-3 rounded-xl border flex items-start gap-2.5',
                          risk.severity === 'high'
                            ? 'bg-red-50 border-red-200'
                            : 'bg-amber-50 border-amber-200'
                        )}
                      >
                        <AlertTriangle
                          size={16}
                          className={cn('shrink-0 mt-0.5', risk.severity === 'high' ? 'text-red-500' : 'text-amber-500')}
                        />
                        <div>
                          <p className={cn('text-xs font-semibold', risk.severity === 'high' ? 'text-red-700' : 'text-amber-700')}>
                            {risk.severity === 'high' ? 'High-Risk Combination' : 'Medium-Risk Combination'}
                          </p>
                          <p className={cn('text-xs mt-0.5', risk.severity === 'high' ? 'text-red-600' : 'text-amber-600')}>
                            {risk.label}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })()}

              {/* OWNER full-access banner */}
              {selectedRole.code === 'OWNER' && (
                <div className="mx-5 mt-4 mb-2 px-4 py-3 rounded-xl bg-purple-50 border border-purple-200 flex items-center gap-2.5">
                  <ShieldCheck size={18} className="text-purple-600 shrink-0" />
                  <p className="text-sm text-purple-700 font-medium">
                    {t('roles.ownerFullAccess')}
                  </p>
                </div>
              )}

              {/* Permission matrix */}
              <div className={cn('px-5 pb-5 space-y-3', selectedRole.code === 'OWNER' ? 'opacity-50 pointer-events-none mt-2' : 'mt-4')}>
                {Object.entries(grouped).map(([group, perms]) => {
                  const expanded = groupExpanded(group);
                  const allChecked = perms.every(p => editPermIds.has(p.id));
                  const someChecked = perms.some(p => editPermIds.has(p.id));

                  return (
                    <div key={group} className="rounded-xl border overflow-hidden"
                         style={{ borderColor: 'rgb(var(--color-border))' }}>
                      {/* Group header */}
                      <div
                        className="flex items-center gap-3 px-4 py-2.5 cursor-pointer select-none"
                        style={{ backgroundColor: 'rgb(var(--color-surface-raised))' }}
                        onClick={() => toggleGroup(group)}
                      >
                        <button
                          onClick={e => {
                            e.stopPropagation();
                            toggleGroupAll(perms, editPermIds, setEditPermIds);
                          }}
                          className={cn(
                            'w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 transition-colors',
                            allChecked
                              ? 'bg-blue-600 border-blue-600'
                              : someChecked
                              ? 'bg-blue-200 border-blue-400'
                              : 'border-slate-300 bg-white'
                          )}
                        >
                          {(allChecked || someChecked) && <Check size={10} className="text-white" />}
                        </button>
                        <span className="text-xs font-semibold uppercase tracking-wide flex-1"
                              style={{ color: 'rgb(var(--color-text-secondary))' }}>
                          {GROUP_LABELS[group] ?? group}
                        </span>
                        <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                          {perms.filter(p => editPermIds.has(p.id)).length}/{perms.length}
                        </span>
                        {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                      </div>

                      {/* Permission rows */}
                      {expanded && (
                        <div className="divide-y" style={{ borderColor: 'rgb(var(--color-border))' }}>
                          {perms.map(perm => (
                            <label
                              key={perm.id}
                              className="flex items-center gap-3 px-4 py-2.5 cursor-pointer hover:bg-[rgb(var(--color-border-subtle))] transition-colors"
                            >
                              <div
                                className={cn(
                                  'w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 transition-colors',
                                  editPermIds.has(perm.id) ? 'bg-blue-600 border-blue-600' : 'border-slate-300 bg-white'
                                )}
                                onClick={() => togglePerm(perm.id, editPermIds, setEditPermIds)}
                              >
                                {editPermIds.has(perm.id) && <Check size={10} className="text-white" />}
                              </div>
                              <div className="flex-1 min-w-0">
                                <span className="text-xs font-mono text-blue-600">{perm.code}</span>
                                {perm.description && (
                                  <span className="ml-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                                    — {perm.description}
                                  </span>
                                )}
                              </div>
                            </label>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Create Role Modal ─────────────────────────────────────── */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-lg card-elevated rounded-2xl shadow-2xl overflow-hidden">
            <div className="px-5 py-4 border-b flex items-center justify-between"
                 style={{ borderColor: 'rgb(var(--color-border))' }}>
              <h3 className="font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
                {t('roles.createRole')}
              </h3>
              <button onClick={() => setShowCreate(false)} className="text-[rgb(var(--color-text-muted))] hover:text-[rgb(var(--color-text-secondary))]">
                <X size={16} />
              </button>
            </div>
            <div className="p-5 space-y-4 max-h-[70vh] overflow-y-auto">
              {/* Basic info */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium mb-1" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                    {t('roles.roleName')} *
                  </label>
                  <input
                    value={newName}
                    onChange={e => setNewName(e.target.value)}
                    className="w-full text-sm px-3 py-2 rounded-lg border border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))]"
                    placeholder="e.g. Store Manager"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium mb-1" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                    {t('roles.roleCode')} *
                  </label>
                  <input
                    value={newCode}
                    onChange={e => setNewCode(e.target.value.toUpperCase().replace(/\s/g, '_'))}
                    className="w-full text-sm px-3 py-2 rounded-lg border border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))] font-mono"
                    placeholder="STORE_MGR"
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                  {t('common.description')}
                </label>
                <input
                  value={newDesc}
                  onChange={e => setNewDesc(e.target.value)}
                  className="w-full text-sm px-3 py-2 rounded-lg border border-[rgb(var(--color-border))] bg-[rgb(var(--color-surface))]"
                />
              </div>
              {/* Color */}
              <div>
                <label className="block text-xs font-medium mb-2" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                  {t('roles.color')}
                </label>
                <div className="flex gap-2 flex-wrap">
                  {COLOR_PRESETS.map(c => (
                    <button
                      key={c}
                      onClick={() => setNewColor(c)}
                      className="w-6 h-6 rounded-full border-2 transition-transform hover:scale-110"
                      style={{
                        backgroundColor: c,
                        borderColor: newColor === c ? '#fff' : 'transparent',
                        outline: newColor === c ? `2px solid ${c}` : 'none',
                      }}
                    />
                  ))}
                </div>
              </div>
              {/* Permissions */}
              <div>
                <label className="block text-xs font-medium mb-2" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                  {t('roles.assignPermissions')} ({newPermIds.size} {t('roles.selected')})
                </label>
                <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
                  {Object.entries(grouped).map(([group, perms]) => (
                    <div key={group} className="rounded-lg border overflow-hidden"
                         style={{ borderColor: 'rgb(var(--color-border))' }}>
                      <div
                        className="flex items-center gap-2 px-3 py-2 cursor-pointer"
                        style={{ backgroundColor: 'rgb(var(--color-surface-raised))' }}
                        onClick={() => toggleGroupAll(perms, newPermIds, setNewPermIds)}
                      >
                        <div className={cn(
                          'w-4 h-4 rounded border-2 flex items-center justify-center shrink-0',
                          perms.every(p => newPermIds.has(p.id)) ? 'bg-blue-600 border-blue-600' :
                          perms.some(p => newPermIds.has(p.id))  ? 'bg-blue-200 border-blue-400' :
                          'border-slate-300 bg-white'
                        )}>
                          {(perms.some(p => newPermIds.has(p.id))) && <Check size={10} className="text-white" />}
                        </div>
                        <span className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                          {GROUP_LABELS[group] ?? group}
                        </span>
                        <span className="ml-auto text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                          {perms.filter(p => newPermIds.has(p.id)).length}/{perms.length}
                        </span>
                      </div>
                      <div className="px-3 py-1.5 flex flex-wrap gap-x-4 gap-y-1">
                        {perms.map(perm => (
                          <label key={perm.id} className="flex items-center gap-1.5 cursor-pointer py-0.5">
                            <div
                              className={cn(
                                'w-3.5 h-3.5 rounded border-2 flex items-center justify-center shrink-0',
                                newPermIds.has(perm.id) ? 'bg-blue-600 border-blue-600' : 'border-slate-300 bg-white'
                              )}
                              onClick={() => togglePerm(perm.id, newPermIds, setNewPermIds)}
                            >
                              {newPermIds.has(perm.id) && <Check size={9} className="text-white" />}
                            </div>
                            <span className="text-[11px] font-mono text-blue-600">{perm.code}</span>
                          </label>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
            {/* Footer */}
            <div className="px-5 py-4 border-t flex justify-end gap-2"
                 style={{ borderColor: 'rgb(var(--color-border))' }}>
              <button
                onClick={() => setShowCreate(false)}
                className="px-4 py-2 rounded-lg text-sm border border-[rgb(var(--color-border))] text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => createMut.mutate()}
                disabled={!newName || !newCode || createMut.isPending}
                className="px-4 py-2 rounded-lg text-sm bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {createMut.isPending ? t('common.saving') : t('roles.createRole')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Delete Confirmation ──────────────────────────────────── */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm card-elevated rounded-2xl shadow-2xl p-6">
            <div className="flex items-start gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center shrink-0">
                <AlertTriangle size={18} className="text-red-600" />
              </div>
              <div>
                <h3 className="font-semibold text-sm" style={{ color: 'rgb(var(--color-text-primary))' }}>
                  {t('roles.deleteTitle')}
                </h3>
                <p className="text-sm mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>
                  {t('roles.deleteConfirm', { name: deleteTarget.name })}
                </p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleteTarget(null)}
                className="px-3 py-2 rounded-lg text-sm border border-[rgb(var(--color-border))] text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => deleteMut.mutate(deleteTarget.id)}
                disabled={deleteMut.isPending}
                className="px-3 py-2 rounded-lg text-sm bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
              >
                {deleteMut.isPending ? t('common.loading') : t('common.delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
