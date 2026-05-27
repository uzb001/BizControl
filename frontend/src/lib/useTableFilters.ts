'use client';

import { useState, useCallback } from 'react';

export interface FilterPreset {
  id: string;
  name: string;
  filters: Record<string, string>;
  createdAt: string;
}

export function useFilterPresets(namespace: string) {
  const storageKey = `bizcontrol_presets_${namespace}`;

  const load = (): FilterPreset[] => {
    if (typeof window === 'undefined') return [];
    try {
      return JSON.parse(localStorage.getItem(storageKey) || '[]');
    } catch { return []; }
  };

  const [presets, setPresets] = useState<FilterPreset[]>(load);

  const save = useCallback((name: string, filters: Record<string, string>) => {
    const preset: FilterPreset = {
      id: Date.now().toString(),
      name,
      filters,
      createdAt: new Date().toISOString(),
    };
    const updated = [...load(), preset];
    localStorage.setItem(storageKey, JSON.stringify(updated));
    setPresets(updated);
    return preset;
  }, [storageKey]);

  const remove = useCallback((id: string) => {
    const updated = load().filter(p => p.id !== id);
    localStorage.setItem(storageKey, JSON.stringify(updated));
    setPresets(updated);
  }, [storageKey]);

  return { presets, save, remove, reload: () => setPresets(load()) };
}

export function useColumnVisibility(allColumns: string[], storageKey?: string) {
  const key = storageKey ? `bizcontrol_cols_${storageKey}` : null;
  const initial = (): Set<string> => {
    if (key && typeof window !== 'undefined') {
      try {
        const stored = JSON.parse(localStorage.getItem(key) || 'null');
        if (stored) return new Set<string>(stored);
      } catch {}
    }
    return new Set(allColumns);
  };

  const [visible, setVisible] = useState<Set<string>>(initial);

  const toggle = useCallback((col: string) => {
    setVisible(prev => {
      const next = new Set(prev);
      if (next.has(col)) next.delete(col);
      else next.add(col);
      if (key) localStorage.setItem(key, JSON.stringify([...next]));
      return next;
    });
  }, [key]);

  const reset = useCallback(() => {
    setVisible(new Set(allColumns));
    if (key) localStorage.removeItem(key);
  }, [allColumns, key]);

  return { visible, toggle, reset, isVisible: (col: string) => visible.has(col) };
}

export function useBulkSelect<T extends { id: number }>() {
  const [selected, setSelected] = useState<Set<number>>(new Set());

  const toggle = useCallback((id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const toggleAll = useCallback((items: T[]) => {
    setSelected(prev => {
      if (prev.size === items.length) return new Set();
      return new Set(items.map(i => i.id));
    });
  }, []);

  const clear = useCallback(() => setSelected(new Set()), []);
  const isSelected = useCallback((id: number) => selected.has(id), [selected]);
  const selectedArray = [...selected];

  return { selected, selectedArray, toggle, toggleAll, clear, isSelected, count: selected.size };
}
