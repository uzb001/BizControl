'use client';

import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { notificationSettingsApi } from '@/lib/api';
import { usePermission } from '@/hooks/usePermission';
import { Bell, Send, Save, AlertTriangle, CheckCircle2, ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';

const COMMON_TZ = [
  'Asia/Tashkent', 'Asia/Almaty', 'Asia/Shanghai', 'Asia/Dubai',
  'Europe/Moscow', 'Europe/Istanbul', 'Europe/London', 'UTC',
];
const LANGS = [
  { code: 'en', label: 'English' },
  { code: 'ru', label: 'Русский' },
  { code: 'uz', label: "O'zbekcha" },
];

/**
 * Notification settings — daily-monitor wiring + Telegram opt-in.
 * Bot token is global (env). Per-company we store the chat id, the local
 * trigger time, the timezone, the dailyMonitorEnabled flag and the language.
 */
export default function NotificationSettingsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const { can } = usePermission();
  const qc = useQueryClient();

  const editable = can('settings.edit_company');
  const { data, isLoading } = useQuery({
    queryKey: ['notification-settings'],
    queryFn: () => notificationSettingsApi.get().then(r => r.data),
    enabled: can('settings.view'),
    retry: false,
  });

  const [form, setForm] = useState({
    telegramChatId: '',
    dailyMonitorEnabled: true,
    dailyMonitorTime: '19:00',
    timezone: 'Asia/Tashkent',
    notificationLanguage: 'en',
  });

  // Hydrate local form whenever the GET completes (also on a refetch after save)
  useEffect(() => {
    if (!data) return;
    setForm({
      telegramChatId: data.telegramChatId ?? '',
      dailyMonitorEnabled: !!data.dailyMonitorEnabled,
      dailyMonitorTime: data.dailyMonitorTime ?? '19:00',
      timezone: data.timezone ?? 'Asia/Tashkent',
      notificationLanguage: data.notificationLanguage ?? 'en',
    });
  }, [data]);

  const saveMut = useMutation({
    mutationFn: () => notificationSettingsApi.update(form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['notification-settings'] });
      toast.success(t('notifications.saved', { defaultValue: 'Notification settings saved' })); },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });
  const testMut = useMutation({
    mutationFn: () => notificationSettingsApi.sendTest(),
    onSuccess: (res: any) => {
      const sent = res?.data?.sent;
      const reason = res?.data?.reason || '';
      if (sent) toast.success(reason || t('notifications.testSent', { defaultValue: 'Test sent' }));
      else      toast.error(reason || t('notifications.testFailed', { defaultValue: 'Test failed' }));
    },
    onError: (e: any) => toast.error(e?.response?.data?.error || t('errors.serverError', { defaultValue: 'Failed' })),
  });

  if (isLoading) return <div className="p-6 text-sm" style={{ color: 'rgb(var(--color-text-muted))' }}>{t('common.loading', { defaultValue: 'Loading…' })}</div>;

  const botConfigured = !!data?.telegramBotConfigured;

  return (
    <div className="max-w-3xl mx-auto">
      <div className="page-header">
        <div className="flex items-center gap-2">
          <button onClick={() => router.back()} className="btn-icon !w-8 !h-8"><ArrowLeft size={16} /></button>
          <h1 className="page-title flex items-center gap-2"><Bell size={20} /> {t('notifications.title', { defaultValue: 'Notifications' })}</h1>
        </div>
      </div>

      {!botConfigured && (
        <div className="card border p-3 mb-4 flex items-start gap-3"
          style={{ background: 'rgb(254, 249, 195)', borderColor: 'rgb(234, 179, 8)' }}>
          <AlertTriangle size={18} className="text-yellow-700 shrink-0 mt-0.5" />
          <div className="text-sm text-yellow-900">
            <div className="font-semibold">{t('notifications.botNotConfiguredTitle', { defaultValue: 'Telegram bot token is not configured.' })}</div>
            <div className="text-xs mt-1">{t('notifications.botNotConfiguredBody', { defaultValue: 'In-app alerts will still appear. To enable Telegram delivery, set APP_TELEGRAM_BOT_TOKEN in the backend environment.' })}</div>
          </div>
        </div>
      )}

      <div className="card p-5 space-y-5">
        {/* Daily monitor */}
        <section>
          <div className="text-sm font-semibold mb-3">{t('notifications.dailyMonitor', { defaultValue: 'Daily operation monitor' })}</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" disabled={!editable}
                checked={form.dailyMonitorEnabled}
                onChange={e => setForm({ ...form, dailyMonitorEnabled: e.target.checked })} />
              {t('notifications.enableDailyMonitor', { defaultValue: 'Run daily operation check' })}
            </label>
            <div>
              <label className="label">{t('notifications.dailyMonitorTime', { defaultValue: 'Daily check time (local)' })}</label>
              <input type="time" disabled={!editable || !form.dailyMonitorEnabled}
                className="input" value={form.dailyMonitorTime}
                onChange={e => setForm({ ...form, dailyMonitorTime: e.target.value })} />
            </div>
            <div>
              <label className="label">{t('notifications.timezone', { defaultValue: 'Timezone' })}</label>
              <select className="input" disabled={!editable}
                value={form.timezone} onChange={e => setForm({ ...form, timezone: e.target.value })}>
                {COMMON_TZ.includes(form.timezone) ? null : <option value={form.timezone}>{form.timezone}</option>}
                {COMMON_TZ.map(tz => <option key={tz} value={tz}>{tz}</option>)}
              </select>
            </div>
            <div>
              <label className="label">{t('notifications.language', { defaultValue: 'Notification language' })}</label>
              <select className="input" disabled={!editable}
                value={form.notificationLanguage} onChange={e => setForm({ ...form, notificationLanguage: e.target.value })}>
                {LANGS.map(l => <option key={l.code} value={l.code}>{l.label}</option>)}
              </select>
            </div>
          </div>
        </section>

        {/* Telegram */}
        <section>
          <div className="text-sm font-semibold mb-3 flex items-center gap-2">
            {t('notifications.telegram', { defaultValue: 'Telegram delivery' })}
            {botConfigured && <span className="badge-green text-[10px] flex items-center gap-1"><CheckCircle2 size={10} /> {t('notifications.botConfigured', { defaultValue: 'Bot configured' })}</span>}
          </div>
          <div className="space-y-3">
            <div>
              <label className="label">{t('notifications.telegramChatId', { defaultValue: 'Telegram chat id' })}</label>
              <input className="input font-mono" disabled={!editable}
                placeholder={t('notifications.telegramChatIdPlaceholder', { defaultValue: 'e.g. 123456789 or -100123456789 (group)' })}
                value={form.telegramChatId}
                onChange={e => setForm({ ...form, telegramChatId: e.target.value })} />
              <p className="text-xs mt-1" style={{ color: 'rgb(var(--color-text-muted))' }}>
                {t('notifications.telegramChatIdHint', { defaultValue: 'Open your chat with the bot, then /start it — its chat id will be in the bot dashboard or via @userinfobot.' })}
              </p>
            </div>
          </div>
        </section>

        <div className="flex flex-wrap justify-between gap-2 pt-2 border-t" style={{ borderColor: 'rgb(var(--color-border))' }}>
          <button onClick={() => testMut.mutate()}
            disabled={testMut.isPending || !editable || !form.telegramChatId}
            className="btn-secondary btn-sm gap-1.5"><Send size={13} /> {testMut.isPending ? t('common.saving', { defaultValue: 'Sending…' }) : t('notifications.sendTest', { defaultValue: 'Send test notification' })}</button>
          <button onClick={() => saveMut.mutate()} disabled={saveMut.isPending || !editable}
            className="btn-primary gap-1.5"><Save size={14} /> {saveMut.isPending ? t('common.saving', { defaultValue: 'Saving…' }) : t('common.save', { defaultValue: 'Save' })}</button>
        </div>
      </div>
    </div>
  );
}
