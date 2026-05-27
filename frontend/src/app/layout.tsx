import type { Metadata } from 'next';
import './globals.css';
import { Toaster } from 'react-hot-toast';
import QueryProvider from '@/components/QueryProvider';
import I18nProvider from '@/components/I18nProvider';
import ThemeProvider from '@/components/ThemeProvider';

export const metadata: Metadata = {
  title: 'BizControl.uz — Business Management Platform',
  description: 'Manage your business: products, sales, purchases, customers, suppliers, cash, debts and reports in one place.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <I18nProvider>
        <ThemeProvider>
        <QueryProvider>
          {children}
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 3000,
              style: { fontSize: '14px', borderRadius: '8px' },
            }}
          />
        </QueryProvider>
        </ThemeProvider>
        </I18nProvider>
      </body>
    </html>
  );
}
