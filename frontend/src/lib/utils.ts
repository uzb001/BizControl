import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatMoney(amount: number | string | undefined, currency = 'UZS'): string {
  if (amount === undefined || amount === null) return `0 ${currency}`;
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  return new Intl.NumberFormat('uz-UZ').format(num) + ' ' + currency;
}

export function formatDate(date: string | undefined): string {
  if (!date) return '';
  return new Date(date).toLocaleDateString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric'
  });
}

export function formatDateTime(date: string | undefined): string {
  if (!date) return '';
  return new Date(date).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

export function getStatusBadgeClass(status: string): string {
  switch (status?.toLowerCase()) {
    case 'active': case 'paid': case 'closed': return 'badge-green';
    case 'inactive': case 'cancelled': return 'badge-gray';
    case 'blocked': case 'risky': case 'overdue': return 'badge-red';
    case 'partial': return 'badge-yellow';
    case 'open': return 'badge-blue';
    default: return 'badge-gray';
  }
}

export function getStockStatusClass(current: number, min: number): string {
  if (current === 0) return 'badge-red';
  if (current <= min) return 'badge-yellow';
  return 'badge-green';
}

export function getStockStatus(current: number, min: number): string {
  if (current === 0) return 'Out of stock';
  if (current <= min) return 'Low stock';
  return 'In stock';
}

export function calcMargin(purchase: number, selling: number): string {
  if (!purchase || purchase === 0) return '0%';
  return (((selling - purchase) / purchase) * 100).toFixed(1) + '%';
}

export function truncate(str: string, len = 30): string {
  return str?.length > len ? str.substring(0, len) + '...' : str;
}

/**
 * Safe value-to-text for rendering in JSX. Never returns a raw object,
 * so it can never trigger React error #31 (objects are not valid as a child).
 */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'object') {
    const o = value as Record<string, unknown>;
    const pick = o.name ?? o.label ?? o.title ?? o.message ?? o.description;
    if (typeof pick === 'string' || typeof pick === 'number') return String(pick);
    try { return JSON.stringify(value); } catch { return '—'; }
  }
  return String(value);
}
