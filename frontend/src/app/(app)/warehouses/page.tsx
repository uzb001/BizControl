'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

/**
 * Warehouse management now lives inside the Stock page as tabs.
 * This route is kept only to redirect any old links/bookmarks.
 */
export default function WarehousesRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace('/stock?tab=warehouses');
  }, [router]);
  return (
    <div className="flex items-center justify-center py-24 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
      Redirecting to Stock → Warehouses…
    </div>
  );
}
