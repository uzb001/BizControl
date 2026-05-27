'use client';

import { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { logisticsApi } from '@/lib/api';
import { formatMoney, formatDateTime } from '@/lib/utils';
import { usePermission } from '@/hooks/usePermission';
import Pagination from '@/components/ui/Pagination';
import EmptyState from '@/components/ui/EmptyState';
import { DataGrid, type GridColumn } from '@/components/datagrid';
import { Ship, Plus, FileDown } from 'lucide-react';

/** List of logistics orders. Permission-gated by logistics.view. */
export default function LogisticsListPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();
  const [page, setPage] = useState(0);

  const { data: orders, isLoading } = useQuery({
    queryKey: ['logistics', page],
    queryFn: () => logisticsApi.list({ page, size: 20 }).then(r => r.data),
    retry: false,
  });
  const canSeeLanded = can('logistics.view_landed_cost');

  const items = useMemo(() => orders?.content ?? [], [orders]);

  const columns: GridColumn<any>[] = [
    { key: 'orderNumber', header: t('logistics.orderNumber', { defaultValue: 'Order #' }), width: 180, render: (o) => (
        <button onClick={() => router.push(`/logistics/${o.id}`)} className="font-mono text-sm hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>{o.orderNumber}</button>
      ) },
    { key: 'createdAt', header: t('common.date', { defaultValue: 'Date' }), width: 160,
      render: (o) => <span className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{formatDateTime(o.createdAt)}</span> },
    { key: 'currency', header: t('common.currency', { defaultValue: 'Currency' }), width: 90, groupable: true, groupValue: (o) => o.currency,
      render: (o) => <span className="text-sm font-mono">{o.currency}</span> },
    { key: 'itemsValue', header: t('logistics.itemsValue', { defaultValue: 'Items' }), width: 130, numeric: true,
      render: (o) => <span className="num">{formatMoney(o.itemsValue, o.currency)}</span> },
    { key: 'expensesTotal', header: t('logistics.expensesTotal', { defaultValue: 'Expenses' }), width: 130, numeric: true,
      render: (o) => <span className="num text-orange-600 dark:text-orange-400">{formatMoney(o.expensesTotal, o.currency)}</span> },
    ...(canSeeLanded ? [{
      key: 'landedTotal', header: t('logistics.landedTotal', { defaultValue: 'Landed total' }), width: 150, numeric: true,
      render: (o: any) => <span className="num font-semibold">{formatMoney(o.landedTotal, o.currency)}</span>,
    } as GridColumn<any>] : []),
    { key: 'status', header: t('common.status', { defaultValue: 'Status' }), width: 110, groupable: true, groupValue: (o) => o.status,
      render: (o) => <span className={o.status === 'confirmed' ? 'badge-green' : o.status === 'cancelled' ? 'badge-red' : 'badge-gray'}>{o.status}</span> },
  ];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title flex items-center gap-2"><Ship size={22} /> {t('logistics.title', { defaultValue: 'Logistics' })}</h1>
        <div className="flex gap-2">
          {can('logistics.export') && (
            <button onClick={async () => {
              const blob = await logisticsApi.exportAll().then(r => r.data);
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a'); a.href = url; a.download = 'logistics_orders.xlsx'; a.click();
              URL.revokeObjectURL(url);
            }} className="btn-secondary btn-sm gap-1.5"><FileDown size={14} /> {t('common.export', { defaultValue: 'Export' })}</button>
          )}
          {can('logistics.create') && (
            <button onClick={() => router.push('/logistics/new')} className="btn-primary btn-sm gap-1.5"><Plus size={14} /> {t('logistics.newOrder', { defaultValue: 'New logistics order' })}</button>
          )}
        </div>
      </div>

      <DataGrid
        gridId="logistics"
        columns={columns}
        rows={items}
        getRowId={(o: any) => o.id}
        loading={isLoading}
        groupable
        onRowOpen={(o: any) => router.push(`/logistics/${o.id}`)}
        emptyState={
          <EmptyState icon={Ship}
            title={t('logistics.emptyTitle', { defaultValue: 'No logistics orders yet' })}
            description={t('logistics.emptyBody', { defaultValue: 'Create your first order to move stock between foreign and local warehouses with landed cost.' })}
            action={can('logistics.create') ? <button onClick={() => router.push('/logistics/new')} className="btn-primary btn-sm">{t('logistics.newOrder', { defaultValue: 'New logistics order' })}</button> : undefined}
          />
        }
      />

      {items.length > 0 && (
        <Pagination page={page} totalPages={orders?.totalPages} totalElements={orders?.totalElements} size={20} onChange={setPage} />
      )}
    </div>
  );
}
