'use client';

import { useRouter } from 'next/navigation';
import { ShieldOff, ArrowLeft, Home } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function AccessDeniedPage() {
  const router = useRouter();
  const { t } = useTranslation();

  return (
    <div className="min-h-[80vh] flex items-center justify-center p-6">
      <div className="max-w-md w-full text-center">
        {/* Icon */}
        <div className="flex justify-center mb-6">
          <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-red-100 to-red-50 flex items-center justify-center shadow-lg">
            <ShieldOff size={44} className="text-red-500" />
          </div>
        </div>

        {/* Heading */}
        <h1 className="text-3xl font-bold mb-2" style={{ color: 'rgb(var(--color-text-primary))' }}>
          {t('accessDenied.title')}
        </h1>
        <p className="text-base mb-2 font-medium" style={{ color: 'rgb(var(--color-text-secondary))' }}>
          {t('accessDenied.subtitle')}
        </p>
        <p className="text-sm mb-8" style={{ color: 'rgb(var(--color-text-muted))' }}>
          {t('accessDenied.description')}
        </p>

        {/* Actions */}
        <div className="flex items-center justify-center gap-3">
          <button
            onClick={() => router.back()}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl border text-sm font-medium transition-colors
                       border-[rgb(var(--color-border))] text-[rgb(var(--color-text-secondary))]
                       hover:bg-[rgb(var(--color-border-subtle))]"
          >
            <ArrowLeft size={15} />
            {t('common.back')}
          </button>
          <button
            onClick={() => router.push('/dashboard')}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium transition-colors
                       bg-blue-600 text-white hover:bg-blue-700 shadow-sm"
          >
            <Home size={15} />
            {t('accessDenied.goHome')}
          </button>
        </div>

        {/* Help note */}
        <p className="text-xs mt-8" style={{ color: 'rgb(var(--color-text-muted))' }}>
          {t('accessDenied.contactAdmin')}
        </p>
      </div>
    </div>
  );
}
