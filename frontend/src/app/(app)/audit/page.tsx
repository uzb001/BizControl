'use client';

import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { auditApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { useTranslation } from 'react-i18next';
import { ShieldCheck, RotateCcw } from 'lucide-react';

export default function AuditPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [actionType, setActionType] = useState('');
  const [entityType, setEntityType] = useState('');

  const { data: logs, isLoading } = useQuery({
    queryKey: ['audit', page, actionType, entityType],
    queryFn: () => auditApi.list({ page, size: 50, actionType, entityType }).then(r => r.data),
  });

  const ACTION_TYPES = ['CREATE', 'UPDATE', 'DELETE', 'DEACTIVATE', 'CANCEL', 'LOGIN', 'PAYMENT', 'STOCK_ADJUST'];
  const ENTITY_TYPES = ['Product', 'Sale', 'Purchase', 'Customer', 'Supplier', 'CashTransaction', 'Debt', 'User'];

  const items = useMemo(() => logs?.content ?? [], [logs]);

  const badgeClass = (a: string) =>
    a === 'CREATE' ? 'badge-green'
    : a === 'DELETE' || a === 'CANCEL' ? 'badge-red'
    : a === 'LOGIN' ? 'badge-blue'
    : 'badge-yellow';

  const columns: GridColumn<any>[] = [
    { key: 'createdAt', header: t('audit.dateTime'), width: 175, render: (l) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{new Date(l.createdAt).toLocaleString('en-GB')}</span> },
    { key: 'userId', header: t('audit.userId'), width: 90, render: (l) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>#{l.userId}</span> },
    { key: 'actionType', header: t('audit.action'), width: 150, groupable: true, groupValue: (l) => l.actionType, render: (l) => <span className={`${badgeClass(l.actionType)} text-xs`}>{l.actionType}</span> },
    { key: 'entityType', header: t('audit.entity'), width: 150, groupable: true, groupValue: (l) => l.entityType, render: (l) => <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>{l.entityType}</span> },
    { key: 'entityId', header: t('audit.entityId'), width: 90, render: (l) => <span className="text-xs font-mono" style={{ color: 'rgb(var(--color-text-muted))' }}>#{l.entityId}</span> },
    { key: 'change', header: t('audit.change'), width: 360, render: (l) => (
        <span className="text-xs">
          {l.oldValue && <span className="text-red-500 dark:text-red-400">{l.oldValue}</span>}
          {l.oldValue && l.newValue && <span className="mx-1" style={{ color: 'rgb(var(--color-text-muted))' }}>→</span>}
          {l.newValue && <span className="text-green-600 dark:text-green-400">{l.newValue}</span>}
        </span>
      ) },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">{t('audit.title')}</h1>
        <ExportButton permission="audit.export" filename="audit_logs" fetcher={() => exportApi.auditLogs()} />
      </div>

      <div className="filter-bar">
        <select className="filter-input" value={actionType} onChange={e => { setActionType(e.target.value); setPage(0); }}>
          <option value="">{t('audit.allActions')}</option>
          {ACTION_TYPES.map(a => <option key={a} value={a}>{a}</option>)}
        </select>
        <select className="filter-input" value={entityType} onChange={e => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">{t('audit.allEntities')}</option>
          {ENTITY_TYPES.map(e => <option key={e} value={e}>{e}</option>)}
        </select>
        <button onClick={() => { setActionType(''); setEntityType(''); setPage(0); }} className="btn-ghost btn-sm gap-1"><RotateCcw size={13} /> {t('common.reset')}</button>
      </div>

      <DataGrid
        gridId="audit"
        columns={columns}
        rows={items}
        getRowId={(l: any) => l.id}
        loading={isLoading}
        groupable
        emptyState={<EmptyState icon={ShieldCheck} title={t('audit.title')} />}
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={logs?.totalPages} totalElements={logs?.totalElements} size={50} onChange={setPage} />
      )}
    </div>
  );
}
