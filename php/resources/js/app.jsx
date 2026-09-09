import '../css/app.css';
import './bootstrap';
import './echo';

import { createInertiaApp, router } from '@inertiajs/react';
import { createRoot } from 'react-dom/client';
import i18n, { initI18n } from '@/i18n';
import LocaleSync from '@/Components/LocaleSync';
import BrandingFavicon from '@/Components/BrandingFavicon';
import ErrorBoundary from '@/Components/ErrorBoundary';
import { ThemeProvider } from '@/context/ThemeContext';
import { toast } from 'sonner';

initI18n();

// Keep the <meta name="csrf-token"> tag in sync across SPA navigations, for any
// code that still reads it directly for non-CSRF purposes. axios (window.axios
// and Inertia's own instance) already reads the live XSRF-TOKEN cookie itself
// on every request via its built-in xsrfCookieName/xsrfHeaderName handling, so
// it needs no help here — Spring Security's CookieCsrfTokenRepository only
// ever validates that cookie's value against the X-XSRF-TOKEN header, not
// X-CSRF-TOKEN from this tag.
function syncCsrfToken(page) {
    const token = page?.props?.csrf_token;
    if (!token) return;
    document.querySelector('meta[name="csrf-token"]')?.setAttribute('content', token);
}

router.on('success', (event) => syncCsrfToken(event.detail.page));

// Demo mode: every write is rejected server-side by EnsureNotDemoMode with a
// 403 { code: 'demo_mode' }. Because that is not a valid Inertia response,
// Inertia fires the cancelable `invalid` event — intercept it to suppress the
// default error modal and show a friendly toast instead. This is what lets the
// demo keep every button clickable and all data editable: the attempt is allowed
// through to the server, quietly blocked there, and the user is simply told why
// nothing changed. Needs a <Toaster /> in the active layout (Client/Inbox/Admin).
router.on('invalid', (event) => {
    const response = event.detail?.response;
    if (response?.status === 403 && response?.data?.code === 'demo_mode') {
        event.preventDefault();
        toast.error(response.data.message || i18n.t('demo.banner') || 'Demo mode: changes are disabled.');
    }
});

// Force a fresh server load when a page is restored from the back/forward cache
// (bfcache). Combined with the no-store headers on authenticated responses, this
// guarantees that pressing Back after logout re-checks auth on the server and
// redirects to login instead of showing a cached dashboard. `event.persisted` is
// true only on a bfcache restore, so normal navigation is unaffected.
window.addEventListener('pageshow', (event) => {
    if (event.persisted) {
        window.location.reload();
    }
});

const appName = import.meta.env.VITE_APP_NAME || 'Hub Notification';

// Wrapper cache keyed by page component. resolve() runs on every navigation;
// returning a fresh wrapper function each time gives the page a new component
// identity, so React remounts it even when Inertia preserves state (e.g. the
// redirect-back after failed validation) — wiping useForm state and losing
// the validation errors before they can render.
const wrappedPages = new WeakMap();

const pages = import.meta.glob('./Pages/**/*.jsx');

createInertiaApp({
    title: (title) => `${title} - ${appName}`,
    resolve: (name) => {
        let importPage = pages[`./Pages/${name}.jsx`];
        if (!importPage) {
            const lowerTarget = `./pages/${name.toLowerCase()}.jsx`;
            const matchingKey = Object.keys(pages).find(
                (key) => key.toLowerCase() === lowerTarget
            );
            if (matchingKey) {
                importPage = pages[matchingKey];
            }
        }
        if (!importPage) {
            throw new Error(`Page not found: ./Pages/${name}.jsx`);
        }
        return importPage().then((module) => {
            const Page = module.default;
            if (!wrappedPages.has(Page)) {
                const Wrapped = function WrappedWithThemeAndLocale(props) {
                    return (
                        <ThemeProvider>
                            <LocaleSync />
                            <BrandingFavicon />
                            <Page {...props} />
                        </ThemeProvider>
                    );
                };
                Wrapped.layout = Page.layout;
                wrappedPages.set(Page, Wrapped);
            }
            return wrappedPages.get(Page);
        });
    },
    setup({ el, App, props }) {
        syncCsrfToken(props.initialPage);
        const i18nProps = props.initialPage?.props?.i18n;
        if (i18nProps?.locale && i18nProps?.translations && typeof i18n.addResourceBundle === 'function') {
            i18n.addResourceBundle(i18nProps.locale, 'translation', i18nProps.translations, true);
            i18n.changeLanguage(i18nProps.locale);
        }
        const root = createRoot(el);
        // Wrap the whole app so a render error in ANY page or layout shows a
        // recoverable fallback instead of a blank white screen (full SPA unmount).
        root.render(
            <ErrorBoundary>
                <App {...props} />
            </ErrorBoundary>
        );
    },
    progress: {
        color: '#4B5563',
    },
});
