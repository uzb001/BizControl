'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { authApi } from '@/lib/api';
import { useAuthStore, CompanyChoice } from '@/store/authStore';
import {
  Building2, CheckCircle2, ArrowRight, Eye, EyeOff, Mail, Lock,
  ShieldCheck, BarChart3, Zap, ArrowLeft, Loader2,
} from 'lucide-react';

export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [companies, setCompanies] = useState<CompanyChoice[] | null>(null);
  const [selectionToken, setSelectionToken] = useState<string | null>(null);
  const [pickingName, setPickingName] = useState('');
  const [picking, setPicking] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(true);
  const [showForgot, setShowForgot] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm<{ login: string; password: string }>();

  const onSubmit = async (data: any) => {
    setLoading(true);
    try {
      const res = await authApi.login(data);
      const payload = res.data;

      if (payload.token) {
        setAuth({
          userId:      payload.userId,
          fullName:    payload.fullName,
          email:       payload.email,
          phone:       payload.phone,
          companyId:   payload.companyId,
          companyName: payload.companyName,
          role:        payload.role,
          roleId:      payload.roleId,
          permissions: payload.permissions ? [...payload.permissions] : [],
        }, payload.token, remember);
        toast.success('Welcome back, ' + payload.fullName + '!');
        router.push('/dashboard');
      } else if (payload.companies?.length > 0) {
        setCompanies(payload.companies);
        setSelectionToken(payload.selectionToken ?? null);
        setPickingName(payload.fullName);
      } else {
        toast.error('No active company found.');
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  const selectCompany = async (companyId: number) => {
    if (!selectionToken) {
      toast.error('Session expired — please log in again.');
      setCompanies(null);
      return;
    }
    setPicking(true);
    try {
      const res = await authApi.selectCompany(companyId, selectionToken);
      const payload = res.data;
      setAuth({
        userId:      payload.userId,
        fullName:    payload.fullName,
        email:       payload.email,
        phone:       payload.phone,
        companyId:   payload.companyId,
        companyName: payload.companyName,
        role:        payload.role,
        roleId:      payload.roleId,
        permissions: payload.permissions ? [...payload.permissions] : [],
      }, payload.token, remember);
      toast.success('Switched to ' + payload.companyName);
      router.push('/dashboard');
    } catch {
      toast.error('Failed to select company. Please try again.');
    } finally {
      setPicking(false);
    }
  };

  // ── Shared brand panel (left side) ───────────────────────────────────
  const BrandPanel = (
    <div
      className="relative hidden lg:flex flex-col justify-between p-12 overflow-hidden"
      style={{
        background:
          'radial-gradient(120% 120% at 0% 0%, rgba(99,102,241,0.55) 0%, transparent 45%),' +
          'radial-gradient(120% 120% at 100% 100%, rgba(139,92,246,0.45) 0%, transparent 50%),' +
          'linear-gradient(180deg, #1b1f3b 0%, #14152b 100%)',
      }}
    >
      <div
        className="absolute inset-0 pointer-events-none"
        style={{ background: 'radial-gradient(80% 120% at 50% -10%, rgba(255,255,255,0.06) 0%, transparent 60%)' }}
      />
      <div className="relative">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-white/10 backdrop-blur flex items-center justify-center ring-1 ring-white/15">
            <span className="text-white font-bold">B</span>
          </div>
          <span className="font-bold text-white text-lg tracking-tight">BizControl.uz</span>
        </div>
      </div>

      <div className="relative space-y-8">
        <div>
          <h2 className="text-3xl font-bold text-white leading-tight tracking-tight">
            Run your entire business<br />from one calm dashboard.
          </h2>
          <p className="text-white/60 mt-3 text-sm leading-relaxed max-w-sm">
            Inventory, sales, purchases, cash, debts and reports — unified, fast,
            and more powerful than spreadsheets.
          </p>
        </div>
        <div className="space-y-3.5">
          {[
            { icon: BarChart3,   text: 'Real-time profit & cash visibility' },
            { icon: ShieldCheck, text: 'Role-based access & full audit trail' },
            { icon: Zap,         text: 'Approvals, alerts and AI insights built in' },
          ].map(({ icon: Icon, text }) => (
            <div key={text} className="flex items-center gap-3 text-sm text-white/85">
              <div className="w-8 h-8 rounded-lg bg-white/10 ring-1 ring-white/10 flex items-center justify-center shrink-0">
                <Icon size={15} className="text-white" />
              </div>
              {text}
            </div>
          ))}
        </div>
      </div>

      <div className="relative flex items-center gap-2 text-xs text-white/45">
        <ShieldCheck size={13} />
        Bank-grade security · Data encrypted in transit
      </div>
    </div>
  );

  // ── Company picker screen ────────────────────────────────────────────
  if (companies) {
    return (
      <div className="min-h-screen grid lg:grid-cols-2" style={{ backgroundColor: 'rgb(var(--color-bg))' }}>
        {BrandPanel}
        <div className="flex items-center justify-center px-6 py-12">
          <div className="w-full max-w-sm animate-fade-in-up">
            <div className="text-center mb-7">
              <div className="w-12 h-12 rounded-2xl flex items-center justify-center mx-auto mb-4"
                   style={{ background: 'rgb(var(--color-primary) / 0.1)', color: 'rgb(var(--color-primary))', border: '1px solid rgb(var(--color-primary) / 0.2)' }}>
                <Building2 size={22} />
              </div>
              <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'rgb(var(--color-text-primary))' }}>Choose a workspace</h1>
              <p className="mt-1.5 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
                Welcome back, {pickingName}.
              </p>
            </div>

            <div className="space-y-2">
              {companies.map(c => (
                <button
                  key={c.companyId}
                  onClick={() => selectCompany(c.companyId)}
                  disabled={picking}
                  className="card-interactive w-full flex items-center gap-3 p-3.5 text-left disabled:opacity-50 group"
                >
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold text-lg shrink-0"
                       style={{ background: 'rgb(var(--color-primary-dark))' }}>
                    {c.companyName.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold truncate" style={{ color: 'rgb(var(--color-text-primary))' }}>{c.companyName}</p>
                    <p className="text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>{c.role}</p>
                  </div>
                  <ArrowRight size={16} className="shrink-0 transition-transform group-hover:translate-x-0.5" style={{ color: 'rgb(var(--color-primary))' }} />
                </button>
              ))}
            </div>

            <button
              onClick={() => { setCompanies(null); setSelectionToken(null); }}
              className="btn-ghost btn-sm mx-auto mt-5 flex items-center gap-1.5"
            >
              <ArrowLeft size={14} /> Back to login
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ── Normal login form ────────────────────────────────────────────────
  return (
    <div className="min-h-screen grid lg:grid-cols-2" style={{ backgroundColor: 'rgb(var(--color-bg))' }}>
      {BrandPanel}

      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm animate-fade-in-up">
          {/* Mobile logo */}
          <Link href="/" className="lg:hidden inline-flex items-center gap-2 mb-8">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: 'rgb(var(--color-primary-dark))' }}>
              <span className="text-white font-bold">B</span>
            </div>
            <span className="font-bold text-lg tracking-tight" style={{ color: 'rgb(var(--color-text-primary))' }}>BizControl.uz</span>
          </Link>

          <div className="mb-7">
            <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'rgb(var(--color-text-primary))' }}>Sign in</h1>
            <p className="mt-1.5 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>
              Welcome back. Enter your details to continue.
            </p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="label">Email or phone</label>
              <div className="relative">
                <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: 'rgb(var(--color-text-muted))' }} />
                <input
                  {...register('login', { required: 'Required' })}
                  className="input pl-9"
                  placeholder="you@company.com"
                  autoComplete="username"
                  autoFocus
                />
              </div>
              {errors.login && <p className="text-xs mt-1.5 text-red-600">{errors.login.message as string}</p>}
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="label mb-0">Password</label>
                <button type="button" onClick={() => setShowForgot(v => !v)} className="text-xs font-medium hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>
                  Forgot password?
                </button>
              </div>
              <div className="relative">
                <Lock size={15} className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" style={{ color: 'rgb(var(--color-text-muted))' }} />
                <input
                  {...register('password', { required: 'Required' })}
                  type={showPassword ? 'text' : 'password'}
                  className="input pl-9 pr-10"
                  placeholder="••••••••"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(s => !s)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 btn-icon !w-7 !h-7"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
              {errors.password && <p className="text-xs mt-1.5 text-red-600">{errors.password.message as string}</p>}
            </div>

            {showForgot && (
              <div className="alert-info text-xs animate-fade-in" style={{ color: 'rgb(var(--color-text-secondary))' }}>
                Passwords are managed per workspace. Ask your company owner or an
                administrator to re-send your invite link, then set a new password.
              </div>
            )}

            <label className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={remember}
                onChange={e => setRemember(e.target.checked)}
                className="w-4 h-4 rounded accent-[rgb(var(--color-primary))]"
              />
              <span className="text-sm" style={{ color: 'rgb(var(--color-text-secondary))' }}>Keep me signed in for 30 days</span>
            </label>

            <button type="submit" className="btn-primary btn-lg w-full mt-1" disabled={loading}>
              {loading ? (
                <><Loader2 size={16} className="animate-spin" /> Signing in…</>
              ) : (
                <>Sign in <ArrowRight size={16} /></>
              )}
            </button>
          </form>

          <p className="text-center text-sm mt-6" style={{ color: 'rgb(var(--color-text-muted))' }}>
            Don&apos;t have an account?{' '}
            <Link href="/signup" className="font-semibold hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>
              Create one free
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
