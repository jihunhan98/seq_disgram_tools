/**
 * Thin wrapper around the backend REST API. Every call surfaces the server's
 * error message so the UI can show something more useful than "failed".
 */
(function () {
    'use strict';

    const baseUrl = window.APP_CONFIG.apiBaseUrl;

    async function request(method, path, body) {
        let response;
        try {
            response = await fetch(baseUrl + path, {
                method,
                headers: body ? { 'Content-Type': 'application/json' } : undefined,
                body: body ? JSON.stringify(body) : undefined
            });
        } catch (cause) {
            throw new Error(`백엔드(${baseUrl})에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.`);
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
        health: () => request('GET', '/api/health'),

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
