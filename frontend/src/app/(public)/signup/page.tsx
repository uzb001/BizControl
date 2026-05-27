'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { authApi } from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import {
  Eye, EyeOff, ArrowRight, Loader2, Rocket, Clock, CreditCard, ShieldCheck,
} from 'lucide-react';

const BUSINESS_TYPES = ['retail_store', 'wholesale', 'import', 'warehouse', 'online_shop', 'mixed'];
const CURRENCIES = ['UZS', 'USD', 'EUR', 'RUB'];

export default function SignupPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<{
    fullName: string; email: string; phone: string; password: string;
    companyName: string; businessType: string; mainCurrency: string;
  }>({
    defaultValues: { mainCurrency: 'UZS', businessType: 'retail_store', fullName: '', email: '', phone: '', password: '', companyName: '' }
  });

  const onSubmit = async (data: any) => {
    if (!data.email && !data.phone) {
      toast.error('Email or phone is required');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.signup(data);
      const { token, ...user } = res.data;
      setAuth(user, token, true);
      toast.success('Account created! Welcome to BizControl!');
      router.push('/dashboard');
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Signup failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-2" style={{ backgroundColor: 'rgb(var(--color-bg))' }}>
      {/* Brand panel */}
      <div
        className="relative hidden lg:flex flex-col justify-between p-12 overflow-hidden"
        style={{
          background:
            'radial-gradient(120% 120% at 0% 0%, rgba(99,102,241,0.55) 0%, transparent 45%),' +
            'radial-gradient(120% 120% at 100% 100%, rgba(139,92,246,0.45) 0%, transparent 50%),' +
            'linear-gradient(180deg, #1b1f3b 0%, #14152b 100%)',
        }}
      >
        <div className="absolute inset-0 pointer-events-none"
             style={{ background: 'radial-gradient(80% 120% at 50% -10%, rgba(255,255,255,0.06) 0%, transparent 60%)' }} />
        <div className="relative flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-white/10 backdrop-blur flex items-center justify-center ring-1 ring-white/15">
            <span className="text-white font-bold">B</span>
          </div>
          <span className="font-bold text-white text-lg tracking-tight">BizControl.uz</span>
        </div>

        <div className="relative space-y-8">
          <h2 className="text-3xl font-bold text-white leading-tight tracking-tight">
            Start running a sharper,<br />more profitable business.
          </h2>
          <div className="space-y-3.5">
            {[
              { icon: Clock,      text: 'Set up your company in under 2 minutes' },
              { icon: CreditCard, text: 'No credit card required to start' },
              { icon: Rocket,     text: 'Inventory, sales, cash & reports — day one' },
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
          Your data is encrypted and never shared.
        </div>
      </div>

      {/* Form */}
      <div className="flex items-center justify-center px-6 py-10 overflow-y-auto">
        <div className="w-full max-w-md animate-fade-in-up">
          <Link href="/" className="lg:hidden inline-flex items-center gap-2 mb-7">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: 'rgb(var(--color-primary-dark))' }}>
              <span className="text-white font-bold">B</span>
            </div>
            <span className="font-bold text-lg tracking-tight" style={{ color: 'rgb(var(--color-text-primary))' }}>BizControl.uz</span>
          </Link>

          <div className="mb-6">
            <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'rgb(var(--color-text-primary))' }}>Create your account</h1>
            <p className="mt-1.5 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>Set up your company workspace — it&apos;s free.</p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="label">Full name *</label>
              <input {...register('fullName', { required: 'Required' })} className="input" placeholder="Your full name" autoFocus />
              {errors.fullName && <p className="text-xs mt-1.5 text-red-600">{errors.fullName.message as string}</p>}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label">Email</label>
                <input {...register('email')} type="email" className="input" placeholder="email@example.com" autoComplete="email" />
              </div>
              <div>
                <label className="label">Phone</label>
                <input {...register('phone')} className="input" placeholder="+998 90 123 45 67" autoComplete="tel" />
              </div>
            </div>

            <div>
              <label className="label">Password *</label>
              <div className="relative">
                <input
                  {...register('password', { required: 'Required', minLength: { value: 6, message: 'Min 6 characters' } })}
                  type={showPassword ? 'text' : 'password'}
                  className="input pr-10"
                  placeholder="Min 6 characters"
                  autoComplete="new-password"
                />
                <button type="button" onClick={() => setShowPassword(s => !s)}
                        className="absolute right-2 top-1/2 -translate-y-1/2 btn-icon !w-7 !h-7"
                        aria-label={showPassword ? 'Hide password' : 'Show password'}>
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
              {errors.password && <p className="text-xs mt-1.5 text-red-600">{errors.password.message as string}</p>}
            </div>

            <div className="pt-1">
              <p className="text-2xs font-semibold uppercase tracking-wider mb-3" style={{ color: 'rgb(var(--color-text-muted))' }}>
                Company information
              </p>
              <div className="space-y-4">
                <div>
                  <label className="label">Company name *</label>
                  <input {...register('companyName', { required: 'Required' })} className="input" placeholder="Your company name" />
                  {errors.companyName && <p className="text-xs mt-1.5 text-red-600">{errors.companyName.message as string}</p>}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="label">Business type</label>
                    <select {...register('businessType')} className="input">
                      {BUSINESS_TYPES.map(t => (
                        <option key={t} value={t}>{t.replace('_', ' ').replace(/\b\w/g, c => c.toUpperCase())}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="label">Currency</label>
                    <select {...register('mainCurrency')} className="input">
                      {CURRENCIES.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                  </div>
                </div>
              </div>
            </div>

            <button type="submit" className="btn-primary btn-lg w-full mt-1" disabled={loading}>
              {loading ? (
                <><Loader2 size={16} className="animate-spin" /> Creating account…</>
              ) : (
                <>Create account <ArrowRight size={16} /></>
              )}
            </button>
          </form>

          <p className="text-center text-sm mt-6" style={{ color: 'rgb(var(--color-text-muted))' }}>
            Already have an account?{' '}
            <Link href="/login" className="font-semibold hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
