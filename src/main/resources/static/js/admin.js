/* ============================================================
   RateMyWorkplace — admin panel logic
   ============================================================ */
(function () {
  const E = RMW.escapeHtml;
  let me = null;
  let isAdmin = false;

  const TABS = [
    { key: 'stats',        label: 'Statistics',    admin: true },
    { key: 'workplaces',   label: 'Workplaces',    perm: 'APPROVE_WORKPLACES' },
    { key: 'proofs',       label: 'Proofs',        perm: 'APPROVE_PROOFS' },
    { key: 'feedback',     label: 'Feedback',      perm: 'MODERATE_FEEDBACK' },
    { key: 'users',        label: 'Users',         perm: 'MANAGE_USERS' },
    { key: 'moderators',   label: 'Moderators',    admin: true },
    { key: 'categories',   label: 'Categories',    admin: true },
    { key: 'siteFeedback', label: 'Site feedback', admin: true },
    { key: 'updates',      label: "What's new",    admin: true }
  ];

  function can(tab) {
    if (tab.admin) return isAdmin;
    if (tab.perm) return isAdmin || (me.moderatorPermissions || []).includes(tab.perm);
    return true;
  }

  function gAlert(type, msg) { RMW.toast(document.getElementById('globalAlert'), type, msg); }

  async function init() {
    me = await RMW.currentUser(true);
    if (!me) { window.location.href = '/login.html?next=/admin.html'; return; }
    isAdmin = me.role === 'ADMIN';
    if (me.role !== 'ADMIN' && me.role !== 'MODERATOR') {
      document.querySelector('.admin-layout').innerHTML = '<p class="muted">You do not have access to the admin panel.</p>';
      return;
    }
    document.getElementById('roleLine').textContent =
        isAdmin ? 'Signed in as administrator.' : 'Signed in as moderator — ' + (me.moderatorPermissions || []).join(', ');

    buildNav();
  }

  function buildNav() {
    const nav = document.getElementById('adminNav');
    const visible = TABS.filter(can);
    nav.innerHTML = visible.map(t => `<button data-tab="${t.key}">${t.label}</button>`).join('');
    nav.querySelectorAll('button').forEach(b => b.addEventListener('click', () => activate(b.dataset.tab)));
    if (visible.length) activate(visible[0].key);
  }

  function activate(key) {
    document.querySelectorAll('.admin-nav button').forEach(b => b.classList.toggle('active', b.dataset.tab === key));
    document.querySelectorAll('.admin-panel').forEach(p => p.classList.toggle('active', p.dataset.panel === key));
    LOADERS[key] && LOADERS[key]();
  }

  // ---------------- Stats ----------------
  async function loadStats() {
    try {
      const s = await RMW.api('/api/admin/stats');
      document.getElementById('statCards').innerHTML = [
        ['Total users', s.totalUsers], ['Verified users', s.verifiedUsers],
        ['New (30d)', s.newUsersLast30Days], ['Moderators', s.moderators],
        ['Companies', s.totalCompanies], ['Approved', s.approvedCompanies],
        ['Pending workplaces', s.pendingCompanies], ['Locations', s.totalLocations],
        ['Total feedback', s.totalFeedback], ['Pending proofs', s.pendingProofs],
        ['Hidden feedback', s.pendingFeedback], ['Open site feedback', s.openSiteFeedback]
      ].map(([l, n]) => `<div class="stat-pill"><div class="num">${n}</div><div class="label">${l}</div></div>`).join('');
      window.__traffic = s.traffic;
      drawChart('pageViews');
    } catch (e) { gAlert('error', e.message); }
  }

  function drawChart(metric) {
    const data = window.__traffic || [];
    const max = Math.max(1, ...data.map(d => d[metric]));
    document.getElementById('trafficChart').innerHTML = data.map(d => {
      const h = Math.round((d[metric] / max) * 100);
      return `<div class="bar" style="height:${h}%" title="${d.day}: ${d[metric]}"></div>`;
    }).join('');
    const total = data.reduce((a, d) => a + d[metric], 0);
    document.getElementById('chartCaption').textContent = `${total} ${metric} over 30 days (peak ${max}/day).`;
  }

  document.querySelectorAll('input[name="metric"]').forEach(r =>
      r.addEventListener('change', () => drawChart(document.querySelector('input[name="metric"]:checked').value)));

  // ---------------- Workplaces ----------------
  async function loadWorkplaces() {
    const box = document.getElementById('workplacesList');
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api('/api/mod/companies/pending?size=50');
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">No pending workplaces. 🎉</p>'; return; }
      box.innerHTML = items.map(c => `
        <div class="card" style="margin-bottom:12px">
          <strong>${E(c.name)}</strong> ${(c.categories || []).map(x => `<span class="tag">${E(x.name)}</span>`).join('')}
          <p class="muted" style="font-size:.88rem">${E(c.description || '')}</p>
          <p class="muted" style="font-size:.82rem">${(c.locations || []).map(l => E([l.label, l.addressLine, l.city, l.state, l.zipCode].filter(Boolean).join(', '))).join(' · ')}</p>
          <button class="btn success small" data-approve="${c.id}">Approve</button>
          <button class="btn danger small" data-reject="${c.id}">Reject</button>
        </div>`).join('');
      box.querySelectorAll('[data-approve]').forEach(b => b.addEventListener('click', () => reviewCompany(b.dataset.approve, 'APPROVE')));
      box.querySelectorAll('[data-reject]').forEach(b => b.addEventListener('click', () => reviewCompany(b.dataset.reject, 'REJECT')));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  async function reviewCompany(id, decision) {
    try { await RMW.api(`/api/mod/companies/${id}/review`, { method: 'POST', body: { decision } }); gAlert('success', 'Workplace ' + decision.toLowerCase() + 'd.'); loadWorkplaces(); }
    catch (e) { gAlert('error', e.message); }
  }

  // ---------------- Proofs ----------------
  async function loadProofs() {
    const box = document.getElementById('proofsList');
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api('/api/mod/proofs/pending?size=50');
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">No pending proofs. 🎉</p>'; return; }
      box.innerHTML = items.map(p => `
        <div class="card" style="margin-bottom:12px">
          <strong>${E(p.companyName)}</strong> ${p.locationLabel ? '· ' + E(p.locationLabel) : '(company-wide)'}
          <span class="badge pending">PENDING</span>
          <p class="muted" style="font-size:.85rem">${E(p.note || 'No note.')}</p>
          <p><a class="pill-link" href="/api/mod/proofs/${p.id}/file" target="_blank">📎 View document (${E(p.originalFileName)})</a></p>
          <button class="btn success small" data-approve="${p.id}">Approve</button>
          <button class="btn danger small" data-reject="${p.id}">Reject</button>
        </div>`).join('');
      box.querySelectorAll('[data-approve]').forEach(b => b.addEventListener('click', () => reviewProof(b.dataset.approve, 'APPROVE')));
      box.querySelectorAll('[data-reject]').forEach(b => b.addEventListener('click', () => reviewProof(b.dataset.reject, 'REJECT')));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  async function reviewProof(id, decision) {
    let note = null;
    if (decision === 'REJECT') { note = prompt('Reason for rejection (optional):') || null; }
    try { await RMW.api(`/api/mod/proofs/${id}/review`, { method: 'POST', body: { decision, note } }); gAlert('success', 'Proof ' + decision.toLowerCase() + 'd.'); loadProofs(); }
    catch (e) { gAlert('error', e.message); }
  }

  // ---------------- Feedback ----------------
  async function loadFeedback() {
    const box = document.getElementById('feedbackList');
    const status = document.getElementById('feedbackStatus').value;
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api(`/api/mod/feedback?status=${status}&size=50&sort=createdAt,desc`);
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">No feedback found.</p>'; return; }
      box.innerHTML = `<table class="data"><thead><tr><th>Author</th><th>Rating</th><th>Content</th><th>Status</th><th>Actions</th></tr></thead><tbody>${
          items.map(f => `<tr>
          <td>${E(f.authorDisplayName)}<br><span class="muted" style="font-size:.78rem">${RMW.fmtDate(f.createdAt)}</span></td>
          <td>${RMW.stars(f.rating)}</td>
          <td style="white-space:normal;max-width:340px">${f.title ? '<strong>' + E(f.title) + '</strong><br>' : ''}${E(f.body)}</td>
          <td><span class="badge ${f.status.toLowerCase()}">${f.status === 'REJECTED' ? 'HIDDEN' : 'VISIBLE'}</span></td>
          <td>
            ${f.status === 'REJECTED'
              ? `<button class="btn success small" data-show="${f.id}">Restore</button>`
              : `<button class="btn small secondary" data-hide="${f.id}">Hide</button>`}
            <button class="btn danger small" data-del="${f.id}">Delete</button>
          </td></tr>`).join('')
      }</tbody></table>`;
      box.querySelectorAll('[data-hide]').forEach(b => b.addEventListener('click', () => moderateFb(b.dataset.hide, 'REJECT')));
      box.querySelectorAll('[data-show]').forEach(b => b.addEventListener('click', () => moderateFb(b.dataset.show, 'APPROVE')));
      box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', () => deleteFb(b.dataset.del)));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  async function moderateFb(id, decision) {
    let note = null;
    if (decision === 'REJECT') note = prompt('Reason / which T&C was violated (optional):') || null;
    try { await RMW.api(`/api/mod/feedback/${id}/moderate`, { method: 'POST', body: { decision, note } }); loadFeedback(); }
    catch (e) { gAlert('error', e.message); }
  }
  async function deleteFb(id) {
    if (!confirm('Permanently delete this feedback?')) return;
    try { await RMW.api(`/api/mod/feedback/${id}`, { method: 'DELETE' }); loadFeedback(); }
    catch (e) { gAlert('error', e.message); }
  }

  // ---------------- Users ----------------
  async function loadUsers() {
    const box = document.getElementById('usersList');
    const q = document.getElementById('userSearch').value.trim();
    const endpoint = isAdmin ? '/api/admin/users' : '/api/mod/users';
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api(`${endpoint}?size=50${q ? '&q=' + encodeURIComponent(q) : ''}`);
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">No users found.</p>'; return; }
      box.innerHTML = `<table class="data"><thead><tr><th>User</th><th>Email / phone</th><th>Status</th><th>Actions</th></tr></thead><tbody>${
          items.map(u => `<tr>
          <td><strong>${E(u.displayName)}</strong><br><span class="muted">@${E(u.username)}</span> · ${E(u.role)}
            ${u.flaggedReason ? `<br><span class="badge rejected" title="${E(u.flaggedReason)}">FLAGGED</span>` : ''}</td>
          <td>${E(u.email)} ${u.emailVerified ? '✅' : '❌'}<br>${E(u.phoneNumber)} ${u.phoneVerified ? '✅' : '❌'}</td>
          <td>${u.enabled ? '<span class="badge approved">Active</span>' : '<span class="badge rejected">Disabled</span>'}</td>
          <td>
            <button class="btn small secondary" data-flag="${u.id}">${u.flaggedReason ? 'Unflag' : 'Flag'}</button>
            ${isAdmin ? `<button class="btn small secondary" data-enable="${u.id}" data-val="${!u.enabled}">${u.enabled ? 'Disable' : 'Enable'}</button>
            <button class="btn small danger" data-del="${u.id}">Delete</button>` : ''}
          </td></tr>`).join('')
      }</tbody></table>`;
      box.querySelectorAll('[data-flag]').forEach(b => b.addEventListener('click', () => flagUser(b.dataset.flag)));
      box.querySelectorAll('[data-enable]').forEach(b => b.addEventListener('click', () => setEnabled(b.dataset.enable, b.dataset.val === 'true')));
      box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', () => deleteUser(b.dataset.del)));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  async function flagUser(id) {
    const reason = prompt('Flag reason (leave empty to clear the flag):');
    if (reason === null) return;
    const endpoint = isAdmin ? `/api/admin/users/${id}/flag` : `/api/mod/users/${id}/flag`;
    try { await RMW.api(endpoint, { method: 'POST', body: { reason: reason || null } }); loadUsers(); }
    catch (e) { gAlert('error', e.message); }
  }
  async function setEnabled(id, enabled) {
    try { await RMW.api(`/api/admin/users/${id}/enabled`, { method: 'POST', body: { enabled } }); loadUsers(); }
    catch (e) { gAlert('error', e.message); }
  }
  async function deleteUser(id) {
    if (!confirm('Delete this user and all their content? This cannot be undone.')) return;
    try { await RMW.api(`/api/admin/users/${id}`, { method: 'DELETE' }); loadUsers(); }
    catch (e) { gAlert('error', e.message); }
  }

  // ---------------- Moderators ----------------
  document.getElementById('saveModBtn').addEventListener('click', async () => {
    const username = document.getElementById('modUsername').value.trim();
    const perms = [...document.querySelectorAll('[data-panel="moderators"] input[type=checkbox]:checked')].map(c => c.value);
    const alertEl = document.getElementById('modAlert');
    if (!username) { RMW.toast(alertEl, 'error', 'Enter a username.'); return; }
    try {
      const u = await RMW.api('/api/admin/moderators', { method: 'POST', body: { username, permissions: perms } });
      RMW.toast(alertEl, 'success', `${u.username} is now ${u.role} (${(u.moderatorPermissions || []).join(', ') || 'no extra permissions'}).`);
    } catch (e) { RMW.toast(alertEl, 'error', e.message); }
  });

  // ---------------- Categories ----------------
  async function loadCategories() {
    const box = document.getElementById('categoriesList');
    try {
      const cats = await RMW.api('/api/categories');
      box.innerHTML = cats.map(c => `<span class="tag" style="cursor:pointer" data-del="${c.id}" title="Click to delete">${E(c.name)} ✕</span>`).join('');
      box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', async () => {
        if (!confirm('Delete category "' + b.textContent.replace(' ✕','') + '"?')) return;
        try { await RMW.api(`/api/admin/categories/${b.dataset.del}`, { method: 'DELETE' }); loadCategories(); }
        catch (e) { RMW.toast(document.getElementById('catAlert'), 'error', e.message); }
      }));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  document.getElementById('addCategoryBtn').addEventListener('click', async () => {
    const name = document.getElementById('newCategory').value.trim();
    if (!name) return;
    try { await RMW.api('/api/admin/categories', { method: 'POST', body: { name } }); document.getElementById('newCategory').value = ''; loadCategories(); }
    catch (e) { RMW.toast(document.getElementById('catAlert'), 'error', e.message); }
  });

  // ---------------- Site feedback ----------------
  async function loadSiteFeedback() {
    const box = document.getElementById('siteFeedbackList');
    const resolved = document.getElementById('siteFbFilter').value;
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api(`/api/admin/site-feedback?resolved=${resolved}&size=50&sort=createdAt,desc`);
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">Nothing here.</p>'; return; }
      box.innerHTML = items.map(f => `
        <div class="card" style="margin-bottom:10px">
          <span class="tag">${E(f.category || 'General')}</span>
          <span class="muted" style="font-size:.8rem">${RMW.fmtDate(f.createdAt)} · ${E(f.authorUsername || 'anonymous')} ${f.contactEmail ? '· ' + E(f.contactEmail) : ''}</span>
          <p style="margin:8px 0">${E(f.message)}</p>
          <button class="btn small ${f.resolved ? 'secondary' : 'success'}" data-id="${f.id}" data-res="${!f.resolved}">${f.resolved ? 'Re-open' : 'Mark resolved'}</button>
        </div>`).join('');
      box.querySelectorAll('[data-id]').forEach(b => b.addEventListener('click', async () => {
        try { await RMW.api(`/api/admin/site-feedback/${b.dataset.id}/resolve`, { method: 'POST', body: { resolved: b.dataset.res === 'true' } }); loadSiteFeedback(); }
        catch (e) { gAlert('error', e.message); }
      }));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }

  // ---------------- Updates ----------------
  async function loadUpdates() {
    const box = document.getElementById('updatesList');
    try {
      const data = await RMW.api('/api/site/updates?size=20');
      const items = data.content || [];
      box.innerHTML = items.map(u => `
        <div class="card" style="margin-bottom:10px">
          <strong>${E(u.title)}</strong> ${u.tag ? `<span class="tag">${E(u.tag)}</span>` : ''}
          <span class="muted" style="font-size:.8rem">${RMW.fmtDate(u.createdAt)}</span>
          <p style="margin:8px 0">${E(u.body)}</p>
          <button class="btn small danger" data-del="${u.id}">Delete</button>
        </div>`).join('') || '<p class="muted">No updates yet.</p>';
      box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', async () => {
        if (!confirm('Delete this update?')) return;
        try { await RMW.api(`/api/admin/site-updates/${b.dataset.del}`, { method: 'DELETE' }); loadUpdates(); }
        catch (e) { gAlert('error', e.message); }
      }));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  document.getElementById('postUpdateBtn').addEventListener('click', async () => {
    const title = document.getElementById('updateTitle').value.trim();
    const body = document.getElementById('updateBody').value.trim();
    const tag = document.getElementById('updateTag').value.trim() || null;
    const alertEl = document.getElementById('updateAlert');
    if (!title || !body) { RMW.toast(alertEl, 'error', 'Title and body are required.'); return; }
    try {
      await RMW.api('/api/admin/site-updates', { method: 'POST', body: { title, body, tag } });
      RMW.toast(alertEl, 'success', 'Update published.');
      document.getElementById('updateTitle').value = ''; document.getElementById('updateBody').value = ''; document.getElementById('updateTag').value = '';
      loadUpdates();
    } catch (e) { RMW.toast(alertEl, 'error', e.message); }
  });

  // reload buttons
  document.getElementById('reloadFeedback').addEventListener('click', loadFeedback);
  document.getElementById('feedbackStatus').addEventListener('change', loadFeedback);
  document.getElementById('userSearchBtn').addEventListener('click', loadUsers);
  document.getElementById('reloadSiteFb').addEventListener('click', loadSiteFeedback);
  document.getElementById('siteFbFilter').addEventListener('change', loadSiteFeedback);

  const LOADERS = {
    stats: loadStats, workplaces: loadWorkplaces, proofs: loadProofs, feedback: loadFeedback,
    users: loadUsers, categories: loadCategories, siteFeedback: loadSiteFeedback, updates: loadUpdates,
    moderators: () => {}
  };

  init();
})();