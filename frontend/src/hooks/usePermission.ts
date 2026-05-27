'use client';

import { useAuthStore } from '@/store/authStore';

/**
 * Returns helpers for permission checking anywhere in the component tree.
 *
 * In "Preview as Role" mode, `can()` checks the preview role's permissions
 * and `isOwner()` / `isAdmin()` reflect the preview role, not the real user.
 *
 * Usage:
 *   const { can, isOwner, isAdmin, previewRole } = usePermission();
 *   if (can('sales.create')) { ... }
 */
export function usePermission() {
  const { user, can, previewRole } = useAuthStore();

  /**
   * When in preview mode → treat the preview role as if it were real.
   * OWNER / ADMIN checks reflect the preview role code, not the actual user.
   */
  const isOwner = () => {
    if (previewRole) return previewRole.code === 'OWNER';
    return user?.role === 'OWNER';
  };

  const isAdmin = () => {
    if (previewRole) return previewRole.code === 'ADMIN' || previewRole.code === 'OWNER';
    return user?.role === 'ADMIN' || user?.role === 'OWNER';
  };

  /** True if the user has ALL of the listed permission codes. */
  const canAll = (...codes: string[]) => codes.every(c => can(c));

  /** True if the user has ANY of the listed permission codes. */
  const canAny = (...codes: string[]) => codes.some(c => can(c));

  return { can, canAll, canAny, isOwner, isAdmin, previewRole };
}
