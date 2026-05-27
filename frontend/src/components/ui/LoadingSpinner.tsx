export default function LoadingSpinner({ text = 'Loading...' }: { text?: string }) {
  return (
    <div
      className="flex items-center justify-center py-16 gap-3"
      style={{ color: 'rgb(var(--color-text-muted))' }}
    >
      <span className="relative inline-flex w-5 h-5">
        <span
          className="absolute inset-0 rounded-full border-2 animate-spin"
          style={{
            borderColor: 'rgb(var(--color-primary) / 0.2)',
            borderTopColor: 'rgb(var(--color-primary))',
          }}
        />
      </span>
      <span className="text-sm">{text}</span>
    </div>
  );
}

export function PageLoading() {
  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <span
        className="w-8 h-8 rounded-full border-[3px] animate-spin"
        style={{
          borderColor: 'rgb(var(--color-primary) / 0.2)',
          borderTopColor: 'rgb(var(--color-primary))',
        }}
      />
    </div>
  );
}
