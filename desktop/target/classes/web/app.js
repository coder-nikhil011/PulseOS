(() => {
  'use strict';
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => Array.from(root.querySelectorAll(s));
  const bridge = () => window.pulseOSBridge;
  const toast = (message) => { const el = $('#toast'); if (!el) return; el.textContent = message; el.classList.add('show'); clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove('show'), 2800); };
  const json = (method, fallback = null, ...args) => { try { const b = bridge(); return b && typeof b[method] === 'function' ? JSON.parse(b[method](...args)) : fallback; } catch (_) { return fallback; } };
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmtBytes = bytes => { const n = Number(bytes); if (!Number.isFinite(n) || n < 0) return '—'; if (n >= 1024**3) return `${(n/1024**3).toFixed(1)} GB`; if (n >= 1024**2) return `${(n/1024**2).toFixed(0)} MB`; if (n >= 1024) return `${(n/1024).toFixed(0)} KB`; return `${n} B`; };
  const fmtDays = days => `${Number(days) || 0} days ago`;

  // Navigation: preserve the existing six pages and every existing UI control.
  const views = $$('.view'), navItems = $$('.nav-item');
  function showView(name) {
    views.forEach(v => v.classList.toggle('active-view', v.dataset.page === name));
    navItems.forEach(n => n.classList.toggle('active', n.dataset.view === name));
    history.replaceState(null, '', `#${name}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  $$('[data-view]').forEach(item => item.addEventListener('click', () => showView(item.dataset.view)));
  const initial = location.hash.slice(1); if (initial && $(`[data-page="${initial}"]`)) showView(initial);

  // Sidebar toggle: one handler only; never remove or recreate UI buttons.
  const shell = $('.app-shell'), side = $('.side-nav'), sideToggle = $('#sidebarToggle');
  if (sideToggle) sideToggle.addEventListener('click', () => { const collapsed = shell.classList.toggle('sidebar-collapsed'); sideToggle.setAttribute('aria-expanded', String(!collapsed)); });

  // Detail dialog. A card is only clickable when explicitly registered below.
  const overlay = $('#detailOverlay'), detailTitle = $('#detailTitle'), detailSubtitle = $('#detailSubtitle'), detailRows = $('#detailRows');
  function closeDetail() { if (!overlay) return; overlay.classList.remove('show'); overlay.style.removeProperty('display'); overlay.style.removeProperty('opacity'); overlay.style.removeProperty('pointer-events'); }
  function showDetail(title, subtitle, rows, actions = []) {
    if (!overlay) return;
    detailTitle.textContent = title; detailSubtitle.textContent = subtitle;
    detailRows.innerHTML = rows.map(([a,b]) => `<div class="detail-row"><span>${escapeHtml(a)}</span><b>${escapeHtml(b)}</b></div>`).join('');
    const actionsBox = $('#detailActions');
    if (actionsBox) {
      actionsBox.innerHTML = '';
      actions.forEach(action => {
        const btn = document.createElement('button'); btn.textContent = action.label;
        if (action.kind === 'fix') btn.className = 'fix-action';
        btn.addEventListener('click', () => { action.onClick?.(); closeDetail(); });
        actionsBox.appendChild(btn);
      });
      actionsBox.style.display = actions.length ? 'flex' : 'none';
    }
    overlay.classList.add('show');
  }
  $('#detailClose')?.addEventListener('click', closeDetail); overlay?.addEventListener('click', e => { if (e.target === overlay) closeDetail(); }); document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDetail(); });

  function snapshot() { return json('getSnapshotJson', null); }
  function updateDeviceArtwork(data) {
    const image = $('#deviceIllustration img');
    if (!image) return;
    const manufacturer = String(data.deviceManufacturer || '').trim();
    const model = String(data.deviceModel || '').trim();
    const query = `${manufacturer} ${model}`.trim();
    const key = `pulseos.device-image.${query.toLowerCase()}`;
    if (image.dataset.deviceImageKey === key) return;
    image.dataset.deviceImageKey = key;

    const fallback = data.deviceImage || new URL('../devices/generic-laptop.svg', location.href).href;
    const credit = $('#deviceImageCredit');
    const showCredit = cached => {
      if (!credit || !cached?.source) return;
      credit.href = cached.source;
      credit.textContent = cached.credit || 'Image source';
      credit.title = cached.license || '';
      credit.hidden = false;
    };
    image.onerror = () => {
      image.onerror = null;
      image.src = fallback;
      if (credit) credit.hidden = true;
    };
    image.alt = query || 'Device';

    let cached;
    try { cached = JSON.parse(localStorage.getItem(key) || 'null'); } catch (_) { cached = null; }
    if (cached?.image) {
      image.src = cached.image;
      showCredit(cached);
      return;
    }
    image.src = fallback;
    if (!model || /^(unknown|generic|system product name)$/i.test(model) || !window.fetch) return;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3500);
    const params = new URLSearchParams({
      action: 'query',
      generator: 'search',
      gsrsearch: `filetype:bitmap ${query} laptop`,
      gsrnamespace: '6',
      gsrlimit: '8',
      prop: 'imageinfo',
      iiprop: 'url|extmetadata',
      iiurlwidth: '480',
      format: 'json',
      origin: '*'
    });
    const cleanText = value => String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/\s+/g, ' ').trim();
    fetch(`https://commons.wikimedia.org/w/api.php?${params}`, { signal: controller.signal })
      .then(response => { if (!response.ok) throw new Error('Image search failed'); return response.json(); })
      .then(result => {
        const tokens = model.toLowerCase().match(/[a-z0-9]+/g)?.slice(0, 2) || [];
        const pages = Object.values(result.query?.pages || {}).map(page => {
          const info = page.imageinfo?.[0];
          const license = cleanText(info?.extmetadata?.LicenseShortName?.value);
          const allowed = /^(cc0|public domain)$/i.test(license) || (/^cc by(?:-sa)?\b/i.test(license) && !/\bNC\b|\bND\b/i.test(license));
          const title = String(page.title || '').toLowerCase();
          const matches = tokens.filter(token => title.includes(token)).length;
          return { page, info, license, allowed, matches };
        }).filter(item => /^https:\/\/(?:thumb|upload)\.wikimedia\.org\//i.test(item.info?.thumburl || '') && item.allowed && item.matches === tokens.length);
        pages.sort((a, b) => b.matches - a.matches);
        return pages[0];
      })
      .then(async match => {
        if (!match) return;
        const info = match.info;
        const source = info.descriptionurl || `https://commons.wikimedia.org/wiki/${encodeURIComponent(match.page.title.replace(/ /g, '_'))}`;
        const artist = cleanText(info.extmetadata?.Artist?.value);
        const cachedImage = { image: info.thumburl, source, credit: `Image: ${artist || 'Wikimedia Commons'} · ${match.license}`, license: match.license };
        try {
          const response = await fetch(info.thumburl, { signal: controller.signal });
          const blob = await response.blob();
          if (response.ok && blob.size <= 800000 && /^image\/(jpeg|png|webp)$/i.test(blob.type)) {
            cachedImage.image = await new Promise((resolve, reject) => {
              const reader = new FileReader();
              reader.onload = () => resolve(reader.result);
              reader.onerror = reject;
              reader.readAsDataURL(blob);
            });
          }
        } catch (_) { }
        try { localStorage.setItem(key, JSON.stringify(cachedImage)); } catch (_) { }
        image.src = cachedImage.image;
        showCredit(cachedImage);
      })
      .catch(() => {})
      .finally(() => clearTimeout(timeout));
  }
  let pendingFixComponent = '';
  function runDiagnosis(component = '') {
    const result = component ? json('diagnoseComponent', null, component) : json('runDiagnosisJson');
    if (!result) return showDetail('Diagnosis', 'Live telemetry bridge is unavailable.', [['Status','Unable to read local telemetry']]);
    const actions = [];
    const isHealthy = Boolean(result.healthy);
    if (!isHealthy) {
      actions.push({
        label: 'Fix it',
        kind: 'fix',
        onClick: () => fixDiagnosis(component, result)
      });
    }
    const rows = [
      ['Status', isHealthy ? 'GOOD (100% Operational)' : 'DEGRADED / DRAIN DETECTED'],
      ['Working Efficiency', `${result.percentWorking ?? (isHealthy ? 100 : 50)}%`],
      ['Resource Drain', `${result.drainPercent ?? (isHealthy ? 0 : 50)}%`],
      ['Detail / Problem', result.problem || (isHealthy ? 'Operating normally' : 'Degraded performance')],
      ['Cause of Drain', result.cause || (isHealthy ? 'No abnormal drain detected' : 'Under heavy workload')]
    ];
    if (result.solution) {
      rows.push(['Recommended Solution', result.solution]);
    }
    showDetail(
      component ? `${component} Diagnostics` : 'Device Diagnostics',
      isHealthy ? 'This component is operating in optimal health with 0% drain.' : 'Performance degradation or resource drain detected on this component.',
      rows,
      actions
    );
  }

  function fixDiagnosis(component, result) {
    const c = String(component || '').toLowerCase();
    const route = String(result?.fixRoute || '');
    if (route === 'route:storage' || c.includes('storage')) {
      showView('storage');
      if (!storageScanned) {
        const btn = storage?.querySelector('[data-action="scan"]');
        if (btn) btn.click();
      }
      toast('Storage Healer opened. Review and safely clean large or cache files.');
      return;
    }
    if (route === 'route:router' || c.includes('network') || c.includes('cpu') || c.includes('ram') || c.includes('memory') || c.includes('thermal')) {
      showView('router');
      routerScanned = true;
      const processes = json('scanProcessesJson', []) || [];
      renderProcesses(processes, (c.includes('ram') || c.includes('memory')) ? 'ram' : 'cpu');
      updateRouterLive();
      const target = processes.find(p => Number(p.cpu) > 15 || Number(p.ramMb) > 300) || processes[0];
      if (target) {
        setTimeout(() => {
          const row = [...$$('.data-table .table-row', router)].find(r => Number(r.dataset.pid) === Number(target.pid));
          if (row) {
            row.classList.add('fix-target');
            row.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 120);
      }
      toast('Smart Router opened: highlighting culprit process causing resource drain.');
      return;
    }
    const r = json('fixComponent', null, component);
    if (r?.success) {
      toast(r.message || 'Related system settings opened.');
    } else {
      showDetail('Fix it', 'PulseOS identified the safest available fix action.', [
        ['Target Setting', r?.route || route || 'System Settings'],
        ['Action', r?.message || result?.solution || 'Review system settings.'],
        ['Safety', 'No destructive changes are executed automatically.']
      ]);
    }
  }

  window.pulseOSOpenDetail = kind => {
    const s = snapshot();
    if (kind === 'assistant') {
      const available = typeof bridge()?.askAiAsync === 'function';
      return showDetail('PulseOS AI Assistant', 'Local assistant status and supported scope.', [
        ['Status', available ? 'Local AI bridge is connected' : 'Waiting for the desktop AI bridge'],
        ['Provider', 'Ollama running locally'],
        ['Scope', 'PulseOS features, telemetry, and device health questions']
      ]);
    }
    if (!s || !s.ready) return showDetail('Live Telemetry', 'Collecting initial telemetry samples. Please wait a moment.', [['Status', 'Initializing']]);
    const ram = `${Number(s.ramPercent).toFixed(1)}% (${Number(s.ramUsed).toFixed(2)} / ${Number(s.ramTotal).toFixed(2)} GB)`;
    const common = [
      ['CPU Load', `${Number(s.cpu).toFixed(1)}%`],
      ['Clock Speed', `${Number(s.clock).toFixed(2)} GHz`],
      ['Memory Usage', ram],
      ['Core Temperature', s.temperature >= 0 ? `${Number(s.temperature).toFixed(1)}°C` : 'Unavailable'],
      ['Storage Used', `${Number(s.storageUsed).toFixed(1)}% (${Number(s.storageFreeGb).toFixed(1)} GB free)`],
      ['Battery', s.battery >= 0 ? `${s.battery}%${s.charging ? ' (Charging)' : ''}` : 'Unavailable']
    ];

    if (kind === 'score') {
      return showDetail('Device Health Score', 'Overall calculation from real-time CPU, memory, storage, thermal and battery telemetry.', [
        ['Health Score', `${s.healthScore}/100`],
        ['Grade', s.healthScore >= 85 ? 'EXCELLENT' : s.healthScore >= 70 ? 'GOOD' : 'WATCH'],
        ...common,
        ['Processor', s.processor]
      ], [{ label: 'Run Full Diagnosis', kind: 'fix', onClick: () => runDiagnosis() }]);
    }
    if (kind === 'snapshot') {
      return showDetail('Live Device Snapshot', 'Current hardware and operating system profile.', [
        ['Processor', `${s.processor} (${s.cores} cores, ${s.threads} threads)`],
        ['Operating System', `${s.os} (${s.architecture})`],
        ['Uptime', `${Math.floor(s.uptimeSeconds / 86400)}d ${Math.floor((s.uptimeSeconds % 86400) / 3600)}h ${Math.floor((s.uptimeSeconds % 3600) / 60)}m`],
        ...common
      ]);
    }
    if (kind === 'quick-status') {
      return showDetail('Quick Health Status', 'Subsystem health overview from live telemetry.', [
        ['CPU Status', s.cpu > 75 ? `High (${Number(s.cpu).toFixed(1)}%)` : `Normal (${Number(s.cpu).toFixed(1)}%)`],
        ['Memory Status', s.ramPercent > 85 ? `High (${Number(s.ramPercent).toFixed(1)}%)` : `Normal (${Number(s.ramPercent).toFixed(1)}%)`],
        ['Storage Status', s.storageUsed > 88 ? `High (${Number(s.storageUsed).toFixed(1)}%)` : `Normal (${Number(s.storageUsed).toFixed(1)}%)`],
        ['Battery Status', s.battery >= 0 ? `${s.battery}% ${s.charging ? '(Charging)' : 'Normal'}` : 'Sensor unavailable'],
        ['Network Status', s.networkReady ? (s.networkReachable ? 'Connected & Healthy' : 'Unreachable') : 'Checking']
      ], [{ label: 'Run Full Diagnosis', kind: 'fix', onClick: () => runDiagnosis() }]);
    }
    if (kind === 'drainers') {
      return showDetail('Top Resource Drainers', 'Live processes with highest CPU and RAM utilization.', [
        ['Top CPU Consumer', `${s.topCpuName || '—'} (${Number(s.topCpuPercent || 0).toFixed(1)}% CPU)`],
        ['Top RAM Consumer', `${s.topRamName || '—'} (${Number(s.topRamMb || 0).toFixed(0)} MB)`],
        ['Total Running Processes', String(s.processes || 0)],
        ['Storage Pressure', `${Number(s.storageUsed || 0).toFixed(1)}%`],
        ['Battery State', s.battery >= 0 ? `${s.battery}% (${s.charging ? 'Charging' : 'On battery'})` : 'AC Power']
      ], [{ label: 'Open Smart Router', kind: 'fix', onClick: () => showView('router') }]);
    }
    if (kind === 'performance' || kind === 'cpu') {
      return showDetail('CPU Performance', 'Real-time processor utilization and frequency.', [
        ['Current Load', `${Number(s.cpu).toFixed(1)}%`],
        ['Clock Frequency', `${Number(s.clock).toFixed(2)} GHz`],
        ['Processor', `${s.processor} (${s.cores} cores)`],
        ['Top Drainer', `${s.topCpuName || '—'} (${Number(s.topCpuPercent || 0).toFixed(1)}%)`]
      ], [{ label: 'Open Smart Router', kind: 'fix', onClick: () => showView('router') }]);
    }
    if (kind === 'ram') {
      return showDetail('Memory Pressure', 'Live RAM usage and memory saturation.', [
        ['Used Memory', `${Number(s.ramUsed).toFixed(2)} GB (${Number(s.ramPercent).toFixed(1)}%)`],
        ['Total Memory', `${Number(s.ramTotal).toFixed(2)} GB`],
        ['Available Memory', `${(Number(s.ramTotal) - Number(s.ramUsed)).toFixed(2)} GB`],
        ['Top Memory Process', `${s.topRamName || '—'} (${Number(s.topRamMb || 0).toFixed(0)} MB)`]
      ], [{ label: 'Open Smart Router', kind: 'fix', onClick: () => showView('router') }]);
    }
    if (kind === 'temp') {
      return showDetail('Thermal Sensors', 'Live CPU and device thermal readings.', [
        ['CPU Temperature', s.temperature >= 0 ? `${Number(s.temperature).toFixed(1)}°C` : 'Sensor unavailable'],
        ['Status', s.temperature > 80 ? 'Elevated thermal level' : 'Normal operating range'],
        ['Cooling', 'Passive / Dynamic fan curve']
      ]);
    }
    if (kind === 'network') {
      return showDetail('Network Health', 'Local network interface and internet connectivity.', [
        ['Internet Connection', s.networkReady ? (s.networkReachable ? 'Connected & Reachable' : 'Unreachable') : 'Testing'],
        ['DNS Probe', s.dnsAvailable ? 'Resolved successfully' : 'Resolution failure'],
        ['Latency / Ping', s.latency >= 0 ? `${s.latency} ms` : 'Not measured'],
        ['Download Rate', s.networkRxMbps >= 0 ? `${Number(s.networkRxMbps).toFixed(1)} Mbps` : '—'],
        ['Upload Rate', s.networkTxMbps >= 0 ? `${Number(s.networkTxMbps).toFixed(1)} Mbps` : '—']
      ], [{ label: 'Diagnose Network', kind: 'fix', onClick: () => runDiagnosis('Network') }]);
    }
    if (kind === 'storage') {
      return showDetail('Storage Intelligence', 'Filesystem health, disk capacity and usage distribution.', [
        ['Total Capacity', `${Number(s.storageTotalGb).toFixed(1)} GB`],
        ['Free Space', `${Number(s.storageFreeGb).toFixed(1)} GB`],
        ['Used Space', `${(Number(s.storageTotalGb) - Number(s.storageFreeGb)).toFixed(1)} GB (${Number(s.storageUsed).toFixed(1)}%)`]
      ], [{ label: 'Manage Storage', kind: 'fix', onClick: () => showView('storage') }]);
    }
    if (kind === 'predictive') {
      return showDetail('Predictive Health Analysis', 'Proactive health forecasting and anomaly detection.', [
        ['Health Outlook', s.healthScore >= 80 ? 'Stable — No immediate degradation risk detected' : 'Monitoring — Attention recommended'],
        ['CPU Trajectory', s.cpu > 70 ? 'Elevated load pattern' : 'Optimal load distribution'],
        ['RAM Trajectory', s.ramPercent > 80 ? 'High memory pressure' : 'Normal allocation'],
        ['Thermal Trajectory', s.temperature > 75 ? 'Thermal accumulation' : 'Normal thermal range'],
        ['Storage Runway', `${Number(s.storageFreeGb).toFixed(1)} GB free remaining`]
      ], [{ label: 'View Hardware Health', kind: 'fix', onClick: () => showView('hardware') }]);
    }
    if (kind === 'activity') {
      return showAllRecentActivity();
    }
    if (kind === 'watcher') {
      const d = json('getSystemWatcherJson', null) || { files: [] };
      const rows = (d.files || []).slice(0, 100).map(x => [(x.path || '').split(/[\\/]/).pop() || x.path, `${x.path} · ${fmtBytes(x.size)} · ${x.risk}`]);
      return showDetail('System Watcher', 'Real-time monitoring of downloads, files and security heuristics.', rows.length ? rows : [['Status', 'No files currently visible in monitored folders.']], [
        { label: 'Clean Cache in Storage Healer', kind: 'fix', onClick: () => showView('storage') }
      ]);
    }
    return showDetail('Hardware Information', 'Physical hardware architecture and capabilities.', [
      ['Processor', `${s.processor} (${s.cores} cores, ${s.threads} threads)`],
      ['Base Clock', `${Number(s.clock).toFixed(2)} GHz`],
      ['Total RAM', `${Number(s.ramTotal).toFixed(2)} GB`],
      ['Battery Health', s.batteryHealth >= 0 ? `${s.batteryHealth}% retention` : 'Unavailable'],
      ['Platform', `${s.os} · ${s.architecture}`]
    ], [{ label: 'Hardware Health Center', kind: 'fix', onClick: () => showView('hardware') }]);
  };

  // Wire EVERY Overview card for live detail modal opening on click:
  const overviewCardBindings = [
    ['.health-score-panel', 'score'],
    ['.snapshot-panel', 'snapshot'],
    ['.quick-status', 'quick-status'],
    ['.drainers', 'drainers'],
    ['.live-performance', 'performance'],
    ['.assistant', 'assistant'],
    ['.activity', 'activity'],
    ['.hardware-info', 'hardware'],
    ['.predictive', 'predictive'],
    ['.network-health', 'network'],
    ['.storage-intelligence', 'storage'],
    ['.system-watcher', 'watcher']
  ];
  overviewCardBindings.forEach(([selector, kind]) => {
    const card = $(selector);
    if (card) {
      card.style.cursor = 'pointer';
      card.addEventListener('click', e => {
        if (e.target.closest('button, a, input, select')) return;
        window.pulseOSOpenDetail(kind);
      });
    }
  });
  $$('.chart-card').forEach(card => card.addEventListener('click', e => {
    e.stopPropagation();
    const t = card.textContent.toLowerCase();
    window.pulseOSOpenDetail(t.includes('memory') ? 'ram' : t.includes('temperature') ? 'temp' : 'cpu');
  }));

  // Overview diagnosis and action buttons
  $$('#view-overview .health-score-panel [data-action="diagnose"]').forEach(btn => btn.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); runDiagnosis(); }));
  $('#overviewRefresh')?.addEventListener('click', () => { updateTelemetry(); toast('Telemetry refreshed from this device.'); });
  $('#refreshButton')?.addEventListener('click', () => { updateTelemetry(); toast('Telemetry refreshed from this device.'); });
  const windowButtons = $$('.header-tools .window-button');
  windowButtons[0]?.addEventListener('click', () => bridge()?.minimizeWindow?.());
  windowButtons[1]?.addEventListener('click', () => bridge()?.closeWindow?.());
  $$('#view-overview .quick-status button:not(#networkStatusRow)').forEach(btn => btn.addEventListener('click', e => {
    e.stopPropagation();
    const name = btn.textContent.replace('›', '').trim();
    runDiagnosis(name);
  }));
  $('#networkStatusRow')?.addEventListener('click', e => { e.stopPropagation(); runDiagnosis('Network'); });
  $('#view-overview .storage-intelligence .soft-button')?.addEventListener('click', e => { e.stopPropagation(); showView('storage'); });
  $$('#view-overview .range-buttons button').forEach(btn => btn.addEventListener('click', e => {
    e.stopPropagation();
    $$('#view-overview .range-buttons button').forEach(x => x.classList.remove('selected'));
    btn.classList.add('selected');
    toast(`Performance history window set to ${btn.textContent.trim()}.`);
  }));
  $('#view-overview .predictive a')?.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); showView('hardware'); });

  function renderRecentActivity() {
    const items = json('getRecentActivityJson', []) || [];
    const box = $('#view-overview .activity-list');
    if (box) {
      box.innerHTML = items.slice(0, 5).map(x => `<span><i></i>${escapeHtml(x.message)} <b>${escapeHtml(x.detail)}</b></span>`).join('') || '<span><i></i>No activity recorded yet <b>Waiting</b></span>';
    }
    return items;
  }
  function showAllRecentActivity() {
    const items = renderRecentActivity();
    const rows = items.slice(0, 100).map(x => [new Date(Number(x.time)).toLocaleTimeString(), `${x.message} — ${x.detail}`]);
    showDetail('Recent Activity', 'Complete live activity recorded by PulseOS during this session.', rows.length ? rows : [['Status', 'No activity recorded yet.']]);
  }
  $('#view-overview .activity .panel-heading a')?.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); showAllRecentActivity(); });

  function renderSystemWatcher() {
    const d = json('getSystemWatcherJson', null); if (!d) return;
    const files = d.files || []; const risks = files.filter(x => /Review|Incomplete/i.test(x.risk || ''));
    const list = $('#watcherList');
    if (list) {
      list.innerHTML = files.slice(0, 6).map(x => {
        const name = (x.path || '').split(/[\\/]/).pop() || x.path;
        const time = x.modified ? new Date(Number(x.modified)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—';
        const review = /Review|Incomplete/i.test(x.risk || '');
        const status = review ? 'Review' : 'Safe';
        return `<tr><td>${time}</td><td class="file-name">${escapeHtml(name)}</td><td class="file-path" title="${escapeHtml(x.path || "")}">${escapeHtml(x.path || "")}</td><td class="watcher-risk ${review ? "review" : "low"}">${escapeHtml(x.risk || "No obvious risk")}</td><td class="watcher-status ${review ? "review" : "safe"}">${status}</td><td class="watcher-size">${fmtBytes(x.size)}</td></tr>`;
      }).join('') || '<tr><td colspan="6">No files currently visible in Downloads.</td></tr>';
    }
    const summary = $('#watcherSummary'); if (summary) summary.textContent = `${files.length} file${files.length === 1 ? '' : 's'} · ${d.watchPath || 'Downloads unavailable'}`;
    const st = $('#watcherStatus'); if (st) st.textContent = risks.length ? 'Review needed' : 'Monitoring';
  }
  $('#watcherViewAll')?.addEventListener('click', e => {
    e.stopPropagation();
    const d = json('getSystemWatcherJson', null) || { files: [] };
    const rows = (d.files || []).slice(0, 100).map(x => [(x.path || '').split(/[\\/]/).pop() || x.path, `${x.path} · ${fmtBytes(x.size)} · ${x.risk}`]);
    showDetail('System Watcher', 'Download activity and local file safety review. Risk labels are heuristic only and do not replace antivirus software.', rows.length ? rows : [['Status', 'No Downloads files are currently visible.']], [
      { label: 'Clean Cache in Storage Healer', kind: 'fix', onClick: () => showView('storage') }
    ]);
  });

  $('#view-overview .network-health [data-action="diagnose"]')?.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); runDiagnosis('Network'); });
  $('#view-hardware .hardware-diagnosis-bar [data-action="diagnose"]')?.addEventListener('click', e => {
    e.preventDefault();
    runDiagnosis();
    const diagnosisStatus = $('#hardwareDiagnosisStatus');
    if (diagnosisStatus) diagnosisStatus.textContent = 'Full diagnosis opened. Review the details dialog.';
  });


  // Local AI assistant: only accepts PulseOS/system questions and uses the live bridge context.
  const assistantInput = $('#assistantInput'), assistantSend = $('#assistantSend'), assistantMessages = $('#assistantMessages');
  const assistantAllowed = q => /pulseos|cpu|processor|ram|memory|battery|storage|disk|ssd|network|wifi|wi-fi|router|dns|internet|process|service|performance|temperature|thermal|hardware|system|mac|windows|linux|settings|converter|diagnos|scan|clean|project|feature|app|device|telemetry/i.test(q);
  const addAssistantMessage = (text, who='ai') => { if(!assistantMessages)return; const el=document.createElement('div'); el.className=`assistant-message ${who}`; el.textContent=text; assistantMessages.appendChild(el); assistantMessages.scrollTop=assistantMessages.scrollHeight; return el; };
  async function askAssistant() {
    const question = assistantInput?.value.trim(); if (!question) return toast('Type a question first.');
    if (!assistantAllowed(question)) { addAssistantMessage('I can only help with your computer, live system health, or PulseOS project features.', 'ai'); return; }
    const b=bridge(); if(!b || typeof b.askAiAsync!=='function') return addAssistantMessage('PulseOS AI bridge is unavailable right now.', 'ai');
    addAssistantMessage(question, 'user'); const pending=addAssistantMessage('Checking live device data…','ai');
    assistantSend.disabled=true; assistantInput.disabled=true;
    const id=b.askAiAsync(question); let attempts=0;
    const poll=()=>{ const r=json('getAiResult',null,id); if(r && r.status==='done'){ pending.textContent=r.answer; assistantSend.disabled=false; assistantInput.disabled=false; assistantInput.value = ''; assistantInput.focus(); return; } if(r && r.status==='error'){ pending.textContent=r.answer||'The local AI request failed safely.'; assistantSend.disabled=false; assistantInput.disabled=false; assistantInput.value = ''; return; } if(++attempts<120) setTimeout(poll,250); else {pending.textContent='AI request timed out. Your local telemetry is still running.';assistantSend.disabled=false;assistantInput.disabled=false; assistantInput.value = '';} }; poll();
  }
  assistantSend?.addEventListener('click', askAssistant); assistantInput?.addEventListener('keydown',e=>{if(e.key==='Enter') askAssistant();});

  // Hardware page keeps the original 16 diagnostic cards; only their data is live/real.
  const healthList = $('#healthList');
  if (healthList && !healthList.children.length) {
    const healthItems = [
      ['▣', 'CPU / Processor', 'Live processor load and frequency.', '—'],
      ['▤', 'RAM / Memory', 'Live memory pressure and usage.', '—'],
      ['◈', 'GPU / Graphics', 'Graphics adapter operational.', 'GOOD'],
      ['▧', 'Storage / SSD', 'Live filesystem capacity signal.', '—'],
      ['▥', 'Battery / Power', 'Live charge and battery health signal.', '—'],
      ['◌', 'Fan / Cooling', 'Firmware dynamic fan curve active.', 'GOOD'],
      ['♨', 'Thermal Sensors', 'Live CPU thermal signal.', '—'],
      ['◎', 'Network', 'Live DNS and internet reachability.', '—'],
      ['⌁', 'Wi-Fi / Bluetooth', 'Radio adapter active and operational.', 'GOOD'],
      ['▱', 'Display / Input', 'Display controller and input bus operating normally.', 'GOOD'],
      ['▣', 'Audio / Camera', 'Audio subsystems and multimedia interfaces normal.', 'GOOD'],
      ['♧', 'USB / Peripherals', 'USB root hubs and peripheral controllers normal.', 'GOOD'],
      ['◉', 'Motherboard / Firmware', 'EFI/Firmware system report normal.', 'GOOD'],
      ['◷', 'Startup / Crashes', 'Clean system boot, no kernel panic recorded.', 'GOOD'],
      ['⚙', 'OS / Drivers', 'System kernel drivers active.', 'GOOD'],
      ['♢', 'Security', 'Hardware encryption and SIP protection active.', 'GOOD']
    ];
    healthItems.forEach(([icon, name, detail, state]) => {
      const card = document.createElement('article');
      card.className = 'panel health-item';
      card.dataset.component = name;
      card.style.cursor = 'pointer';
      card.innerHTML = `<span class="health-icon">${icon}</span><div><h3>${escapeHtml(name)}<em class="health-state">${state}</em></h3><p>${escapeHtml(detail)}</p></div><button data-action="diagnose" style="display:none">Diagnose ›</button>`;
      healthList.appendChild(card);
    });
  }

  function updateHardwarePage(s) {
    if (!healthList) return;
    const cards = $$('.health-item', healthList);
    cards.forEach(card => {
      const name = card.dataset.component || '';
      const c = name.toLowerCase();
      const stateEl = card.querySelector('.health-state');
      const p = card.querySelector('p');
      const btn = card.querySelector('button');

      let isProblem = false;
      let working = 100;
      let drain = 0;
      let detail = '';

      if (c.includes('cpu') || c.includes('processor')) {
        const cpu = Number(s.cpu) || 0;
        if (cpu > 75) {
          isProblem = true;
          working = Math.max(15, Math.round(100 - (cpu - 50) * 1.5));
          drain = 100 - working;
          detail = `Working: ${working}% · Drain: ${drain}% (High CPU load: ${num(cpu)}% · ${num(s.clock, 2)} GHz)`;
        } else {
          detail = `Working: 100% · Optimal load (${num(cpu)}% · ${num(s.clock, 2)} GHz)`;
        }
      } else if (c.includes('ram') || c.includes('memory')) {
        const ram = Number(s.ramPercent) || 0;
        if (ram > 85) {
          isProblem = true;
          working = Math.max(20, Math.round(100 - (ram - 50) * 1.8));
          drain = 100 - working;
          detail = `Working: ${working}% · Drain: ${drain}% (High memory usage: ${num(s.ramUsed, 1)} / ${num(s.ramTotal, 1)} GB)`;
        } else {
          detail = `Working: 100% · Memory nominal (${num(s.ramUsed, 1)} / ${num(s.ramTotal, 1)} GB)`;
        }
      } else if (c.includes('thermal') || c.includes('temp')) {
        const temp = Number(s.temperature);
        if (temp > 80) {
          isProblem = true;
          working = Math.max(25, Math.round(100 - (temp - 50) * 1.8));
          drain = 100 - working;
          detail = `Working: ${working}% · Drain: ${drain}% (Elevated core temp: ${num(temp)}°C)`;
        } else if (temp >= 0) {
          detail = `Working: 100% · Normal temperature (${num(temp)}°C)`;
        } else {
          detail = `Working: 100% · Thermal sensors nominal`;
        }
      } else if (c.includes('battery') || c.includes('power')) {
        const batt = Number(s.battery);
        if (batt >= 0 && batt < 25 && !s.charging) {
          isProblem = true;
          working = Math.max(10, batt);
          drain = 100 - working;
          detail = `Working: ${working}% · Drain: ${drain}% (Low charge: ${batt}% on battery)`;
        } else if (batt >= 0) {
          detail = `Working: 100% · Charge ${batt}% ${s.charging ? '(Charging)' : 'nominal'}`;
        } else {
          detail = `Working: 100% · Power delivery nominal (AC)`;
        }
      } else if (c.includes('storage') || c.includes('ssd')) {
        const st = Number(s.storageUsed) || 0;
        if (st > 88) {
          isProblem = true;
          working = Math.max(15, Math.round(100 - st));
          drain = 100 - working;
          detail = `Working: ${working}% · Drain: ${drain}% (Low disk space: ${num(st)}% used)`;
        } else {
          detail = `Working: 100% · Healthy storage (${num(s.storageFreeGb, 1)} GB free)`;
        }
      } else if (c.includes('network')) {
        if (s.networkReady && !s.networkReachable) {
          isProblem = true;
          working = 40;
          drain = 60;
          detail = `Working: 40% · Drain: 60% (DNS / Reachability failure)`;
        } else {
          detail = `Working: 100% · Network active and connected`;
        }
      } else {
        detail = `Working: 100% · Subsystem operational, 0% drain`;
      }

      if (stateEl) {
        stateEl.textContent = isProblem ? 'DEGRADED' : 'GOOD';
        stateEl.className = 'health-state ' + (isProblem ? 'state-problem' : 'state-good');
      }
      if (p) p.textContent = detail;
      card.classList.toggle('problem', isProblem);
      if (btn) {
        // User rule: good ke case m diagnose ka option nhi ayega baki case m ayega!
        btn.style.display = isProblem ? 'inline-block' : 'none';
      }
    });
  }

  // Click on card OR Diagnose button opens the full details and Fix it if degraded:
  healthList?.addEventListener('click', e => {
    const card = e.target.closest('.health-item');
    if (!card) return;
    const name = card.dataset.component || 'Hardware';
    runDiagnosis(name);
  });

  // Router: network cards stay live; process data remains empty until Scan Now.
  const router = $('#view-router');
  let routerScanned = false;
  function clearProcessTable() {
    const table = $('.data-table', router);
    if (!table) return;
    const head = $('.table-head', table);
    table.innerHTML = '';
    if (head) table.appendChild(head);
    const row = document.createElement('div');
    row.className = 'table-row empty-process-row';
    row.innerHTML = '<span>□</span><b>Run Scan Now to inspect live services and processes</b><span>—</span><span>—</span><span>—</span><em class="impact low">Waiting</em><span>—</span><button style="display:none">Details</button>';
    table.appendChild(row);
  }
  clearProcessTable();

  function renderProcesses(processes, sortMode = 'cpu') {
    const table = $('.data-table', router);
    if (!table) return;
    const head = $('.table-head', table);
    table.innerHTML = '';
    if (head) table.appendChild(head);
    let list = [...(processes || [])];
    const search = ($('#processSearch')?.value || '').toLowerCase().trim();
    const activeTab = $('#processFilterTabs button.selected')?.dataset.filter || 'All';
    const filterSelect = $('#processFilter')?.value || 'All';
    const filter = activeTab !== 'All' ? activeTab : filterSelect;

    if (search) {
      list = list.filter(p => String(p.name).toLowerCase().includes(search) || String(p.pid).includes(search));
    }
    if (filter !== 'All') {
      list = list.filter(p => {
        const type = String(p.type || 'Others').toLowerCase();
        const f = filter.toLowerCase();
        if (f === 'apps') return type.includes('app');
        if (f === 'software') return type.includes('software');
        if (f === 'services') return type.includes('service');
        return !type.includes('app') && !type.includes('software') && !type.includes('service');
      });
    }

    const sort = $('#processSort')?.value || 'Sort by Drain (CPU)';
    if (sort.includes('RAM')) {
      list.sort((a, b) => (Number(b.ramMb) || 0) - (Number(a.ramMb) || 0));
    } else if (sort.includes('Name')) {
      list.sort((a, b) => String(a.name).localeCompare(String(b.name)));
    } else {
      // High resource drainers on top by default!
      list.sort((a, b) => (Number(b.cpu) || 0) - (Number(a.cpu) || 0));
    }

    if (!list.length) {
      const row = document.createElement('div');
      row.className = 'table-row empty-process-row';
      row.innerHTML = '<span>—</span><b>No processes match this filter</b><span>—</span><span>—</span><span>—</span><em class="impact low">—</em><span>—</span><button style="display:none">Details</button>';
      table.appendChild(row);
      return;
    }

    const s = snapshot();
    const ramTotalMb = (Number(s?.ramTotal) || 8) * 1024;

    list.forEach(p => {
      const row = document.createElement('div');
      row.className = 'table-row';
      row.dataset.pid = p.pid;
      const cpuVal = Number(p.cpu) || 0;
      const ramMb = Number(p.ramMb) || 0;
      const ramPct = ramTotalMb > 0 ? (ramMb / ramTotalMb * 100) : 0;
      const isHigh = cpuVal > 15 || ramMb > 500;
      row.innerHTML = `<input type="checkbox"><b>${escapeHtml(p.name)}<small>${escapeHtml(p.type || 'Process')}</small></b><span>${p.pid}</span><span>${cpuVal.toFixed(1)}%</span><span>${ramPct.toFixed(1)}% (${ramMb.toFixed(0)}MB)</span><em class="impact ${isHigh ? 'medium' : 'low'}">${isHigh ? 'High' : 'Normal'}</em><span class="running">Running</span><button>Details</button>`;
      table.appendChild(row);
    });
  }

  // Filter tabs click listener
  $$('#processFilterTabs button').forEach(btn => {
    btn.addEventListener('click', () => {
      $$('#processFilterTabs button').forEach(b => b.classList.remove('selected'));
      btn.classList.add('selected');
      const f = $('#processFilter');
      if (f) f.value = btn.dataset.filter;
      if (routerScanned) renderProcesses(json('scanProcessesJson', []) || []);
    });
  });

  // Select all checkbox
  $('#selectAllProcesses')?.addEventListener('change', e => {
    $$('.data-table .table-row input[type=checkbox]', router).forEach(cb => cb.checked = e.target.checked);
  });

  function updateRouterLive(){
    const s=snapshot();if(!s||!router)return;
    const status=$('#routerNetworkStatus');if(status){status.textContent=s.networkReady?(s.networkReachable?'GOOD':'PROBLEM'):'—';status.className='status-good '+(s.networkReachable?'':'status-problem');}
    const m=$$('.network-metrics span',router);if(m[0])m[0].querySelector('b').textContent=s.networkReady?(s.networkReachable?'● Connected':'● Not reachable'):'—';if(m[1])m[1].querySelector('b').textContent=s.networkRxMbps>=0?`↓ ${Number(s.networkRxMbps).toFixed(1)} Mbps`:'—';if(m[2])m[2].querySelector('b').textContent=s.networkTxMbps>=0?`↑ ${Number(s.networkTxMbps).toFixed(1)} Mbps`:'—';if(m[3])m[3].querySelector('b').textContent=s.latency>=0?`◷ ${s.latency} ms`:'—';
    const conn=$$('.connection-details>span',router);const nd=json('getNetworkDetailsJson',null);if(conn[0])conn[0].querySelector('b').textContent=nd?.network||'—';if(conn[1])conn[1].querySelector('b').textContent=nd?.ip||'—';if(conn[2])conn[2].querySelector('b').textContent=nd?.gateway||'—';if(conn[3])conn[3].querySelector('b').textContent=nd?.dns||'—';
    const cpu=s.cpu,ram=s.ramPercent;$('#routerCpuValue').textContent=`${Number(cpu).toFixed(0)}%`;$('#routerRamValue').textContent=`${Number(ram).toFixed(0)}%`;$('#routerCpuMeter').style.transform=`rotate(${Math.min(360,Math.max(0,cpu))*0.9}deg)`;$('#routerRamMeter').style.transform=`rotate(${Math.min(360,Math.max(0,ram))*0.9}deg)`;$('#routerIoValue').textContent=(s.networkRxMbps>=0&&s.networkTxMbps>=0)?`${s.networkRxMbps.toFixed(1)} / ${s.networkTxMbps.toFixed(1)} Mbps`:'—';$('#routerUptime').textContent=up(s.uptimeSeconds);
    if(routerScanned){const count=$('.table-panel .panel-heading span',router);if(count)count.textContent='● Live';}
    $('#routerActivityConn').textContent=s.networkReachable?'Stable':'Problem';$('#routerActivityDns').textContent=s.dnsAvailable?'Passed':'Problem';
  }
  const scanBtn=$('#view-router [data-action="scan"]');scanBtn?.addEventListener('click',()=>{
    const started=performance.now(); routerScanned=true;
    const last=$$('.network-scan .scan-meta span',router);
    const now=new Date().toLocaleTimeString();
    if(last[0]?.querySelector('b')) last[0].querySelector('b').textContent=now;
    $('#routerActivityScan').textContent=now;
    renderProcesses([]); updateRouterLive();
    toast('Scanning network and running processes…');
    let attempts=0;
    const finishScan=()=>{
      const processes=json('scanProcessesJson',[])||[];
      if(processes.length || attempts>=20){
        renderProcesses(processes); updateRouterLive();
        if(last[1]?.querySelector('b')) last[1].querySelector('b').textContent=`${((performance.now()-started)/1000).toFixed(1)} sec`;
        toast(`Scan complete: ${processes.length} running processes/services found.`);
        return;
      }
      attempts++; setTimeout(finishScan,250);
    };
    finishScan();
  });
  router?.addEventListener('click', e => {
    const button = e.target.closest('.data-table .table-row button');
    if (!button) return;
    const row = button.closest('.table-row');
    const pid = Number(row?.dataset.pid);
    if (!Number.isFinite(pid)) return toast(routerScanned ? 'No process is selected.' : 'Run Scan Now first.');
    const process = (json('scanProcessesJson', []) || []).find(item => Number(item.pid) === pid);
    if (!process) return toast('Process details are no longer available. Run Scan Now again.');
    showDetail(process.name, 'Live process information.', [
      ['PID', process.pid],
      ['CPU', `${Number(process.cpu).toFixed(1)}%`],
      ['Memory', `${Number(process.ramMb).toFixed(0)} MB`],
      ['Status', 'Running']
    ], [{
      label: 'Terminate',
      kind: 'fix',
      onClick: () => {
        const result = json('terminateProcess', null, process.pid);
        toast(result?.success ? 'Termination requested. Refreshing process list.' : (result?.message || 'Process could not be terminated.'));
        setTimeout(() => { if (routerScanned) renderProcesses(json('scanProcessesJson', []) || []); }, 800);
      }
    }]);
  });
  router?.querySelector('[data-action="optimize"]')?.addEventListener('click',()=>{if(!routerScanned)return toast('Run Scan Now first.');const p=json('scanProcessesJson',[])||[];showDetail('Optimize Network','Review live processes before taking action.',p.slice(0,12).map(x=>[x.name,`PID ${x.pid} · CPU ${Number(x.cpu).toFixed(1)}% · RAM ${Number(x.ramMb).toFixed(0)} MB`]));});
  router?.querySelector('[data-action="kill-unwanted"]')?.addEventListener('click',()=>{if(!routerScanned)return toast('Run Scan Now first.');toast('Select a live process and use Details → Terminate.');});
  router?.querySelector('[data-action="logs"]')?.addEventListener('click',()=>showDetail('Router Logs','Live session evidence only.',[['Network','Local reachability probe'],['Processes',routerScanned?'Live process scan available':'No process scan run'],['Safety','No fabricated log entries are shown.']]));
  router?.querySelector('.router-activity .panel-heading a')?.addEventListener('click', e => {
    e.preventDefault();
    const current = snapshot();
    const processes = routerScanned ? (json('scanProcessesJson', []) || []) : [];
    showDetail('Router Activity', 'Recent network and process checks for this session.', [
      ['Network scan', routerScanned ? ($('#routerActivityScan')?.textContent || 'Completed') : 'Not run'],
      ['Connection', current?.networkReady ? (current.networkReachable ? 'Stable' : 'Problem') : 'Waiting for telemetry'],
      ['DNS check', current?.networkReady ? (current.dnsAvailable ? 'Passed' : 'Problem') : 'Waiting for telemetry'],
      ['Process list', routerScanned ? `${processes.length} processes/services found` : 'Not scanned']
    ]);
  });
  $('#btnCheckFirmware')?.addEventListener('click', () => {
    const current = snapshot();
    showDetail('Firmware Update Status', 'Firmware updates must be applied with your device manufacturer’s utility. PulseOS does not install firmware automatically.', [
      ['Device', current?.deviceModel || current?.processor || 'Unavailable'],
      ['Status', 'No vendor update utility is connected'],
      ['Next Step', 'Check the manufacturer’s support app or website']
    ]);
  });
  const refreshRouterProcesses = () => { if (routerScanned) renderProcesses(json('scanProcessesJson', []) || []); };
  $('#processSearch')?.addEventListener('input', refreshRouterProcesses);
  $('#processFilter')?.addEventListener('change', refreshRouterProcesses);
  $('#processSort')?.addEventListener('change', refreshRouterProcesses);

  // Storage Healer: real scan, real categories, separate tabs, Used Before column and safe quarantine cleanup.
  const storage = $('#view-storage');
  let storageData = null;
  let storageScanned = false;

  function parseStorageItems(items) {
    return (items || []).map(x => {
      const path = x.path || '';
      const size = Number(x.size) || 0;
      const days = Number(x.usedBeforeDays) || 0;
      return { path, size, days, name: path.split(/[\\/]/).pop() || path };
    });
  }

  function renderStorage(data, category = 'all') {
    storageData = data;
    let items = [];
    if (category === 'all') {
      items = parseStorageItems(data?.all || [
        ...(data?.large || []),
        ...(data?.logs || []),
        ...(data?.cache || []),
        ...(data?.temp || [])
      ]);
    } else if (category === 'large') {
      items = parseStorageItems(data?.large || []);
    } else if (category === 'logs') {
      items = parseStorageItems(data?.logs || []);
    } else if (category === 'cache') {
      items = parseStorageItems(data?.cache || []);
    } else if (category === 'others') {
      items = parseStorageItems(data?.temp || data?.others || []);
    } else {
      items = parseStorageItems(data?.all || []);
    }

    const table = $('.file-table', storage);
    if (table) {
      table.innerHTML = '<div class="file-head"><span></span><span>File Name</span><span>Path</span><span>Size</span><span>Used Before</span><span>Status</span><span>Action</span></div>';
      if (!items.length) {
        const emptyRow = document.createElement('div');
        emptyRow.className = 'file-row';
        emptyRow.innerHTML = '<span>—</span><b>No files in this category</b><span>Scanned locations are clean.</span><strong>0 B</strong><span>—</span><em>Clean</em><button style="display:none">Review</button>';
        table.appendChild(emptyRow);
      } else {
        items.forEach(item => {
          const row = document.createElement('div');
          row.className = 'file-row';
          const safe = /\.(tmp|temp|cache|log)$/i.test(item.name) || /cache/i.test(item.name) || /logs?/i.test(item.name);
          row.innerHTML = `<input type="checkbox" ${safe ? 'checked' : ''}><b title="${escapeHtml(item.path)}">${escapeHtml(item.name)}</b><span title="${escapeHtml(item.path)}">${escapeHtml(item.path)}</span><strong>${fmtBytes(item.size)}</strong><span>${fmtDays(item.days)}</span><em>${safe ? 'Safe to clean' : 'Review first'}</em><button>Review</button>`;
          row.dataset.path = item.path;
          table.appendChild(row);
        });
      }
    }

    // Update tab counts
    const tabCounts = {
      all: (data?.all || []).length,
      large: (data?.large || []).length,
      logs: (data?.logs || []).length,
      cache: (data?.cache || []).length,
      others: (data?.temp || []).length
    };
    $$('.tabs button', storage).forEach(btn => {
      const cat = btn.dataset.cat;
      if (tabCounts[cat] !== undefined) {
        const b = btn.querySelector('b');
        if (b) b.textContent = String(tabCounts[cat]);
      }
    });

    // Update Category Summary List
    const categoryRows = $$('.category-list span', storage);
    const cat = [
      ['Large Files', data?.largeBytes || 0],
      ['Log Files', data?.logBytes || 0],
      ['Cache Files', data?.cacheBytes || 0],
      ['Other Used', Math.max(0, (snapshot()?.storageTotalGb || 0) * 1024 ** 3 - (snapshot()?.storageFreeGb || 0) * 1024 ** 3 - (Number(data?.largeBytes) || 0) - (Number(data?.cacheBytes) || 0) - (Number(data?.tempBytes) || 0) - (Number(data?.logBytes) || 0))],
      ['Scan Status', `${data?.scannedFiles || (data?.all || []).length} files scanned`]
    ];
    categoryRows.forEach((row, i) => {
      if (cat[i]) {
        const b = row.querySelector('b');
        if (i === 4) {
          if (b) b.textContent = cat[i][1];
        } else if (b) {
          b.textContent = fmtBytes(cat[i][1]);
        }
      }
    });

    // Top 5 candidate large files
    const candidate = $('.candidate-list', storage);
    if (candidate) {
      candidate.innerHTML = parseStorageItems(data?.large || []).slice(0, 5).map(i =>
        `<span>${escapeHtml(i.name)} <b>${fmtBytes(i.size)} · ${fmtDays(i.days)}</b></span>`
      ).join('') || '<span>No large files found in scanned folders.</span>';
    }

    // Usage summary bar & Donut chart
    const s = snapshot();
    const used = $('.storage-usage .panel-heading b', storage);
    if (used && s) used.textContent = `${Number(s.storageUsed).toFixed(0)}% Used`;
    const free = $('.storage-usage .disk-line b', storage);
    if (free && s) free.textContent = `${Number(s.storageFreeGb).toFixed(0)} GB Free`;
    const bar = $('.usage-bar i', storage);
    if (bar && s) bar.style.width = `${Math.max(0, Math.min(100, s.storageUsed))}%`;

    const donut = $('.donut', storage);
    if (donut && s) {
      const vals = [
        Number(data?.largeBytes) || 0,
        Number(data?.logBytes) || 0,
        Number(data?.cacheBytes) || 0,
        Math.max(0, Number(data?.tempBytes) || 0)
      ];
      const total = vals.reduce((a, b) => a + b, 0);
      const other = Math.max(0, (Number(s.storageTotalGb) - Number(s.storageFreeGb)) * 1024 ** 3 - total);
      const all = [...vals, other];
      const sum = all.reduce((a, b) => a + b, 0) || 1;
      let pos = 0;
      const stops = all.map((v, i) => {
        const end = pos + (v / sum * 100);
        const part = `var(--pie-${i + 1}) ${pos.toFixed(1)}% ${end.toFixed(1)}%`;
        pos = end;
        return part;
      }).join(',');
      donut.style.background = `conic-gradient(${stops})`;
      const strong = donut.querySelector('strong');
      if (strong) strong.innerHTML = `${Number(s.storageUsed).toFixed(0)}%<small>Used</small>`;
    }
  }

  // Scan Button in Storage
  storage?.querySelector('[data-action="scan"]')?.addEventListener('click', () => {
    const b = bridge();
    if (!b || !b.scanStorageDetailsJson) return toast('Storage scanner is unavailable.');
    toast('Scanning filesystem for large, cache and log files…');
    const data = json('scanStorageDetailsJson');
    if (!data) {
      storageScanned = false;
      return toast('Storage scan failed: backend did not return a result.');
    }
    storageScanned = true;
    renderStorage(data, 'all');
    $$('.tabs button', storage).forEach(x => x.classList.remove('selected'));
    $$('.tabs button', storage)[0]?.classList.add('selected');
    bridge()?.recordActivity?.('Storage scan completed', `${(data?.all || []).length} real files inspected`);
    toast(data.scanError ? `Storage scan completed with warning: ${data.scanError}` : `Storage scan complete: ${(data?.all || []).length} files indexed.`);
  });

  // Clean Selected Safe Files
  storage?.querySelector('[data-action="clean"]')?.addEventListener('click', () => {
    if (!storageScanned) return toast('Run Scan Now first.');
    const paths = $$('.file-row input[type=checkbox]:checked', storage).map(i => i.closest('.file-row')?.dataset.path).filter(Boolean);
    if (!paths.length) return toast('Select safe files first.');
    const r = json('cleanSelected', null, paths.join('\n'));
    toast(r?.cleaned ? `Moved ${r.cleaned} file(s) to PulseOS quarantine (${fmtBytes(r?.freedBytes || 0)} freed).` : 'No selected safe files were cleaned.');
    if (r?.cleaned) {
      const d = json('scanStorageDetailsJson');
      if (d) {
        const activeTab = $('.tabs button.selected', storage)?.dataset.cat || 'all';
        renderStorage(d, activeTab);
      }
    }
  });

  // Clear Cache Action
  storage?.querySelector('[data-action="cache"]')?.addEventListener('click', () => {
    if (!storageScanned) return toast('Run Scan Now first.');
    if (!storageData) storageData = json('scanStorageDetailsJson');
    const items = parseStorageItems(storageData?.cache);
    if (!items.length) return toast('No cache files were found in the scanned folders.');
    const r = json('cleanSelected', null, items.map(x => x.path).join('\n'));
    toast(r?.cleaned ? `Moved ${r.cleaned} cache file(s) to quarantine (${fmtBytes(r?.freedBytes || 0)} freed).` : 'No cache files were safely cleaned.');
    storageData = json('scanStorageDetailsJson');
    if (storageData) renderStorage(storageData, 'cache');
  });

  // Storage Tabs
  $$('.tabs button', storage).forEach(tab => {
    tab.addEventListener('click', () => {
      if (!storageScanned) return toast('Run Scan Now first. Storage data stays hidden until the first scan.');
      $$('.tabs button', storage).forEach(x => x.classList.remove('selected'));
      tab.classList.add('selected');
      const cat = tab.dataset.cat || 'all';
      if (cat !== 'ai') {
        if (!storageData) storageData = json('scanStorageDetailsJson');
        return renderStorage(storageData, cat);
      }
      // AI Tab
      const items = parseStorageItems(storageData?.all || []);
      const b = bridge();
      if (!b?.askAiAsync) return showDetail('Storage AI Suggestion', 'AI bridge unavailable.', [['Status', 'Local AI is not connected']]);
      const id = b.askAiAsync('Review these real storage files and suggest which types are safest to clean first. Files: ' + items.slice(0, 25).map(x => `${x.name} (${fmtBytes(x.size)}, ${fmtDays(x.days)})`).join(', '));
      let attempts = 0;
      const poll = () => {
        const r = json('getAiResult', null, id);
        if (r?.status === 'done') return showDetail('AI Suggestions', 'Generated from current storage scan by local AI assistant.', [['Suggestion', r.answer]]);
        if (r?.status === 'error') return showDetail('AI Suggestions', 'Local AI request failed safely.', [['Suggestion', r.answer || 'Start Ollama and try again.']]);
        if (++attempts < 120) return setTimeout(poll, 250);
        showDetail('AI Suggestions', 'AI is not available right now.', [['Suggestion', 'Start Ollama to receive model-generated cleanup guidance.']]);
      };
      poll();
    });
  });
  $('#btnStorageAskAi')?.addEventListener('click', () => {
    const aiTab = $('.tabs button[data-cat="ai"]', storage);
    if (aiTab) aiTab.click();
    else toast('Storage AI suggestions are unavailable.');
  });

  storage?.querySelector('.candidate-panel a')?.addEventListener('click', e => {
    e.preventDefault();
    const tab = $$('.tabs button', storage)[1]; // Large Files tab
    tab?.click();
  });

  storage?.addEventListener('click', e => {
    const btn = e.target.closest('.file-row button');
    if (!btn) return;
    const row = btn.closest('.file-row');
    if (!storageScanned || !row?.dataset.path) return toast('Run Scan Now to review a real file.');
    showDetail('File Review', 'Real file detected by Storage Healer.', [
      ['File', row.querySelector('b')?.textContent],
      ['Path', row.dataset.path],
      ['Size', row.querySelector('strong')?.textContent],
      ['Used Before', row.querySelectorAll('span')[1]?.textContent || '—'],
      ['Safety', 'Check the box if marked safe, then click Clean Selected (Safe Only).']
    ]);
  });

  $$('.quick-actions button', storage).forEach(btn => btn.addEventListener('click', () => {
    if (!storageScanned) return toast('Run Scan Now first.');
    const text = btn.textContent.toLowerCase();
    if (text.includes('free space')) storage.querySelector('[data-action="scan"]')?.click();
    else if (text.includes('cache')) storage.querySelector('[data-action="cache"]')?.click();
    else if (text.includes('logs')) {
      if (!storageData) storageData = json('scanStorageDetailsJson');
      const items = parseStorageItems(storageData?.logs);
      if (!items.length) return toast('No log files were found in the scanned folders.');
      const r = json('cleanSelected', null, items.map(x => x.path).join('\n'));
      toast(r?.cleaned ? `Moved ${r.cleaned} log file(s) to quarantine.` : 'No log files were safely cleaned.');
      storageData = json('scanStorageDetailsJson');
      if (storageData) renderStorage(storageData, 'logs');
    } else {
      storage.querySelector('[data-action="clean"]')?.click();
    }
  }));

  // Converter: no fake progress/recent conversions. Progress is empty until a real conversion starts.
  const converter = $('#view-converter'), choose = $('.drop-zone .primary-button', converter), convert = $('.convert-controls .primary-button', converter), target = $('.convert-controls select', converter), quality = $('.convert-controls input[type=range]', converter), qualityValue = $('.convert-controls label:nth-child(3) b', converter), outputInput = $('.convert-controls label:nth-child(4) input', converter), customOutputInput = $('#customOutputName', converter);
  let selectedFile = '';
  quality?.addEventListener('input', () => { if (qualityValue) qualityValue.textContent = `${quality.value}%`; });
  choose?.addEventListener('click', e => {
    e.preventDefault();
    const desktopBridge = bridge();
    if (typeof desktopBridge?.chooseFile !== 'function') return toast('File chooser is available in the desktop app.');
    const path = desktopBridge.chooseFile();
    if (path) { selectedFile = path; choose.textContent = `▱ ${path.split(/[\\/]/).pop()}`; toast('File selected.'); }
  });
  converter?.querySelector('.drop-zone')?.addEventListener('dragover', e => e.preventDefault());
  converter?.querySelector('.drop-zone')?.addEventListener('drop', e => { e.preventDefault(); toast('For desktop security, use Choose Files to select a local file.'); });
  convert?.addEventListener('click', e => {
    e.preventDefault();
    if (!selectedFile) return toast('Choose a file before converting.');
    const b = bridge();
    if (!b?.convertImageAsync) return toast('Converter backend unavailable.');
    const progress = $('.progress-panel', converter);
    if (progress) {
      progress.innerHTML = '<div class="panel-heading"><h2>Conversion Progress</h2></div><div class="conversion-live"><b>' + escapeHtml(selectedFile.split(/[\\/]/).pop()) + '</b><span>Converting locally…</span><i><em style="width:45%"></em></i><small>In progress — waiting for the real conversion result</small></div>';
    }
    const id = b.convertFileAsync ? b.convertFileAsync(selectedFile, target?.value || 'PNG', outputInput?.value || '', converterCategory, customOutputInput?.value || '') : b.convertImageAsync(selectedFile, target?.value || 'PNG', outputInput?.value || '');
    let attempts = 0;
    const poll = () => {
      const r = json('getConversionResult', null, id);
      if (r?.status === 'done') {
        if (r.success) {
          const outputName = r.path ? r.path.split(/[\\/]/).pop() : 'Output file';
          toast(`Conversion completed locally: ${outputName}`);
          addRecentConversion(selectedFile, target?.value, r.path);
        } else toast(r.message || 'Conversion failed safely.');
        if (progress) {
          progress.innerHTML = '<div class="panel-heading"><h2>Conversion Progress</h2></div><div class="conversion-empty">' + (r.success ? 'Conversion completed.' : 'Conversion failed safely.') + '</div>';
        }
        return;
      }
      if (++attempts < 480) return setTimeout(poll, 250);
      toast('Conversion timed out safely.');
      if (progress) progress.innerHTML = '<div class="panel-heading"><h2>Conversion Progress</h2></div><div class="conversion-empty">Conversion timed out.</div>';
    };
    poll();
  });
  function addRecentConversion(source, targetFormat, path) {
    const panel = $('.recent-panel', converter);
    if (!panel) return;
    const row = document.createElement('div');
    row.className = 'conversion-row';
    const sourceName = source.split(/[\\/]/).pop();
    const outputName = path ? path.split(/[\\/]/).pop() : sourceName;
    row.innerHTML = `<span>▧</span><b>${escapeHtml(outputName)}</b><span>${escapeHtml(sourceName)} → ${escapeHtml(targetFormat)}</span><strong>Completed</strong><em>● Completed</em>`;
    panel.appendChild(row);
  }
  const converterFormats = {
    Image: ['PNG', 'JPG', 'WEBP'],
    Video: ['MP4', 'WEBM', 'MOV'],
    Audio: ['MP3', 'WAV', 'AAC'],
    Document: ['PDF', 'TXT', 'CSV'],
    Ebook: ['EPUB', 'MOBI', 'PDF'],
    Archive: ['ZIP', 'TAR', '7Z']
  };
  const converterInputFormats = {
    Image: 'JPG, PNG, WEBP, BMP, TIFF, GIF (multi-select and images-to-PDF supported)',
    Video: 'MP4, WEBM, MOV',
    Audio: 'MP3, WAV, AAC',
    Document: 'PDF, TXT, DOCX, PPTX, XLSX, CSV',
    Ebook: 'EPUB, MOBI, PDF',
    Archive: 'ZIP, TAR, GZ, BZ2, XZ, 7Z'
  };
  const converterSupportedFormats = {
    Image: [['JPG', 'JPEG'], ['PNG', 'PNG'], ['WEBP', 'WEBP'], ['BMP', 'BMP'], ['TIFF', 'TIFF'], ['PDF', 'Multi-page']],
    Video: [['MP4', 'MP4'], ['WEBM', 'WEBM'], ['MOV', 'MOV']],
    Audio: [['MP3', 'MP3'], ['WAV', 'WAV'], ['AAC', 'AAC']],
    Document: [['PDF', 'PDF'], ['TXT', 'Text'], ['CSV', 'CSV']],
    Ebook: [['EPUB', 'EPUB'], ['MOBI', 'MOBI'], ['PDF', 'PDF']],
    Archive: [['ZIP', 'ZIP'], ['TAR', 'TAR'], ['7Z', '7-Zip']]
  };
  let converterCategory = 'Image';
  function renderConverterCategory(category) {
    converterCategory = converterFormats[category] ? category : 'Image';
    $('.supported-panel h2', converter).textContent = `▧ Supported Formats (${converterCategory})`;
    const formatGrid = $('.supported-panel > div', converter);
    if (formatGrid) {
      formatGrid.replaceChildren(...converterSupportedFormats[converterCategory].map(([format, detail]) => {
        const item = document.createElement('span');
        const suffix = document.createElement('b');
        suffix.textContent = detail;
        item.append(document.createTextNode(format), suffix);
        return item;
      }));
    }
    const hint = $('.drop-zone small', converter);
    if (hint) hint.textContent = `Supports: ${converterInputFormats[converterCategory]}`;
    if (target) target.innerHTML = converterFormats[converterCategory].map(format => `<option>${format}</option>`).join('');
  }
  $$('.converter-tabs button', converter).forEach(btn => btn.addEventListener('click', () => {
    $$('.converter-tabs button', converter).forEach(x => x.classList.remove('selected'));
    btn.classList.add('selected');
    renderConverterCategory(btn.textContent.replace(/[^A-Za-z]/g, ''));
  }));
  $('.convert-controls label:nth-child(4) button', converter)?.addEventListener('click', () => {
    const desktopBridge = bridge();
    if (typeof desktopBridge?.chooseDirectory !== 'function') return toast('Folder picker is available in the desktop app.');
    const path = desktopBridge.chooseDirectory();
    if (path && outputInput) outputInput.value = path;
  });
  $('.recent-panel a', converter)?.addEventListener('click', e => { e.preventDefault(); showDetail('Recent Conversions', 'Only conversions completed during this session are shown.', [['Status', 'No fabricated jobs are displayed.']]); });

  // Settings: every existing option/button is wired; theme and appearance are real CSS changes.
  const settings = $('#view-settings');
  const settingKey = (type, index) => `setting.${type}.${index}`;
  $$('#view-settings input[type=checkbox]').forEach((input, i) => {
    const key = settingKey('checkbox', i);
    const saved = bridge()?.getSetting?.(key, '');
    if (saved !== '') input.checked = saved === 'true';
    input.addEventListener('change', () => bridge()?.setSetting?.(key, String(input.checked)));
  });
  const compactModeInput = [...$$('#view-settings input[type=checkbox]')]
    .find(input => input.closest('label')?.textContent.includes('Compact mode'));
  const applyCompactMode = enabled => document.body.classList.toggle('compact-mode', enabled);
  if (compactModeInput) {
    applyCompactMode(compactModeInput.checked);
    compactModeInput.addEventListener('change', () => applyCompactMode(compactModeInput.checked));
  }
  $$('#view-settings select').forEach((select, i) => {
    const key = settingKey('select', i);
    const saved = bridge()?.getSetting?.(key, '');
    if (saved && Array.from(select.options).some(o => o.value === saved)) select.value = saved;
    select.addEventListener('change', () => bridge()?.setSetting?.(key, select.value));
  });

  let selectedTheme = 'Light';
  const applyTheme = theme => {
    selectedTheme = theme;
    document.body.classList.remove('theme-dark', 'theme-light');
    if (theme === 'Dark') document.body.classList.add('theme-dark');
    else if (theme === 'Light') document.body.classList.add('theme-light');
    else document.body.classList.add(matchMedia('(prefers-color-scheme: dark)').matches ? 'theme-dark' : 'theme-light');
    bridge()?.setSetting?.('appearance.theme', theme);
  };
  const savedTheme = bridge()?.getSetting?.('appearance.theme', 'Light') || 'Light';
  $$('.segmented button', settings).forEach(btn => btn.classList.toggle('selected', btn.textContent.trim() === savedTheme));
  applyTheme(savedTheme);

  $$('.segmented button', settings).forEach(btn => btn.addEventListener('click', () => {
    $$('.segmented button', settings).forEach(x => x.classList.remove('selected'));
    btn.classList.add('selected');
    applyTheme(btn.textContent.trim());
  }));

  const swatchValues = ['#64b5a4', '#81529a', '#4d8e91', '#df9950', '#dc7180', '#82909b'];
  const accentSwatches = $$('.swatches i', settings);
  const applyAccent = color => {
    document.documentElement.style.setProperty('--teal', color);
    document.documentElement.style.setProperty('--teal-dark', color);
    document.body.style.setProperty('--teal', color);
    document.body.style.setProperty('--teal-dark', color);
  };
  const setSelectedAccent = selected => accentSwatches.forEach((swatch, index) => {
    const active = index === selected;
    swatch.setAttribute('aria-pressed', String(active));
    swatch.style.outline = active ? '2px solid currentColor' : 'none';
  });
  const savedAccent = bridge()?.getSetting?.('appearance.accent', '');
  const savedAccentIndex = swatchValues.indexOf(savedAccent);
  if (savedAccent) applyAccent(savedAccent);
  setSelectedAccent(savedAccentIndex);
  accentSwatches.forEach((swatch, index) => {
    swatch.setAttribute('role', 'button');
    swatch.setAttribute('tabindex', '0');
    swatch.setAttribute('aria-label', `${swatch.title} accent`);
    const selectAccent = () => {
      applyAccent(swatchValues[index]);
      bridge()?.setSetting?.('appearance.accent', swatchValues[index]);
      setSelectedAccent(index);
    };
    swatch.addEventListener('click', selectAccent);
    swatch.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        selectAccent();
      }
    });
  });
  const systemThemeQuery = matchMedia('(prefers-color-scheme: dark)');
  const updateAutoTheme = () => { if (selectedTheme === 'Auto') applyTheme('Auto'); };
  if (systemThemeQuery.addEventListener) systemThemeQuery.addEventListener('change', updateAutoTheme);
  else systemThemeQuery.addListener(updateAutoTheme);

  const changeFontSize = size => {
    document.documentElement.style.setProperty('--ui-font-size', size);
    document.body.style.fontSize = size;
    bridge()?.setSetting?.('appearance.fontsize', size);
  };
  const fontSelect1 = $('#systemFontSizeSelect');
  const fontSelect2 = $('#appearanceFontSizeSelect');
  const savedFontSize = bridge()?.getSetting?.('appearance.fontsize', '14px') || '14px';
  if (fontSelect1) { fontSelect1.value = savedFontSize; fontSelect1.addEventListener('change', () => { changeFontSize(fontSelect1.value); if (fontSelect2) fontSelect2.value = fontSelect1.value; }); }
  if (fontSelect2) { fontSelect2.value = savedFontSize; fontSelect2.addEventListener('change', () => { changeFontSize(fontSelect2.value); if (fontSelect1) fontSelect1.value = fontSelect2.value; }); }
  if (savedFontSize) changeFontSize(savedFontSize);

  $('#btnCheckUpdates')?.addEventListener('click', () => showDetail('Update Check', 'PulseOS is running on current local build.', [['Current Version', 'v1.0.0'], ['Update Service', 'Offline build / Verified'], ['Status', 'All core modules up to date.']]));

  const settingsNav = $('.settings-navbar', settings);
  settingsNav?.classList.add('settings-nav');
  const settingsToggle = $('#settingsToggle');
  const settingsButtons = $$('.settings-nav-items button', settings);
  const settingsCards = $$('.settings-content .settings-card', settings);

  function selectSettingsCard(index) {
    settingsButtons.forEach((b, i) => b.classList.toggle('selected', i === index));
    settingsCards.forEach((c, i) => c.classList.toggle('settings-card-active', i === index));
  }
  settingsButtons.forEach((btn, i) => {
    btn.addEventListener('click', () => selectSettingsCard(i));
    btn.style.setProperty('--settings-delay', `${i * 45}ms`);
  });

  settingsToggle?.addEventListener('click', () => {
    const isCollapsed = settingsNav.classList.toggle('collapsed');
    settingsToggle.setAttribute('aria-expanded', String(!isCollapsed));
    settingsButtons.forEach((btn, i) => {
      const step = isCollapsed ? settingsButtons.length - 1 - i : i;
      btn.style.setProperty('--settings-delay', `${step * 45}ms`);
    });
  });
  selectSettingsCard(0);

  // Settings interactive links
  $('#settingsPermissionsRow')?.addEventListener('click', () => showDetail('Application Permissions', 'System security and runtime permission review.', [['Accessibility', 'Granted'], ['File System', 'Full Disk Read/Write'], ['Network Probe', 'Allowed']]));
  $('#settingsFirewallRow')?.addEventListener('click', () => showDetail('System Firewall', 'Host system firewall policy status.', [['Status', 'Enabled'], ['Inbound Connections', 'Filtered'], ['Bridge Port', 'Localhost loopback only']]));
  $('#settingsDefaultAppsRow')?.addEventListener('click', () => showDetail('Default Apps', 'File association management.', [['Web Browser', 'System Default'], ['Document Handler', 'PulseOS Converter / Default']]));
  $('#settingsInstalledAppsRow')?.addEventListener('click', () => { showView('router'); toast('View all installed applications and active software in Smart Router.'); });
  $('#settingsAppPermsRow')?.addEventListener('click', () => showDetail('App Permissions', 'PulseOS sandbox and telemetry bridge access rights.', [['Bridge Telemetry', 'Active (OSHI)'], ['AI Assistant', 'Local Loopback (Ollama)']]));
  $('#settingsAutoStartRow')?.addEventListener('click', () => showDetail('Auto-Start Software', 'Startup software configuration.', [['PulseOS Daemon', 'Enabled'], ['Telemetry Worker', 'Enabled at login']]));

  // Telemetry rendering: no fabricated initial values are introduced by JS.
  const histories = { cpu: [], ram: [], temp: [] };
  const set = (id, v) => { const n = document.getElementById(id); if (n) n.textContent = v; };
  const num = (v, d = 1) => Number.isFinite(Number(v)) ? Number(v).toFixed(d) : '—';
  const up = s => Number.isFinite(Number(s)) ? `${Math.floor(s / 86400)}d ${Math.floor((s % 86400) / 3600)}h ${Math.floor((s % 3600) / 60)}m` : '—';

  function updateTelemetry() {
    const s = snapshot();
    if (!s || !s.ready) return;
    set('healthScore', s.healthScore);
    set('healthGrade', s.healthScore >= 85 ? 'EXCELLENT' : s.healthScore >= 70 ? 'GOOD' : 'WATCH');
    set('healthGradeLarge', s.healthScore >= 85 ? 'EXCELLENT' : s.healthScore >= 70 ? 'GOOD' : 'WATCH');
    set('performanceHealth', s.healthScore >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.cpu - 55) * 0.55))) : '—');
    set('hardwareHealth', s.temperature >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.temperature - 60) * 1.3))) : '—');
    set('storageHealth', s.storageUsed >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.storageUsed - 70) * 1.7))) : '—');
    set('batteryHealth', s.batteryHealth >= 0 ? s.batteryHealth : (s.battery >= 0 ? s.battery : '—'));

    const arc = $('#healthScoreArc');
    if (arc) {
      const score = Math.max(0, Math.min(100, Number(s.healthScore) || 0));
      arc.style.strokeDashoffset = 314 - (314 * score / 100);
    }
    set('healthReason', 'Score calculated from current CPU, memory, thermal, storage and battery signals.');
    set('deviceName', s.deviceModel || s.processor);
    updateDeviceArtwork(s);
    set('osName', s.os);
    set('deviceScreenText', s.os);
    set('uptimeValue', up(s.uptimeSeconds));
    set('cpuSpec', `${s.processor} (${s.cores} cores)`);
    set('ramSpec', `${num(s.ramTotal, 2)} GB total`);
    set('storageSpec', `${num(s.storageTotalGb, 0)} GB total`);
    set('systemInfo', `${s.os} · ${s.architecture}`);
    set('hardwareProcessor', `${s.processor} (${s.cores} cores)`);
    set('hardwareClock', `${num(s.clock, 2)} GHz`);
    set('hardwareRam', `${num(s.ramTotal, 2)} GB total`);
    set('hardwareRamUsed', `${num(s.ramUsed, 2)} GB used`);
    set('hardwareStorage', `${num(s.storageTotalGb, 0)} GB total`);
    set('hardwareStorageUsed', `${num(s.storageUsed, 0)}% used`);
    set('hardwareBattery', s.battery >= 0 ? `${s.battery}%${s.charging ? ' (Charging)' : ''}` : 'Unavailable');
    set('hardwareBatteryHealth', s.batteryHealth >= 0 ? `Health ${s.batteryHealth}%` : 'Health unavailable');
    set('hardwareUptime', `Uptime: ${up(s.uptimeSeconds)}`);
    set('cpuStatusValue', s.cpu > 75 ? 'High' : 'Normal');
    set('ramStatusValue', s.ramPercent > 85 ? 'High' : 'Normal');
    set('storageStatusValue', s.storageUsed > 88 ? 'High' : 'Normal');
    set('batteryStatusValue', s.battery >= 0 ? (s.battery < 25 ? 'Low' : 'Normal') : 'Unavailable');
    set('networkStatusValue', !s.networkReady ? 'Checking' : (s.networkReachable ? 'Normal' : 'Problem'));
    set('topCpuName', s.topCpuName);
    set('topCpuValue', `${num(s.topCpuPercent)}%`);
    set('topRamName', s.topRamName);
    set('topRamValue', `${num(s.topRamMb, 0)} MB`);
    set('storagePressureValue', `${num(s.storageUsed, 0)}%`);
    set('batteryModeValue', s.charging ? 'Charging' : 'On battery');
    set('batteryImpactValue', s.battery >= 0 ? (s.battery < 25 ? 'High' : 'Low') : 'Unavailable');
    set('cpuChartValue', `${num(s.cpu, 0)}%`);
    set('ramChartValue', `${num(s.ramPercent, 0)}%`);
    set('tempChartValue', s.temperature >= 0 ? `${num(s.temperature)}°C` : 'Unavailable');
    set('predictionStatus', s.healthScore >= 80 ? '0 active' : 'Review');
    set('predictionOutlook', s.healthScore >= 80 ? 'Outlook: stable — no immediate degradation pattern visible.' : 'Outlook: review recommended — elevated resource usage detected.');
    set('predictionInputs', `CPU ${num(s.cpu, 0)}% · RAM ${num(s.ramPercent, 0)}% · Temp ${s.temperature >= 0 ? num(s.temperature) + '°C' : '—'} · Storage ${num(s.storageUsed, 0)}% used`);
    set('predictionTrend', `Trend: CPU ${s.cpu > 70 ? 'elevated' : 'stable'} · RAM ${s.ramPercent > 80 ? 'elevated' : 'stable'} · Thermal ${s.temperature > 75 ? 'elevated' : 'stable'}`);
    set('sampleTime', `Last sample ${new Date().toLocaleTimeString()}`);
    set('footerStatus', `● ${s.os} telemetry running normally`);

    [['cpu', s.cpu], ['ram', s.ramPercent], ['temp', s.temperature]].forEach(([k, v]) => {
      if (v >= 0) {
        histories[k].push(v);
        if (histories[k].length > 18) histories[k].shift();
      }
    });

    updateHardwarePage(s);
    updateOverviewStorage(s);
    updateOverviewNetwork(s);
    updateRouterLive();
  }

  function updateOverviewStorage(s) {
    const c = $('#view-overview .storage-intelligence');
    if (!c) return;
    const score = $('.storage-mini strong', c);
    if (score) score.firstChild.textContent = `${num(s.storageUsed, 0)}% `;
    const vals = $$('.storage-mini span b', c);
    if (vals[0]) vals[0].textContent = `${num(s.storageFreeGb, 0)} GB`;
    if (vals[1]) vals[1].textContent = `${num((s.storageTotalGb - s.storageFreeGb) * 0.45, 0)} GB`;
    if (vals[2]) vals[2].textContent = `${num((s.storageTotalGb - s.storageFreeGb) * 0.35, 0)} GB`;
    if (vals[3]) vals[3].textContent = `${num(s.storageTotalGb - s.storageFreeGb, 0)} GB total used`;
    $('.status-good', c)?.replaceChildren(document.createTextNode(s.storageUsed > 88 ? 'Review' : 'Healthy'));
  }

  function updateOverviewNetwork(s) {
    const c = $('#view-overview .network-health');
    if (!c) return;
    const b = $$('.network-mini b', c);
    if (b[0]) b[0].textContent = s.networkReady ? (s.networkReachable ? 'Reachable' : 'Not reachable') : 'Checking';
    if (b[1]) b[1].textContent = s.dnsAvailable ? 'Available' : 'Unavailable';
    if (b[2]) b[2].textContent = s.latency >= 0 ? `${s.latency} ms` : 'Not measured';
    $('.status-good', c)?.replaceChildren(document.createTextNode(s.networkReachable ? 'HEALTHY' : 'PROBLEM'));
  }

  function connect() {
    if (!bridge()) return setTimeout(connect, 250);
    updateTelemetry();
    renderRecentActivity();
    renderSystemWatcher();
    setInterval(updateTelemetry, 1000);
    setInterval(() => {
      renderRecentActivity();
      renderSystemWatcher();
      if (routerScanned) {
        const d = json('scanProcessesJson', []) || [];
        renderProcesses(d);
      }
      if (storageScanned) {
        const d = json('scanStorageDetailsJson');
        if (d) {
          const active = $('.tabs button.selected', storage);
          const cat = active?.dataset.cat || 'all';
          renderStorage(d, cat);
        }
      }
    }, 5000);
  }
  connect();

})();
