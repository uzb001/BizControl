'use client';

import { useEffect } from 'react';
import '@/lib/i18n';

export default function I18nProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    // i18n initializes on import; nothing else needed
  }, []);
  return <>{children}</>;
}
