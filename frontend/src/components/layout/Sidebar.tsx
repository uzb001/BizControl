'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/store/authStore';
import { useSidebarStore } from '@/store/sidebarStore';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';
import {
  LayoutDashboard,
  Package,
  FolderOpen,
  Warehouse,
  Users,
  Truck,
  ShoppingCart,
  PackageSearch,
  Banknote,
  Building2,
  ClipboardList,
  BarChart3,
  AlertTriangle,
  TrendingDown,
  Star,
  Lock,
  Upload,
  Shield,
  UserCog,
  Settings,
  ChevronLeft,
  ChevronRight,
  KeyRound,
  Lightbulb,
  CheckSquare,
  Timer,
  Scale,
  Factory,
  FlaskConical,
  Globe2,
  Ship,
  X,
} from 'lucide-react';

interface NavItem {
  href: string;
  labelKey: string;
  icon: React.ReactNode;
  exact?: boolean;
  /** Permission code required to see this item. Absent = always visible. */
  permission?: string;
}

const mainNav: NavItem[] = [
  { href: '/dashboard',  labelKey: 'nav.dashboard',  icon: <LayoutDashboard size={16} />, exact: true, permission: 'dashboard.view' },
  { href: '/products',   labelKey: 'nav.products',   icon: <Package size={16} />,         permission: 'products.view' },
  { href: '/categories', labelKey: 'nav.categories', icon: <FolderOpen size={16} />,      permission: 'products.view' },
  { href: '/stock',      labelKey: 'nav.stock',      icon: <Warehouse size={16} />,       permission: 'stock.view' },
  { href: '/customers',  labelKey: 'nav.customers',  icon: <Users size={16} />,           permission: 'customers.view' },
  { href: '/suppliers',  labelKey: 'nav.suppliers',  icon: <Truck size={16} />,           permission: 'suppliers.view' },
  { href: '/countries',  labelKey: 'nav.countries',  icon: <Globe2 size={16} />,          permission: 'countries.view' },
  { href: '/sales',      labelKey: 'nav.sales',      icon: <ShoppingCart size={16} />,    permission: 'sales.view' },
  { href: '/purchases',  labelKey: 'nav.purchases',  icon: <PackageSearch size={16} />,   permission: 'purchases.view' },
  { href: '/logistics',  labelKey: 'nav.logistics',  icon: <Ship size={16} />,            permission: 'logistics.view' },
  { href: '/cashbox',    labelKey: 'nav.cashbox',    icon: <Banknote size={16} />,        permission: 'cashbox.view' },
  { href: '/bank',       labelKey: 'nav.bank',       icon: <Building2 size={16} />,       permission: 'bank.view' },
  { href: '/debts',      labelKey: 'nav.debts',      icon: <ClipboardList size={16} />,   permission: 'debts.view_customer' },
];

const productionNav: NavItem[] = [
  { href: '/production',        labelKey: 'nav.production',       icon: <Factory size={16} />,      exact: true, permission: 'production.view' },
  { href: '/production/orders', labelKey: 'nav.productionOrders', icon: <ClipboardList size={16} />, permission: 'production.view' },
  { href: '/production/bom',    labelKey: 'nav.bom',             icon: <FlaskConical size={16} />,  permission: 'bom.view' },
];

const analyticsNav: NavItem[] = [
  { href: '/reports',                 labelKey: 'nav.reports',       icon: <BarChart3 size={16} />,    exact: true, permission: 'reports.view' },
  { href: '/accounting',              labelKey: 'nav.accounting',    icon: <Scale size={16} />,        permission: 'reports.view' },
  { href: '/reports/dead-stock',      labelKey: 'nav.deadStock',     icon: <AlertTriangle size={16} />,permission: 'reports.view_dead_stock' },
  { href: '/reports/money-leak',      labelKey: 'nav.moneyLeak',     icon: <TrendingDown size={16} />, permission: 'reports.view_money_leak' },
  { href: '/reports/customer-scores', labelKey: 'nav.trustScores',   icon: <Star size={16} />,         permission: 'reports.view_customer_scores' },
  { href: '/advisor',                 labelKey: 'nav.advisor',       icon: <Lightbulb size={16} />,    permission: 'reports.view' },
  { href: '/daily-close',             labelKey: 'nav.dailyClose',    icon: <Lock size={16} />,         permission: 'daily_close.view' },
  { href: '/import',                  labelKey: 'nav.importData',    icon: <Upload size={16} />,       permission: 'import.view' },
];

