import { Head, useForm, router } from '@inertiajs/react';
import ClientLayout from '@/Layouts/ClientLayout';
import { Trash2, MessageCircle, Instagram as InstagramIcon } from 'lucide-react';

function inputCls(error) {
    return `w-full rounded-lg border ${error ? 'border-red-400' : 'border-neutral-300 dark:border-neutral-600'} bg-white dark:bg-neutral-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500`;
}

function Field({ label, error, children }) {
    return (
        <div>
            <label className="block text-xs font-medium text-neutral-500 dark:text-neutral-400 mb-1">{label}</label>
            {children}
            {error && <p className="mt-1 text-xs text-red-500">{error}</p>}
        </div>
    );
}

function AccountRow({ account }) {
    const handleDisconnect = () => {
        if (!confirm(`Disconnect ${account.display_name}?`)) return;
        router.delete(`/app/channels/social/${account.id}`, { preserveScroll: true });
    };
    return (
        <div className="flex items-center justify-between rounded-lg border border-neutral-200 dark:border-neutral-700 px-4 py-3">
            <div>
                <p className="text-sm font-medium text-neutral-900 dark:text-neutral-100">{account.display_name}</p>
                <p className="text-xs text-neutral-500 dark:text-neutral-400 capitalize">{account.status}</p>
            </div>
            <button type="button" onClick={handleDisconnect}
                className="rounded-lg border border-neutral-200 dark:border-neutral-700 p-2 text-neutral-500 hover:text-red-500 hover:border-red-300 transition">
                <Trash2 className="h-4 w-4" />
            </button>
        </div>
    );
}

function MessengerCard({ accounts }) {
    const { data, setData, post, processing, errors, reset } = useForm({
        page_id: '', page_name: '', page_access_token: '',
    });

    const handleSubmit = (e) => {
        e.preventDefault();
        post('/app/channels/social/messenger', { preserveScroll: true, onSuccess: () => reset() });
    };

    return (
        <div className="rounded-xl border border-neutral-200 dark:border-neutral-700 bg-white dark:bg-neutral-900 p-6 space-y-4">
            <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-500 text-white shrink-0">
                    <MessageCircle className="h-4 w-4" />
                </div>
                <h3 className="font-semibold text-neutral-900 dark:text-neutral-100">Facebook Messenger</h3>
            </div>

            {accounts.length > 0 && (
                <div className="space-y-2">
                    {accounts.map(a => <AccountRow key={a.id} account={a} />)}
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-3 pt-2 border-t border-neutral-100 dark:border-neutral-800">
                <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    Connect another Facebook Page by pasting its Page ID and a long-lived Page Access Token
                    (from Meta Business Manager &gt; System Users, with pages_messaging permission).
                </p>
                <Field label="Page ID *" error={errors.page_id}>
                    <input type="text" value={data.page_id} onChange={e => setData('page_id', e.target.value)}
                        placeholder="1234567890" className={inputCls(errors.page_id)} />
                </Field>
                <Field label="Page Name">
                    <input type="text" value={data.page_name} onChange={e => setData('page_name', e.target.value)}
                        placeholder="My Business Page" className={inputCls()} />
                </Field>
                <Field label="Page Access Token *" error={errors.page_access_token}>
                    <input type="password" value={data.page_access_token} onChange={e => setData('page_access_token', e.target.value)}
                        placeholder="EAAG..." className={inputCls(errors.page_access_token)} />
                </Field>
                <button type="submit" disabled={processing}
                    className="w-full rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-60 py-2 text-sm font-medium text-white transition">
                    {processing ? 'Connecting…' : 'Connect Page'}
                </button>
            </form>
        </div>
    );
}

function InstagramCard({ accounts }) {
    const { data, setData, post, processing, errors, reset } = useForm({
        instagram_account_id: '', username: '', access_token: '', facebook_page_id: '',
    });

    const handleSubmit = (e) => {
        e.preventDefault();
        post('/app/channels/social/instagram', { preserveScroll: true, onSuccess: () => reset() });
    };

    return (
        <div className="rounded-xl border border-neutral-200 dark:border-neutral-700 bg-white dark:bg-neutral-900 p-6 space-y-4">
            <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-purple-500 to-pink-500 text-white shrink-0">
                    <InstagramIcon className="h-4 w-4" />
                </div>
                <h3 className="font-semibold text-neutral-900 dark:text-neutral-100">Instagram DMs</h3>
            </div>

            {accounts.length > 0 && (
                <div className="space-y-2">
                    {accounts.map(a => <AccountRow key={a.id} account={a} />)}
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-3 pt-2 border-t border-neutral-100 dark:border-neutral-800">
                <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    Connect an Instagram Business account linked to a Facebook Page, using a long-lived
                    access token with instagram_manage_messages permission.
                </p>
                <Field label="Instagram Account ID *" error={errors.instagram_account_id}>
                    <input type="text" value={data.instagram_account_id} onChange={e => setData('instagram_account_id', e.target.value)}
                        placeholder="17841400000000000" className={inputCls(errors.instagram_account_id)} />
                </Field>
                <Field label="Username">
                    <input type="text" value={data.username} onChange={e => setData('username', e.target.value)}
                        placeholder="mybrand" className={inputCls()} />
                </Field>
                <Field label="Access Token *" error={errors.access_token}>
                    <input type="password" value={data.access_token} onChange={e => setData('access_token', e.target.value)}
                        placeholder="EAAG..." className={inputCls(errors.access_token)} />
                </Field>
                <Field label="Linked Facebook Page ID">
                    <input type="text" value={data.facebook_page_id} onChange={e => setData('facebook_page_id', e.target.value)}
                        placeholder="1234567890" className={inputCls()} />
                </Field>
                <button type="submit" disabled={processing}
                    className="w-full rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-60 py-2 text-sm font-medium text-white transition">
                    {processing ? 'Connecting…' : 'Connect Account'}
                </button>
            </form>
        </div>
    );
}

export default function ChannelSetupIndex({ messengerAccounts, instagramAccounts, flash }) {
    const flashMessages = flash ?? {};

    return (
        <ClientLayout title="Channel Setup">
            <Head title="Channel Setup" />
            <div className="max-w-4xl space-y-5">
                <div>
                    <h2 className="text-xl font-semibold text-neutral-900 dark:text-neutral-100">Channel Setup</h2>
                    <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
                        Connect a Facebook Page or Instagram Business account to receive and reply to messages
                        from the Inbox.
                    </p>
                </div>

                {flashMessages.success && (
                    <div className="rounded-lg bg-green-50 dark:bg-green-900/30 text-green-800 dark:text-green-200 px-4 py-2 text-sm">
                        {flashMessages.success}
                    </div>
                )}
                {flashMessages.error && (
                    <div className="rounded-lg bg-red-50 dark:bg-red-900/30 text-red-800 dark:text-red-200 px-4 py-2 text-sm">
                        {flashMessages.error}
                    </div>
                )}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                    <MessengerCard accounts={messengerAccounts ?? []} />
                    <InstagramCard accounts={instagramAccounts ?? []} />
                </div>
            </div>
        </ClientLayout>
    );
}
