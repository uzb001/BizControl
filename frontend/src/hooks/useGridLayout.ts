'use client';

import { useState, useCallback } from 'react';

export type Density = 'comfortable' | 'compact' | 'condensed';

export interface GridLayoutState {
  order: string[];
  widths: Record<string, number>;
  hidden: string[];
  density: Density;
  groupBy: string | null;
}

/**
 * Persisted, reconciled grid layout (column order, widths, visibility, density)
 * stored per `gridId` in localStorage. Resilient to columns being added/removed
 * between releases.
 */
export function useGridLayout(
  gridId: string,
  columnKeys: string[],
  defaultWidths: Record<string, number>,
  defaultDensity: Density = 'compact',
) {
  const storageKey = `bizcontrol_grid_${gridId}`;

  const buildBase = (): GridLayoutState => ({
    order: [...columnKeys],
    widths: { ...defaultWidths },
    hidden: [],
    density: defaultDensity,
    groupBy: null,
  });

  const load = (): GridLayoutState => {
    const base = buildBase();
    if (typeof window === 'undefined') return base;
    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) return base;
      const saved = JSON.parse(raw) as Partial<GridLayoutState>;
      const savedOrder = (saved.order || []).filter(k => columnKeys.includes(k));
      const missing = columnKeys.filter(k => !savedOrder.includes(k));
      return {
        order: [...savedOrder, ...missing],
        widths: { ...defaultWidths, ...(saved.widths || {}) },
        hidden: (saved.hidden || []).filter(k => columnKeys.includes(k)),
        density: saved.density || defaultDensity,
        groupBy: saved.groupBy && columnKeys.includes(saved.groupBy) ? saved.groupBy : null,
      };
    } catch {
      return base;
    }
  };

  const [state, setState] = useState<GridLayoutState>(load);

  const update = useCallback((mut: (prev: GridLayoutState) => GridLayoutState) => {
    setState(prev => {
      const next = mut(prev);
      try { localStorage.setItem(storageKey, JSON.stringify(next)); } catch {}
      return next;
    });
  }, [storageKey]);

  const setWidth = useCallback((key: string, width: number) => {
    update(prev => ({ ...prev, widths: { ...prev.widths, [key]: Math.max(64, Math.round(width)) } }));
  }, [update]);

  const moveColumn = useCallback((fromKey: string, toKey: string) => {
    update(prev => {
      if (fromKey === toKey) return prev;
      const order = [...prev.order];
      const fromIdx = order.indexOf(fromKey);
      const toIdx = order.indexOf(toKey);
      if (fromIdx === -1 || toIdx === -1) return prev;
      order.splice(fromIdx, 1);
      order.splice(toIdx, 0, fromKey);
      return { ...prev, order };
    });
  }, [update]);

  const toggleHidden = useCallback((key: string) => {
    update(prev => ({
      ...prev,
      hidden: prev.hidden.includes(key) ? prev.hidden.filter(k => k !== key) : [...prev.hidden, key],
    }));
  }, [update]);

  const setDensity = useCallback((density: Density) => {
    update(prev => ({ ...prev, density }));
  }, [update]);

  const setGroupBy = useCallback((groupBy: string | null) => {
    update(prev => ({ ...prev, groupBy }));
  }, [update]);

  const reset = useCallback(() => {
    const base = buildBase();
    setState(base);
    try { localStorage.removeItem(storageKey); } catch {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [storageKey]);

  return {
    order: state.order,
    widths: state.widths,
    hidden: state.hidden,
    density: state.density,
    groupBy: state.groupBy,
    setWidth,
    moveColumn,
    toggleHidden,
    setDensity,
    setGroupBy,
    reset,
  };
}
