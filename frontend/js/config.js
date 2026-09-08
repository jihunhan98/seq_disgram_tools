/**
 * Runtime configuration for the static frontend.
 *
 * The page is served from port 5001 and the API lives on port 5000 of the same
 * host, so the default is derived from the current location. Override it here
 * (or set window.SEQ_DIAGRAM_API_BASE before this file loads) when the backend
 * runs elsewhere.
 */
window.APP_CONFIG = {
    apiBaseUrl:
        window.SEQ_DIAGRAM_API_BASE ||
        `${window.location.protocol}//${window.location.hostname || 'localhost'}:5000`,

    /** Debounce before the editor re-renders the preview, in milliseconds. */
    renderDebounceMs: 350
};