const adminNav: NavItem[] = [
  { href: '/approvals',    labelKey: 'nav.approvals',    icon: <CheckSquare size={16} />, permission: 'approvals.view' },
  { href: '/temp-access',  labelKey: 'nav.tempAccess',  icon: <Timer size={16} />,      permission: 'users.change_role' },
  { href: '/audit',        labelKey: 'nav.audit',        icon: <Shield size={16} />,     permission: 'audit.view' },
  { href: '/audit/access', labelKey: 'nav.accessLog',    icon: <Lock size={16} />,       permission: 'audit.view' },
  { href: '/roles',        labelKey: 'nav.roles',        icon: <KeyRound size={16} />,   permission: 'roles.view' },
  { href: '/roles/matrix', labelKey: 'nav.accessMatrix', icon: <LayoutDashboard size={16} />, permission: 'roles.view' },
  { href: '/users',     labelKey: 'nav.users',     icon: <UserCog size={16} />,     permission: 'users.view' },
  { href: '/settings',  labelKey: 'nav.settings',  icon: <Settings size={16} />,    permission: 'settings.view' },
];

function NavLink({
  item,
  pathname,
  collapsed,
  onNavigate,
}: {
  item: NavItem;
  pathname: string;
  collapsed: boolean;
  onNavigate?: () => void;
}) {
  const { t } = useTranslation();
  const active = item.exact
    ? pathname === item.href
    : pathname === item.href || pathname.startsWith(item.href + '/');

  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      title={collapsed ? t(item.labelKey) : undefined}
      className={cn(
        'flex items-center gap-3 rounded-lg text-sm transition-all duration-150 group relative',
        collapsed ? 'px-2.5 py-2.5 justify-center' : 'px-3 py-2',
        active
          ? 'bg-blue-600 text-white shadow-sm'
          : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'
      )}
    >
      <span className={cn('shrink-0 transition-colors', active ? 'text-white' : 'text-slate-500 group-hover:text-slate-300')}>
        {item.icon}
      </span>
      {!collapsed && (
        <span className="truncate font-medium leading-none">{t(item.labelKey)}</span>
      )}
      {collapsed && (
        <span className="absolute left-full ml-2 px-2 py-1 bg-slate-800 text-white text-xs rounded-md
                         opacity-0 pointer-events-none group-hover:opacity-100 whitespace-nowrap z-50 shadow-lg
                         translate-x-1 group-hover:translate-x-0 transition-all duration-150">
          {t(item.labelKey)}
        </span>
      )}
    </Link>
  );
}

