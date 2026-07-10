/* ============================================================
   RateMyWorkplace — shared frontend helpers
   ============================================================ */
const RMW = (() => {

    // ---- cookie / CSRF ----
    function getCookie(name) {
        const match = document.cookie.match('(^|;)\\s*' + name + '\\s*=\\s*([^;]+)');
        return match ? decodeURIComponent(match.pop()) : null;
    }

    async function ensureCsrf() {
        if (!getCookie('XSRF-TOKEN')) {
            try { await fetch('/api/auth/csrf', { credentials: 'same-origin' }); } catch (e) { /* ignore */ }
        }
    }

    // ---- core fetch wrapper ----
    async function api(path, { method = 'GET', body, form, headers = {} } = {}, _retried = false) {
        const opts = { method, credentials: 'same-origin', headers: { ...headers } };

        if (method !== 'GET' && method !== 'HEAD') {
            await ensureCsrf();
            const token = getCookie('XSRF-TOKEN');
            if (token) opts.headers['X-XSRF-TOKEN'] = token;
        }
        if (form) {
            opts.body = form; // FormData / URLSearchParams — let the browser set Content-Type
        } else if (body !== undefined) {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(body);
        }

        const res = await fetch(path, opts);

        // The CSRF token rotates on login/logout; transparently refresh once and retry.
        if (res.status === 403 && method !== 'GET' && method !== 'HEAD' && !_retried) {
            try { await fetch('/api/auth/csrf', { credentials: 'same-origin' }); } catch (e) { /* ignore */ }
            return api(path, { method, body, form, headers }, true);
        }
        const text = await res.text();
        let data = null;
        if (text) { try { data = JSON.parse(text); } catch (e) { data = text; } }

        if (res.status === 401 && data && data.code === 'SESSION_INVALIDATED') {
            cachedUser = null;
            const target = '/index.html?sessionExpired=1';
            if (window.location.pathname + window.location.search !== target) {
                window.location.href = target;
            }
            return new Promise(() => {}); // navigation is underway; don't resolve into stale UI
        }

        if (!res.ok) {
            const message = (data && data.message) || (typeof data === 'string' && data) || res.statusText;
            const err = new Error(message || 'Request failed');
            err.status = res.status;
            err.data = data;
            throw err;
        }
        return data;
    }

    // ---- auth ----
    let cachedUser = undefined;
    async function currentUser(force = false) {
        if (cachedUser !== undefined && !force) return cachedUser;
        try {
            const data = await api('/api/auth/me');
            cachedUser = (data && data.username) ? data : null;
        } catch (e) {
            if (e.status === 401) {
                cachedUser = null;
            } else {
                return null;
            }
        }
        return cachedUser;
    }

    async function login(username, password) {
        await ensureCsrf();
        const formData = new URLSearchParams({ username, password });
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': getCookie('XSRF-TOKEN') || '' },
            body: formData
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || 'Invalid credentials');
        }
        cachedUser = undefined;
        return true;
    }

    async function logout() {
        try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
        cachedUser = null;
        window.location.href = '/index.html';
    }

    // ---- rendering helpers ----
    function stars(value) {
        const rounded = Math.round(value);
        let out = '';
        for (let i = 1; i <= 5; i++) {
            out += i <= rounded ? '★' : '<span class="empty">★</span>';
        }
        return `<span class="stars">${out}</span>`;
    }

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /** Render a user's avatar: their uploaded picture, or their initials as a fallback. */
    function avatarHtml(user, sizeClass = 'sm') {
        const name = (user && (user.displayName || user.username)) || '?';
        const initials = name.trim().charAt(0) || '?';
        const px = sizeClass === 'lg' ? 96 : 30;
        if (user && user.avatarUrl) {
            return `<img class="avatar ${sizeClass}" width="${px}" height="${px}" src="${escapeHtml(user.avatarUrl)}" alt="${escapeHtml(name)}">`;
        }
        return `<span class="avatar ${sizeClass}" aria-hidden="true">${escapeHtml(initials)}</span>`;
    }

    function fmtDate(iso) {
        if (!iso) return '';
        try { return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }); }
        catch (e) { return iso; }
    }

    function qs(name, defaultValue = null) {
        return new URLSearchParams(window.location.search).get(name) ?? defaultValue;
    }

    function toast(el, type, message) {
        if (!el) return;
        el.className = `alert ${type} show`;
        el.textContent = message;
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    function clearToast(el) { if (el) el.className = 'alert'; }

    function showToast(message, type = 'info', duration = 6000) {
        let stack = document.getElementById('rmwToastStack');
        if (!stack) {
            stack = document.createElement('div');
            stack.id = 'rmwToastStack';
            stack.className = 'toast-stack';
            document.body.appendChild(stack);
        }
        const el = document.createElement('div');
        el.className = `toast ${type}`;
        el.textContent = message;
        stack.appendChild(el);
        setTimeout(() => {
            el.classList.add('closing');
            el.addEventListener('animationend', () => el.remove(), { once: true });
        }, duration);
    }

    function setLoading(btn, loading, label = 'Please wait…') {
        if (!btn) return;
        if (loading) {
            if (btn.dataset.originalHtml === undefined) {
                btn.dataset.originalHtml = btn.innerHTML;
            }
            btn.disabled = true;
            btn.classList.add('loading');
            btn.setAttribute('aria-busy', 'true');
            btn.innerHTML = `<span class="spinner-circle"></span> ${escapeHtml(label)}`;
        } else {
            btn.disabled = false;
            btn.classList.remove('loading');
            btn.removeAttribute('aria-busy');
            if (btn.dataset.originalHtml !== undefined) {
                btn.innerHTML = btn.dataset.originalHtml;
                delete btn.dataset.originalHtml;
            }
        }
    }

    // ---- theme (light / dark) ----
    function currentTheme() {
        return document.documentElement.getAttribute('data-theme')
            || localStorage.getItem('rmw-theme') || 'light';
    }
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        try { localStorage.setItem('rmw-theme', theme); } catch (e) { /* ignore */ }
        const btn = document.getElementById('themeToggle');
        if (btn) {
            btn.textContent = theme === 'dark' ? '☀️' : '🌙';
            btn.title = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
        }
    }
    function toggleTheme() { applyTheme(currentTheme() === 'dark' ? 'light' : 'dark'); }
    // Apply the stored preference as early as possible (a head snippet also does this to avoid flash).
    applyTheme(localStorage.getItem('rmw-theme') || 'light');

    // ---- minimal, safe markdown renderer (admin-authored update bodies) ----
    function markdown(src) {
        if (!src) return '';
        let s = escapeHtml(src);
        const blocks = [];
        s = s.replace(/```([\s\S]*?)```/g, (m, code) => { blocks.push(code.replace(/^\n/, '')); return `B${blocks.length - 1}`; });
        const inline = [];
        s = s.replace(/`([^`]+)`/g, (m, c) => { inline.push(c); return `I${inline.length - 1}`; });
        s = s.replace(/^###### (.*)$/gm, '<h6>$1</h6>')
            .replace(/^##### (.*)$/gm, '<h5>$1</h5>')
            .replace(/^#### (.*)$/gm, '<h4>$1</h4>')
            .replace(/^### (.*)$/gm, '<h3>$1</h3>')
            .replace(/^## (.*)$/gm, '<h2>$1</h2>')
            .replace(/^# (.*)$/gm, '<h1>$1</h1>');
        s = s.replace(/^\s*([-*_])\1{2,}\s*$/gm, '<hr>');
        s = s.replace(/^&gt; ?(.*)$/gm, '<blockquote>$1</blockquote>')
            .replace(/<\/blockquote>\n<blockquote>/g, '\n');
        s = s.replace(/!\[([^\]]*)\]\((https?:\/\/[^\s)]+|\/[^\s)]*)\)/g, '<img alt="$1" src="$2">');
        s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+|\/[^\s)]*)\)/g,
            '<a href="$2" target="_blank" rel="noopener nofollow">$1</a>');
        s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/__([^_]+)__/g, '<strong>$1</strong>')
            .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');
        s = s.replace(/(?:^|\n)((?:[-*+] .*(?:\n|$))+)/g, (m, list) => {
            const items = list.trim().split('\n').map(li => `<li>${li.replace(/^[-*+] /, '')}</li>`).join('');
            return `\n<ul>${items}</ul>\n`;
        });
        s = s.replace(/(?:^|\n)((?:\d+\. .*(?:\n|$))+)/g, (m, list) => {
            const items = list.trim().split('\n').map(li => `<li>${li.replace(/^\d+\. /, '')}</li>`).join('');
            return `\n<ol>${items}</ol>\n`;
        });
        s = s.split(/\n{2,}/).map(b => {
            const t = b.trim();
            if (!t) return '';
            if (/^<(h\d|ul|ol|pre|blockquote|hr|table|img)/.test(t)) return t;
            return `<p>${t.replace(/\n/g, '<br>')}</p>`;
        }).join('\n');
        s = s.replace(/I(\d+)/g, (m, i) => `<code>${inline[i]}</code>`);
        s = s.replace(/B(\d+)/g, (m, i) => `<pre><code>${blocks[i]}</code></pre>`);
        return s;
    }

    // ---- shared header / footer ----
    async function mountHeader() {
        const header = document.getElementById('site-header');
        if (!header) return;
        header.innerHTML = `
          <div class="container nav">
            <a class="brand" href="/index.html">Rate<span class="dot">My</span>Workplace</a>
            <button class="nav-toggle" aria-label="Menu" id="navToggle">☰</button>
            <nav class="nav-links" id="navLinks">
              <a href="/workplaces.html">Browse</a>
              <a href="/updates.html">Updates</a>
              <a href="/submit-workplace.html">Add Workplace</a>
              <span id="authArea"></span>
              <button class="theme-toggle" id="themeToggle" type="button" aria-label="Toggle dark mode">🌙</button>
            </nav>
          </div>`;
        document.getElementById('navToggle').addEventListener('click', () => {
            document.getElementById('navLinks').classList.toggle('open');
        });
        const themeBtn = document.getElementById('themeToggle');
        themeBtn.addEventListener('click', toggleTheme);
        applyTheme(currentTheme()); // sync the toggle icon to the active theme

        const user = await currentUser();
        const authArea = document.getElementById('authArea');
        if (user) {
            const adminLink = (user.role === 'ADMIN' || user.role === 'MODERATOR')
                ? '<a href="/admin.html" role="menuitem">Admin</a>' : '';
            authArea.innerHTML = `
              <div class="user-menu" id="userMenu">
                <button class="user-menu-trigger" id="userMenuTrigger" type="button" aria-haspopup="true" aria-expanded="false">
                  <span id="headerAvatarSlot">${avatarHtml(user)}</span>
                  <span class="name">${escapeHtml(user.displayName)}</span>
                  <span class="caret" aria-hidden="true">▾</span>
                </button>
                <div class="user-menu-dropdown" id="userMenuDropdown" role="menu">
                  <a href="/profile.html" role="menuitem">Profile</a>
                  ${adminLink}
                  <a href="#" id="logoutLink" role="menuitem">Logout</a>
                </div>
              </div>`;
            const userMenu = document.getElementById('userMenu');
            const trigger = document.getElementById('userMenuTrigger');
            const closeMenu = () => { userMenu.classList.remove('open'); trigger.setAttribute('aria-expanded', 'false'); };
            trigger.addEventListener('click', (e) => {
                e.stopPropagation();
                const opening = !userMenu.classList.contains('open');
                userMenu.classList.toggle('open', opening);
                trigger.setAttribute('aria-expanded', String(opening));
            });
            document.addEventListener('click', (e) => { if (!userMenu.contains(e.target)) closeMenu(); });
            document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeMenu(); });
            document.getElementById('logoutLink').addEventListener('click', (e) => { e.preventDefault(); logout(); });
        } else {
            authArea.innerHTML = `<a class="btn small" href="/login.html">Log in</a>`;
        }
    }

    async function mountFooter() {
        const footer = document.getElementById('site-footer');
        if (!footer) return;
        footer.innerHTML = `
          <div class="container">
            <div class="footer-grid">
              <div>
                <h3>Rate<span style="color:var(--primary)">My</span>Workplace</h3>
                <p>Honest, verified feedback about workplaces and working conditions — from people who were actually there.</p>
                <p class="muted">Feedback is only accepted from members who verify their employment.</p>
              </div>
              <div>
                <h3>Send us feedback</h3>
                <p class="muted" style="font-size:.85rem">Spotted a bug or have an idea? Let the admins know.</p>
                <div id="siteFeedbackAlert" class="alert"></div>
                <form id="siteFeedbackForm">
                  <div class="field"><input name="contactEmail" type="email" placeholder="Your email (optional)"></div>
                  <div class="field">
                    <select name="category">
                      <option value="Bug">Bug report</option>
                      <option value="Idea">Feature idea</option>
                      <option value="Content">Report content</option>
                      <option value="Other">Other</option>
                    </select>
                  </div>
                  <div class="field"><textarea name="message" rows="3" placeholder="Your message" required></textarea></div>
                  <button class="btn small" type="submit">Send feedback</button>
                </form>
              </div>
              <div>
                <h3>What's new</h3>
                <div id="footerNews"><p class="muted">Loading…</p></div>
                <p style="margin-top:10px"><a href="/updates.html">View all updates →</a></p>
              </div>
            </div>
            <div class="footer-bottom">
              © ${new Date().getFullYear()} RateMyWorkplace. Built as a complete Spring Boot demo. Ads shown across the site are illustrative AdSense slots.
            </div>
          </div>`;

        // load news
        try {
            const news = await api('/api/site/updates/latest');
            const box = document.getElementById('footerNews');
            // "What's new" shows only the title and date; each links to the full post.
            box.innerHTML = news.length ? news.map(n => `
              <a class="news-item" href="/update.html?id=${n.id}" style="display:block;text-decoration:none">
                <div class="t">${escapeHtml(n.title)}</div>
                <div class="d">${fmtDate(n.createdAt)}</div>
              </a>`).join('') : '<p class="muted">No updates yet.</p>';
        } catch (e) { /* ignore */ }

        // site feedback form
        const form = document.getElementById('siteFeedbackForm');
        const alertEl = document.getElementById('siteFeedbackAlert');
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fd = new FormData(form);
            try {
                await api('/api/site/feedback', { method: 'POST', body: {
                        contactEmail: fd.get('contactEmail') || null,
                        category: fd.get('category'),
                        message: fd.get('message')
                    }});
                toast(alertEl, 'success', 'Thanks! Your feedback was sent.');
                form.reset();
            } catch (err) {
                toast(alertEl, 'error', err.message);
            }
        });
    }

    function showSessionExpiredNoticeIfNeeded() {
        if (qs('sessionExpired') !== '1') return;
        showToast('Your session has ended because your account access changed. Please log in again.', 'warn', 8000);
        const url = new URL(window.location.href);
        url.searchParams.delete('sessionExpired');
        window.history.replaceState({}, '', url.pathname + url.search + url.hash);
    }

    async function mountChrome() {
        await mountHeader();
        await mountFooter();
        showSessionExpiredNoticeIfNeeded();
    }

    function updateHeaderAvatar(user) {
        const slot = document.getElementById('headerAvatarSlot');
        if (slot) slot.innerHTML = avatarHtml(user);
    }

    return { api, currentUser, login, logout, stars, avatarHtml, escapeHtml, fmtDate, qs, toast, clearToast, showToast,
        setLoading, markdown, applyTheme, toggleTheme, currentTheme,
        mountChrome, mountHeader, mountFooter, updateHeaderAvatar, ensureCsrf };
})();

document.addEventListener('DOMContentLoaded', () => { RMW.mountChrome(); });