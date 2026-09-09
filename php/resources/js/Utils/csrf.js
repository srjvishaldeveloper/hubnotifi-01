/**
 * CSRF header for raw fetch() calls. Spring Security's CookieCsrfTokenRepository
 * validates the X-XSRF-TOKEN header against the XSRF-TOKEN cookie — axios (and
 * Inertia's internal axios instance) attaches this automatically, but a plain
 * fetch() call does not, so every such call must read the cookie and set it
 * by hand or it gets rejected with 403.
 */
export function getXsrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}
