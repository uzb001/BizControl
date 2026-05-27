'use client';

import { useState, useRef, useEffect } from 'react';
import { FilterPreset } from '@/lib/useTableFilters';
import { Bookmark, Plus, Check, Trash2, X } from 'lucide-react';
import { cn } from '@/lib/utils';

interface Props {
  presets: FilterPreset[];
  onApply: (filters: Record<string, string>) => void;
  onSave: (name: string) => void;
  onDelete: (id: string) => void;
  /** key of the currently-applied view, for the active checkmark (optional) */
  activeId?: string | null;
}

/**
 * Saved Views — named filter/sort snapshots persisted per page.
 * (Component name kept as FilterPresets for import stability.)
 */
export default function FilterPresets({ presets, onApply, onSave, onDelete, activeId }: Props) {
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) { setOpen(false); setSaving(false); } };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [open]);

  const handleSave = () => {
    if (!name.trim()) return;
    onSave(name.trim());
    setName('');
    setSaving(false);
  };

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen(v => !v)} className="btn-secondary btn-sm gap-1.5" title="Saved views">
        <Bookmark size={14} />
        <span className="hidden sm:inline">Views</span>
        {presets.length > 0 && (
          <span className="text-2xs px-1.5 py-0.5 rounded-full font-semibold"
                style={{ background: 'rgb(var(--color-primary) / 0.12)', color: 'rgb(var(--color-primary))' }}>
            {presets.length}
          </span>
        )}
      </button>

      {open && (
        <div className="popover absolute right-0 top-full mt-1.5 z-40 p-2 w-64">
          <div className="flex items-center justify-between px-1 pb-1.5">
            <span className="text-2xs font-semibold uppercase tracking-wider" style={{ color: 'rgb(var(--color-text-muted))' }}>Saved views</span>
            <button onClick={() => setSaving(v => !v)} className="text-xs font-medium inline-flex items-center gap-1 hover:underline" style={{ color: 'rgb(var(--color-primary))' }}>
              <Plus size={12} /> Save current
            </button>
          </div>

          {saving && (
            <div className="flex gap-1.5 mb-2">
              <input autoFocus className="input !py-1 text-xs flex-1" placeholder="View name…"
                     value={name} onChange={e => setName(e.target.value)}
                     onKeyDown={e => { if (e.key === 'Enter') handleSave(); if (e.key === 'Escape') setSaving(false); }} />
              <button onClick={handleSave} className="btn-primary btn-sm">Save</button>
            </div>
          )}

          {presets.length === 0 ? (
            <p className="text-xs text-center py-3" style={{ color: 'rgb(var(--color-text-muted))' }}>No saved views yet</p>
          ) : (
            <div className="space-y-0.5 max-h-56 overflow-y-auto">
              {presets.map(p => (
                <div key={p.id} className="flex items-center gap-1 group rounded-lg px-1 hover:bg-[rgb(var(--color-border-subtle))]">
                  <button onClick={() => { onApply(p.filters); setOpen(false); }}
                          className="flex items-center gap-2 text-sm text-left flex-1 py-1.5 px-1.5 min-w-0"
                          style={{ color: 'rgb(var(--color-text-secondary))' }}>
                    {activeId === p.id
                      ? <Check size={13} style={{ color: 'rgb(var(--color-primary))' }} className="shrink-0" />
                      : <Bookmark size={13} className="shrink-0 opacity-50" />}
                    <span className={cn('truncate', activeId === p.id && 'font-semibold')}>{p.name}</span>
                  </button>
                  <button onClick={() => onDelete(p.id)}
                          className="btn-icon !w-7 !h-7 opacity-0 group-hover:opacity-100 hover:!text-red-600 shrink-0"
                          aria-label="Delete view">
                    <Trash2 size={13} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
