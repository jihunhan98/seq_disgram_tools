/**
 * Sequence Diagram Tool - application logic.
 *
 * Flow: natural language -> in-house AI API -> Mermaid code -> live preview,
 * with the code editable by hand and the result stored in Oracle.
 */
(function () {
    'use strict';

    const PLACEHOLDER =
        '<p class="placeholder">아직 다이어그램이 없습니다.<br>자연어로 요청하거나 Mermaid 코드를 직접 입력해 보세요.</p>';

    const el = {
        aiStatus: document.getElementById('ai-status'),
        btnNew: document.getElementById('btn-new'),
        btnReload: document.getElementById('btn-reload'),
        search: document.getElementById('search'),
        list: document.getElementById('diagram-list'),
        listEmpty: document.getElementById('list-empty'),
        title: document.getElementById('title'),
        description: document.getElementById('description'),
        docState: document.getElementById('doc-state'),
        btnSave: document.getElementById('btn-save'),
        btnDelete: document.getElementById('btn-delete'),
        tabGenerate: document.getElementById('tab-generate'),
        tabRefine: document.getElementById('tab-refine'),
        promptHelp: document.getElementById('prompt-help'),
        prompt: document.getElementById('prompt'),
        aiHint: document.getElementById('ai-hint'),
        btnRunAi: document.getElementById('btn-run-ai'),
        preview: document.getElementById('preview'),
        renderError: document.getElementById('render-error'),
        editor: document.getElementById('editor'),
        btnCopy: document.getElementById('btn-copy'),
        btnPaste: document.getElementById('btn-paste'),
        btnZoomIn: document.getElementById('btn-zoom-in'),
        btnZoomOut: document.getElementById('btn-zoom-out'),
        btnZoomReset: document.getElementById('btn-zoom-reset'),
        zoomLevel: document.getElementById('zoom-level'),
        btnDownloadSvg: document.getElementById('btn-download-svg'),
        toast: document.getElementById('toast'),
        confirm: document.getElementById('confirm'),
        confirmTitle: document.getElementById('confirm-title'),
        confirmMessage: document.getElementById('confirm-message'),
        confirmOk: document.getElementById('confirm-ok'),
        confirmCancel: document.getElementById('confirm-cancel')
    };

    const state = {
        currentId: null,
        diagrams: [],
        mode: 'generate',
        zoom: 1,
        lastPrompt: null,
        saved: { title: '', description: '', code: '' },
        renderToken: 0
    };

    let renderTimer = null;
    let toastTimer = null;

    /* ------------------------------------------------------------------ */
    /* Bootstrap                                                           */
    /* ------------------------------------------------------------------ */

    function init() {
        mermaid.initialize({
            startOnLoad: false,
            theme: 'default',
            securityLevel: 'strict',
            sequence: { useMaxWidth: false, showSequenceNumbers: false }
        });

        wireEvents();
        resetDocument();
        loadHealth();
        loadList();
    }

    function wireEvents() {
        el.btnNew.addEventListener('click', onNew);
        el.btnReload.addEventListener('click', () => loadList());
        el.search.addEventListener('input', renderList);

        el.tabGenerate.addEventListener('click', () => setMode('generate'));
        el.tabRefine.addEventListener('click', () => setMode('refine'));
        el.btnRunAi.addEventListener('click', runAi);

        el.editor.addEventListener('input', () => {
            scheduleRender();
            updateDocState();
        });
        el.editor.addEventListener('keydown', handleEditorTab);
        el.title.addEventListener('input', updateDocState);
        el.description.addEventListener('input', updateDocState);

        el.btnCopy.addEventListener('click', copyCode);
        el.btnPaste.addEventListener('click', pasteCode);
        el.btnSave.addEventListener('click', save);
        el.btnDelete.addEventListener('click', remove);

        el.btnZoomIn.addEventListener('click', () => setZoom(state.zoom + 0.1));
        el.btnZoomOut.addEventListener('click', () => setZoom(state.zoom - 0.1));
        el.btnZoomReset.addEventListener('click', () => setZoom(1));
        el.btnDownloadSvg.addEventListener('click', downloadSvg);

        // Ctrl/Cmd+S saves, matching what people expect from an editor.
        document.addEventListener('keydown', (event) => {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
                event.preventDefault();
                save();
            }
        });

        window.addEventListener('beforeunload', (event) => {
            if (isDirty()) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
    }

    async function loadHealth() {
        try {
            const health = await Api.health();
            if (health.aiConfigured) {
                setStatus('ok', `AI 연결됨 · ${health.aiModel}`);
            } else {
                setStatus('warn', 'AI 미설정');
                el.aiHint.textContent =
                    'config/application-local.yml 에 app.ai.base-url 을 설정해야 AI 생성이 동작합니다.';
            }
        } catch (error) {
            setStatus('warn', '백엔드 연결 실패');
            el.aiHint.textContent = error.message;
        }
    }

    function setStatus(kind, text) {
        el.aiStatus.className = `status-pill status-pill--${kind}`;
        el.aiStatus.textContent = text;
    }

    /* ------------------------------------------------------------------ */
    /* Saved diagram list                                                  */
    /* ------------------------------------------------------------------ */

    async function loadList() {
        try {
            state.diagrams = await Api.listDiagrams();
            renderList();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    function renderList() {
        const keyword = el.search.value.trim().toLowerCase();
        const items = state.diagrams.filter((item) =>
            !keyword ||
            item.title.toLowerCase().includes(keyword) ||
            (item.description || '').toLowerCase().includes(keyword));

        el.list.replaceChildren();
        items.forEach((item) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = item.id === state.currentId ? 'is-active' : '';

            const title = document.createElement('span');
            title.className = 'item-title';
            title.textContent = item.title;

            const meta = document.createElement('span');
            meta.className = 'item-meta';
            meta.textContent = formatDate(item.updatedAt);

            button.append(title, meta);
            button.addEventListener('click', () => open(item.id));

            const li = document.createElement('li');
            li.appendChild(button);
            el.list.appendChild(li);
        });

        el.listEmpty.hidden = items.length > 0;
        el.listEmpty.textContent = state.diagrams.length === 0
            ? '저장된 다이어그램이 없습니다.'
            : '검색 결과가 없습니다.';
    }

    function formatDate(value) {
        if (!value) {
            return '';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return '';
        }
        const pad = (n) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
            + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    /* ------------------------------------------------------------------ */
    /* Document lifecycle                                                  */
    /* ------------------------------------------------------------------ */

    async function open(id) {
        if (id === state.currentId && !isDirty()) {
            return;
        }
        if (isDirty() && !(await confirmDialog('저장하지 않은 변경사항이 있습니다', '변경사항을 버리고 다른 다이어그램을 열까요?'))) {
            return;
        }

        try {
            const diagram = await Api.getDiagram(id);
            state.currentId = diagram.id;
            state.lastPrompt = diagram.lastPrompt || null;
            el.title.value = diagram.title;
            el.description.value = diagram.description || '';
            el.editor.value = diagram.mermaidCode || '';
            snapshot();
            setMode('refine');
            el.prompt.value = '';
            renderPreview();
            renderList();
            updateDocState();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function onNew() {
        if (isDirty() && !(await confirmDialog('저장하지 않은 변경사항이 있습니다', '변경사항을 버리고 새로 시작할까요?'))) {
            return;
        }
        resetDocument();
        renderList();
    }

    function resetDocument() {
        state.currentId = null;
        state.lastPrompt = null;
        el.title.value = '';
        el.description.value = '';
        el.editor.value = '';
        el.prompt.value = '';
        setMode('generate');
        snapshot();
        renderPreview();
        updateDocState();
    }

    function snapshot() {
        state.saved = {
            title: el.title.value,
            description: el.description.value,
            code: el.editor.value
        };
    }

    function isDirty() {
        return el.title.value !== state.saved.title
            || el.description.value !== state.saved.description
            || el.editor.value !== state.saved.code;
    }

    function updateDocState() {
        el.btnDelete.disabled = state.currentId === null;

        if (state.currentId === null) {
            el.docState.textContent = isDirty()
                ? '저장되지 않은 새 다이어그램입니다. (변경됨)'
                : '저장되지 않은 새 다이어그램입니다.';
            el.btnSave.textContent = '저장';
        } else {
            el.docState.textContent = isDirty()
                ? `#${state.currentId} · 변경사항이 있습니다 (저장 시 덮어쓰기)`
                : `#${state.currentId} · 저장된 내용과 동일합니다`;
            el.btnSave.textContent = '덮어쓰기 저장';
        }
    }

    /* ------------------------------------------------------------------ */
    /* AI generate / refine                                                */
    /* ------------------------------------------------------------------ */

    function setMode(mode) {
        state.mode = mode;
        const generating = mode === 'generate';

        el.tabGenerate.classList.toggle('is-active', generating);
        el.tabRefine.classList.toggle('is-active', !generating);
        el.btnRunAi.querySelector('.btn-label').textContent = generating ? 'AI로 생성' : 'AI로 수정 반영';
        el.promptHelp.textContent = generating
            ? '그리고 싶은 흐름을 문장으로 설명하면 AI가 Mermaid 시퀀스 다이어그램으로 만들어 줍니다.'
            : '현재 Mermaid 코드에 반영할 수정사항을 문장으로 적어주세요. 기존 흐름은 유지된 채 요청한 부분만 바뀝니다.';
        el.prompt.placeholder = generating
            ? '예) 사용자가 로그인하면 API 게이트웨이가 인증 서버에 토큰을 요청하고, 성공하면 프로필 서비스에서 사용자 정보를 조회한다. 실패하면 401을 반환한다.'
            : '예) 인증 실패 시 감사 로그를 남기는 단계를 추가하고, 토큰 재발급 흐름을 alt 블록으로 분리해줘.';
    }

    async function runAi() {
        const prompt = el.prompt.value.trim();
        if (!prompt) {
            toast('자연어 요청을 입력하세요.', 'error');
            el.prompt.focus();
            return;
        }
        if (state.mode === 'refine' && !el.editor.value.trim()) {
            toast('수정할 Mermaid 코드가 없습니다. 먼저 생성하거나 코드를 입력하세요.', 'error');
            return;
        }

        setBusy(true);
        try {
            const result = state.mode === 'generate'
                ? await Api.generate(prompt)
                : await Api.refine(el.editor.value, prompt);

            el.editor.value = result.mermaidCode;
            state.lastPrompt = prompt;
            renderPreview();
            updateDocState();
            setMode('refine');
            el.prompt.value = '';
            toast('AI가 다이어그램을 생성했습니다.', 'success');
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            setBusy(false);
        }
    }

    function setBusy(busy) {
        el.btnRunAi.disabled = busy;
        el.btnRunAi.querySelector('.spinner').hidden = !busy;
        el.btnRunAi.querySelector('.btn-label').textContent = busy
            ? '생성 중…'
            : (state.mode === 'generate' ? 'AI로 생성' : 'AI로 수정 반영');
    }

    /* ------------------------------------------------------------------ */
    /* Preview                                                             */
    /* ------------------------------------------------------------------ */

    function scheduleRender() {
        clearTimeout(renderTimer);
        renderTimer = setTimeout(renderPreview, window.APP_CONFIG.renderDebounceMs);
    }

    async function renderPreview() {
        const code = el.editor.value.trim();
        el.renderError.hidden = true;

        if (!code) {
            el.preview.innerHTML = PLACEHOLDER;
            return;
        }

        // Renders can overlap while typing; only the newest one may paint.
        const token = ++state.renderToken;
        const renderId = `mermaid-render-${Date.now()}-${token}`;

        try {
            await mermaid.parse(code);
            const { svg } = await mermaid.render(renderId, code);

            // Must happen before painting: the rendered SVG carries renderId as
            // its own id, so cleaning up afterwards would delete the drawing.
            dropMermaidScratchNodes(renderId);

            if (token !== state.renderToken) {
                return;
            }
            el.preview.innerHTML = svg;
            applyZoom();
        } catch (error) {
            dropMermaidScratchNodes(renderId);
            if (token !== state.renderToken) {
                return;
            }
            // Keep the previous drawing on screen and explain what is wrong.
            el.renderError.hidden = false;
            el.renderError.textContent = `Mermaid 문법 오류\n${(error && error.message) || error}`;
        }
    }

    /** Removes the scratch nodes mermaid leaves in the body while rendering. */
    function dropMermaidScratchNodes(renderId) {
        document.getElementById(renderId)?.remove();
        document.getElementById(`d${renderId}`)?.remove();
    }

    function setZoom(value) {
        state.zoom = Math.min(3, Math.max(0.3, Math.round(value * 10) / 10));
        el.zoomLevel.textContent = `${Math.round(state.zoom * 100)}%`;
        applyZoom();
    }

    function applyZoom() {
        const svg = el.preview.querySelector('svg');
        if (svg) {
            svg.style.transformOrigin = 'top center';
            svg.style.transform = `scale(${state.zoom})`;
        }
    }

    function downloadSvg() {
        const svg = el.preview.querySelector('svg');
        if (!svg) {
            toast('내보낼 다이어그램이 없습니다.', 'error');
            return;
        }

        const clone = svg.cloneNode(true);
        clone.style.transform = '';
        const source = new XMLSerializer().serializeToString(clone);
        const blob = new Blob([source], { type: 'image/svg+xml;charset=utf-8' });
        const url = URL.createObjectURL(blob);

        const link = document.createElement('a');
        link.href = url;
        link.download = `${(el.title.value.trim() || 'sequence-diagram')}.svg`;
        link.click();
        URL.revokeObjectURL(url);
    }

    /* ------------------------------------------------------------------ */
    /* Editor helpers                                                      */
    /* ------------------------------------------------------------------ */

    function handleEditorTab(event) {
        if (event.key !== 'Tab') {
            return;
        }
        event.preventDefault();

        const { selectionStart, selectionEnd, value } = el.editor;
        el.editor.value = `${value.slice(0, selectionStart)}    ${value.slice(selectionEnd)}`;
        el.editor.selectionStart = el.editor.selectionEnd = selectionStart + 4;
        scheduleRender();
        updateDocState();
    }

    async function copyCode() {
        const code = el.editor.value;
        if (!code.trim()) {
            toast('복사할 코드가 없습니다.', 'error');
            return;
        }

        try {
            await navigator.clipboard.writeText(code);
        } catch (error) {
            // Clipboard API needs a secure context; fall back to a selection copy.
            el.editor.select();
            document.execCommand('copy');
            el.editor.setSelectionRange(code.length, code.length);
        }
        toast('Mermaid 코드를 복사했습니다.', 'success');
    }

    async function pasteCode() {
        try {
            const text = await navigator.clipboard.readText();
            if (!text.trim()) {
                toast('클립보드가 비어 있습니다.', 'error');
                return;
            }
            el.editor.value = text;
            renderPreview();
            updateDocState();
            toast('클립보드 내용을 붙여넣었습니다.', 'success');
        } catch (error) {
            toast('브라우저가 붙여넣기 권한을 막았습니다. 편집창에서 Ctrl+V 를 사용하세요.', 'error');
            el.editor.focus();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Persistence                                                         */
    /* ------------------------------------------------------------------ */

    async function save() {
        const title = el.title.value.trim();
        const code = el.editor.value.trim();

        if (!title) {
            toast('제목을 입력하세요.', 'error');
            el.title.focus();
            return;
        }
        if (!code) {
            toast('저장할 Mermaid 코드가 없습니다.', 'error');
            return;
        }

        const payload = {
            title,
            description: el.description.value.trim() || null,
            mermaidCode: code,
            lastPrompt: state.lastPrompt
        };

        try {
            const saved = state.currentId === null
                ? await Api.createDiagram(payload)
                : await Api.updateDiagram(state.currentId, payload);

            state.currentId = saved.id;
            snapshot();
            updateDocState();
            await loadList();
            toast(`저장했습니다. (#${saved.id})`, 'success');
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    async function remove() {
        if (state.currentId === null) {
            return;
        }
        const confirmed = await confirmDialog(
            '다이어그램 삭제',
            `"${el.title.value.trim()}" 을(를) 삭제합니다. 이 작업은 되돌릴 수 없습니다.`);
        if (!confirmed) {
            return;
        }

        try {
            await Api.deleteDiagram(state.currentId);
            toast('삭제했습니다.', 'success');
            resetDocument();
            await loadList();
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    /* ------------------------------------------------------------------ */
    /* Toast + confirm                                                     */
    /* ------------------------------------------------------------------ */

    function toast(message, kind) {
        clearTimeout(toastTimer);
        el.toast.textContent = message;
        el.toast.className = `toast${kind ? ` toast--${kind}` : ''}`;
        el.toast.hidden = false;
        toastTimer = setTimeout(() => {
            el.toast.hidden = true;
        }, kind === 'error' ? 6000 : 3000);
    }

    function confirmDialog(title, message) {
        el.confirmTitle.textContent = title;
        el.confirmMessage.textContent = message;
        el.confirm.hidden = false;

        return new Promise((resolve) => {
            const finish = (result) => {
                el.confirm.hidden = true;
                el.confirmOk.removeEventListener('click', onOk);
                el.confirmCancel.removeEventListener('click', onCancel);
                document.removeEventListener('keydown', onKey);
                resolve(result);
            };
            const onOk = () => finish(true);
            const onCancel = () => finish(false);
            const onKey = (event) => {
                if (event.key === 'Escape') {
                    finish(false);
                }
            };

            el.confirmOk.addEventListener('click', onOk);
            el.confirmCancel.addEventListener('click', onCancel);
            document.addEventListener('keydown', onKey);
            el.confirmOk.focus();
        });
    }

    document.addEventListener('DOMContentLoaded', init);
})();
