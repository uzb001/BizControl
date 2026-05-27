import { create } from 'zustand';
import Cookies from 'js-cookie';

export interface CompanyChoice {
  companyId: number;
  companyName: string;
  role: string;
  roleId: number | null;
}

export interface AuthUser {
  userId: number;
  fullName: string;
  email?: string;
  phone?: string;
  companyId: number;
  companyName: string;
  role: string;        // role code e.g. "OWNER", "ADMIN", "SELLER"
  roleId?: number | null;
  permissions?: string[];  // loaded at login; ["*"] for OWNER
}

/** Lightweight role snapshot used during "Preview as Role" mode. */
export interface PreviewRole {
  id: number;
  code: string;
  name: string;
  permissions: string[];  // explicit permission codes (no "*")
}

interface AuthStore {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  /** Populated when user belongs to multiple companies — before a company is selected */
  companies: CompanyChoice[];

  /** Non-null while the CEO/ADMIN is previewing the app as a different role. */
  previewRole: PreviewRole | null;

  setAuth: (user: AuthUser, token: string, remember?: boolean) => void;
  setCompanies: (userId: number, fullName: string, email: string | undefined, phone: string | undefined, companies: CompanyChoice[]) => void;
  clearAuth: () => void;

  /** Enter preview mode — the `can()` function will use these permissions. */
  setPreviewRole: (role: PreviewRole) => void;
  /** Exit preview mode — restores the real user's permissions. */
  exitPreview: () => void;

  /** Check if the current user has the given permission code.
   *  In preview mode, checks the preview role's explicit permissions instead.
   *  OWNER (permissions = ["*"]) always returns true (unless in preview mode). */
  can: (permCode: string) => boolean;
}

function loadUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const raw = Cookies.get('user');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

export const useAuthStore = create<AuthStore>((set, get) => ({
  user:            loadUser(),
  token:           typeof window !== 'undefined' ? Cookies.get('token') || null : null,
  isAuthenticated: typeof window !== 'undefined' ? !!Cookies.get('token') : false,
  companies:       [],
  previewRole:     null,

  setAuth: (user, token, remember = false) => {
    // Clear any stale token/permissions first so a new login never inherits old state.
    Cookies.remove('token');
    Cookies.remove('user');
    // "Remember me" extends the session cookie from 1 day to 30 days.
    const expires = remember ? 30 : 1;
    Cookies.set('token', token, { expires, sameSite: 'lax' });
    Cookies.set('user',  JSON.stringify(user), { expires, sameSite: 'lax' });
    set({ user, token, isAuthenticated: true, companies: [], previewRole: null });
  },

  setCompanies: (_userId, _fullName, _email, _phone, companies) => {
    // No token yet — user must pick a company
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      companies,
      previewRole: null,
    });
  },

  clearAuth: () => {
    Cookies.remove('token');
    Cookies.remove('user');
    set({ user: null, token: null, isAuthenticated: false, companies: [], previewRole: null });
  },

  setPreviewRole: (role: PreviewRole) => {
    set({ previewRole: role });
  },

  exitPreview: () => {
    set({ previewRole: null });
  },

  can: (permCode: string) => {
    const { user, previewRole } = get();
    if (!user) return false;

    // If we are in preview mode, check only the preview role's permissions
    if (previewRole) {
      return previewRole.permissions.includes(permCode);
    }

    const perms = user.permissions ?? [];
    if (perms.includes('*')) return true;   // OWNER shortcut
    return perms.includes(permCode);
  },
}));
