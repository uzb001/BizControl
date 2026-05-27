'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AlertTriangle, RotateCcw, LayoutDashboard } from 'lucide-react';

/**
 * Route-segment error boundary for the authenticated app area.
 * Replaces Next.js's default white "Application error" screen with a safe,
 * branded fallback whenever any page in (app) throws during render.
 */
export default function AppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const router = useRouter();

  useEffect(() => {
    // eslint-disable-next-line no-console
    console.error('[App segment error]', error);
  }, [error]);

  return (
    <div className="min-h-[60vh] flex items-center justify-center p-6">
      <div className="card p-8 max-w-md w-full flex flex-col items-center text-center gap-4">
        <div className="w-14 h-14 rounded-full bg-red-50 dark:bg-red-500/10 flex items-center justify-center">
          <AlertTriangle size={26} className="text-red-500" />
        </div>
        <div>
          <h1 className="text-lg font-bold" style={{ color: 'rgb(var(--color-text-primary))' }}>
            Something went wrong
          </h1>
          <p className="text-sm mt-2" style={{ color: 'rgb(var(--color-text-muted))' }}>
            This page hit an unexpected error. Your data is safe — try reloading the
            section or go back to the dashboard.
          </p>
        </div>
        <div className="flex items-center gap-2 w-full justify-center">
          <button onClick={() => reset()} className="btn-primary btn-sm gap-1.5">
            <RotateCcw size={14} /> Try again
          </button>
          <button onClick={() => router.push('/dashboard')} className="btn-secondary btn-sm gap-1.5">
            <LayoutDashboard size={14} /> Dashboard
          </button>
        </div>
      </div>
    </div>
  );
}
