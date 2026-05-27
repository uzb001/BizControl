'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Sidebar from '@/components/layout/Sidebar';
import TopBar from '@/components/layout/TopBar';
import CommandCenter from '@/components/ui/CommandCenter';
import PreviewRoleBanner from '@/components/ui/PreviewRoleBanner';
import { useAuthStore } from '@/store/authStore';
import { useSidebarStore } from '@/store/sidebarStore';
import { cn } from '@/lib/utils';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const { collapsed, mobileOpen, closeMobile } = useSidebarStore();

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) return null;

  return (
    <div className="h-screen overflow-hidden">
      <Sidebar />

      {/* Mobile drawer backdrop */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/50 backdrop-blur-sm lg:hidden"
          onClick={closeMobile}
          aria-hidden="true"
        />
      )}

      {/* Content shell — full width on mobile, offset by the sidebar on desktop */}
      <div
        className={cn(
          'flex flex-col h-screen overflow-hidden transition-[margin] duration-200 w-full',
          collapsed
            ? 'lg:ml-14 lg:w-[calc(100%-3.5rem)]'
            : 'lg:ml-60 lg:w-[calc(100%-15rem)]'
        )}
      >
        <TopBar />
        <CommandCenter />
        <PreviewRoleBanner />
        <main
          className="flex-1 overflow-y-auto p-4 sm:p-6"
          style={{ backgroundColor: 'rgb(var(--color-bg))' }}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
