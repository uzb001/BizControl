'use client';

import { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import { useAuthStore, PreviewRole } from '@/store/authStore';
import { useThemeStore } from '@/store/themeStore';
import { useSidebarStore } from '@/store/sidebarStore';
import { authApi, alertsApi, rolesApi } from '@/lib/api';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { LANGUAGES } from '@/lib/i18n';
import {
  Bell, Sun, Moon, Monitor, ChevronDown, Settings, LogOut,
  Globe, Search, Building2, CheckCircle2, Eye, Menu,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function TopBar() {
  const router = useRouter();
  const { user, clearAuth, setAuth, previewRole, setPreviewRole, exitPreview } = useAuthStore();
  const { theme, setTheme } = useThemeStore();
  const { openMobile } = useSidebarStore();
  const { t, i18n } = useTranslation();
  const [userMenuOpen,    setUserMenuOpen]    = useState(false);
  const [langMenuOpen,    setLangMenuOpen]    = useState(false);
  const [themeMenuOpen,   setThemeMenuOpen]   = useState(false);
  const [companyMenuOpen, setCompanyMenuOpen] = useState(false);
  const [previewMenuOpen, setPreviewMenuOpen] = useState(false);
  const userMenuRef    = useRef<HTMLDivElement>(null);
  const langMenuRef    = useRef<HTMLDivElement>(null);
  const companyMenuRef = useRef<HTMLDivElement>(null);

  // Only OWNER and ADMIN can use Preview as Role
  const canPreview = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const { data: rolesData = [] } = useQuery({
    queryKey: ['roles-for-preview', user?.companyId],
    queryFn: () => rolesApi.list().then(r => r.data as Array<{ id: number; code: string; name: string }>),
    enabled: canPreview && previewMenuOpen,
    staleTime: 60_000,
  });

  const handlePreviewRole = async (roleId: number, roleName: string, roleCode: string) => {
    try {
      const res = await rolesApi.get(roleId);
      const roleDetail = res.data as { permissionCodes?: string[]; permissions?: Array<{ code: string }> };
      // Backend returns permissionCodes as a Set<string>
      const perms: string[] = roleDetail.permissionCodes
        ? Array.from(roleDetail.permissionCodes as unknown as string[])
        : (roleDetail.permissions ?? []).map((p: { code: string }) => p.code);
      const preview: PreviewRole = { id: roleId, code: roleCode, name: roleName, permissions: perms };
      setPreviewRole(preview);
      setPreviewMenuOpen(false);
      setUserMenuOpen(false);
      toast.success(`Previewing as ${roleName}`);
    } catch {
      toast.error('Failed to load role permissions');
    }
  };

  const { data: alertCount } = useQuery({
    queryKey: ['alertCount'],
    queryFn: () => alertsApi.count().then(r => r.data.count),
    refetchInterval: 30_000,
  });

  // Fetch company list for the switcher
  const { data: companiesData } = useQuery({
    queryKey: ['myCompanies'],
    queryFn: () => authApi.companies().then(r => r.data),
    enabled: companyMenuOpen,
    staleTime: 60_000,
  });

  const switchCompanyMut = useMutation({
    mutationFn: (companyId: number) => authApi.selectCompany(companyId).then(r => r.data),
    onSuccess: (data) => {
      setAuth({
        userId:      data.userId,
        fullName:    data.fullName,
        email:       data.email,
        phone:       data.phone,
        companyId:   data.companyId,
        companyName: data.companyName,
        role:        data.role,
        roleId:      data.roleId,
        permissions: data.permissions ? [...data.permissions] : [],
      }, data.token);
      setCompanyMenuOpen(false);
      toast.success(t('topBar.switchedTo', { name: data.companyName }));
      router.push('/dashboard');
    },
    onError: () => toast.error(t('errors.serverError')),
  });

  const handleLogout = async () => {
    try { await authApi.logout(); } catch (_) {}
    clearAuth();
    router.push('/login');
    toast.success(t('common.logout'));
  };

  // Close menus on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (userMenuRef.current    && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
        setPreviewMenuOpen(false);
      }
      if (langMenuRef.current    && !langMenuRef.current.contains(e.target as Node))    setLangMenuOpen(false);
      if (companyMenuRef.current && !companyMenuRef.current.contains(e.target as Node)) setCompanyMenuOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const currentLang = LANGUAGES.find(l => l.code === i18n.language) ?? LANGUAGES[0];
  const themeIcon = theme === 'dark' ? <Moon size={15} /> : theme === 'light' ? <Sun size={15} /> : <Monitor size={15} />;

  const companies: Array<{ companyId: number; companyName: string; role: string }> = companiesData ?? [];

  return (
    <header
      className="h-14 flex items-center justify-between px-5 sticky top-0 z-30"
      style={{
        backgroundColor: 'rgb(var(--color-surface))',
        borderBottom: '1px solid rgb(var(--color-border))',
        boxShadow: '0 1px 0 0 rgb(var(--color-border))',
      }}
    >
      {/* Left: mobile menu + Command Center trigger */}
      <div className="flex-1 flex items-center gap-2">
        <button
          onClick={openMobile}
          className="lg:hidden flex items-center justify-center w-9 h-9 rounded-lg text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]"
          aria-label="Open menu"
        >
          <Menu size={18} />
        </button>
        <button
          onClick={() => {
            const evt = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true });
            document.dispatchEvent(evt);
          }}
          className={cn(
            'hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm transition-colors',
            'text-[rgb(var(--color-text-muted))] bg-[rgb(var(--color-border-subtle))] hover:bg-[rgb(var(--color-border))]'
          )}
        >
          <Search size={13} />
          <span className="text-xs">{t('commandCenter.triggerLabel')}</span>
          <kbd className="ml-1 px-1 py-0.5 text-[9px] bg-[rgb(var(--color-border))] rounded border border-[rgb(var(--color-border))]">
            Ctrl+K
          </kbd>
        </button>
      </div>

      {/* Right controls */}
      <div className="flex items-center gap-1">

        {/* Company switcher */}
        <div className="relative" ref={companyMenuRef}>
          <button
            onClick={() => { setCompanyMenuOpen(o => !o); setUserMenuOpen(false); setLangMenuOpen(false); setThemeMenuOpen(false); }}
            className={cn(
              'hidden sm:flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-colors',
              'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
            )}
            title={t('topBar.switchCompany')}
          >
            <Building2 size={14} />
            <span className="hidden md:block max-w-24 truncate">{user?.companyName}</span>
            <ChevronDown size={11} />
          </button>
          {companyMenuOpen && (
            <div className="absolute right-0 top-full mt-1.5 w-56 card-elevated shadow-xl py-1 z-50 overflow-hidden">
              <p className="px-3 pt-2 pb-1 text-xs font-semibold uppercase tracking-wider"
                 style={{ color: 'rgb(var(--color-text-muted))' }}>
                {t('topBar.myCompanies')}
              </p>
              {companies.length === 0 && (
                <p className="px-3 py-2 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
                  {t('common.loading')}
                </p>
              )}
              {companies.map((c) => (
                <button
                  key={c.companyId}
                  disabled={c.companyId === user?.companyId || switchCompanyMut.isPending}
                  onClick={() => switchCompanyMut.mutate(c.companyId)}
                  className={cn(
                    'w-full text-left px-3 py-2.5 text-sm flex items-center gap-2.5 transition-colors',
                    c.companyId === user?.companyId
                      ? 'font-semibold text-blue-600'
                      : 'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
                  )}
                >
                  <div className="w-6 h-6 rounded-lg bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold shrink-0">
                    {c.companyName.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="truncate text-xs font-medium">{c.companyName}</div>
                    <div className="text-[10px]" style={{ color: 'rgb(var(--color-text-muted))' }}>{c.role}</div>
                  </div>
                  {c.companyId === user?.companyId && (
                    <CheckCircle2 size={14} className="text-blue-600 shrink-0" />
                  )}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Language switcher */}
        <div className="relative" ref={langMenuRef}>
          <button
            onClick={() => { setLangMenuOpen(o => !o); setUserMenuOpen(false); setThemeMenuOpen(false); setCompanyMenuOpen(false); }}
            className={cn(
              'flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-colors',
              'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
            )}
          >
            <Globe size={14} />
            <span className="hidden sm:block">{currentLang.flag} {currentLang.code.toUpperCase()}</span>
          </button>
          {langMenuOpen && (
            <div className="absolute right-0 top-full mt-1.5 w-40 card-elevated shadow-xl py-1 z-50 overflow-hidden">
              {LANGUAGES.map(lang => (
                <button
                  key={lang.code}
                  onClick={() => { i18n.changeLanguage(lang.code); setLangMenuOpen(false); }}
                  className={cn(
                    'w-full text-left px-3 py-2 text-sm flex items-center gap-2.5 transition-colors',
                    i18n.language === lang.code
                      ? 'text-blue-600 font-semibold bg-blue-50'
                      : 'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
                  )}
                >
                  <span>{lang.flag}</span>
                  <span>{lang.label}</span>
                  {i18n.language === lang.code && (
                    <span className="ml-auto w-1.5 h-1.5 rounded-full bg-blue-600" />
                  )}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Theme toggle */}
        <div className="relative">
          <button
            onClick={() => { setThemeMenuOpen(o => !o); setUserMenuOpen(false); setLangMenuOpen(false); setCompanyMenuOpen(false); }}
            className={cn(
              'flex items-center justify-center w-8 h-8 rounded-lg transition-colors',
              'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
            )}
            title="Theme"
          >
            {themeIcon}
          </button>
          {themeMenuOpen && (
            <div className="absolute right-0 top-full mt-1.5 w-36 card-elevated shadow-xl py-1 z-50 overflow-hidden">
              {(
                [
                  { value: 'light',  label: 'Light',  icon: <Sun size={14} /> },
                  { value: 'dark',   label: 'Dark',   icon: <Moon size={14} /> },
                  { value: 'system', label: 'System', icon: <Monitor size={14} /> },
                ] as const
              ).map(opt => (
                <button
                  key={opt.value}
                  onClick={() => { setTheme(opt.value); setThemeMenuOpen(false); }}
                  className={cn(
                    'w-full text-left px-3 py-2 text-sm flex items-center gap-2.5 transition-colors',
                    theme === opt.value
                      ? 'text-blue-600 font-semibold bg-blue-50'
                      : 'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
                  )}
                >
                  {opt.icon}
                  <span>{opt.label}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Alert bell */}
        <button
          onClick={() => router.push('/dashboard')}
          className={cn(
            'relative flex items-center justify-center w-8 h-8 rounded-lg transition-colors',
            'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
          )}
          title="Alerts"
        >
          <Bell size={15} />
          {alertCount && alertCount > 0 ? (
            <span className="absolute top-0.5 right-0.5 w-4 h-4 bg-red-500 text-white text-[9px] rounded-full flex items-center justify-center font-bold leading-none">
              {alertCount > 9 ? '9+' : alertCount}
            </span>
          ) : null}
        </button>

        {/* User menu */}
        <div className="relative ml-1" ref={userMenuRef}>
          <button
            onClick={() => { setUserMenuOpen(o => !o); setLangMenuOpen(false); setThemeMenuOpen(false); setCompanyMenuOpen(false); }}
            className={cn(
              'flex items-center gap-2 px-2 py-1.5 rounded-lg transition-colors',
              'hover:bg-[rgb(var(--color-border-subtle))]'
            )}
          >
            <div className="w-7 h-7 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-semibold">
              {user?.fullName?.charAt(0)?.toUpperCase()}
            </div>
            <span
              className="text-sm font-medium hidden sm:block max-w-28 truncate"
              style={{ color: 'rgb(var(--color-text-primary))' }}
            >
              {user?.fullName}
            </span>
            <ChevronDown size={12} style={{ color: 'rgb(var(--color-text-muted))' }} />
          </button>

          {userMenuOpen && (
            <div className="absolute right-0 top-full mt-1.5 w-64 card-elevated shadow-xl py-1 z-50 overflow-hidden">
              <div className="px-4 py-3 border-b" style={{ borderColor: 'rgb(var(--color-border))' }}>
                <p className="text-sm font-semibold" style={{ color: 'rgb(var(--color-text-primary))' }}>
                  {user?.fullName}
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'rgb(var(--color-text-muted))' }}>
                  {previewRole ? (
                    <span className="text-amber-600 font-semibold inline-flex items-center gap-1">
                      <Eye size={12} /> Previewing as {previewRole.name}
                    </span>
                  ) : (
                    <>{user?.role} · {user?.companyName}</>
                  )}
                </p>
              </div>

              {/* Preview as Role — only for OWNER / ADMIN */}
              {canPreview && (
                <div className="border-b" style={{ borderColor: 'rgb(var(--color-border))' }}>
                  {previewRole ? (
                    <button
                      onClick={() => { exitPreview(); setUserMenuOpen(false); toast.success('Exited preview mode'); }}
                      className="w-full text-left px-4 py-2.5 text-sm flex items-center gap-2.5 transition-colors text-amber-600 hover:bg-amber-50"
                    >
                      <Eye size={14} />
                      Exit Role Preview
                    </button>
                  ) : (
                    <div className="relative">
                      <button
                        onClick={() => setPreviewMenuOpen(o => !o)}
                        className={cn(
                          'w-full text-left px-4 py-2.5 text-sm flex items-center gap-2.5 transition-colors',
                          'text-violet-600 hover:bg-violet-50'
                        )}
                      >
                        <Eye size={14} />
                        Preview as Role
                        <ChevronDown size={11} className="ml-auto" />
                      </button>
                      {previewMenuOpen && (
                        <div
                          className="absolute right-full top-0 mr-1 w-48 card-elevated shadow-xl py-1 z-50 overflow-hidden"
                        >
                          <p className="px-3 pt-2 pb-1 text-[10px] font-semibold uppercase tracking-wider"
                             style={{ color: 'rgb(var(--color-text-muted))' }}>
                            Select role to preview
                          </p>
                          {rolesData
                            .filter(r => r.code !== 'OWNER' && r.code !== user?.role)
                            .map(r => (
                              <button
                                key={r.id}
                                onClick={() => handlePreviewRole(r.id, r.name, r.code)}
                                className={cn(
                                  'w-full text-left px-3 py-2 text-sm transition-colors',
                                  'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
                                )}
                              >
                                {r.name}
                                <span className="ml-1 text-[10px] opacity-50">({r.code})</span>
                              </button>
                            ))}
                          {rolesData.filter(r => r.code !== 'OWNER' && r.code !== user?.role).length === 0 && (
                            <p className="px-3 py-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
                              No other roles available
                            </p>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              <button
                onClick={() => { router.push('/settings'); setUserMenuOpen(false); }}
                className={cn(
                  'w-full text-left px-4 py-2.5 text-sm flex items-center gap-2.5 transition-colors',
                  'text-[rgb(var(--color-text-secondary))] hover:bg-[rgb(var(--color-border-subtle))]'
                )}
              >
                <Settings size={14} />
                {t('common.settings')}
              </button>
              <button
                onClick={handleLogout}
                className="w-full text-left px-4 py-2.5 text-sm flex items-center gap-2.5 transition-colors text-red-600 hover:bg-red-50"
              >
                <LogOut size={14} />
                {t('common.logout')}
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
