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
    { key: 'updates',      label: "What's new",    admin: true },
    { key: 'audit',        label: 'Records',       admin: true }
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
      box.innerHTML = items.map(c => {
        const website = c.website
            ? `<a href="${E(c.website)}" target="_blank" rel="noopener nofollow">${E(c.website)}</a>` : '<span class="muted">—</span>';
        const locations = (c.locations || []).map(l => `
          <li>${E([l.label, l.addressLine, l.city, l.state, l.zipCode, l.country].filter(Boolean).join(', '))}</li>`).join('');
        const submitter = c.submittedByDisplayName
            ? `${E(c.submittedByDisplayName)} (@${E(c.submittedByUsername)})` : 'unknown';
        return `
        <div class="card" style="margin-bottom:12px">
          <div style="cursor:pointer" data-toggle="${c.id}">
            <strong>${E(c.name)}</strong> ${(c.categories || []).map(x => `<span class="tag">${E(x.name)}</span>`).join('')}
            <span class="muted" style="font-size:.8rem;float:right">▼ details</span>
          </div>
          <div class="company-details" id="cdet-${c.id}" style="display:none;margin-top:10px;border-top:1px solid var(--border);padding-top:10px">
            <p style="margin:.3em 0"><strong>Website:</strong> ${website}</p>
            <p style="margin:.3em 0"><strong>Submitted by:</strong> ${submitter}</p>
            <p style="margin:.3em 0"><strong>Description:</strong><br>${E(c.description || '—')}</p>
            <p style="margin:.3em 0"><strong>Categories:</strong> ${(c.categories || []).map(x => E(x.name)).join(', ') || '—'}</p>
            <p style="margin:.3em 0 .2em"><strong>Locations (${(c.locations || []).length}):</strong></p>
            <ul style="margin:.2em 0 .4em 1.1em">${locations || '<li class="muted">none</li>'}</ul>
          </div>
          <div style="margin-top:10px">
            <button class="btn success small" data-approve="${c.id}">Approve</button>
            <button class="btn danger small" data-reject="${c.id}">Reject</button>
          </div>
        </div>`;
      }).join('');
      box.querySelectorAll('[data-toggle]').forEach(el => el.addEventListener('click', () => {
        const d = document.getElementById('cdet-' + el.dataset.toggle);
        d.style.display = d.style.display === 'none' ? 'block' : 'none';
      }));
      box.querySelectorAll('[data-approve]').forEach(b => b.addEventListener('click', () => reviewCompany(b.dataset.approve, 'APPROVE')));
      box.querySelectorAll('[data-reject]').forEach(b => b.addEventListener('click', () => reviewCompany(b.dataset.reject, 'REJECT')));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  async function reviewCompany(id, decision) {
    try { await RMW.api(`/api/mod/companies/${id}/review`, { method: 'POST', body: { decision } }); gAlert('success', 'Workplace ' + decision.toLowerCase() + 'd.'); loadWorkplaces(); }
    catch (e) { gAlert('error', e.message); }
  }

  // ---------------- Add workplace directly (admin-only, skips the pending queue) ----------------
  const AW_DEPARTMENTS = ['IT', 'MEDICAL', 'ADMIN', 'SALES', 'MARKETING', 'HR', 'FINANCE',
    'OPERATIONS', 'CUSTOMER_SERVICE', 'ENGINEERING', 'RETAIL', 'LOGISTICS', 'LEGAL', 'OTHER'];
  function awDeptLabel(d) { return d.split('_').map(w => w[0] + w.slice(1).toLowerCase()).join(' '); }
  const awSelectedCategories = new Set();

  function awRenderCategoryChips() {
    document.getElementById('awCategoryChips').innerHTML =
        [...awSelectedCategories].map(c => `<span class="tag" data-c="${E(c)}">${E(c)} ✕</span>`).join('') ||
        '<span class="muted" style="font-size:.85rem">No categories yet</span>';
    document.querySelectorAll('#awCategoryChips .tag').forEach(t =>
        t.addEventListener('click', () => { awSelectedCategories.delete(t.dataset.c); awRenderCategoryChips(); }));
  }

  function awAddCustomDeptTag(scopeEl, name) {
    const v = name.trim();
    if (!v) return;
    const container = scopeEl.querySelector('[data-f="departments"]');
    if ([...container.querySelectorAll('input[type=checkbox]')].some(c => c.value.toLowerCase() === v.toLowerCase())) return;
    const label = document.createElement('label');
    label.className = 'tag muted';
    label.style.cssText = 'cursor:pointer;user-select:none';
    label.innerHTML = `<input type="checkbox" value="${E(v)}" checked style="margin-right:4px;vertical-align:middle">${E(v)}`;
    label.title = 'Custom entry — uncheck to leave it out';
    container.appendChild(label);
  }

  function awAddLocation() {
    const div = document.createElement('div');
    div.className = 'card';
    div.style.marginBottom = '12px';
    div.innerHTML = `
        <div class="field-row">
          <div class="field"><label>Label (optional)</label><input type="text" data-f="label" placeholder="Downtown branch"></div>
          <div class="field"><label>Address</label><input type="text" data-f="addressLine" required></div>
        </div>
        <div class="field-row">
          <div class="field"><label>City</label><input type="text" data-f="city" required></div>
          <div class="field"><label>State</label><input type="text" data-f="state"></div>
          <div class="field"><label>ZIP</label><input type="text" data-f="zipCode" required></div>
        </div>
        <div class="field">
          <label>Departments at this location (optional)</label>
          <div class="tags" data-f="departments">
            ${AW_DEPARTMENTS.map(d => `<label class="tag muted" style="cursor:pointer;user-select:none">
                <input type="checkbox" value="${d}" style="margin-right:4px;vertical-align:middle">${awDeptLabel(d)}</label>`).join('')}
          </div>
          <div style="display:flex;gap:8px;margin-top:8px">
            <input type="text" class="customDeptInput" placeholder="Not listed? Add a custom department / position" maxlength="60" style="flex:1">
            <button type="button" class="btn secondary small addCustomDept">+ Add</button>
          </div>
        </div>
        <button type="button" class="btn ghost small removeLoc">Remove location</button>`;
    div.querySelector('.removeLoc').addEventListener('click', () => div.remove());
    const customInput = div.querySelector('.customDeptInput');
    const addCustom = () => { awAddCustomDeptTag(div, customInput.value); customInput.value = ''; customInput.focus(); };
    div.querySelector('.addCustomDept').addEventListener('click', addCustom);
    customInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); addCustom(); } });
    document.getElementById('awLocations').appendChild(div);
  }

  function awResetForm() {
    document.getElementById('awName').value = '';
    document.getElementById('awWebsite').value = '';
    document.getElementById('awDescription').value = '';
    awSelectedCategories.clear();
    awRenderCategoryChips();
    document.getElementById('awLocations').innerHTML = '';
    awAddLocation();
    RMW.clearToast(document.getElementById('addWorkplaceAlert'));
  }

  document.getElementById('toggleAddWorkplaceBtn').addEventListener('click', () => {
    const card = document.getElementById('addWorkplaceCard');
    const opening = card.classList.contains('hidden');
    if (opening && !document.getElementById('awLocations').children.length) awResetForm();
    card.classList.toggle('hidden', !opening);
    if (opening) card.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  document.getElementById('awCancelBtn').addEventListener('click', () => document.getElementById('addWorkplaceCard').classList.add('hidden'));
  document.getElementById('awAddLocationBtn').addEventListener('click', awAddLocation);
  document.getElementById('awCategoryInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const v = e.target.value.trim();
      if (v) { awSelectedCategories.add(v); awRenderCategoryChips(); }
      e.target.value = '';
    }
  });
  document.getElementById('awSubmitBtn').addEventListener('click', async () => {
    const alertEl = document.getElementById('addWorkplaceAlert');
    const locations = [...document.querySelectorAll('#awLocations .card')].map(card => {
      const get = f => card.querySelector(`[data-f="${f}"]`).value.trim();
      const departments = [...card.querySelectorAll('[data-f="departments"] input:checked')].map(c => c.value);
      return { label: get('label'), addressLine: get('addressLine'), city: get('city'),
        state: get('state'), zipCode: get('zipCode'), country: 'USA', departments };
    });
    const name = document.getElementById('awName').value.trim();
    if (!name) { RMW.toast(alertEl, 'error', 'Enter a company / workplace name.'); return; }
    if (!locations.length) { RMW.toast(alertEl, 'error', 'Add at least one location.'); return; }
    try {
      await RMW.api('/api/admin/companies', { method: 'POST', body: {
          name,
          website: document.getElementById('awWebsite').value.trim() || null,
          description: document.getElementById('awDescription').value.trim() || null,
          categories: [...awSelectedCategories],
          locations
        }});
      gAlert('success', `"${name}" was published.`);
      document.getElementById('addWorkplaceCard').classList.add('hidden');
      awResetForm();
      loadWorkplaces();
    } catch (err) {
      let msg = err.message;
      if (err.data && err.data.fieldErrors) msg = Object.values(err.data.fieldErrors).join(' ');
      RMW.toast(alertEl, 'error', msg);
    }
  });

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
          <p class="muted" style="font-size:.85rem;margin:6px 0 2px">
            Submitted by <strong>${E(p.submitterDisplayName || 'Unknown')}</strong>
            ${p.submitterUsername ? '(@' + E(p.submitterUsername) + ')' : ''}
            · ${RMW.fmtDate(p.createdAt)}
          </p>
          <p class="muted" style="font-size:.85rem;margin:2px 0">
            Name on account: <strong>${E(p.submitterFullName || '—')}</strong>
            <span style="font-size:.78rem">— should match the name on the document</span>
          </p>
          <p class="muted" style="font-size:.85rem;margin:2px 0">Note: ${E(p.note || 'No note.')}</p>
          <p><a class="pill-link" href="/api/mod/proofs/${p.id}/file" download="${E(p.originalFileName)}">⬇ Download document (${E(p.originalFileName)})</a>
             <span class="muted" style="font-size:.78rem">— downloads instead of opening, to avoid running malicious/corrupt files</span></p>
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
    try {
      await RMW.api(`/api/mod/proofs/${id}/review`, { method: 'POST', body: { decision, note } });
      gAlert('success', 'Proof ' + decision.toLowerCase() + 'd.');
    } catch (e) {
      // The user may have cancelled this submission after the list was loaded but before
      // it was reviewed — surface that plainly instead of a generic "not found", and
      // reload so the now-stale row doesn't just sit there to be clicked again.
      gAlert('error', e.status === 404 ? 'This submission was cancelled by the user and no longer exists.' : e.message);
    }
    loadProofs();
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
          <td style="white-space:normal;max-width:340px;overflow-wrap:anywhere;word-break:break-word">${f.title ? '<strong>' + E(f.title) + '</strong><br>' : ''}${E(f.body)}</td>
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
      box.innerHTML = `<p class="muted" style="font-size:.82rem">Click a user to view their full profile.</p>
        <table class="data"><thead><tr><th>User</th><th>Email / phone</th><th>Status</th><th>Actions</th></tr></thead><tbody>${
          items.map(u => `<tr class="user-row" data-uid="${u.id}" style="cursor:pointer">
          <td><strong>${E(u.displayName)}</strong><br><span class="muted">@${E(u.username)}</span> · ${E(u.role)}
            ${u.flaggedReason ? `<br><span class="badge rejected" title="${E(u.flaggedReason)}">FLAGGED</span>
              <span class="muted flag-reason" title="${E(u.flaggedReason)}">${E(u.flaggedReason)}</span>` : ''}</td>
          <td>${E(u.email)} ${u.emailVerified ? '✅' : '❌'}<br>${E(u.phoneNumber)} ${u.phoneVerified ? '✅' : '❌'}</td>
          <td>${u.enabled ? '<span class="badge approved">Active</span>' : '<span class="badge rejected">Disabled</span>'}</td>
          <td>
            <button class="btn small secondary" data-flag="${u.id}">${u.flaggedReason ? 'Unflag' : 'Flag'}</button>
            ${isAdmin ? `<button class="btn small secondary" data-enable="${u.id}" data-val="${!u.enabled}">${u.enabled ? 'Disable' : 'Enable'}</button>
            <button class="btn small danger" data-del="${u.id}">Delete</button>` : ''}
            </td></tr>
          <tr class="user-detail" id="udet-${u.id}" style="display:none"><td colspan="4" style="background:var(--surface-2)">
            <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:6px 18px;font-size:.86rem">
              <div><span class="muted">Full name</span><br><strong>${E(u.fullName || '—')}</strong></div>
              <div><span class="muted">Display name</span><br>${E(u.displayName)}</div>
              <div><span class="muted">Username</span><br>@${E(u.username)}</div>
              <div><span class="muted">Role</span><br>${E(u.role)}${(u.moderatorPermissions||[]).length ? ' — ' + u.moderatorPermissions.map(E).join(', ') : ''}</div>
              <div><span class="muted">Email</span><br>${E(u.email)} ${u.emailVerified ? '✅ verified' : '❌ unverified'}</div>
              <div><span class="muted">Phone</span><br>${E(u.phoneNumber)} ${u.phoneVerified ? '✅ verified' : '❌ unverified'}</div>
              <div><span class="muted">Account</span><br>${u.enabled ? 'Active' : 'Disabled'}</div>
              <div><span class="muted">Joined</span><br>${RMW.fmtDate(u.createdAt)}</div>
              <div><span class="muted">Last login</span><br>${u.lastLoginAt ? RMW.fmtDate(u.lastLoginAt) : 'never'}</div>
            </div>
            ${u.flaggedReason ? `<p style="margin:8px 0 0"><span class="badge rejected">FLAGGED</span> <strong>Reason:</strong> ${E(u.flaggedReason)}</p>` : ''}
          </td></tr>`).join('')
      }</tbody></table>`;
      box.querySelectorAll('.user-row').forEach(row => row.addEventListener('click', (e) => {
        if (e.target.closest('button')) return; // let action buttons work without toggling
        const d = document.getElementById('udet-' + row.dataset.uid);
        d.style.display = d.style.display === 'none' ? 'table-row' : 'none';
      }));
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
  const MOD_PERM_LABELS = {
    APPROVE_WORKPLACES: 'Approve workplaces',
    APPROVE_PROOFS: 'Approve proofs',
    MODERATE_FEEDBACK: 'Moderate feedback',
    MANAGE_USERS: 'Manage users'
  };

  function setModCheckboxes(perms) {
    document.querySelectorAll('[data-panel="moderators"] input[type=checkbox]').forEach(c => {
      c.checked = (perms || []).includes(c.value);
    });
  }

  async function saveModerator(username, perms) {
    const alertEl = document.getElementById('modAlert');
    try {
      const u = await RMW.api('/api/admin/moderators', { method: 'POST', body: { username, permissions: perms } });
      RMW.toast(alertEl, 'success', `${u.username} is now ${u.role} (${(u.moderatorPermissions || []).join(', ') || 'no extra permissions'}).`);
      loadModerators();
    } catch (e) { RMW.toast(alertEl, 'error', e.message); }
  }

  document.getElementById('saveModBtn').addEventListener('click', () => {
    const username = document.getElementById('modUsername').value.trim();
    const perms = [...document.querySelectorAll('[data-panel="moderators"] input[type=checkbox]:checked')].map(c => c.value);
    if (!username) { RMW.toast(document.getElementById('modAlert'), 'error', 'Enter a username.'); return; }
    saveModerator(username, perms);
  });

  async function loadModerators() {
    const box = document.getElementById('modList');
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      // No dedicated "list moderators" endpoint — the user search already returns role +
      // permissions, so filter client-side. Fine at this app's scale.
      const data = await RMW.api('/api/admin/users?size=200');
      const mods = (data.content || []).filter(u => u.role === 'MODERATOR');
      if (!mods.length) { box.innerHTML = '<p class="muted">No moderators yet.</p>'; return; }
      box.innerHTML = mods.map(u => `
        <div class="mod-list-item">
          ${RMW.avatarHtml(u)}
          <span class="who">${E(u.displayName)} <span class="muted" style="font-weight:400">@${E(u.username)}</span></span>
          <span class="perms">${(u.moderatorPermissions || []).map(p => `<span class="tag">${E(MOD_PERM_LABELS[p] || p)}</span>`).join('') || '<span class="tag muted">none</span>'}</span>
          <button class="btn small secondary" data-edit="${E(u.username)}">Edit</button>
          <button class="btn small danger revoke" data-revoke="${E(u.username)}">Revoke</button>
        </div>`).join('');
      box.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => {
        const u = mods.find(m => m.username === b.dataset.edit);
        document.getElementById('modUsername').value = u.username;
        setModCheckboxes(u.moderatorPermissions);
        document.getElementById('modUsername').scrollIntoView({ behavior: 'smooth', block: 'center' });
      }));
      box.querySelectorAll('[data-revoke]').forEach(b => b.addEventListener('click', () => {
        if (confirm(`Revoke all moderator permissions from @${b.dataset.revoke}?`)) saveModerator(b.dataset.revoke, []);
      }));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }

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
          <p style="margin:8px 0;overflow-wrap:anywhere;word-break:break-word">${E(f.message)}</p>
          <button class="btn small ${f.resolved ? 'secondary' : 'success'}" data-id="${f.id}" data-res="${!f.resolved}">${f.resolved ? 'Re-open' : 'Mark resolved'}</button>
        </div>`).join('');
      box.querySelectorAll('[data-id]').forEach(b => b.addEventListener('click', async () => {
        try { await RMW.api(`/api/admin/site-feedback/${b.dataset.id}/resolve`, { method: 'POST', body: { resolved: b.dataset.res === 'true' } }); loadSiteFeedback(); }
        catch (e) { gAlert('error', e.message); }
      }));
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }

  // ---------------- Updates ----------------
  let editingUpdateId = null;
  let updateCache = [];

  function resetUpdateForm() {
    editingUpdateId = null;
    document.getElementById('updateTitle').value = '';
    document.getElementById('updateBody').value = '';
    document.getElementById('updateTag').value = '';
    document.getElementById('postUpdateBtn').textContent = 'Publish update';
    const cancel = document.getElementById('cancelEditUpdate');
    if (cancel) cancel.style.display = 'none';
  }

  function startEditUpdate(id) {
    const u = updateCache.find(x => String(x.id) === String(id));
    if (!u) return;
    editingUpdateId = u.id;
    document.getElementById('updateTitle').value = u.title || '';
    document.getElementById('updateBody').value = u.body || '';
    document.getElementById('updateTag').value = u.tag || '';
    document.getElementById('postUpdateBtn').textContent = 'Save changes';
    let cancel = document.getElementById('cancelEditUpdate');
    if (!cancel) {
      cancel = document.createElement('button');
      cancel.id = 'cancelEditUpdate';
      cancel.className = 'btn ghost small';
      cancel.style.marginLeft = '8px';
      cancel.textContent = 'Cancel edit';
      cancel.addEventListener('click', resetUpdateForm);
      document.getElementById('postUpdateBtn').after(cancel);
    }
    cancel.style.display = 'inline-flex';
    document.getElementById('updateTitle').scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  async function loadUpdates() {
    const box = document.getElementById('updatesList');
    try {
      const data = await RMW.api('/api/site/updates?size=20');
      updateCache = data.content || [];
      box.innerHTML = updateCache.map(u => `
        <div class="card" style="margin-bottom:10px">
          <strong>${E(u.title)}</strong> ${u.tag ? `<span class="tag">${E(u.tag)}</span>` : ''}
          <span class="muted" style="font-size:.8rem">${RMW.fmtDate(u.createdAt)}</span>
          <p class="muted" style="margin:8px 0;font-size:.85rem">${E((u.body || '').slice(0, 160))}${(u.body || '').length > 160 ? '…' : ''}</p>
          <a class="btn small secondary" href="/update.html?id=${u.id}" target="_blank">View</a>
          <button class="btn small secondary" data-edit="${u.id}">Edit</button>
          <button class="btn small danger" data-del="${u.id}">Delete</button>
        </div>`).join('') || '<p class="muted">No updates yet.</p>';
      box.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => startEditUpdate(b.dataset.edit)));
      box.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click', async () => {
        if (!confirm('Delete this update?')) return;
        try {
          await RMW.api(`/api/admin/site-updates/${b.dataset.del}`, { method: 'DELETE' });
          if (String(editingUpdateId) === String(b.dataset.del)) resetUpdateForm();
          loadUpdates();
        } catch (e) { gAlert('error', e.message); }
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
      if (editingUpdateId) {
        await RMW.api(`/api/admin/site-updates/${editingUpdateId}`, { method: 'PUT', body: { title, body, tag } });
        RMW.toast(alertEl, 'success', 'Update saved.');
      } else {
        await RMW.api('/api/admin/site-updates', { method: 'POST', body: { title, body, tag } });
        RMW.toast(alertEl, 'success', 'Update published.');
      }
      resetUpdateForm();
      loadUpdates();
    } catch (e) { RMW.toast(alertEl, 'error', e.message); }
  });

  // ---------------- Records (audit log) ----------------
  let auditPage = 0;
  async function loadAudit() {
    const box = document.getElementById('auditList');
    const cat = document.getElementById('auditCategory').value;
    box.innerHTML = '<p class="muted">Loading…</p>';
    try {
      const data = await RMW.api(`/api/admin/audit?category=${cat}&page=${auditPage}&size=30`);
      const items = data.content || [];
      if (!items.length) { box.innerHTML = '<p class="muted">No records yet.</p>'; document.getElementById('auditPagination').innerHTML = ''; return; }
      const badgeClass = a => a === 'APPROVED' ? 'approved' : (a === 'REJECTED' ? 'rejected' : 'pending');
      box.innerHTML = items.map(a => `
        <div class="card" style="margin-bottom:10px">
          <span class="tag">${E(a.category)}</span>
          <span class="badge ${badgeClass(a.action)}">${E(a.action)}</span>
          <span class="muted" style="font-size:.8rem">${RMW.fmtDate(a.createdAt)} · by @${E(a.actor || 'system')}</span>
          <p style="margin:8px 0 4px"><strong>${E(a.summary)}</strong></p>
          ${a.detail ? `<pre style="white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;font-family:inherit;margin:0;color:var(--muted);font-size:.85rem">${E(a.detail)}</pre>` : ''}
        </div>`).join('');
      renderAuditPagination(data);
    } catch (e) { box.innerHTML = `<p class="muted">${E(e.message)}</p>`; }
  }
  function renderAuditPagination(data) {
    const pag = document.getElementById('auditPagination');
    const total = data.page.totalPages, cur = data.page.number;
    if (total <= 1) { pag.innerHTML = ''; return; }
    let html = `<button ${cur===0?'disabled':''} data-p="${cur-1}">‹</button>`;
    for (let i = 0; i < total; i++) html += `<button class="${i===cur?'active':''}" data-p="${i}">${i+1}</button>`;
    html += `<button ${cur>=total-1?'disabled':''} data-p="${cur+1}">›</button>`;
    pag.innerHTML = html;
    pag.querySelectorAll('button[data-p]').forEach(b => b.addEventListener('click', () => { auditPage = parseInt(b.dataset.p,10); loadAudit(); }));
  }
  document.getElementById('reloadAudit').addEventListener('click', () => { auditPage = 0; loadAudit(); });
  document.getElementById('auditCategory').addEventListener('change', () => { auditPage = 0; loadAudit(); });

  // reload buttons
  document.getElementById('reloadFeedback').addEventListener('click', loadFeedback);
  document.getElementById('feedbackStatus').addEventListener('change', loadFeedback);
  document.getElementById('userSearchBtn').addEventListener('click', loadUsers);
  document.getElementById('reloadSiteFb').addEventListener('click', loadSiteFeedback);
  document.getElementById('siteFbFilter').addEventListener('change', loadSiteFeedback);

  const LOADERS = {
    stats: loadStats, workplaces: loadWorkplaces, proofs: loadProofs, feedback: loadFeedback,
    users: loadUsers, categories: loadCategories, siteFeedback: loadSiteFeedback, updates: loadUpdates,
    audit: loadAudit, moderators: loadModerators
  };

  init();
})();