'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { approvalsApi } from '@/lib/api';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import toast from 'react-hot-toast';
import {
  CheckCircle2, XCircle, Clock, AlertTriangle, CheckCheck,
  X, ChevronDown, Filter, TrendingDown, Percent, PackagePlus, Ban, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';

const STATUS_META: Record<ApprovalStatus, { color: string; icon: React.ReactNode }> = {
  pending:   { color: 'badge-yellow', icon: <Clock className="w-3 h-3" /> },
  approved:  { color: 'badge-green',  icon: <CheckCircle2 className="w-3 h-3" /> },
  rejected:  { color: 'badge-red',    icon: <XCircle className="w-3 h-3" /> },
  cancelled: { color: 'badge-gray',   icon: <X className="w-3 h-3" /> },
};

const TRIGGER_META: Record<string, { icon: LucideIcon; label: string }> = {
  SALE_BELOW_COST:    { icon: TrendingDown, label: 'Sale Below Cost' },
  HIGH_DISCOUNT:      { icon: Percent,      label: 'High Discount' },
  LARGE_STOCK_ADJUST: { icon: PackagePlus,  label: 'Large Stock Adjust' },
  SALE_CANCEL:        { icon: Ban,          label: 'Sale Cancel' },
  PURCHASE_CANCEL:    { icon: Ban,          label: 'Purchase Cancel' },
  LARGE_EXPENSE:      { icon: Wallet,       label: 'Large Expense' },
};

export default function ApprovalsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();

  const [statusFilter, setStatusFilter] = useState<string>('pending');
  const [decideModal, setDecideModal] = useState<{ id: number; action: 'approve' | 'reject' } | null>(null);
  const [note, setNote] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['approvals', statusFilter],
    queryFn: () => approvalsApi.list({ status: statusFilter || undefined }).then(r => r.data),
  });

  const approvals = (data as any)?.content ?? data ?? [];

  const approveMutation = useMutation({
    mutationFn: ({ id, note }: { id: number; note: string }) => approvalsApi.approve(id, note),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['approvals'] });
      toast.success(t('approvals.approved'));
      setDecideModal(null);
      setNote('');
    },
    onError: () => toast.error(t('errors.serverError')),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, note }: { id: number; note: string }) => approvalsApi.reject(id, note),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['approvals'] });
      toast.success(t('approvals.rejected'));
      setDecideModal(null);
      setNote('');
    },
    onError: () => toast.error(t('errors.serverError')),
  });

  const handleDecide = () => {
    if (!decideModal) return;
    if (decideModal.action === 'approve') {
      approveMutation.mutate({ id: decideModal.id, note });
    } else {
      rejectMutation.mutate({ id: decideModal.id, note });
    }
  };

  const isPending = approveMutation.isPending || rejectMutation.isPending;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('approvals.title')}</h1>
          <p className="page-subtitle">{t('approvals.subtitle')}</p>
        </div>
      </div>

      {/* Status filter */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {(['pending', 'approved', 'rejected', 'cancelled', ''] as const).map(s => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors border ${
              statusFilter === s
                ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]'
                : 'border-[var(--color-border)] text-[var(--color-text-muted)] hover:bg-[var(--color-border)]'
            }`}
          >
            {s === '' ? t('common.all') : t(`approvals.status_${s}`, s)}
          </button>
        ))}
      </div>

      <div className="card p-0">
        {isLoading ? (
          <div className="p-10 text-center"><LoadingSpinner /></div>
        ) : approvals.length === 0 ? (
          <div className="p-10 text-center text-[var(--color-text-muted)]">
            <CheckCheck className="w-10 h-10 mx-auto mb-2 opacity-30" />
            <p>{t('approvals.empty')}</p>
          </div>
        ) : (
          <div className="divide-y divide-[var(--color-border)]">
            {approvals.map((req: any) => {
              const meta = STATUS_META[req.status as ApprovalStatus] ?? STATUS_META.pending;
              const trigger = TRIGGER_META[req.triggerType];
              const TriggerIcon = trigger?.icon;
              const triggerLabel = trigger?.label ?? req.triggerType;
              return (
                <div key={req.id} className="p-4 hover:bg-[var(--color-surface-hover,var(--color-border)/10)] transition-colors">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        <span className={`badge ${meta.color} flex items-center gap-1`}>
                          {meta.icon}
                          {req.status}
                        </span>
                        <span className="text-sm font-semibold flex items-center gap-1.5" style={{ color: 'rgb(var(--color-text-primary))' }}>
                          {TriggerIcon && <TriggerIcon className="w-3.5 h-3.5" style={{ color: 'rgb(var(--color-primary))' }} />}
                          {triggerLabel}
                        </span>
                        {req.entityType && (
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {req.entityType} #{req.entityId}
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-[var(--color-text-muted)] mt-1">{req.description}</p>
                      {req.decisionNote && (
                        <p className="text-xs text-[var(--color-text-muted)] mt-1 italic">
                          Note: {req.decisionNote}
                        </p>
                      )}
                      <p className="text-xs text-[var(--color-text-muted)] mt-2">
                        {t('approvals.requestedAt')}: {new Date(req.createdAt).toLocaleString()}
                        {req.expiresAt && ` · ${t('approvals.expiresAt')}: ${new Date(req.expiresAt).toLocaleString()}`}
                      </p>
                    </div>
                    {req.status === 'pending' && (
                      <div className="flex gap-2 shrink-0">
                        <button
                          className="btn-icon text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20"
                          title={t('approvals.approve')}
                          onClick={() => { setDecideModal({ id: req.id, action: 'approve' }); setNote(''); }}
                        >
                          <CheckCircle2 className="w-5 h-5" />
                        </button>
                        <button
                          className="btn-icon text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                          title={t('approvals.reject')}
                          onClick={() => { setDecideModal({ id: req.id, action: 'reject' }); setNote(''); }}
                        >
                          <XCircle className="w-5 h-5" />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Decision modal */}
      {decideModal && (
        <div className="modal-overlay" onClick={() => setDecideModal(null)}>
          <div className="modal-content max-w-md" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">
                {decideModal.action === 'approve' ? t('approvals.approveRequest') : t('approvals.rejectRequest')}
              </h2>
              <button className="btn-icon" onClick={() => setDecideModal(null)}>
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="modal-body space-y-4">
              <div>
                <label className="label">{t('approvals.decisionNote')}</label>
                <textarea
                  className="input h-24 resize-none"
                  placeholder={t('approvals.decisionNotePlaceholder')}
                  value={note}
                  onChange={e => setNote(e.target.value)}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setDecideModal(null)}>
                {t('common.cancel')}
              </button>
              <button
                className={`btn-primary ${decideModal.action === 'reject' ? 'bg-red-600 hover:bg-red-700' : ''}`}
                onClick={handleDecide}
                disabled={isPending}
              >
                {decideModal.action === 'approve'
                  ? (isPending ? t('common.saving') : t('approvals.approve'))
                  : (isPending ? t('common.saving') : t('approvals.reject'))}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
