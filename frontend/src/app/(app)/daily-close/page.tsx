'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { dailyCloseApi, exportApi } from '@/lib/api';
import ExportButton from '@/components/ui/ExportButton';
import { formatMoney, formatDate } from '@/lib/utils';
import LoadingSpinner from '@/components/ui/LoadingSpinner';
import Modal from '@/components/ui/Modal';
import { useTranslation } from 'react-i18next';

export default function DailyClosePage() {
  const qc = useQueryClient();
  const { t } = useTranslation();
  const [showModal, setShowModal] = useState(false);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().slice(0, 10));
  const [actualCash, setActualCash] = useState('');
  const [comment, setComment] = useState('');

  const { data: listData, isLoading } = useQuery({
    queryKey: ['daily-close-list'],
    queryFn: () => dailyCloseApi.list().then(r => r.data),
  });

  const { data: prepared, isFetching: preparing } = useQuery({
    queryKey: ['daily-close-prepare', selectedDate],
    queryFn: () => dailyCloseApi.prepare(selectedDate).then(r => r.data),
    enabled: showModal,
  });

  const saveMutation = useMutation({
    mutationFn: (data: any) => dailyCloseApi.createOrUpdate(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['daily-close-list'] });
      toast.success('Day recorded');
      setShowModal(false);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  const closeMutation = useMutation({
    mutationFn: (id: number) => dailyCloseApi.close(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['daily-close-list'] });
      toast.success('Day closed and locked');
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Failed'),
  });

  const handleSave = () => {
    if (!prepared || !actualCash) return;
    saveMutation.mutate({
      closeDate: selectedDate,
      actualCash: parseFloat(actualCash),
      expectedCash: prepared.expectedCash,
      totalSales: prepared.totalSales,
      totalExpenses: prepared.totalExpenses,
      totalProfit: prepared.totalProfit,
      comment,
    });
  };

  const closes = listData?.content ?? listData ?? [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Daily Closing</h1>
          <p className="text-sm text-gray-500">Record and lock each day's cash count and results</p>
        </div>
        <div className="flex gap-2">
          <ExportButton permission="reports.export" filename="daily_close" fetcher={() => exportApi.dailyClose()} />
          <button onClick={() => setShowModal(true)} className="btn-primary btn-sm">
            + New Day Close
          </button>
        </div>
      </div>

      {isLoading ? <LoadingSpinner /> : (
        <>
          {closes.length === 0 ? (
            <div className="text-center py-16 text-gray-400">
              <div className="text-4xl mb-3">🔒</div>
              <p className="font-medium">No daily closes yet</p>
              <p className="text-sm mt-1">Close your first day to start tracking</p>
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Total Sales</th>
                    <th>Total Expenses</th>
                    <th>Profit</th>
                    <th>Expected Cash</th>
                    <th>Actual Cash</th>
                    <th>Difference</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {closes.map((dc: any) => {
                    const diff = parseFloat(dc.cashDifference);
                    return (
                      <tr key={dc.id} className={dc.status === 'closed' ? 'opacity-80' : ''}>
                        <td className="font-medium">{dc.closeDate}</td>
                        <td className="text-blue-600">{formatMoney(dc.totalSales)}</td>
                        <td className="text-red-600">{formatMoney(dc.totalExpenses)}</td>
                        <td className="text-green-600 font-medium">{formatMoney(dc.totalProfit)}</td>
                        <td>{formatMoney(dc.expectedCash)}</td>
                        <td>{formatMoney(dc.actualCash)}</td>
                        <td className={`font-medium ${diff > 0 ? 'text-green-600' : diff < 0 ? 'text-red-600' : 'text-gray-500'}`}>
                          {diff > 0 ? '+' : ''}{formatMoney(dc.cashDifference)}
                        </td>
                        <td>
                          {dc.status === 'closed'
                            ? <span className="badge-green text-xs">Closed</span>
                            : <span className="badge-yellow text-xs">Open</span>}
                        </td>
                        <td>
                          {dc.status === 'open' && (
                            <button
                              onClick={() => closeMutation.mutate(dc.id)}
                              disabled={closeMutation.isPending}
                              className="btn-danger btn-sm text-xs"
                            >
                              Lock Day
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <Modal
        open={showModal}
        onClose={() => { setShowModal(false); setActualCash(''); setComment(''); }}
        title="Record Daily Close"
        size="md"
      >
        <div className="space-y-4">
          <div>
            <label className="label">Date</label>
            <input
              type="date"
              className="input"
              value={selectedDate}
              onChange={e => setSelectedDate(e.target.value)}
            />
          </div>

          {preparing ? (
            <div className="text-center py-4 text-gray-400 text-sm">Loading day data...</div>
          ) : prepared && (
            <>
              <div className="grid grid-cols-2 gap-3 p-4 bg-gray-50 rounded-lg">
                {[
                  ['Total Sales', formatMoney(prepared.totalSales), 'text-blue-600'],
                  ['Total Expenses', formatMoney(prepared.totalExpenses), 'text-red-600'],
                  ['Profit', formatMoney(prepared.totalProfit), 'text-green-600'],
                  ['Cash In', formatMoney(prepared.cashIn), 'text-green-600'],
                ].map(([label, value, color]) => (
                  <div key={label as string}>
                    <p className="text-xs text-gray-500">{label as string}</p>
                    <p className={`font-semibold ${color as string}`}>{value as string}</p>
                  </div>
                ))}
              </div>

              <div>
                <label className="label">
                  Expected Cash in Register: <strong>{formatMoney(prepared.expectedCash)}</strong>
                </label>
                <label className="label mt-3">Actual Cash Count *</label>
                <input
                  type="number"
                  step="0.01"
                  className="input"
                  placeholder="Count the physical cash and enter here"
                  value={actualCash}
                  onChange={e => setActualCash(e.target.value)}
                  autoFocus
                />
                {actualCash && prepared.expectedCash && (
                  <p className={`text-sm mt-1 font-medium ${
                    parseFloat(actualCash) - parseFloat(prepared.expectedCash) < 0
                      ? 'text-red-600'
                      : parseFloat(actualCash) - parseFloat(prepared.expectedCash) > 0
                      ? 'text-green-600'
                      : 'text-gray-500'
                  }`}>
                    Difference: {(parseFloat(actualCash) - parseFloat(prepared.expectedCash)).toFixed(2)}
                  </p>
                )}
              </div>

              <div>
                <label className="label">Comment</label>
                <textarea
                  className="input"
                  rows={2}
                  placeholder="Any notes for this closing..."
                  value={comment}
                  onChange={e => setComment(e.target.value)}
                />
              </div>

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  disabled={!actualCash || saveMutation.isPending}
                  className="btn-primary"
                >
                  {saveMutation.isPending ? 'Saving...' : 'Save Closing'}
                </button>
              </div>
            </>
          )}
        </div>
      </Modal>
    </div>
  );
}
