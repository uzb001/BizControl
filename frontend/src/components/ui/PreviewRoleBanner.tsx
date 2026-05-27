'use client';

import { Eye, X } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';

/**
 * Shown as a sticky banner at the top of the main content area
 * while the user is previewing the app through another role's lens.
 */
export default function PreviewRoleBanner() {
  const { previewRole, exitPreview } = useAuthStore();

  if (!previewRole) return null;

  return (
    <div
      className="sticky top-0 z-40 flex items-center justify-between gap-3 px-5 py-2 text-sm font-medium"
      style={{
        backgroundColor: 'rgb(234 179 8)',   // amber-400
        color: 'rgb(28 25 23)',              // stone-900
      }}
    >
      <div className="flex items-center gap-2">
        <Eye size={15} />
        <span>
          Previewing as <strong>{previewRole.name}</strong> ({previewRole.code})
          — your actions are read-only and use the role&apos;s permissions.
        </span>
      </div>
      <button
        onClick={exitPreview}
        className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold transition-colors"
        style={{ backgroundColor: 'rgba(0,0,0,0.15)' }}
        title="Exit preview mode"
      >
        <X size={13} />
        Exit Preview
      </button>
    </div>
  );
}
