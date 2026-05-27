'use client';

import { usePermission } from '@/hooks/usePermission';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { ShieldOff } from 'lucide-react';

interface PermissionGuardProps {
  /** The permission code(s) to check. If multiple, ALL must be satisfied (use anyOf for OR logic). */
  require?: string;
  /** True if any one of these codes is enough. */
  anyOf?: string[];
  /** If true, redirects to /access-denied instead of rendering inline block. */
  redirect?: boolean;
  children: React.ReactNode;
  /** Optional fallback UI instead of the default blocked card. */
  fallback?: React.ReactNode;
}

export default function PermissionGuard({
  require: requireCode,
  anyOf,
  redirect = false,
  children,
  fallback,
}: PermissionGuardProps) {
  const { can, canAny } = usePermission();
  const router = useRouter();

  const allowed = requireCode
    ? can(requireCode)
    : anyOf
    ? canAny(...anyOf)
    : true;

  useEffect(() => {
    if (!allowed && redirect) {
      router.push('/access-denied');
    }
  }, [allowed, redirect, router]);

  if (allowed) return <>{children}</>;
  if (redirect) return null;

  if (fallback) return <>{fallback}</>;

  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="w-14 h-14 rounded-2xl bg-red-100 flex items-center justify-center mb-4">
        <ShieldOff size={26} className="text-red-500" />
      </div>
      <h3 className="text-base font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
        Access Denied
      </h3>
      <p className="text-sm mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>
        You don't have permission to view this section.
      </p>
    </div>
  );
}
