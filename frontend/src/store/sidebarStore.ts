import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SidebarStore {
  /** Desktop icon-only collapse (persisted). */
  collapsed: boolean;
  setCollapsed: (v: boolean) => void;
  toggle: () => void;
  /** Mobile/tablet off-canvas drawer open state (transient, not persisted). */
  mobileOpen: boolean;
  openMobile: () => void;
  closeMobile: () => void;
  toggleMobile: () => void;
}

export const useSidebarStore = create<SidebarStore>()(
  persist(
    (set) => ({
      collapsed: false,
      setCollapsed: (collapsed) => set({ collapsed }),
      toggle: () => set((s) => ({ collapsed: !s.collapsed })),
      mobileOpen: false,
      openMobile: () => set({ mobileOpen: true }),
      closeMobile: () => set({ mobileOpen: false }),
      toggleMobile: () => set((s) => ({ mobileOpen: !s.mobileOpen })),
    }),
    {
      name: 'bizcontrol_sidebar',
      // Only persist the desktop collapse preference — never the drawer state.
      partialize: (s) => ({ collapsed: s.collapsed }),
    }
  )
);
