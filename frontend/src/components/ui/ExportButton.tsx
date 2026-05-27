'use client';

import { useState } from 'react';
import toast from 'react-hot-toast';
import { Download, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import { downloadBlob } from '@/lib/api';

interface ExportButtonProps {
  /** Permission code required to see/use the button, e.g. 'sales.export'. */
  permission: string;
  /** Base file name (date is appended) → e.g. 'sales' → sales_2026-05-22.xlsx */
  filename: string;
  /** Returns an axios blob response. Capture current filters in the closure so
   *  the export respects the on-screen search/filters/date-range. */
  fetcher: () => Promise<{ data: Blob }>;
  label?: string;
  size?: 'sm' | 'md';
}

/**
 * One reusable, permission-gated Excel export button used across every list/report.
 * Hidden when the user lacks `permission`; the backend still enforces 403.
 */
export default function ExportButton({ permission, filename, fetcher, label, size = 'sm' }: ExportButtonProps) {
  const { can } = usePermission();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  if (!can(permission)) return null;

  const handleExport = async () => {
    setLoading(true);
    try {
      const res = await fetcher();
      const date = new Date().toISOString().slice(0, 10);
      downloadBlob(res.data, `${filename}_${date}.xlsx`);
      toast.success(t('export.success', { defaultValue: 'Exported to Excel' }));
    } catch (e: any) {
      if (e?.response?.status === 403) {
        toast.error(t('export.denied', { defaultValue: 'You do not have permission to export.' }));
      } else {
        toast.error(t('export.failed', { defaultValue: 'Export failed. Please try again.' }));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <button
      onClick={handleExport}
      disabled={loading}
      className={`btn-secondary ${size === 'sm' ? 'btn-sm' : ''} gap-1.5`}
      title={t('common.export')}
    >
      {loading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
      {label ?? t('common.export')}
    </button>
  );
}
