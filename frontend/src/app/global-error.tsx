'use client';

/**
 * Root-level error boundary. Catches errors that escape the root layout itself
 * (it must render its own <html>/<body>). This is the last line of defence
 * against a fully blank white crash page.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body style={{ margin: 0, fontFamily: 'system-ui, sans-serif', background: '#0f172a', color: '#e2e8f0' }}>
        <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
          <div style={{ maxWidth: 420, textAlign: 'center' }}>
            <div style={{ fontSize: 40, marginBottom: 12 }}>⚠️</div>
            <h1 style={{ fontSize: 20, fontWeight: 700, margin: '0 0 8px' }}>Application error</h1>
            <p style={{ fontSize: 14, color: '#94a3b8', margin: '0 0 20px' }}>
              The app hit an unexpected error and could not continue. Please reload.
            </p>
            <button
              onClick={() => reset()}
              style={{
                background: '#2563eb', color: '#fff', border: 'none', borderRadius: 8,
                padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer',
              }}
            >
              Reload app
            </button>
          </div>
        </div>
      </body>
    </html>
  );
}
