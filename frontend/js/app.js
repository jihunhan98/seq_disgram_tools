/**
 * Sequence Diagram Tool - application logic.
 *
 * Three screens:
 *   auth   - 로그인 / 회원가입
 *   list   - 로그인한 회원의 다이어그램 목록. 새 다이어그램도 여기서 만든다.
 *   editor - 목록에서 항목을 클릭했을 때 열리는 편집 화면.
 */
(function () {
    'use strict';

    const PLACEHOLDER =
        '<p class="placeholder">아직 다이어그램이 없습니다.<br>자연어로 수정 요청을 하거나 Mermaid 코드를 직접 입력해 보세요.</p>';

    const el = {};
    [
        'view-auth', 'view-list', 'view-editor', 'app-header',
        'tab-login', 'tab-signup', 'form-login', 'form-signup',
        'login-username', 'login-password', 'login-error',
        'signup-username', 'signup-password', 'signup-password2',
        'signup-name', 'signup-employee-no', 'signup-error',
        'brand-home', 'ai-status', 'member-badge', 'btn-logout',
        'list-count', 'search', 'btn-open-new', 'new-panel', 'btn-close-new',
        'new-title', 'new-description', 'new-prompt', 'new-hint',
        'btn-blank', 'btn-generate', 'diagram-grid', 'list-empty',
        'btn-back', 'doc-state', 'btn-delete', 'btn-save', 'title', 'description',
        'prompt', 'ai-hint', 'btn-refine',
        'preview', 'render-error', 'editor',
        'btn-copy', 'btn-paste', 'btn-zoom-in', 'btn-zoom-out', 'btn-zoom-reset',
        'zoom-level', 'btn-download-svg',
        'toast', 'confirm', 'confirm-title', 'confirm-message', 'confirm-ok', 'confirm-cancel'
    ].forEach((id) => {
        el[id.replace(/-([a-z])/g, (m, c) => c.toUpperCase())] = document.getElementById(id);
    });

    const state = {
        member: null,
        view: 'auth',
        diagrams: [],
        currentId: null,
        lastPrompt: null,
        zoom: 1,
        saved: { title: '', description: '', code: '' },
        renderToken: 0
    };

    let renderTimer = null;
    let toastTimer = null;

    /* ------------------------------------------------------------------ */
    /* Bootstrap                                                           */
    /* ------------------------------------------------------------------ */

    async function init() {
        mermaid.initialize({
            startOnLoad: false,
            theme: 'default',
            securityLevel: 'strict',
            sequence: { useMaxWidth: false, showSequenceNumbers: false }
        });

        wireEvents();
        Api.onUnauthorized = () => {
            state.member = null;
            showAuth('세션이 만료되었습니다. 다시 로그인해 주세요.');
        };

        loadHealth();
        await restoreSession();
    }

    /** A stored token means the member can go straight back to their list. */
    async function restoreSession() {
        if (!Api.getToken()) {
            showAuth();
            return;
        }
        try {
            state.member = await Api.me();
            await enterApp();
        } catch (error) {
            showAuth();
        }
    }

    function wireEvents() {
        el.tabLogin.addEventListener('click', () => setAuthTab('login'));
        el.tabSignup.addEventListener('click', () => setAuthTab('signup'));
        el.formLogin.addEventListener('submit', login);
        el.formSignup.addEventListener('submit', signup);

        el.btnLogout.addEventListener('click', logout);
        el.brandHome.addEventListener('click', () => goToList());

        el.search.addEventListener('input', renderList);
        el.btnOpenNew.addEventListener('click', () => toggleNewPanel(true));
        el.btnCloseNew.addEventListener('click', () => toggleNewPanel(false));
        el.btnGenerate.addEventListener('click', generateNew);
        el.btnBlank.addEventListener('click', startBlank);

        el.btnBack.addEventListener('click', () => goToList());
        el.btnRefine.addEventListener('click', refine);
        el.btnSave.addEventListener('click', save);
        el.btnDelete.addEventListener('click', remove);

        el.editor.addEventListener('input', () => {
            scheduleRender();
            updateDocState();
        });
        el.editor.addEventListener('keydown', handleEditorTab);
        el.title.addEventListener('input', updateDocState);
        el.description.addEventListener('input', updateDocState);

        el.btnCopy.addEventListener('click', copyCode);
        el.btnPaste.addEventListener('click', pasteCode);
        el.btnZoomIn.addEventListener('click', () => setZoom(state.zoom + 0.1));
        el.btnZoomOut.addEventListener('click', () => setZoom(state.zoom - 0.1));
        el.btnZoomReset.addEventListener('click', () => setZoom(1));
        el.btnDownloadSvg.addEventListener('click', downloadSvg);

        document.addEventListener('keydown', (event) => {
            if (state.view === 'editor'
                && (event.ctrlKey || event.metaKey)
                && event.key.toLowerCase() === 's') {
                event.preventDefault();
                save();
            }
        });

        window.addEventListener('beforeunload', (event) => {
            if (state.view === 'editor' && isDirty()) {
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
                const message =
                    'config/application-local.yml 에 app.ai.base-url 을 설정해야 AI 생성이 동작합니다.';
                el.aiHint.textContent = message;
                el.newHint.textContent = message;
            }
        } catch (error) {
            setStatus('warn', '백엔드 연결 실패');
            el.aiHint.textContent = error.message;
            el.newHint.textContent = error.message;
        }
    }

    function setStatus(kind, text) {
        el.aiStatus.className = `status-pill status-pill--${kind}`;
        el.aiStatus.textContent = text;
    }

    /* ------------------------------------------------------------------ */
    /* View switching                                                      */
    /* ------------------------------------------------------------------ */

    function showView(name) {
        state.view = name;
        el.viewAuth.hidden = name !== 'auth';
        el.viewList.hidden = name !== 'list';
        el.viewEditor.hidden = name !== 'editor';
        el.appHeader.hidden = name === 'auth';
        window.scrollTo(0, 0);
    }

    function showAuth(message) {
        Api.setToken(null);
        state.member = null;
        state.diagrams = [];
        showView('auth');
        setAuthTab('login');
        if (message) {
            showFormError(el.loginError, message);
        }
    }

    async function enterApp() {
        el.memberBadge.textContent = `${state.member.name} · ${state.member.employeeNo}`;
        await goToList();
    }

    async function goToList() {
        if (state.view === 'editor' && isDirty()) {
            const leave = await confirmDialog(
                '저장하지 않은 변경사항이 있습니다', '변경사항을 버리고 목록으로 돌아갈까요?');
            if (!leave) {
                return;
            }
        }
        showView('list');
        await loadList();
    }

    /* ------------------------------------------------------------------ */
    /* Auth                                                                */
    /* ------------------------------------------------------------------ */

    function setAuthTab(tab) {
        const login = tab === 'login';
        el.tabLogin.classList.toggle('is-active', login);
        el.tabSignup.classList.toggle('is-active', !login);
        el.formLogin.hidden = !login;
        el.formSignup.hidden = login;
        el.loginError.hidden = true;
        el.signupError.hidden = true;
    }

    function showFormError(target, message) {
        target.textContent = message;
        target.hidden = false;
    }

    async function login(event) {
        event.preventDefault();
        el.loginError.hidden = true;

        try {
            const result = await Api.login({
                username: el.loginUsername.value.trim(),
                password: el.loginPassword.value
            });
            Api.setToken(result.token);
            state.member = result.member;
            el.loginPassword.value = '';
            await enterApp();
        } catch (error) {
            showFormError(el.loginError, error.message);
        }
    }

    async function signup(event) {
        event.preventDefault();
        el.signupError.hidden = true;

        if (el.signupPassword.value !== el.signupPassword2.value) {
            showFormError(el.signupError, '비밀번호가 서로 일치하지 않습니다.');
            return;
        }

        try {
            const result = await Api.signup({
                username: el.signupUsername.value.trim(),
                password: el.signupPassword.value,
                name: el.signupName.value.trim(),
                employeeNo: el.signupEmployeeNo.value.trim()
            });
            Api.setToken(result.token);
            state.member = result.member;
            el.formSignup.reset();
            await enterApp();
        } catch (error) {
            showFormError(el.signupError, error.message);
        }
    }

    async function logout() {
        try {
            await Api.logout();
        } catch (error) {
            // Even if the server call fails the local token must go.
        }
        el.formLogin.reset();
        showAuth();
    }

    /* ------------------------------------------------------------------ */
    /* List screen                                                         */
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

        el.diagramGrid.replaceChildren();
        items.forEach((item) => {
            const card = document.createElement('button');
            card.type = 'button';
            card.className = 'diagram-card';

            const title = document.createElement('span');
            title.className = 'card-title';
            title.textContent = item.title;

            const description = document.createElement('span');
            description.className = 'card-description';
            description.textContent = item.description || '설명 없음';

            const meta = document.createElement('span');
            meta.className = 'card-meta';
            meta.textContent = `수정 ${formatDate(item.updatedAt)}`;

            card.append(title, description, meta);
            card.addEventListener('click', () => openDiagram(item.id));

            const li = document.createElement('li');
            li.appendChild(card);
            el.diagramGrid.appendChild(li);
        });

        el.listCount.textContent = state.diagrams.length
            ? `${state.diagrams.length}건`
            : '';

        const empty = items.length === 0;
        el.listEmpty.hidden = !empty;
        el.listEmpty.textContent = state.diagrams.length === 0
            ? '아직 저장된 다이어그램이 없습니다. “+ 새 다이어그램”으로 시작해 보세요.'
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

    function toggleNewPanel(open) {
        el.newPanel.hidden = !open;
        if (open) {
            el.newTitle.focus();
        } else {
            el.newTitle.value = '';
            el.newDescription.value = '';
            el.newPrompt.value = '';
        }
    }

    /** 목록 화면에서 자연어로 새 다이어그램을 만든다. */
    async function generateNew() {
        const title = el.newTitle.value.trim();
        const requirement = el.newPrompt.value.trim();

        if (!title) {
            toast('제목을 입력하세요.', 'error');
            el.newTitle.focus();
            return;
        }
        if (!requirement) {
            toast('자연어 요청을 입력하세요.', 'error');
            el.newPrompt.focus();
            return;
        }

        setBusy(el.btnGenerate, true, 'AI로 생성');
        try {
            const result = await Api.generate(requirement);
            openNewDocument(title, el.newDescription.value.trim(), result.mermaidCode, requirement);
            toast('AI가 다이어그램을 생성했습니다. 저장하면 목록에 추가됩니다.', 'success');
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            setBusy(el.btnGenerate, false, 'AI로 생성');
        }
    }

    function startBlank() {
        const title = el.newTitle.value.trim();
        if (!title) {
            toast('제목을 입력하세요.', 'error');
            el.newTitle.focus();
            return;
        }
        openNewDocument(
            title,
            el.newDescription.value.trim(),
            'sequenceDiagram\n    participant User as 사용자\n    participant API as 서비스\n    User->>API: 요청\n    API-->>User: 응답',
            null);
    }

    function openNewDocument(title, description, code, prompt) {
        state.currentId = null;
        state.lastPrompt = prompt;
        el.title.value = title;
        el.description.value = description;
        el.editor.value = code;
        el.prompt.value = '';

        toggleNewPanel(false);
        showView('editor');
        // Unsaved on purpose, so the dirty marker shows until it is stored.
        state.saved = { title: '', description: '', code: '' };
        setZoom(1);
        renderPreview();
        updateDocState();
    }

    /* ------------------------------------------------------------------ */
    /* Editor screen                                                       */
    /* ------------------------------------------------------------------ */

    async function openDiagram(id) {
        try {
            const diagram = await Api.getDiagram(id);
            state.currentId = diagram.id;
            state.lastPrompt = diagram.lastPrompt || null;
            el.title.value = diagram.title;
            el.description.value = diagram.description || '';
            el.editor.value = diagram.mermaidCode || '';
            el.prompt.value = '';

            snapshot();
            showView('editor');
            setZoom(1);
            renderPreview();
            updateDocState();
        } catch (error) {
            toast(error.message, 'error');
        }
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
        const isNew = state.currentId === null;
        el.btnDelete.hidden = isNew;
        el.btnSave.textContent = isNew ? '저장' : '덮어쓰기 저장';

        if (isNew) {
            el.docState.textContent = '아직 저장되지 않은 새 다이어그램입니다.';
        } else {
            el.docState.textContent = isDirty()
                ? `#${state.currentId} · 변경사항이 있습니다 (저장 시 덮어쓰기)`
                : `#${state.currentId} · 저장된 내용과 동일합니다`;
        }
    }

    /** 편집 화면의 자연어 요청은 항상 "현재 코드에 반영"이다. */
    async function refine() {
        const instruction = el.prompt.value.trim();
        if (!instruction) {
            toast('수정 요청을 입력하세요.', 'error');
            el.prompt.focus();
            return;
        }
        if (!el.editor.value.trim()) {
            toast('수정할 Mermaid 코드가 없습니다.', 'error');
            return;
        }

        setBusy(el.btnRefine, true, 'AI로 수정 반영');
        try {
            const result = await Api.refine(el.editor.value, instruction);
            el.editor.value = result.mermaidCode;
            state.lastPrompt = instruction;
            el.prompt.value = '';
            renderPreview();
            updateDocState();
            toast('수정사항을 반영했습니다.', 'success');
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            setBusy(el.btnRefine, false, 'AI로 수정 반영');
        }
    }

    function setBusy(button, busy, label) {
        button.disabled = busy;
        button.querySelector('.spinner').hidden = !busy;
        button.querySelector('.btn-label').textContent = busy ? '생성 중…' : label;
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
            state.currentId = null;
            snapshot();
            toast('삭제했습니다.', 'success');
            showView('list');
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
