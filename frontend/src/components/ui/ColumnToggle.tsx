'use client';

import { useState } from 'react';

interface Props {
  columns: string[];
  visible: Set<string>;
  onToggle: (col: string) => void;
  onReset: () => void;
}

export default function ColumnToggle({ columns, visible, onToggle, onReset }: Props) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(v => !v)}
        className="btn-secondary btn-sm gap-1.5"
        title="Toggle columns"
      >
        <span>⊞</span>
        <span className="hidden sm:inline">Columns</span>
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-1 z-40 bg-white border border-gray-200 rounded-xl shadow-lg p-3 min-w-48">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold text-gray-500 uppercase">Columns</span>
              <button onClick={onReset} className="text-xs text-blue-600 hover:underline">Reset</button>
            </div>
            <div className="space-y-1">
              {columns.map(col => (
                <label key={col} className="flex items-center gap-2 cursor-pointer hover:bg-gray-50 px-1 py-0.5 rounded">
                  <input
                    type="checkbox"
                    checked={visible.has(col)}
                    onChange={() => onToggle(col)}
                    className="w-3.5 h-3.5 accent-blue-600"
                  />
                  <span className="text-sm text-gray-700">{col}</span>
                </label>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
