'use client';

import React, {
  useCallback, useEffect, useMemo, useRef, useState,
} from 'react';
import { cn } from '@/lib/utils';
import { useGridLayout, type Density } from '@/hooks/useGridLayout';
import { useIsMobile } from '@/hooks/useIsMobile';
import {
  ChevronUp, ChevronDown, ChevronsUpDown, GripVertical, SlidersHorizontal,
  RotateCcw, Check, Eye, EyeOff, Layers, ChevronRight, X,
} from 'lucide-react';

export type GridAlign = 'left' | 'right' | 'center';

export interface GridColumn<T> {
  key: string;
  header: string;
  width?: number;
  align?: GridAlign;
  sortable?: boolean;
  /** server sort field; defaults to key */
  sortKey?: string;
  editable?: boolean;
  editType?: 'text' | 'number';
  /** right-aligned tabular figures */
  numeric?: boolean;
  /** raw value for editing + default rendering */
  accessor?: (row: T) => string | number | null | undefined;
  /** custom cell renderer */
  render?: (row: T, rowIndex: number) => React.ReactNode;
  /** allow hide in the columns menu (default true) */
  hideable?: boolean;
  /** offer this column in the "Group by" menu */
  groupable?: boolean;
  /** scalar value used to bucket rows when grouping (defaults to accessor / row[key]) */
  groupValue?: (row: T) => string;
  /** show a subtotal for this column in group + grand-total rows */
  aggregate?: 'sum' | 'avg' | 'count';
  /** numeric value for aggregation (defaults to parseFloat(row[key])) */
  aggregateValue?: (row: T) => number;
  /** render an aggregate value (defaults to a tabular number) */
  aggregateRender?: (value: number) => React.ReactNode;
}

export interface DataGridProps<T> {
  gridId: string;
  columns: GridColumn<T>[];
  rows: T[];
  getRowId: (row: T) => number | string;
  loading?: boolean;

  selectable?: boolean;
  selectedIds?: Set<number | string>;
  onSelectionChange?: (ids: Set<number | string>) => void;

  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  onSortChange?: (key: string, dir: 'asc' | 'desc') => void;

  onCellEdit?: (row: T, columnKey: string, value: string) => void;
  onRowOpen?: (row: T) => void;
  rowClassName?: (row: T) => string;

  toolbar?: boolean;
  groupable?: boolean;
  emptyState?: React.ReactNode;
  skeletonRows?: number;
}

const DENSITY_LABEL: Record<Density, string> = {
  comfortable: 'Cozy',
  compact: 'Compact',
  condensed: 'Dense',
};

