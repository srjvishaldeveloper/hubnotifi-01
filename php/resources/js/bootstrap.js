import axios from 'axios';
window.axios = axios;

window.axios.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest';

// No manual CSRF header needed here: axios's built-in xsrfCookieName/
// xsrfHeaderName defaults already read the XSRF-TOKEN cookie and attach it as
// X-XSRF-TOKEN on every request — which is what Spring Security's
// CookieCsrfTokenRepository actually validates (the old X-CSRF-TOKEN-from-
// meta-tag pattern this used to set was a Laravel-era header the backend
// never checks, so it was silently doing nothing).
