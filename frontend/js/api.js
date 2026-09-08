/**
 * Thin wrapper around the backend REST API. Every call surfaces the server's
 * error message so the UI can show something more useful than "failed".
 *
 * The login token is kept in localStorage and sent as a bearer token, which
 * keeps the cross-origin setup (page on 5001, API on 5000) free of cookie and
 * SameSite concerns.
 */
(function () {
    'use strict';

    const baseUrl = window.APP_CONFIG.apiBaseUrl;
    const TOKEN_KEY = 'seqDiagram.token';

    function readToken() {
        try {
            return localStorage.getItem(TOKEN_KEY);
        } catch (error) {
            return null;
        }
    }

    function writeToken(token) {
        try {
            if (token) {
                localStorage.setItem(TOKEN_KEY, token);
            } else {
                localStorage.removeItem(TOKEN_KEY);
            }
        } catch (error) {
            // Private browsing can refuse storage; the session then lasts
            // until the tab is closed, which is still usable.
        }
    }

    let token = readToken();

    async function request(method, path, body, options) {
        const headers = {};
        if (body) {
            headers['Content-Type'] = 'application/json';
        }
        if (token && !(options && options.anonymous)) {
            headers.Authorization = `Bearer ${token}`;
        }

        let response;
        try {
            response = await fetch(baseUrl + path, {
                method,
                headers,
                body: body ? JSON.stringify(body) : undefined
            });
        } catch (cause) {
            throw new Error(`백엔드(${baseUrl})에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.`);
        }

        if (response.status === 401 && !(options && options.anonymous)) {
            // The stored token is gone or expired: drop it and let the app
            // fall back to the login screen.
            Api.setToken(null);
            if (typeof Api.onUnauthorized === 'function') {
                Api.onUnauthorized();
            }
        }

        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        let payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (cause) {
                payload = null;
            }
        }

        if (!response.ok) {
            const message = (payload && (payload.message || payload.error)) || text ||
                `요청이 실패했습니다 (HTTP ${response.status})`;
            throw new Error(message);
        }

        return payload;
    }

    window.Api = {
        onUnauthorized: null,

        getToken: () => token,
        setToken(value) {
            token = value || null;
            writeToken(token);
        },

        health: () => request('GET', '/api/health', null, { anonymous: true }),

        signup: (payload) => request('POST', '/api/auth/signup', payload, { anonymous: true }),
        login: (payload) => request('POST', '/api/auth/login', payload, { anonymous: true }),
        logout: () => request('POST', '/api/auth/logout'),
        me: () => request('GET', '/api/auth/me'),

        listDiagrams: () => request('GET', '/api/diagrams'),
        getDiagram: (id) => request('GET', `/api/diagrams/${id}`),
        createDiagram: (payload) => request('POST', '/api/diagrams', payload),
        updateDiagram: (id, payload) => request('PUT', `/api/diagrams/${id}`, payload),
        deleteDiagram: (id) => request('DELETE', `/api/diagrams/${id}`),

        generate: (requirement) => request('POST', '/api/ai/generate', { requirement }),
        refine: (mermaidCode, instruction) =>
            request('POST', '/api/ai/refine', { mermaidCode, instruction })
    };
})();
