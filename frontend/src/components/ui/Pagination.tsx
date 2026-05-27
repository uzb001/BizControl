'use client';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onChange: (page: number) => void;
}

export default function Pagination({ page, totalPages, totalElements, size, onChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const start = page * size + 1;
  const end = Math.min((page + 1) * size, totalElements);

  return (
    <div className="flex items-center justify-between px-4 py-3 bg-white border-t border-gray-200">
      <p className="text-sm text-gray-500">
        Showing <strong>{start}–{end}</strong> of <strong>{totalElements}</strong>
      </p>
      <div className="flex items-center gap-1">
        <button
          onClick={() => onChange(0)}
          disabled={page === 0}
          className="btn-ghost btn-sm disabled:opacity-40"
        >«</button>
        <button
          onClick={() => onChange(page - 1)}
          disabled={page === 0}
          className="btn-ghost btn-sm disabled:opacity-40"
        >‹</button>

        {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
          const p = Math.max(0, Math.min(page - 2, totalPages - 5)) + i;
          return (
            <button
              key={p}
              onClick={() => onChange(p)}
              className={`btn-sm rounded-lg px-3 py-1.5 text-sm ${p === page ? 'bg-blue-600 text-white' : 'btn-ghost'}`}
            >
              {p + 1}
            </button>
          );
        })}

        <button
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="btn-ghost btn-sm disabled:opacity-40"
        >›</button>
        <button
          onClick={() => onChange(totalPages - 1)}
          disabled={page >= totalPages - 1}
          className="btn-ghost btn-sm disabled:opacity-40"
        >»</button>
      </div>
    </div>
  );
}
