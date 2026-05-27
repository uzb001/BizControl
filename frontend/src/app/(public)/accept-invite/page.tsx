'use client';

import { useState, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import Link from 'next/link';
import { authApi } from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import { KeyRound, Eye, EyeOff } from 'lucide-react';

interface FormValues {
  fullName: string;
  password: string;
  confirmPassword: string;
}

function AcceptInviteForm() {
  const router      = useRouter();
  const params      = useSearchParams();
  const token       = params.get('token') ?? '';
  const { setAuth } = useAuthStore();
  const [loading,  setLoading]  = useState(false);
  const [showPwd,  setShowPwd]  = useState(false);

  const { register, handleSubmit, watch, formState: { errors } } = useForm<FormValues>();

  const onSubmit = async (data: FormValues) => {
    if (!token) {
      toast.error('Invalid invitation link.');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.acceptInvite(token, data.fullName, data.password);
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
      }, payload.token);
      toast.success('Welcome to ' + payload.companyName + '!');
      router.push('/dashboard');
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Invalid or expired invitation link.');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 to-gray-100 flex items-center justify-center px-4">
        <div className="text-center">
          <p className="text-red-600 font-medium">Invalid invitation link.</p>
          <Link href="/login" className="text-blue-600 hover:underline mt-2 block">Go to Login</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-gray-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-14 h-14 bg-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-blue-600/30">
            <KeyRound size={24} className="text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Accept Invitation</h1>
          <p className="text-gray-500 mt-1 text-sm">Set your name and password to join the company</p>
        </div>

        <div className="card p-8">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label className="label">Full Name</label>
              <input
                {...register('fullName', { required: 'Name is required', minLength: { value: 2, message: 'Too short' } })}
                className="input"
                placeholder="Your full name"
              />
              {errors.fullName && <p className="text-red-500 text-xs mt-1">{errors.fullName.message}</p>}
            </div>

            <div>
              <label className="label">Password</label>
              <div className="relative">
                <input
                  {...register('password', { required: 'Password is required', minLength: { value: 6, message: 'At least 6 characters' } })}
                  type={showPwd ? 'text' : 'password'}
                  className="input pr-10"
                  placeholder="Choose a password"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd(p => !p)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password.message}</p>}
            </div>

            <div>
              <label className="label">Confirm Password</label>
              <input
                {...register('confirmPassword', {
                  required: 'Required',
                  validate: v => v === watch('password') || 'Passwords do not match',
                })}
                type="password"
                className="input"
                placeholder="Repeat your password"
              />
              {errors.confirmPassword && <p className="text-red-500 text-xs mt-1">{errors.confirmPassword.message}</p>}
            </div>

            <button type="submit" className="btn-primary w-full mt-2" disabled={loading}>
              {loading ? 'Joining...' : 'Join Company'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default function AcceptInvitePage() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center">Loading...</div>}>
      <AcceptInviteForm />
    </Suspense>
  );
}