export default function DataGrid<T>({
  gridId,
  columns,
  rows,
  getRowId,
  loading = false,
  selectable = false,
  selectedIds,
  onSelectionChange,
  sortBy,
  sortDir,
  onSortChange,
  onCellEdit,
  onRowOpen,
  rowClassName,
  toolbar = true,
  groupable = false,
  emptyState,
  skeletonRows = 8,
}: DataGridProps<T>) {
  const columnKeys = useMemo(() => columns.map(c => c.key), [columns]);
  const defaultWidths = useMemo(() => {
    const w: Record<string, number> = {};
    columns.forEach(c => { w[c.key] = c.width ?? 160; });
    return w;
  }, [columns]);

  const layout = useGridLayout(gridId, columnKeys, defaultWidths);
  const isMobile = useIsMobile();
  const colByKey = useMemo(() => {
    const m = new Map<string, GridColumn<T>>();
    columns.forEach(c => m.set(c.key, c));
    return m;
  }, [columns]);

  const visibleColumns = useMemo(
    () => layout.order
      .map(k => colByKey.get(k))
      .filter((c): c is GridColumn<T> => !!c && !layout.hidden.includes(c.key)),
    [layout.order, layout.hidden, colByKey],
  );

  // ── Grouping + aggregation ─────────────────────────────────────────────
  const groupableColumns = useMemo(() => columns.filter(c => c.groupable), [columns]);
  const groupBy = groupable && layout.groupBy && colByKey.has(layout.groupBy) ? layout.groupBy : null;
  const isGrouped = !!groupBy;
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  const groupValueOf = useCallback((col: GridColumn<T>, row: T): string => {
    const v = col.groupValue ? col.groupValue(row)
      : col.accessor ? col.accessor(row)
      : (row as any)[col.key];
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }, []);

  const aggValueOf = (col: GridColumn<T>, row: T): number => {
    const raw = col.aggregateValue ? col.aggregateValue(row) : parseFloat(String((row as any)[col.key]));
    return isNaN(raw) ? 0 : raw;
  };
  const aggregateFor = (col: GridColumn<T>, rs: T[]): number | null => {
    if (!col.aggregate) return null;
    if (col.aggregate === 'count') return rs.length;
    const nums = rs.map(r => aggValueOf(col, r));
    const sum = nums.reduce((a, b) => a + b, 0);
    return col.aggregate === 'avg' ? (nums.length ? sum / nums.length : 0) : sum;
  };
  const renderAgg = (col: GridColumn<T>, value: number): React.ReactNode =>
    col.aggregateRender ? col.aggregateRender(value) : <span className="num">{value.toLocaleString()}</span>;

  const groups = useMemo(() => {
    if (!groupBy) return null;
    const col = colByKey.get(groupBy);
    if (!col) return null;
    const map = new Map<string, T[]>();
    rows.forEach(r => {
      const k = groupValueOf(col, r);
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(r);
    });
    return Array.from(map.entries()).map(([key, gr]) => ({ key, rows: gr }));
  }, [groupBy, rows, colByKey, groupValueOf]);

  const toggleGroup = useCallback((key: string) => {
    setCollapsedGroups(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }, []);

  const hasAggregates = useMemo(() => visibleColumns.some(c => c.aggregate), [visibleColumns]);

  const [groupMenuOpen, setGroupMenuOpen] = useState(false);
  const groupMenuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!groupMenuOpen) return;
    const h = (e: MouseEvent) => {
      if (groupMenuRef.current && !groupMenuRef.current.contains(e.target as Node)) setGroupMenuOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [groupMenuOpen]);

  // ── Selection ─────────────────────────────────────────────────────────
  const lastIndexRef = useRef<number | null>(null);
  const selCount = selectedIds?.size ?? 0;
  const allSelected = rows.length > 0 && rows.every(r => selectedIds?.has(getRowId(r)));
  const someSelected = selCount > 0 && !allSelected;
  const headerCbRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (headerCbRef.current) headerCbRef.current.indeterminate = someSelected;
  }, [someSelected]);

  const emitSelection = useCallback((next: Set<number | string>) => {
    onSelectionChange?.(next);
  }, [onSelectionChange]);

  const toggleAll = useCallback(() => {
    if (!onSelectionChange) return;
    if (allSelected) emitSelection(new Set());
    else emitSelection(new Set(rows.map(getRowId)));
  }, [allSelected, rows, getRowId, emitSelection, onSelectionChange]);

  const toggleRow = useCallback((index: number, withShift: boolean) => {
    if (!onSelectionChange) return;
    const id = getRowId(rows[index]);
    const next = new Set(selectedIds ?? []);
    if (withShift && lastIndexRef.current !== null) {
      const [a, b] = [lastIndexRef.current, index].sort((x, y) => x - y);
      const shouldSelect = !next.has(id);
      for (let i = a; i <= b; i++) {
        const rid = getRowId(rows[i]);
        if (shouldSelect) next.add(rid); else next.delete(rid);
      }
    } else {
      if (next.has(id)) next.delete(id); else next.add(id);
    }
    lastIndexRef.current = index;
    emitSelection(next);
  }, [rows, selectedIds, getRowId, emitSelection, onSelectionChange]);

  // ── Sort ──────────────────────────────────────────────────────────────
  const handleSort = useCallback((col: GridColumn<T>) => {
    if (!col.sortable || !onSortChange) return;
    const key = col.sortKey ?? col.key;
    const nextDir: 'asc' | 'desc' = sortBy === key && sortDir === 'desc' ? 'asc' : 'desc';
    onSortChange(key, sortBy === key ? (sortDir === 'asc' ? 'desc' : 'asc') : nextDir);
  }, [onSortChange, sortBy, sortDir]);

  // ── Keyboard navigation + inline edit ──────────────────────────────────
  const gridRef = useRef<HTMLDivElement>(null);
  const activeCellRef = useRef<HTMLTableCellElement | null>(null);
  const editRef = useRef<HTMLInputElement>(null);
  const selectOnFocusRef = useRef(false);
  const [active, setActive] = useState<{ r: number; c: number } | null>(null);
  const [editing, setEditing] = useState(false);
  const [editValue, setEditValue] = useState('');

  const maxR = rows.length - 1;
  const maxC = visibleColumns.length - 1;

  useEffect(() => {
    if (active && activeCellRef.current) {
      activeCellRef.current.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  }, [active]);

  useEffect(() => {
    if (editing && editRef.current) {
      editRef.current.focus();
      if (selectOnFocusRef.current) editRef.current.select();
    }
  }, [editing]);

  const cellValue = (col: GridColumn<T>, row: T): string => {
    const v = col.accessor ? col.accessor(row) : '';
    return v === null || v === undefined ? '' : String(v);
  };

  // Dedupe commits: Enter both commits and blurs the input, which would
  // otherwise fire onCellEdit twice. Each edit session is committed once.
  const editSessionRef = useRef(0);
  const committedSessionRef = useRef(-1);

  const commitEdit = useCallback((move: 'down' | 'right' | 'none') => {
    if (!active) return;
    if (committedSessionRef.current === editSessionRef.current) { setEditing(false); return; }
    committedSessionRef.current = editSessionRef.current;
    const col = visibleColumns[active.c];
    const row = rows[active.r];
    if (col && onCellEdit) onCellEdit(row, col.key, editValue);
    setEditing(false);
    gridRef.current?.focus();
    if (move === 'down') setActive(a => a ? { ...a, r: Math.min(a.r + 1, maxR) } : a);
    if (move === 'right') setActive(a => a ? { ...a, c: Math.min(a.c + 1, maxC) } : a);
  }, [active, visibleColumns, rows, onCellEdit, editValue, maxR, maxC]);

  const cancelEdit = useCallback(() => {
    committedSessionRef.current = editSessionRef.current; // suppress blur commit
    setEditing(false);
    gridRef.current?.focus();
  }, []);

  // start edit at a specific cell
  const startEditAt = useCallback((cell: { r: number; c: number }, initial?: string) => {
    const col = visibleColumns[cell.c];
    if (!col?.editable || !onCellEdit) return;
    editSessionRef.current += 1; // new edit session for commit dedupe
    if (initial !== undefined) { selectOnFocusRef.current = false; setEditValue(initial); }
    else { selectOnFocusRef.current = true; setEditValue(cellValue(col, rows[cell.r])); }
    setEditing(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleColumns, onCellEdit, rows]);

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (rows.length === 0) return;
    if (isGrouped) return; // active-cell nav/edit disabled in grouped (analysis) mode

    // Editing mode keys
    if (editing) {
      if (e.key === 'Enter') { e.preventDefault(); commitEdit('down'); }
      else if (e.key === 'Escape') { e.preventDefault(); cancelEdit(); }
      else if (e.key === 'Tab') { e.preventDefault(); commitEdit('right'); }
      return;
    }

    const a = active ?? { r: 0, c: 0 };
    switch (e.key) {
      case 'ArrowDown': e.preventDefault(); setActive({ r: Math.min(a.r + 1, maxR), c: a.c }); break;
      case 'ArrowUp':   e.preventDefault(); setActive({ r: Math.max(a.r - 1, 0), c: a.c }); break;
      case 'ArrowRight':e.preventDefault(); setActive({ r: a.r, c: Math.min(a.c + 1, maxC) }); break;
      case 'ArrowLeft': e.preventDefault(); setActive({ r: a.r, c: Math.max(a.c - 1, 0) }); break;
      case 'Home':      e.preventDefault(); setActive(e.ctrlKey ? { r: 0, c: 0 } : { r: a.r, c: 0 }); break;
      case 'End':       e.preventDefault(); setActive(e.ctrlKey ? { r: maxR, c: maxC } : { r: a.r, c: maxC }); break;
      case 'PageDown':  e.preventDefault(); setActive({ r: Math.min(a.r + 10, maxR), c: a.c }); break;
      case 'PageUp':    e.preventDefault(); setActive({ r: Math.max(a.r - 10, 0), c: a.c }); break;
      case 'Enter': {
        e.preventDefault();
        const col = visibleColumns[a.c];
        if (col?.editable && onCellEdit) { setActive(a); startEditAt(a, undefined); }
        else if (onRowOpen) onRowOpen(rows[a.r]);
        break;
      }
      case ' ': {
        if (selectable) { e.preventDefault(); toggleRow(a.r, false); }
        break;
      }
      default: {
        // Type-to-edit (Excel-style)
        const col = visibleColumns[a.c];
        if (col?.editable && onCellEdit && e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
          e.preventDefault();
          setActive(a);
          startEditAt(a, e.key);
        }
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, editing, active, maxR, maxC, visibleColumns, onCellEdit, onRowOpen, selectable, toggleRow, commitEdit, cancelEdit, startEditAt, isGrouped]);

  // ── Column resize ───────────────────────────────────────────────────────
  const resizeRef = useRef<{ key: string; startX: number; startW: number } | null>(null);
  const onResizeDown = useCallback((e: React.PointerEvent, key: string) => {
    e.preventDefault();
    e.stopPropagation();
    resizeRef.current = { key, startX: e.clientX, startW: layout.widths[key] ?? 160 };
    const move = (ev: PointerEvent) => {
      if (!resizeRef.current) return;
      const delta = ev.clientX - resizeRef.current.startX;
      layout.setWidth(resizeRef.current.key, resizeRef.current.startW + delta);
    };
    const up = () => {
      resizeRef.current = null;
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      document.body.style.cursor = '';
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    document.body.style.cursor = 'col-resize';
  }, [layout]);

  // ── Column reorder (DnD) ─────────────────────────────────────────────────
  const dragKeyRef = useRef<string | null>(null);
  const [dragOverKey, setDragOverKey] = useState<string | null>(null);

  // ── Columns menu ─────────────────────────────────────────────────────────
  const [colsMenuOpen, setColsMenuOpen] = useState(false);
  const colsMenuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!colsMenuOpen) return;
    const h = (e: MouseEvent) => {
      if (colsMenuRef.current && !colsMenuRef.current.contains(e.target as Node)) setColsMenuOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [colsMenuOpen]);

  const totalWidth = (selectable ? 44 : 0) +
    visibleColumns.reduce((sum, c) => sum + (layout.widths[c.key] ?? 160), 0);

  const alignClass = (a?: GridAlign) => a === 'right' ? 'text-right' : a === 'center' ? 'text-center' : 'text-left';

  const rowIndexById = useMemo(() => {
    const m = new Map<number | string, number>();
    rows.forEach((r, i) => m.set(getRowId(r), i));
    return m;
  }, [rows, getRowId]);

  const renderDataRow = (row: T) => {
    const id = getRowId(row);
    const r = rowIndexById.get(id) ?? 0;
    const isSel = selectedIds?.has(id) ?? false;
    return (
      <tr key={String(id)} className={cn('dgrid-row', isSel && 'dgrid-row-selected', rowClassName?.(row))}>
        {selectable && (
          <td className="dgrid-sticky-left" onClick={(e) => e.stopPropagation()}>
            <input
              type="checkbox"
              className="dgrid-cb"
              checked={isSel}
              onClick={(e) => { e.preventDefault(); toggleRow(r, e.shiftKey); }}
              onChange={() => {}}
              aria-label="Select row"
            />
          </td>
        )}
        {visibleColumns.map((col, c) => {
          const isActive = !isGrouped && active?.r === r && active?.c === c;
          const isEditing = isActive && editing;
          return (
            <td
              key={col.key}
              ref={isActive ? activeCellRef : undefined}
              className={cn(
                'dgrid-td',
                alignClass(col.align),
                col.numeric && 'num tabular-nums',
                isActive && 'dgrid-td-active',
                col.editable && onCellEdit && 'dgrid-td-editable',
              )}
              onClick={() => { if (!isGrouped) setActive({ r, c }); }}
              onDoubleClick={() => { if (col.editable && onCellEdit) { setActive({ r, c }); startEditAt({ r, c }, undefined); } }}
            >
              {isEditing ? (
                <input
                  ref={editRef}
                  className="dgrid-edit"
                  type={col.editType === 'number' ? 'number' : 'text'}
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  onBlur={() => commitEdit('none')}
                />
              ) : (
                col.render ? col.render(row, r) : <span className="dgrid-cell-text">{cellValue(col, row)}</span>
              )}
            </td>
          );
        })}
      </tr>
    );
  };

  // ── Mobile card rendering ───────────────────────────────────────────────
  const mobileCols = useMemo(() => {
    const cols = visibleColumns.filter(c => c.key !== 'rn');
    const actionsCol = cols.find(c => c.key === 'actions') ?? null;
    const content = cols.filter(c => c.key !== 'actions');
    return { primary: content[0] ?? null, rest: content.slice(1), actionsCol };
  }, [visibleColumns]);

  const renderCard = (row: T) => {
    const id = getRowId(row);
    const r = rowIndexById.get(id) ?? 0;
    const isSel = selectedIds?.has(id) ?? false;
    const { primary, rest, actionsCol } = mobileCols;
    return (
      <div
        key={String(id)}
        className={cn('dgrid-card', isSel && 'dgrid-card-selected', rowClassName?.(row))}
        onClick={() => onRowOpen?.(row)}
      >
        <div className="dgrid-card-head">
          <div className="dgrid-card-title">
            {primary && (primary.render ? primary.render(row, r) : cellValue(primary, row))}
          </div>
          {selectable && (
            <input
              type="checkbox"
              className="dgrid-cb shrink-0"
              checked={isSel}
              onClick={(e) => { e.stopPropagation(); e.preventDefault(); toggleRow(r, false); }}
              onChange={() => {}}
              aria-label="Select row"
            />
          )}
        </div>
        <div className="dgrid-card-body">
          {rest.map(col => (
            <div key={col.key} className="dgrid-card-field">
              <span className="dgrid-card-label">{col.header}</span>
              <span className="dgrid-card-value">{col.render ? col.render(row, r) : cellValue(col, row)}</span>
            </div>
          ))}
        </div>
        {actionsCol && (
          <div className="dgrid-card-actions" onClick={(e) => e.stopPropagation()}>
            {actionsCol.render ? actionsCol.render(row, r) : null}
          </div>
        )}
      </div>
    );
  };

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="dgrid-shell">
      {toolbar && (
        <div className="dgrid-toolbar">
          <div className="flex items-center gap-2 text-xs" style={{ color: 'rgb(var(--color-text-muted))' }}>
            {selCount > 0
              ? <span className="font-semibold" style={{ color: 'rgb(var(--color-primary))' }}>{selCount} selected</span>
              : <span>{rows.length} {rows.length === 1 ? 'row' : 'rows'}</span>}
          </div>
          <div className="flex items-center gap-2">
            {/* Density */}
            <div className="segment !p-0.5">
              {(['comfortable', 'compact', 'condensed'] as Density[]).map(d => (
                <button
                  key={d}
                  onClick={() => layout.setDensity(d)}
                  className={cn('segment-item !px-2 !py-1', layout.density === d && 'active')}
                  title={`${DENSITY_LABEL[d]} density`}
                >
                  {DENSITY_LABEL[d]}
                </button>
              ))}
            </div>
            {/* Group by */}
            {groupable && groupableColumns.length > 0 && (
              <div className="relative" ref={groupMenuRef}>
                <button onClick={() => setGroupMenuOpen(o => !o)}
                        className={cn('btn-secondary btn-sm gap-1.5', isGrouped && '!border-[rgb(var(--color-primary))]')}>
                  <Layers size={13} />
                  {isGrouped ? colByKey.get(groupBy!)?.header : 'Group'}
                  {isGrouped && (
                    <span onClick={(e) => { e.stopPropagation(); layout.setGroupBy(null); }} className="ml-0.5 -mr-1 hover:opacity-70">
                      <X size={12} />
                    </span>
                  )}
                </button>
                {groupMenuOpen && (
                  <div className="popover absolute right-0 top-full mt-1.5 w-52 py-1.5 z-30">
                    <p className="px-3 py-1 text-2xs font-semibold uppercase tracking-wider" style={{ color: 'rgb(var(--color-text-muted))' }}>Group by</p>
                    <button onClick={() => { layout.setGroupBy(null); setGroupMenuOpen(false); }}
                            className="w-full flex items-center gap-2.5 px-3 py-1.5 text-sm hover:bg-[rgb(var(--color-border-subtle))]"
                            style={{ color: 'rgb(var(--color-text-secondary))' }}>
                      <span className="flex-1 text-left">None</span>
                      {!isGrouped && <Check size={13} style={{ color: 'rgb(var(--color-primary))' }} />}
                    </button>
                    {groupableColumns.map(c => (
                      <button key={c.key} onClick={() => { layout.setGroupBy(c.key); setGroupMenuOpen(false); }}
                              className="w-full flex items-center gap-2.5 px-3 py-1.5 text-sm hover:bg-[rgb(var(--color-border-subtle))]"
                              style={{ color: 'rgb(var(--color-text-secondary))' }}>
                        <span className="flex-1 text-left">{c.header}</span>
                        {groupBy === c.key && <Check size={13} style={{ color: 'rgb(var(--color-primary))' }} />}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {/* Columns */}
            <div className="relative" ref={colsMenuRef}>
              <button onClick={() => setColsMenuOpen(o => !o)} className="btn-secondary btn-sm gap-1.5">
                <SlidersHorizontal size={13} /> Columns
              </button>
              {colsMenuOpen && (
                <div className="popover absolute right-0 top-full mt-1.5 w-56 py-1.5 z-30">
                  <p className="px-3 py-1 text-2xs font-semibold uppercase tracking-wider" style={{ color: 'rgb(var(--color-text-muted))' }}>
                    Toggle columns
                  </p>
                  <div className="max-h-72 overflow-y-auto">
                    {columns.filter(c => c.hideable !== false).map(c => {
                      const shown = !layout.hidden.includes(c.key);
                      return (
                        <button
                          key={c.key}
                          onClick={() => layout.toggleHidden(c.key)}
                          className="w-full flex items-center gap-2.5 px-3 py-1.5 text-sm transition-colors hover:bg-[rgb(var(--color-border-subtle))]"
                          style={{ color: 'rgb(var(--color-text-secondary))' }}
                        >
                          {shown ? <Eye size={14} style={{ color: 'rgb(var(--color-primary))' }} /> : <EyeOff size={14} />}
                          <span className="flex-1 text-left">{c.header}</span>
                          {shown && <Check size={13} style={{ color: 'rgb(var(--color-primary))' }} />}
                        </button>
                      );
                    })}
                  </div>
                  <div className="border-t mt-1 pt-1" style={{ borderColor: 'rgb(var(--color-border))' }}>
                    <button onClick={() => { layout.reset(); setColsMenuOpen(false); }}
                            className="w-full flex items-center gap-2 px-3 py-1.5 text-sm hover:bg-[rgb(var(--color-border-subtle))]"
                            style={{ color: 'rgb(var(--color-text-secondary))' }}>
                      <RotateCcw size={13} /> Reset layout
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {isMobile ? (
        <div className="dgrid-cards">
          {loading ? (
            Array.from({ length: skeletonRows }).map((_, i) => (
              <div key={`skc-${i}`} className="dgrid-card">
                <div className="skeleton h-5 w-2/3 rounded mb-3" />
                <div className="skeleton h-3 w-full rounded mb-2" />
                <div className="skeleton h-3 w-1/2 rounded" />
              </div>
            ))
          ) : rows.length === 0 ? (
            <div className="dgrid-empty">{emptyState}</div>
          ) : (
            rows.map(row => renderCard(row))
          )}
        </div>
      ) : (
      <div
        ref={gridRef}
        className={cn('dgrid-scroll', `density-${layout.density}`)}
        tabIndex={0}
        onKeyDown={onKeyDown}
        role="grid"
        aria-rowcount={rows.length}
      >
        <table className="dgrid" style={{ width: totalWidth, minWidth: '100%' }}>
          <colgroup>
            {selectable && <col style={{ width: 44 }} />}
            {visibleColumns.map(c => <col key={c.key} style={{ width: layout.widths[c.key] ?? 160 }} />)}
          </colgroup>
          <thead>
            <tr>
              {selectable && (
                <th className="dgrid-th-select dgrid-sticky-left">
                  <input
                    ref={headerCbRef}
                    type="checkbox"
                    checked={allSelected}
                    onChange={toggleAll}
                    className="dgrid-cb"
                    aria-label="Select all rows"
                  />
                </th>
              )}
              {visibleColumns.map(col => {
                const key = col.sortKey ?? col.key;
                const sorted = sortBy === key;
                return (
                  <th
                    key={col.key}
                    className={cn('dgrid-th', alignClass(col.align), dragOverKey === col.key && 'dgrid-th-dragover')}
                    onDragOver={(e) => { if (dragKeyRef.current) { e.preventDefault(); setDragOverKey(col.key); } }}
                    onDragLeave={() => setDragOverKey(k => k === col.key ? null : k)}
                    onDrop={(e) => {
                      e.preventDefault();
                      if (dragKeyRef.current && dragKeyRef.current !== col.key) layout.moveColumn(dragKeyRef.current, col.key);
                      dragKeyRef.current = null; setDragOverKey(null);
                    }}
                  >
                    <div className="dgrid-th-inner">
                      <span
                        className="dgrid-th-grip"
                        draggable
                        onDragStart={(e) => { dragKeyRef.current = col.key; e.dataTransfer.effectAllowed = 'move'; }}
                        onDragEnd={() => { dragKeyRef.current = null; setDragOverKey(null); }}
                        title="Drag to reorder"
                      >
                        <GripVertical size={12} />
                      </span>
                      <button
                        type="button"
                        className={cn('dgrid-th-label', col.sortable && 'dgrid-th-sortable')}
                        onClick={() => handleSort(col)}
                      >
                        <span className="truncate">{col.header}</span>
                        {col.sortable && (
                          sorted
                            ? (sortDir === 'asc' ? <ChevronUp size={13} className="shrink-0" style={{ color: 'rgb(var(--color-primary))' }} />
                                                 : <ChevronDown size={13} className="shrink-0" style={{ color: 'rgb(var(--color-primary))' }} />)
                            : <ChevronsUpDown size={12} className="shrink-0 opacity-40" />
                        )}
                      </button>
                    </div>
                    <span className="dgrid-resize" onPointerDown={(e) => onResizeDown(e, col.key)} />
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: skeletonRows }).map((_, r) => (
                <tr key={`sk-${r}`} className="dgrid-row">
                  {selectable && <td className="dgrid-sticky-left"><div className="skeleton h-4 w-4 rounded" /></td>}
                  {visibleColumns.map(c => (
                    <td key={c.key} className={alignClass(c.align)}>
                      <div className={cn('skeleton h-3.5 rounded', c.numeric ? 'ml-auto w-16' : 'w-3/4')} />
                    </td>
                  ))}
                </tr>
              ))
            ) : isGrouped && groups ? (
              <>
                {groups.map(g => {
                  const collapsed = collapsedGroups.has(g.key);
                  return (
                    <React.Fragment key={g.key}>
                      <tr className="dgrid-group-row" onClick={() => toggleGroup(g.key)}>
                        {selectable && <td className="dgrid-sticky-left" />}
                        {visibleColumns.map(col => {
                          const isGroupCol = col.key === groupBy;
                          const agg = aggregateFor(col, g.rows);
                          return (
                            <td key={col.key} className={cn('dgrid-group-cell', alignClass(col.align), col.numeric && 'num')}>
                              {isGroupCol ? (
                                <span className="inline-flex items-center gap-1.5 font-semibold">
                                  <ChevronRight size={13} className={cn('transition-transform', !collapsed && 'rotate-90')} />
                                  {g.key}
                                  <span className="dgrid-group-count">{g.rows.length}</span>
                                </span>
                              ) : agg !== null ? renderAgg(col, agg) : null}
                            </td>
                          );
                        })}
                      </tr>
                      {!collapsed && g.rows.map(row => renderDataRow(row))}
                    </React.Fragment>
                  );
                })}
                {hasAggregates && (
                  <tr className="dgrid-total-row">
                    {selectable && <td className="dgrid-sticky-left" />}
                    {visibleColumns.map((col, ci) => {
                      const agg = aggregateFor(col, rows);
                      return (
                        <td key={col.key} className={cn('dgrid-total-cell', alignClass(col.align), col.numeric && 'num')}>
                          {ci === 0 && !col.aggregate ? <span className="font-bold">Total</span> : agg !== null ? renderAgg(col, agg) : null}
                        </td>
                      );
                    })}
                  </tr>
                )}
              </>
            ) : (
              rows.map(row => renderDataRow(row))
            )}
          </tbody>
        </table>

        {!loading && rows.length === 0 && (
          <div className="dgrid-empty">{emptyState}</div>
        )}
      </div>
      )}
    </div>
  );
}
