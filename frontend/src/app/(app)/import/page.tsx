'use client';

import { useState, useRef, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { importApi } from '@/lib/api';
import {
  Upload, FileSpreadsheet, ArrowRight, ArrowLeft, RotateCcw,
  CheckCircle, XCircle, AlertTriangle, History, ChevronDown, Loader2,
} from 'lucide-react';

type Step = 'upload' | 'map' | 'preview' | 'importing' | 'done';
type Module = 'products' | 'customers' | 'suppliers';

const MODULE_COLUMNS: Record<Module, string[]> = {
  products: ['name', 'sku', 'unit', 'purchasePrice', 'sellingPrice', 'currentStock', 'minStockLevel', 'category', 'barcode'],
  customers: ['name', 'phone', 'city', 'customerType', 'debtLimit', 'notes'],
  suppliers: ['name', 'phone', 'country', 'city', 'currency'],
};
const MODULE_REQUIRED: Record<Module, string[]> = {
  products: ['name'],
  customers: ['name'],
  suppliers: ['name'],
};

const DISPLAY_STEPS = ['upload', 'map', 'preview', 'done'] as const;

export default function ImportPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);

  const [tab, setTab] = useState<'wizard' | 'history'>('wizard');
  const [step, setStep] = useState<Step>('upload');
  const [module, setModule] = useState<Module>('products');
  const [file, setFile] = useState<File | null>(null);
  const [headers, setHeaders] = useState<string[]>([]);
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [skipDuplicates, setSkipDuplicates] = useState(true);
  const [batchId, setBatchId] = useState<number | null>(null);
  const [previewData, setPreviewData] = useState<{
    totalRows: number;
    previewRows: any[];
    validRows: number;
    errorRows: number;
    errors: string[];
  } | null>(null);
  const [importResult, setImportResult] = useState<{
    successRows: number;
    failedRows: number;
    duplicateRows: number;
    errorSummary: string | null;
  } | null>(null);

  const { data: history, isLoading: historyLoading } = useQuery({
    queryKey: ['import-history'],
    queryFn: () => importApi.history().then(r => r.data),
    enabled: tab === 'history',
  });

  const previewMutation = useMutation({
    mutationFn: (fd: FormData) => importApi.preview(fd).then(r => r.data),
    onSuccess: (data) => {
      setBatchId(data.batchId);
      setPreviewData({
        totalRows: data.totalRows,
        previewRows: data.previewRows ?? [],
        validRows: data.validRows ?? 0,
        errorRows: data.errorRows ?? 0,
        errors: data.errors ?? [],
      });
      setStep('preview');
    },
  });

  const confirmMutation = useMutation({
    mutationFn: (fd: FormData) => importApi.confirm(fd).then(r => r.data),
    onSuccess: (data) => {
      setImportResult(data);
      setStep('done');
      qc.invalidateQueries({ queryKey: ['import-history'] });
    },
  });

  const rollbackMutation = useMutation({
    mutationFn: (id: number) => importApi.rollback(id).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['import-history'] }),
  });

  const handleFileChange = useCallback((f: File) => {
    setFile(f);
    // Read just the first line to extract headers
    const reader = new FileReader();
    reader.onload = (ev) => {
      const text = ev.target?.result as string;
      const firstLine = text.split('\n')[0] || '';
      // Handle quoted CSV headers
      const cols = firstLine.split(',').map(c => c.trim().replace(/^"|"$/g, ''));
      setHeaders(cols);
      // Auto-map by similarity
      const autoMap: Record<string, string> = {};
      const targets = MODULE_COLUMNS[module];
      for (const h of cols) {
        const lo = h.toLowerCase().replace(/[\s_-]/g, '');
        const match = targets.find(c => c.toLowerCase() === lo || lo.includes(c.toLowerCase()));
        if (match) autoMap[h] = match;
      }
      setMapping(autoMap);
      setStep('map');
    };
    reader.readAsText(f);
  }, [module]);

  const buildFormData = () => {
    const fd = new FormData();
    fd.append('file', file!);
    fd.append('module', module);
    fd.append('skipDuplicates', String(skipDuplicates));
    if (batchId) fd.append('batchId', String(batchId));
    for (const [header, field] of Object.entries(mapping)) {
      if (field) fd.append(`map_${header}`, field);
    }
    return fd;
  };

  const handlePreview = () => {
    if (!file) return;
    previewMutation.mutate(buildFormData());
    setStep('importing');
  };

  const handleConfirm = () => {
    if (!file) return;
    confirmMutation.mutate(buildFormData());
    setStep('importing');
  };

  const reset = () => {
    setStep('upload');
    setFile(null);
    setHeaders([]);
    setMapping({});
    setPreviewData(null);
    setImportResult(null);
    setBatchId(null);
    if (fileRef.current) fileRef.current.value = '';
  };

  const currentIdx = DISPLAY_STEPS.indexOf(step as any);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('import.title')}</h1>
          <p className="page-subtitle">{t('import.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <button
            className={`btn-sm ${tab === 'wizard' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('wizard')}
          >
            <Upload className="w-4 h-4" />
            {t('import.newImport')}
          </button>
          <button
            className={`btn-sm ${tab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setTab('history')}
          >
            <History className="w-4 h-4" />
            {t('import.history')}
          </button>
        </div>
      </div>

      {/* ── History Tab ── */}
      {tab === 'history' && (
        <div className="card p-0">
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('import.module')}</th>
                  <th>{t('import.fileName')}</th>
                  <th>{t('import.totalRows')}</th>
                  <th>{t('import.successRows')}</th>
                  <th>{t('import.failedRows')}</th>
                  <th>{t('import.status')}</th>
                  <th>{t('common.date')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {historyLoading ? (
                  <tr><td colSpan={8} className="text-center py-8"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></td></tr>
                ) : !history?.length ? (
                  <tr><td colSpan={8} className="text-center py-10 text-gray-400">{t('import.noHistory')}</td></tr>
                ) : history.map((b: any) => (
                  <tr key={b.id}>
                    <td className="font-medium capitalize">{b.module}</td>
                    <td className="text-sm text-gray-500">{b.fileName || '—'}</td>
                    <td>{b.totalRows}</td>
                    <td className="text-green-600 font-medium">{b.successRows}</td>
                    <td className={b.failedRows > 0 ? 'text-red-500 font-medium' : ''}>{b.failedRows}</td>
                    <td>
                      <span className={`badge ${
                        b.status === 'confirmed' ? 'badge-green' :
                        b.status === 'rolled_back' ? 'badge-gray' :
                        b.status === 'preview' ? 'badge-blue' : 'badge-yellow'
                      }`}>
                        {b.status}
                      </span>
                    </td>
                    <td className="text-sm text-gray-500">
                      {new Date(b.createdAt).toLocaleString()}
                    </td>
                    <td>
                      {b.status === 'confirmed' && (
                        <button
                          className="btn-sm btn-secondary text-red-600 hover:bg-red-50"
                          onClick={() => rollbackMutation.mutate(b.id)}
                          disabled={rollbackMutation.isPending}
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                          {t('import.rollback')}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── Wizard Tab ── */}
      {tab === 'wizard' && (
        <>
          {/* Step indicator */}
          <div className="flex items-center gap-3 mb-8">
            {DISPLAY_STEPS.map((s, i) => {
              const isActive = step === s || (step === 'importing' && i === currentIdx - 1);
              const isDone = currentIdx > i && step !== 'importing';
              return (
                <div key={s} className="flex items-center gap-2">
                  <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${
                    isActive ? 'bg-blue-600 text-white'
                    : isDone ? 'bg-green-500 text-white'
                    : 'bg-[var(--color-border)] text-[var(--color-text-muted)]'
                  }`}>
                    {isDone ? <CheckCircle className="w-4 h-4" /> : i + 1}
                  </div>
                  <span className={`text-sm font-medium ${isActive ? 'text-blue-600' : 'text-[var(--color-text-muted)]'}`}>
                    {t(`import.step${s.charAt(0).toUpperCase() + s.slice(1)}`)}
                  </span>
                  {i < DISPLAY_STEPS.length - 1 && <div className="w-8 h-px bg-[var(--color-border)]" />}
                </div>
              );
            })}
          </div>

          {/* ── Step 1: Upload ── */}
          {step === 'upload' && (
            <div className="card p-8 max-w-2xl">
              <div className="mb-5">
                <label className="label">{t('import.importType')}</label>
                <select className="input" value={module} onChange={e => setModule(e.target.value as Module)}>
                  <option value="products">{t('nav.products')}</option>
                  <option value="customers">{t('nav.customers')}</option>
                  <option value="suppliers">{t('nav.suppliers')}</option>
                </select>
              </div>

              <div
                className="border-2 border-dashed border-[var(--color-border)] rounded-xl p-12 text-center cursor-pointer hover:border-blue-400 hover:bg-blue-50/10 transition-colors"
                onClick={() => fileRef.current?.click()}
                onDrop={e => { e.preventDefault(); const f = e.dataTransfer.files[0]; if (f) handleFileChange(f); }}
                onDragOver={e => e.preventDefault()}
              >
                <FileSpreadsheet className="w-12 h-12 mx-auto mb-3 text-gray-400" />
                <p className="font-medium text-[var(--color-text)]">{t('import.dropFile')}</p>
                <p className="text-sm text-[var(--color-text-muted)] mt-1">{t('import.fileHint')}</p>
                {file && <p className="text-sm text-blue-600 mt-2 font-medium">{file.name}</p>}
              </div>
              <input
                ref={fileRef}
                type="file"
                accept=".csv,.xlsx,.xls"
                className="hidden"
                onChange={e => { const f = e.target.files?.[0]; if (f) handleFileChange(f); }}
              />

              <div className="mt-6 p-4 bg-[var(--color-surface-2,#f9fafb)] rounded-lg">
                <p className="text-xs font-semibold text-[var(--color-text-muted)] mb-2">{t('import.columns')} {module}:</p>
                <div className="flex flex-wrap gap-1">
                  {MODULE_COLUMNS[module].map(col => (
                    <span key={col} className={`px-2 py-0.5 rounded text-xs ${
                      MODULE_REQUIRED[module].includes(col)
                        ? 'bg-red-100 text-red-700 font-medium'
                        : 'bg-[var(--color-border)] text-[var(--color-text-muted)]'
                    }`}>
                      {col}{MODULE_REQUIRED[module].includes(col) ? ' *' : ''}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* ── Step 2: Column Mapping ── */}
          {step === 'map' && (
            <div className="card p-6 max-w-3xl">
              <h3 className="font-semibold text-[var(--color-text)] mb-4">
                {t('import.mapColumns')} → {module}
              </h3>
              <div className="space-y-2 mb-4">
                {headers.map(h => (
                  <div key={h} className="flex items-center gap-4">
                    <div className="w-44 text-sm font-medium text-[var(--color-text)] truncate bg-[var(--color-border)] px-3 py-1.5 rounded">
                      {h}
                    </div>
                    <ArrowRight className="w-4 h-4 text-[var(--color-text-muted)] shrink-0" />
                    <select
                      className="input flex-1"
                      value={mapping[h] || ''}
                      onChange={e => setMapping(prev => ({ ...prev, [h]: e.target.value }))}
                    >
                      <option value="">— {t('import.skip')} —</option>
                      {MODULE_COLUMNS[module].map(col => (
                        <option key={col} value={col}>{col}</option>
                      ))}
                    </select>
                  </div>
                ))}
              </div>

              <div className="flex items-center gap-2 mb-5 p-3 bg-[var(--color-surface-2,#f9fafb)] rounded-lg">
                <input
                  type="checkbox"
                  id="skipDupes"
                  checked={skipDuplicates}
                  onChange={e => setSkipDuplicates(e.target.checked)}
                  className="rounded"
                />
                <label htmlFor="skipDupes" className="text-sm text-[var(--color-text-muted)] cursor-pointer">
                  {t('import.skipDuplicates')}
                </label>
              </div>

              <div className="flex gap-2">
                <button onClick={reset} className="btn-secondary btn-sm">
                  <ArrowLeft className="w-4 h-4" /> {t('common.back')}
                </button>
                <button
                  onClick={handlePreview}
                  disabled={previewMutation.isPending}
                  className="btn-primary btn-sm"
                >
                  {t('import.preview')} <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}

          {/* ── Loading ── */}
          {step === 'importing' && (
            <div className="card p-14 text-center max-w-md mx-auto">
              <Loader2 className="w-10 h-10 animate-spin mx-auto mb-4 text-blue-500" />
              <p className="font-medium text-[var(--color-text)]">{t('import.processing')}</p>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">{t('common.pleaseWait')}</p>
            </div>
          )}

          {/* ── Step 3: Preview ── */}
          {step === 'preview' && previewData && (
            <div className="space-y-4">
              {/* Summary bar */}
              <div className="flex gap-4">
                <div className="card p-4 flex-1 text-center">
                  <div className="text-2xl font-bold text-[var(--color-text)]">{previewData.totalRows}</div>
                  <div className="text-xs text-[var(--color-text-muted)]">{t('import.totalRows')}</div>
                </div>
                <div className="card p-4 flex-1 text-center">
                  <div className="text-2xl font-bold text-green-600">{previewData.validRows}</div>
                  <div className="text-xs text-[var(--color-text-muted)]">{t('import.validRows')}</div>
                </div>
                <div className="card p-4 flex-1 text-center">
                  <div className={`text-2xl font-bold ${previewData.errorRows > 0 ? 'text-red-500' : 'text-gray-400'}`}>
                    {previewData.errorRows}
                  </div>
                  <div className="text-xs text-[var(--color-text-muted)]">{t('import.errorRows')}</div>
                </div>
              </div>

              {previewData.errors.length > 0 && (
                <div className="p-4 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg">
                  <p className="text-sm font-semibold text-red-700 dark:text-red-400 mb-2 flex items-center gap-1">
                    <AlertTriangle className="w-4 h-4" />
                    {previewData.errors.length} {t('import.errorsFound')}
                  </p>
                  <ul className="text-xs text-red-600 dark:text-red-400 space-y-0.5">
                    {previewData.errors.slice(0, 10).map((e, i) => <li key={i}>• {e}</li>)}
                    {previewData.errors.length > 10 && (
                      <li className="text-gray-400">...{t('import.andMore', { count: previewData.errors.length - 10 })}</li>
                    )}
                  </ul>
                </div>
              )}

              <div className="card p-0">
                <div className="p-4 border-b border-[var(--color-border)]">
                  <p className="text-sm text-[var(--color-text-muted)]">
                    {t('import.showing')} {previewData.previewRows.length} {t('import.ofRows', { total: previewData.totalRows })}
                  </p>
                </div>
                <div className="overflow-x-auto">
                  <table className="table text-xs">
                    <thead>
                      <tr>
                        {Object.values(mapping).filter(Boolean).map(col => <th key={col}>{col}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {previewData.previewRows.map((row: any, i: number) => (
                        <tr key={i}>
                          {Object.values(mapping).filter(Boolean).map(col => (
                            <td key={col}>{row[col] ?? '—'}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="flex gap-2">
                <button onClick={() => setStep('map')} className="btn-secondary btn-sm">
                  <ArrowLeft className="w-4 h-4" /> {t('common.back')}
                </button>
                <button
                  onClick={handleConfirm}
                  disabled={previewData.validRows === 0 || confirmMutation.isPending}
                  className="btn-primary btn-sm"
                >
                  {t('import.confirmImport', { count: previewData.validRows })}
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}

          {/* ── Step 4: Done ── */}
          {step === 'done' && importResult && (
            <div className="card p-8 max-w-lg">
              <div className="text-center mb-6">
                {importResult.failedRows === 0 ? (
                  <CheckCircle className="w-14 h-14 mx-auto mb-3 text-green-500" />
                ) : (
                  <AlertTriangle className="w-14 h-14 mx-auto mb-3 text-yellow-500" />
                )}
                <h3 className="text-xl font-bold text-[var(--color-text)]">{t('import.importComplete')}</h3>
              </div>
              <div className="grid grid-cols-2 gap-4 mb-6">
                <div className="p-4 bg-green-50 dark:bg-green-900/10 rounded-lg text-center">
                  <div className="text-2xl font-bold text-green-600">{importResult.successRows}</div>
                  <div className="text-sm text-green-700 dark:text-green-400">{t('import.imported')}</div>
                </div>
                <div className="p-4 bg-red-50 dark:bg-red-900/10 rounded-lg text-center">
                  <div className="text-2xl font-bold text-red-600">{importResult.failedRows}</div>
                  <div className="text-sm text-red-700 dark:text-red-400">{t('import.failed')}</div>
                </div>
              </div>
              {importResult.duplicateRows > 0 && (
                <div className="p-3 bg-yellow-50 dark:bg-yellow-900/10 rounded-lg mb-4 text-sm text-yellow-700 dark:text-yellow-400">
                  {t('import.duplicatesSkipped', { count: importResult.duplicateRows })}
                </div>
              )}
              {importResult.errorSummary && (
                <div className="p-3 bg-red-50 dark:bg-red-900/10 rounded-lg mb-4">
                  <p className="text-xs font-semibold text-red-700 dark:text-red-400 mb-1">{t('import.errors')}:</p>
                  <p className="text-xs text-red-600 dark:text-red-400">{importResult.errorSummary}</p>
                </div>
              )}
              <div className="flex gap-2">
                <button onClick={reset} className="btn-primary flex-1">{t('import.importAnother')}</button>
                <button onClick={() => setTab('history')} className="btn-secondary flex-1">
                  <History className="w-4 h-4" /> {t('import.viewHistory')}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