export default function Sidebar() {
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { t } = useTranslation();
  const { collapsed, toggle, mobileOpen, closeMobile } = useSidebarStore();
  const { can } = usePermission();

  // Detect tablet/mobile so the off-canvas drawer always renders full labels
  // (the icon-only "collapsed" mode is a desktop-only affordance).
  const [isMobile, setIsMobile] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 1023px)');
    const update = () => setIsMobile(mq.matches);
    update();
    mq.addEventListener('change', update);
    return () => mq.removeEventListener('change', update);
  }, []);
  const showCollapsed = collapsed && !isMobile;

  /** A nav item is visible if it has no permission requirement OR the user has it. */
  const visible = (item: NavItem) => !item.permission || can(item.permission);

  const visibleMain       = mainNav.filter(visible);
  const visibleProduction = productionNav.filter(visible);
  const visibleAnalytics  = analyticsNav.filter(visible);
  const visibleAdmin      = adminNav.filter(visible);

  return (
    <aside
      className={cn(
        'fixed inset-y-0 left-0 z-50 flex flex-col transition-transform duration-200 bg-slate-900',
        // Width: full-label drawer on mobile, icon/expanded on desktop
        showCollapsed ? 'w-60 lg:w-14' : 'w-60',
        // Off-canvas on mobile, always visible on desktop
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
        'lg:translate-x-0'
      )}
      style={{ borderRight: '1px solid rgba(255,255,255,0.06)' }}
    >
      {/* Logo */}
      <div className={cn(
        'h-16 flex items-center border-b shrink-0',
        showCollapsed ? 'px-2.5 justify-center' : 'px-4 gap-3',
        'border-white/5'
      )}>
        <div className="w-8 h-8 bg-blue-600 rounded-xl flex items-center justify-center shrink-0 shadow-lg shadow-blue-600/30">
          <span className="text-white font-bold text-sm">B</span>
        </div>
        {!showCollapsed && (
          <div className="flex-1 min-w-0">
            <div className="font-bold text-white text-sm leading-tight">BizControl</div>
            <div className="text-xs text-slate-500 truncate">{user?.companyName}</div>
          </div>
        )}
        {/* Mobile drawer close button */}
        <button onClick={closeMobile} className="lg:hidden text-slate-400 hover:text-white p-1 -mr-1" aria-label="Close menu">
          <X size={18} />
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto overflow-x-hidden py-3 px-2 space-y-0.5">
        {/* Main */}
        {visibleMain.map(item => (
          <NavLink key={item.href} item={item} pathname={pathname} collapsed={showCollapsed} onNavigate={closeMobile} />
        ))}

        {/* Production */}
        {visibleProduction.length > 0 && (
          <>
            <div className={cn('pt-4 pb-1', showCollapsed ? 'hidden' : '')}>
              <p className="text-xs text-slate-600 uppercase tracking-widest px-3 font-semibold select-none">
                {t('nav.productionGroup')}
              </p>
            </div>
            {showCollapsed && <div className="my-2 border-t border-white/5 mx-1" />}
            {visibleProduction.map(item => (
              <NavLink key={item.href} item={item} pathname={pathname} collapsed={showCollapsed} onNavigate={closeMobile} />
            ))}
          </>
        )}

        {/* Analytics */}
        {visibleAnalytics.length > 0 && (
          <>
            <div className={cn('pt-4 pb-1', showCollapsed ? 'hidden' : '')}>
              <p className="text-xs text-slate-600 uppercase tracking-widest px-3 font-semibold select-none">
                {t('nav.analytics')}
              </p>
            </div>
            {showCollapsed && <div className="my-2 border-t border-white/5 mx-1" />}
            {visibleAnalytics.map(item => (
              <NavLink key={item.href} item={item} pathname={pathname} collapsed={showCollapsed} onNavigate={closeMobile} />
            ))}
          </>
        )}

        {/* Admin */}
        {visibleAdmin.length > 0 && (
          <>
            <div className={cn('pt-4 pb-1', showCollapsed ? 'hidden' : '')}>
              <p className="text-xs text-slate-600 uppercase tracking-widest px-3 font-semibold select-none">
                {t('nav.admin')}
              </p>
            </div>
            {showCollapsed && <div className="my-2 border-t border-white/5 mx-1" />}
            {visibleAdmin.map(item => (
              <NavLink key={item.href} item={item} pathname={pathname} collapsed={showCollapsed} onNavigate={closeMobile} />
            ))}
          </>
        )}
      </nav>

      {/* User info + Collapse toggle */}
      <div className={cn(
        'border-t border-white/5 shrink-0',
        showCollapsed ? 'p-2 flex flex-col items-center gap-2' : 'px-3 py-3'
      )}>
        {!showCollapsed && (
          <div className="flex items-center gap-3 mb-2">
            <div className="w-7 h-7 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-semibold shrink-0">
              {user?.fullName?.charAt(0)?.toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold text-white truncate">{user?.fullName}</div>
              <div className="text-xs text-slate-500">{user?.role}</div>
            </div>
          </div>
        )}
        {showCollapsed && (
          <div className="w-7 h-7 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-semibold">
            {user?.fullName?.charAt(0)?.toUpperCase()}
          </div>
        )}
        {/* Collapse toggle is a desktop-only affordance */}
        <button
          onClick={toggle}
          className={cn(
            'hidden lg:flex items-center justify-center rounded-lg text-slate-500 hover:text-slate-300 hover:bg-white/5 transition-colors',
            showCollapsed ? 'w-8 h-8' : 'w-full py-1.5 gap-2 text-xs'
          )}
          title={showCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {showCollapsed ? <ChevronRight size={14} /> : (
            <>
              <ChevronLeft size={14} />
              <span>Collapse</span>
            </>
          )}
        </button>
      </div>
    </aside>
  );
}
