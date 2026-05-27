import { cn, getStatusBadgeClass, getStockStatusClass, getStockStatus } from '@/lib/utils';

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={cn(getStatusBadgeClass(status))}>
      {status}
    </span>
  );
}

export function StockBadge({
  current, min,
  currentStock, minStock,
}: {
  current?: number; min?: number;
  currentStock?: number; minStock?: number;
}) {
  const c = current ?? currentStock ?? 0;
  const m = min ?? minStock ?? 0;
  return (
    <span className={cn(getStockStatusClass(c, m))}>
      {getStockStatus(c, m)}
    </span>
  );
}

export function PaymentBadge({
  paid, total,
  status,
}: {
  paid?: number; total?: number;
  status?: string;
}) {
  if (status) {
    return <span className={cn(getStatusBadgeClass(status))}>{status}</span>;
  }
  const unpaid = (total ?? 0) - (paid ?? 0);
  if (unpaid <= 0) return <span className="badge-green">Paid</span>;
  if ((paid ?? 0) > 0) return <span className="badge-yellow">Partial</span>;
  return <span className="badge-red">Unpaid</span>;
}
