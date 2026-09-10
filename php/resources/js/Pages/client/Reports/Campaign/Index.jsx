import ClientLayout from '@/Layouts/ClientLayout';
import { Head, Link } from '@inertiajs/react';
import { formatInTz } from '@/Utils/datetime';
import { usePage } from '@inertiajs/react';
import { useTranslation } from 'react-i18next';

const STATUS_COLORS = {
    draft: 'bg-gray-100 text-gray-700',
    scheduled: 'bg-blue-100 text-blue-700',
    sending: 'bg-amber-100 text-amber-700',
    sent: 'bg-emerald-100 text-emerald-700',
    paused: 'bg-orange-100 text-orange-700',
    failed: 'bg-red-100 text-red-700',
};

export default function CampaignReportIndex({ campaigns }) {
    const { t } = useTranslation();
    const userTz = usePage().props.timezone || 'Asia/Dhaka';

    return (
        <ClientLayout title={t('reports.campaigns_title')}>
            <Head title={t('reports.campaigns_title')} />

            <div className="space-y-6">
                <div>
                    <h1 className="text-xl font-bold text-gray-900 dark:text-white">{t('reports.campaigns_title')}</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400">{t('reports.campaigns_subtitle')}</p>
                </div>

                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40">
                                <th className="px-4 py-2 text-left text-xs text-gray-500">{t('reports.col_campaign')}</th>
                                <th className="px-4 py-2 text-left text-xs text-gray-500">{t('reports.col_channel')}</th>
                                <th className="px-4 py-2 text-left text-xs text-gray-500">{t('reports.col_status')}</th>
                                <th className="px-4 py-2 text-right text-xs text-gray-500">{t('reports.col_recipients')}</th>
                                <th className="px-4 py-2 text-right text-xs text-gray-500">{t('reports.col_delivered')}</th>
                                <th className="px-4 py-2 text-right text-xs text-gray-500">{t('reports.col_read')}</th>
                                <th className="px-4 py-2 text-right text-xs text-gray-500">{t('reports.col_failed')}</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                            {campaigns.map(c => (
                                <tr key={c.uuid} className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                                    <td className="px-4 py-2.5">
                                        <Link href={route('client.reports.campaigns.show', c.uuid)} className="font-medium text-indigo-600 hover:underline dark:text-indigo-400">
                                            {c.name}
                                        </Link>
                                        <div className="text-xs text-gray-400">{formatInTz(c.created_at, userTz)}</div>
                                    </td>
                                    <td className="px-4 py-2.5 text-gray-600 dark:text-gray-400 capitalize">{c.channel}</td>
                                    <td className="px-4 py-2.5">
                                        <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium capitalize ${STATUS_COLORS[c.status] ?? 'bg-gray-100 text-gray-700'}`}>
                                            {c.status}
                                        </span>
                                    </td>
                                    <td className="px-4 py-2.5 text-right text-gray-700 dark:text-gray-300">{c.total}</td>
                                    <td className="px-4 py-2.5 text-right text-emerald-600">{c.delivered_pct}%</td>
                                    <td className="px-4 py-2.5 text-right text-violet-600">{c.read_pct}%</td>
                                    <td className="px-4 py-2.5 text-right text-red-500">{c.failed_pct}%</td>
                                </tr>
                            ))}
                            {campaigns.length === 0 && (
                                <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400">{t('reports.no_campaigns')}</td></tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </ClientLayout>
    );
}
