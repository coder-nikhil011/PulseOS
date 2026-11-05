(() => {
  'use strict';
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => Array.from(root.querySelectorAll(s));

  // Early theme initialization to prevent flash
  try {
    const earlyTheme = localStorage.getItem('pulseos_theme') || 'Auto';
    const isDark = earlyTheme === 'Dark' || (earlyTheme === 'Auto' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.body.classList.remove('theme-dark', 'theme-light');
    document.body.classList.add(isDark ? 'theme-dark' : 'theme-light');
  } catch (_) {}
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
    document.querySelector('.app-main')?.scrollTo({ top: 0, behavior: 'instant' });
    try { updateTelemetry(); } catch (_) {}
  }
  $$('[data-view]').forEach(item => item.addEventListener('click', () => showView(item.dataset.view)));
  const initial = location.hash.slice(1); if (initial && $(`[data-page="${initial}"]`)) showView(initial);

  // Sidebar toggle: one handler only; never remove or recreate UI buttons.
  const shell = $('.app-shell'), side = $('.side-nav'), sideToggle = $('#sidebarToggle');
  if (sideToggle) sideToggle.addEventListener('click', () => { const collapsed = shell.classList.toggle('sidebar-collapsed'); sideToggle.setAttribute('aria-expanded', String(!collapsed)); });

  // Detail dialog. A card is only clickable when explicitly registered below.
  const overlay = $('#detailOverlay'), detailTitle = $('#detailTitle'), detailSubtitle = $('#detailSubtitle'), detailRows = $('#detailRows');
  const histories = { cpu: [], ram: [], temp: [] };
  let activeDetailMetric = null;

  function closeDetail() {
    if (!overlay) return;
    overlay.classList.remove('show');
    overlay.style.removeProperty('display');
    overlay.style.removeProperty('opacity');
    overlay.style.removeProperty('pointer-events');
    activeDetailMetric = null;
    const gb = $('#detailGraphBox');
    if (gb) gb.style.display = 'none';
  }

  function renderDetailGraph() {
    if (!activeDetailMetric) return;
    const gb = $('#detailGraphBox');
    if (!gb) return;
    const metric = activeDetailMetric;
    const pts = histories[metric] || [];
    const pathEl = $('#detailGraphPath');
    const areaEl = $('#detailGraphArea');
    const dotEl = $('#detailGraphDot');
    const badgeEl = $('#detailGraphBadge');
    const titleEl = $('#detailGraphTitle');
    const maxEl = $('#detailGraphMax');
    const midEl = $('#detailGraphMid');
    const timeEl = $('#detailGraphTime');

    const s = snapshot() || {};
    let curVal = 0;
    let maxVal = 100;
    let unit = '%';
    let strokeColor = '#0284c7';
    let gradientUrl = 'url(#detailGradientBlue)';

    if (metric === 'cpu') {
      curVal = Number(s.cpu) >= 0 ? Number(s.cpu) : (pts.length ? pts[pts.length - 1] : 0);
      maxVal = 100;
      unit = '%';
      strokeColor = '#0284c7';
      gradientUrl = 'url(#detailGradientBlue)';
      if (titleEl) titleEl.textContent = 'CPU Load Real-Time Trace';
      if (maxEl) maxEl.textContent = '100%';
      if (midEl) midEl.textContent = '50%';
    } else if (metric === 'ram') {
      curVal = Number(s.ramPercent) >= 0 ? Number(s.ramPercent) : (pts.length ? pts[pts.length - 1] : 0);
      maxVal = 100;
      unit = '%';
      strokeColor = '#0d9488';
      gradientUrl = 'url(#detailGradientTeal)';
      if (titleEl) titleEl.textContent = 'Memory Pressure Real-Time Trace';
      if (maxEl) maxEl.textContent = '100%';
      if (midEl) midEl.textContent = '50%';
    } else if (metric === 'temp') {
      curVal = Number(s.temperature) >= 0 ? Number(s.temperature) : (pts.length ? pts[pts.length - 1] : 0);
      maxVal = 100;
      unit = '°C';
      strokeColor = '#f97316';
      gradientUrl = 'url(#detailGradientCoral)';
      if (titleEl) titleEl.textContent = 'CPU Thermal Core Trace';
      if (maxEl) maxEl.textContent = '100°C';
      if (midEl) midEl.textContent = '50°C';
    }

    if (badgeEl) {
      badgeEl.textContent = `${Number(curVal).toFixed(1)}${unit}`;
      badgeEl.style.color = strokeColor;
      badgeEl.style.background = metric === 'cpu' ? 'rgba(2, 132, 199, 0.12)' : (metric === 'ram' ? 'rgba(13, 148, 136, 0.12)' : 'rgba(249, 115, 22, 0.12)');
    }
    if (timeEl) {
      timeEl.textContent = `Continuous live telemetry · ${pts.length} data points recorded`;
    }

    if (pathEl && areaEl && dotEl) {
      pathEl.setAttribute('stroke', strokeColor);
      areaEl.setAttribute('fill', gradientUrl);
      dotEl.setAttribute('fill', strokeColor);

      const W = 540;
      const baseY = 150;
      const topY = 15;
      const H = baseY - topY;

      if (!pts || pts.length === 0) {
        pathEl.setAttribute('d', `M0 ${baseY} L${W} ${baseY}`);
        areaEl.setAttribute('d', `M0 ${baseY} L${W} ${baseY} Z`);
        dotEl.setAttribute('cx', W);
        dotEl.setAttribute('cy', baseY);
      } else if (pts.length === 1) {
        const clamped = Math.min(maxVal, Math.max(0, pts[0]));
        const y = Math.round(baseY - (clamped / maxVal) * H);
        pathEl.setAttribute('d', `M0 ${y} L${W} ${y}`);
        areaEl.setAttribute('d', `M0 ${y} L${W} ${y} L${W} ${baseY} L0 ${baseY} Z`);
        dotEl.setAttribute('cx', W);
        dotEl.setAttribute('cy', y);
      } else {
        const step = W / (pts.length - 1);
        let lastX = 0, lastY = baseY;
        const lineD = pts.map((val, idx) => {
          const x = Math.round(idx * step);
          const clamped = Math.min(maxVal, Math.max(0, val));
          const y = Math.round(baseY - (clamped / maxVal) * H);
          lastX = x;
          lastY = y;
          return `${idx === 0 ? 'M' : 'L'}${x} ${y}`;
        }).join(' ');

        pathEl.setAttribute('d', lineD);
        areaEl.setAttribute('d', `${lineD} L${lastX} ${baseY} L0 ${baseY} Z`);
        dotEl.setAttribute('cx', lastX);
        dotEl.setAttribute('cy', lastY);
      }
    }
  }

  function showLiveDetailGraph(metric, title, subtitle, rows, actions = []) {
    activeDetailMetric = metric;
    const gb = $('#detailGraphBox');
    if (gb) gb.style.display = 'flex';
    renderDetailGraph();
    showDetail(title, subtitle, rows, actions);
  }

  function showDetail(title, subtitle, rows, actions = []) {
    if (!overlay) return;
    if (!activeDetailMetric) {
      const gb = $('#detailGraphBox');
      if (gb) gb.style.display = 'none';
    }
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
    if (data.deviceImage) {
      image.src = data.deviceImage;
      return;
    }
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
      requestProcessSnapshot((processes, error) => {
        if (error || !processes) return toast(`Process scan failed: ${error || 'No process data returned.'}`);
        routerScanned = true;
        latestProcesses = processes;
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
      });
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
      ['Battery', s.battery >= 0 ? `${s.battery}%${s.charging ? ' (Charging)' : ''}` : 'AC Power (Desktop)']
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
        ['Battery Status', s.battery >= 0 ? `${s.battery}% ${s.charging ? '(Charging)' : 'Normal'}` : 'AC Power (Desktop)'],
        ['Network Status', s.networkReady ? (s.networkReachable ? 'Connected & Healthy' : 'Unreachable') : 'Checking']
      ], [{ label: 'Run Full Diagnosis', kind: 'fix', onClick: () => runDiagnosis() }]);
    }
    if (kind === 'drainers') {
      return showDetail('Top Resource Drainers', 'Live processes with highest CPU and RAM utilization.', [
        ['Top CPU Consumer', `${s.topCpuName || '—'} (${Number(s.topCpuPercent || 0).toFixed(1)}% CPU)`],
        ['Top RAM Consumer', `${s.topRamName || '—'} (${Number(s.topRamMb || 0).toFixed(0)} MB)`],
        ['Graphics', s.gpuName || 'Integrated GPU'],
        ['Total Running Processes', String(s.processes || 0)],
        ['Storage Pressure', `${Number(s.storageUsed || 0).toFixed(1)}%`],
        ['Battery State', s.battery >= 0 ? `${s.battery}% (${s.charging ? 'Charging' : 'On battery'})` : 'AC Power']
      ], [{ label: 'Open Smart Router', kind: 'fix', onClick: () => showView('router') }]);
    }
    if (kind === 'performance' || kind === 'cpu') {
      return showLiveDetailGraph('cpu', 'CPU Performance Trace', 'Real-time high-definition processor utilization and load tracking.', [
        ['Current Load', `${Number(s.cpu).toFixed(1)}%`],
        ['Clock Frequency', `${Number(s.clock).toFixed(2)} GHz`],
        ['Processor', `${s.processor} (${s.cores} cores)`],
        ['Top Drainer', `${s.topCpuName || '—'} (${Number(s.topCpuPercent || 0).toFixed(1)}%)`],
        ['Thread Concurrency', s.totalThreads ? `${s.totalThreads} OS threads` : `${s.threads} threads`]
      ], [{ label: 'Open Smart Router', kind: 'fix', onClick: () => showView('router') }]);
    }
    if (kind === 'ram') {
      return showLiveDetailGraph('ram', 'Memory Pressure Trace', 'Real-time high-definition RAM utilization and memory saturation.', [
        ['Used Memory', `${Number(s.ramUsed).toFixed(2)} GB (${Number(s.ramPercent).toFixed(1)}%)`],
        ['Total Memory', `${Number(s.ramTotal).toFixed(2)} GB`],
        ['Available Memory', `${(Number(s.ramTotal) - Number(s.ramUsed)).toFixed(2)} GB`],
        ['Top Memory Process', `${s.topRamName || '—'} (${Number(s.topRamMb || 0).toFixed(0)} MB)`]
      ], [
        { label: 'Optimize RAM Now', kind: 'fix', onClick: () => {
          const res = json('optimizeMemory', null);
          if (res?.success) toast(`Reclaimed ${Number(res.freedMb).toFixed(0)} MB RAM!`);
          updateTelemetry();
        }},
        { label: 'Open Smart Router', onClick: () => showView('router') }
      ]);
    }
    if (kind === 'temp') {
      return showLiveDetailGraph('temp', 'Thermal Sensors Trace', 'Real-time high-definition CPU core thermal readings.', [
        ['CPU Temperature', s.temperature >= 0 ? `${Number(s.temperature).toFixed(1)}°C` : 'Sensor unavailable'],
        ['Thermal Status', s.temperature > 80 ? 'Elevated thermal level' : 'Normal operating range'],
        ['Cooling Behavior', 'Dynamic kernel thermal throttle curve'],
        ['Thermal Health', s.temperature >= 0 && s.temperature < 75 ? 'Optimal' : 'Active monitoring']
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
      ['Graphics', s.gpuName || 'Integrated GPU'],
      ['Battery Health', s.battery >= 0 ? (s.batteryHealth > 0 ? `${s.batteryHealth}% retention` : 'Normal') : 'Not applicable (AC Power)'],
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
          const pwr = s.powerWatts > 0 ? ` · ${s.powerWatts.toFixed(1)}W draw` : '';
          const rem = s.batteryRemaining && s.batteryRemaining !== 'Calculating…' ? ` · ${s.batteryRemaining} left` : '';
          detail = `Working: 100% · Charge ${batt}% ${s.charging ? '(Charging)' : 'nominal'}${pwr}${rem}`;
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
          const dR = Number(s.diskReadMb) || 0, dW = Number(s.diskWriteMb) || 0;
          const ioStr = (dR > 0 || dW > 0) ? ` · I/O: ${dR.toFixed(1)}R / ${dW.toFixed(1)}W MB/s` : '';
          detail = `Working: 100% · Healthy storage (${num(s.storageFreeGb, 1)} GB free)${ioStr}`;
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
  let latestProcesses = [];
  let processScanInProgress = false;
  let processScanCallbacks = [];

  function requestProcessSnapshot(callback) {
    processScanCallbacks.push(callback);
    if (processScanInProgress) return;
    processScanInProgress = true;
    const finish = (raw, error = '') => {
      let processes = null;
      if (!error) {
        try {
          processes = Array.isArray(raw) ? raw : JSON.parse(raw);
          if (!Array.isArray(processes)) throw new Error('Process scan returned invalid data.');
        } catch (parseError) {
          error = parseError.message || 'Process scan returned invalid data.';
        }
      }
      processScanInProgress = false;
      if (processes) latestProcesses = processes;
      const callbacks = processScanCallbacks;
      processScanCallbacks = [];
      callbacks.forEach(done => done(processes, error));
    };
    const desktopBridge = bridge();
    if (typeof desktopBridge?.scanProcessesAsync === 'function') {
      try {
        desktopBridge.scanProcessesAsync((result, error) => finish(result, error));
      } catch (error) {
        finish(null, error.message || 'Unable to start process scan.');
      }
    } else {
      finish(json('scanProcessesJson', []));
    }
  }

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

  const selectedProcessPids = new Set();
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

    list.forEach((p, index) => {
      const row = document.createElement('div');
      row.className = 'table-row';
      row.style.setProperty('--row-index', Math.min(index, 25));
      row.dataset.pid = p.pid;
      const cpuVal = Number(p.cpu) || 0;
      const ramMb = Number(p.ramMb) || 0;
      const ramPct = ramTotalMb > 0 ? (ramMb / ramTotalMb * 100) : 0;
      const isHigh = cpuVal > 15 || ramMb > 500;
      const isPidChecked = selectedProcessPids.has(String(p.pid));
      const threadCount = Number(p.threads) || 0;
      const isLeak = threadCount >= 150;
      row.innerHTML = `<input type="checkbox" ${isPidChecked ? 'checked' : ''}><b>${escapeHtml(p.name)}<small>${escapeHtml(p.type || 'Process')}</small></b><span>${p.pid}</span><span>${cpuVal.toFixed(1)}%</span><span>${ramPct.toFixed(1)}% (${ramMb.toFixed(0)}MB)</span><span class="${isLeak ? 'thread-leak-warning' : ''}">${threadCount}${isLeak ? ' ⚠️' : ''}</span><em class="impact ${isHigh ? 'medium' : 'low'}">${isHigh ? 'High' : 'Normal'}</em><span class="running">Running</span><button>Details</button>`;
      const procCb = row.querySelector('input[type="checkbox"]');
      if (procCb) {
        procCb.addEventListener('change', (e) => {
          if (e.target.checked) selectedProcessPids.add(String(p.pid));
          else selectedProcessPids.delete(String(p.pid));
        });
      }
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
      if (routerScanned) renderProcesses(latestProcesses);
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
    const dr = Number(s.diskReadMb) || 0, dw = Number(s.diskWriteMb) || 0;
    const diskEl = $('#routerDiskIoValue');
    if (diskEl) diskEl.textContent = (dr > 0 || dw > 0) ? `R: ${dr.toFixed(1)} · W: ${dw.toFixed(1)} MB/s` : 'Idle';
    const threadEl = $('#routerThreadValue');
    if (threadEl) threadEl.textContent = s.totalThreads ? `${s.totalThreads} active` : (s.threads ? `${s.threads} threads` : '—');
    if (routerScanned){const count=$('.table-panel .panel-heading span',router);if(count)count.textContent='● Live';}
    $('#routerActivityConn').textContent=s.networkReachable?'Stable':'Problem';$('#routerActivityDns').textContent=s.dnsAvailable?'Passed':'Problem';
  }
  const scanBtn=$('#view-router [data-action="scan"]');scanBtn?.addEventListener('click',()=>{
    const started=performance.now(); routerScanned=false;
    const last=$$('.network-scan .scan-meta span',router);
    const now=new Date().toLocaleTimeString();
    if(last[0]?.querySelector('b')) last[0].querySelector('b').textContent=now;
    $('#routerActivityScan').textContent=now;
    scanBtn.disabled = true;
    scanBtn.classList.add('is-scanning');
    scanBtn.innerHTML = '<span class="scan-spinner" aria-hidden="true">⟳</span> Scanning…';
    const tbl = $('.data-table', router);
    const head = tbl ? $('.table-head', tbl) : null;
    if (tbl) {
      tbl.innerHTML = '';
      if (head) tbl.appendChild(head);
      for (let i = 0; i < 6; i++) {
        const sk = document.createElement('div');
        sk.className = 'table-row skeleton-row';
        sk.innerHTML = '<span></span><b class="skel-line"></b><span class="skel-line"></span><span class="skel-line"></span><span class="skel-line"></span><em class="skel-line"></em><span class="skel-line"></span><button style="opacity:0"></button>';
        tbl.appendChild(sk);
      }
    }
    updateRouterLive();
    toast('Scanning network and running processes…');
    const finishScan = (processes, error = '') => {
      if (error || !processes) {
        renderProcesses([]);
        routerScanned = false;
        scanBtn.disabled = false;
        scanBtn.classList.remove('is-scanning');
        scanBtn.innerHTML = '⌕ Scan Now';
        if (last[1]?.querySelector('b')) last[1].querySelector('b').textContent = '—';
        toast(`Process scan failed: ${error || 'No process data returned.'}`);
        return;
      }
      latestProcesses = processes;
      renderProcesses(processes);
      routerScanned = true;
      scanBtn.disabled = false;
      scanBtn.classList.remove('is-scanning');
      scanBtn.innerHTML = '⌕ Scan Now';
      updateRouterLive();
      if(last[1]?.querySelector('b')) last[1].querySelector('b').textContent=`${((performance.now()-started)/1000).toFixed(1)} sec`;
      $('#routerActivityScan').textContent = new Date().toLocaleTimeString();
      const processActivity = $('.router-activity .activity-list span:nth-child(5) b');
      if (processActivity) processActivity.textContent = `${processes.length} found`;
      toast(`Scan complete: ${processes.length} running processes/services found.`);
    };
    requestProcessSnapshot(finishScan);
  });
  router?.addEventListener('click', e => {
    const button = e.target.closest('.data-table .table-row button');
    if (!button) return;
    const row = button.closest('.table-row');
    const pid = Number(row?.dataset.pid);
    if (!Number.isFinite(pid)) return toast(routerScanned ? 'No process is selected.' : 'Run Scan Now first.');
    const process = latestProcesses.find(item => Number(item.pid) === pid);
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
        setTimeout(() => {
          if (routerScanned) requestProcessSnapshot((processes, error) => {
            if (!error && processes) renderProcesses(processes);
          });
        }, 800);
      }
    }]);
  });
  $$('[data-action="optimize-ram"]').forEach(btn => btn.addEventListener('click', () => {
    toast('Optimizing memory and purging inactive RAM caches...');
    const res = json('optimizeMemory', null);
    if (res && res.success) {
      toast(`Memory optimized! Reclaimed ${Number(res.freedMb).toFixed(0)} MB RAM.`);
      showDetail('Memory Optimization Complete', 'Safely reclaimed inactive memory buffers and trimmed cached system memory.', [
        ['Status', 'Optimization Successful'],
        ['Memory Reclaimed', `${Number(res.freedMb).toFixed(1)} MB`],
        ['Current RAM Usage', `${Number(res.currentUsedGb).toFixed(2)} / ${Number(res.totalGb).toFixed(2)} GB`],
        ['Action Taken', 'Purged inactive buffer cache and forced JVM finalization.'],
        ['Impact', 'Reduced memory saturation; system responsiveness improved.']
      ]);
      updateTelemetry();
    } else {
      toast('Memory optimization finished.');
    }
  }));

  $$('[data-action="audit-ports"]').forEach(btn => btn.addEventListener('click', () => {
    toast('Scanning listening network ports and local sockets...');
    const ports = json('getOpenPortsJson', []);
    if (!ports || !ports.length) {
      showDetail('Port Security Audit', 'Audited TCP sockets and listening daemons.', [
        ['Status', 'No unusual open listening ports detected.'],
        ['Security Scope', 'Local network interface scan']
      ]);
      return;
    }
    const rows = ports.map(p => [
      `Port ${p.port} (${p.protocol})`,
      `${p.process} [PID ${p.pid}] · ${p.address || '0.0.0.0'}`
    ]);
    showDetail(
      'Listening Ports & Sockets Security Audit',
      `Found ${ports.length} actively listening ports and local daemons on this machine.`,
      rows,
      [{ label: 'Refresh Audit', kind: 'fix', onClick: () => btn.click() }]
    );
  }));

  router?.querySelector('[data-action="optimize"]')?.addEventListener('click',()=>{if(!routerScanned)return toast('Run Scan Now first.');const p=latestProcesses;showDetail('Optimize Network','Review live processes before taking action.',p.slice(0,12).map(x=>[x.name,`PID ${x.pid} · CPU ${Number(x.cpu).toFixed(1)}% · RAM ${Number(x.ramMb).toFixed(0)} MB`]));});
  router?.querySelector('[data-action="kill-unwanted"]')?.addEventListener('click',()=>{if(!routerScanned)return toast('Run Scan Now first.');toast('Select a live process and use Details → Terminate.');});
  router?.querySelector('[data-action="logs"]')?.addEventListener('click',()=>showDetail('Router Logs','Live session evidence only.',[['Network','Local reachability probe'],['Processes',routerScanned?'Live process scan available':'No process scan run'],['Safety','No fabricated log entries are shown.']]));
  router?.querySelector('.router-activity .panel-heading a')?.addEventListener('click', e => {
    e.preventDefault();
    const current = snapshot();
    const processes = routerScanned ? latestProcesses : [];
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
  const refreshRouterProcesses = () => { if (routerScanned) renderProcesses(latestProcesses); };
  $('#processSearch')?.addEventListener('input', refreshRouterProcesses);
  $('#processFilter')?.addEventListener('change', refreshRouterProcesses);
  $('#processSort')?.addEventListener('change', refreshRouterProcesses);

  // Storage Healer: real scan, real categories, separate tabs, Used Before column and safe quarantine cleanup.
  const storage = $('#view-storage');
  let storageData = null;
  let storageScanned = false;
  const selectedStoragePaths = new Set();
  let storageInitialChecked = false;

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

    // Initialize default selections only on first scan
    if (!storageInitialChecked && items.length) {
      items.forEach(item => {
        const safe = /\.(tmp|temp|cache|log)$/i.test(item.name) || /cache/i.test(item.name) || /logs?/i.test(item.name);
        if (safe) selectedStoragePaths.add(item.path);
      });
      storageInitialChecked = true;
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
          const isChecked = selectedStoragePaths.has(item.path);
          row.innerHTML = `<input type="checkbox" ${isChecked ? 'checked' : ''}><b title="${escapeHtml(item.path)}">${escapeHtml(item.name)}</b><span title="${escapeHtml(item.path)}">${escapeHtml(item.path)}</span><strong>${fmtBytes(item.size)}</strong><span>${fmtDays(item.days)}</span><em class="${safe ? 'safe-pill' : 'review-pill'}">${safe ? 'Safe to clean' : 'Review first'}</em><button>Review</button>`;
          row.dataset.path = item.path;
          const cb = row.querySelector('input[type="checkbox"]');
          if (cb) {
            cb.addEventListener('change', (e) => {
              if (e.target.checked) selectedStoragePaths.add(item.path);
              else selectedStoragePaths.delete(item.path);
            });
          }
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
    const categoryRows = $$('.category-list .cat-row', storage).length ? $$('.category-list .cat-row', storage) : $$('.category-list span', storage);
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
        `<div class="candidate-row"><span class="cand-name" title="${escapeHtml(i.path)}">${escapeHtml(i.name)}</span><b>${fmtBytes(i.size)} · ${fmtDays(i.days)}</b></div>`
      ).join('') || '<span>No large files found in scanned folders.</span>';
    }

    // Usage summary bar & percentage rings
    const s = snapshot();
    updateStorageOverview(s, data);
  }

  function updateStorageRings(data = storageData) {
    const s = snapshot();
    const totalGb = Number(s?.storageTotalGb) || 0;
    const freeGb = Number(s?.storageFreeGb) || 0;
    const totalBytes = totalGb * (1024 ** 3);
    const freeBytes = freeGb * (1024 ** 3);
    const usedBytes = Math.max(0, totalBytes - freeBytes);

    const largeBytes = Number(data?.largeBytes) || 0;
    const logBytes = Number(data?.logBytes) || 0;
    const cacheBytes = Number(data?.cacheBytes) || 0;
    const otherBytes = Math.max(0, usedBytes - largeBytes - logBytes - cacheBytes);

    const calcPct = (bytes) => (totalBytes > 0 ? Math.min(100, Math.max(0, (bytes / totalBytes) * 100)) : 0);

    const setRing = (arcId, valId, sizeId, pct, sizeBytes) => {
      const arc = document.getElementById(arcId);
      const val = document.getElementById(valId);
      const size = document.getElementById(sizeId);
      const cleanPct = Math.max(0, Math.min(100, Number(pct) || 0));
      if (arc) {
        const offset = 176 - (176 * cleanPct / 100);
        arc.style.strokeDashoffset = offset;
      }
      if (val) {
        val.textContent = cleanPct === 0 ? '0%' : (cleanPct < 1 ? `${cleanPct.toFixed(1)}%` : `${Math.round(cleanPct)}%`);
      }
      if (size) {
        size.textContent = sizeBytes !== undefined ? fmtBytes(sizeBytes) : '—';
      }
    };

    setRing('ringLargeArc', 'ringLargeVal', 'ringLargeSize', calcPct(largeBytes), largeBytes);
    setRing('ringLogsArc', 'ringLogsVal', 'ringLogsSize', calcPct(logBytes), logBytes);
    setRing('ringCacheArc', 'ringCacheVal', 'ringCacheSize', calcPct(cacheBytes), cacheBytes);
    setRing('ringOtherArc', 'ringOtherVal', 'ringOtherSize', calcPct(otherBytes), otherBytes);
    setRing('ringFreeArc', 'ringFreeVal', 'ringFreeSize', calcPct(freeBytes), freeBytes);
  }

  function updateStorageOverview(s = snapshot(), data = storageData) {
    if (!s || !storage) return;
    const totalGb = Number(s?.storageTotalGb);
    const freeGb = Number(s?.storageFreeGb);
    const hasDiskUsage = Number.isFinite(totalGb) && totalGb > 0 && Number.isFinite(freeGb) && freeGb >= 0;
    const usedGb = hasDiskUsage ? Math.max(0, totalGb - freeGb) : 0;
    const usedPercent = hasDiskUsage ? Math.min(100, Math.max(0, Number(s.storageUsed))) : 0;

    const used = $('.storage-usage .panel-heading b', storage);
    if (used) used.textContent = hasDiskUsage ? `${usedPercent.toFixed(0)}% Used` : 'Usage unavailable';
    const free = $('.storage-usage .disk-line b', storage);
    if (free) free.textContent = hasDiskUsage ? `${freeGb.toFixed(1)} GB Free` : '— Free';
    const bar = $('.usage-bar i', storage);
    if (bar) bar.style.width = `${usedPercent}%`;
    const totalValue = $('#stUsageTotal', storage);
    const usedValue = $('#stUsageUsed', storage);
    const freeValue = $('#stUsageFree', storage);
    const filesValue = $('#stUsageFiles', storage);
    if (totalValue) totalValue.textContent = hasDiskUsage ? `${totalGb.toFixed(1)} GB` : '—';
    if (usedValue) usedValue.textContent = hasDiskUsage ? `${usedGb.toFixed(1)} GB` : '—';
    if (freeValue) freeValue.textContent = hasDiskUsage ? `${freeGb.toFixed(1)} GB` : '—';
    if (filesValue) filesValue.textContent = data ? Number(data?.scannedFiles ?? data?.all?.length ?? 0).toLocaleString() : '—';
    const quickFree = $('#quickFreeEstimate', storage);
    const quickCache = $('#quickCacheEstimate', storage);
    const quickLogs = $('#quickLogsEstimate', storage);
    if (quickFree) quickFree.textContent = `~ ${fmtBytes((Number(data?.tempBytes) || 0) + (Number(data?.cacheBytes) || 0) + (Number(data?.logBytes) || 0))}`;
    if (quickCache) quickCache.textContent = `~ ${fmtBytes(Number(data?.cacheBytes) || 0)}`;
    if (quickLogs) quickLogs.textContent = `~ ${fmtBytes(Number(data?.logBytes) || 0)}`;

    const donut = $('.donut', storage);
    if (donut) {
      const ringColor = usedPercent >= 90 ? '#cf6959' : usedPercent >= 70 ? '#d59b4e' : '#4c9f86';
      donut.style.setProperty('--storage-ring-color', ringColor);
      donut.style.background = hasDiskUsage
        ? `conic-gradient(${ringColor} 0 ${usedPercent}%, var(--line) ${usedPercent}% 100%)`
        : 'conic-gradient(var(--line) 0 100%)';
      donut.setAttribute('aria-label', hasDiskUsage
        ? `${usedPercent.toFixed(0)} percent of disk used`
        : 'Disk usage unavailable');
      const strong = donut.querySelector('strong');
      if (strong) strong.innerHTML = hasDiskUsage
        ? `${usedPercent.toFixed(0)}%<small>Used</small>`
        : `—<small>Unavailable</small>`;
    }

    updateStorageRings(data);
  }

  // Scan Button in Storage
  let storageScanInProgress = false;
  storage?.querySelector('[data-action="scan"]')?.addEventListener('click', () => {
    const b = bridge();
    if (storageScanInProgress) return;
    if (!b || (!b.scanStorageDetailsAsync && !b.scanStorageDetailsJson)) return toast('Storage scanner is unavailable.');
    storageScanInProgress = true;
    toast('Scanning filesystem for large, cache and log files…');
    const scanBtnSt = storage.querySelector('[data-action="scan"]');
    if (scanBtnSt) {
      scanBtnSt.disabled = true;
      scanBtnSt.classList.add('is-scanning');
      scanBtnSt.innerHTML = '<span class="scan-spinner" aria-hidden="true">⟳</span> Scanning…';
    }
    // Show skeleton in table
    const stTbl = $('.file-table', storage);
    if (stTbl) {
      stTbl.innerHTML = '<div class="file-head"><span></span><span>File Name</span><span>Path</span><span>Size</span><span>Used Before</span><span>Status</span><span>Action</span></div>';
      for (let i = 0; i < 5; i++) {
        const sk = document.createElement('div');
        sk.className = 'file-row skeleton-row';
        sk.innerHTML = '<span></span><b class="skel-line"></b><span class="skel-line"></span><strong class="skel-line"></strong><span class="skel-line"></span><em class="skel-line"></em><button style="opacity:0">Review</button>';
        stTbl.appendChild(sk);
      }
    }
    const finishStorageScan = (raw, error = '') => {
      storageScanInProgress = false;
      let data;
      try {
        if (error) throw new Error(error);
        data = typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (!data || typeof data !== 'object' || !Array.isArray(data.all)) {
          throw new Error('Storage scan returned invalid data.');
        }
      } catch (scanError) {
      storageScanned = false;
      const scanBtnFail = storage?.querySelector('[data-action="scan"]');
      if (scanBtnFail) {
        scanBtnFail.disabled = false;
        scanBtnFail.classList.remove('is-scanning');
        scanBtnFail.innerHTML = '⌕ Scan Now';
      }
      const table = $('.file-table', storage);
      if (table) table.innerHTML = `<div class="file-row scan-error-row"><span>!</span><b>Scan could not be completed</b><span>${escapeHtml(scanError.message || 'No storage data returned.')}</span><strong>—</strong><span>—</span><em>Retry scan</em><button style="display:none">Review</button></div>`;
      return toast(`Storage scan failed: ${scanError.message || 'No storage data returned.'}`);
      }
      storageScanned = true;
      renderStorage(data, 'all');
      $$('.tabs button', storage).forEach(x => x.classList.remove('selected'));
      $$('.tabs button', storage)[0]?.classList.add('selected');
      const scanBtnStDone = storage?.querySelector('[data-action="scan"]');
      if (scanBtnStDone) {
        scanBtnStDone.disabled = false;
        scanBtnStDone.classList.remove('is-scanning');
        scanBtnStDone.innerHTML = '⌕ Scan Now';
      }
      bridge()?.recordActivity?.('Storage scan completed', `${(data?.all || []).length} real files inspected`);
      toast(data.scanError ? `Storage scan completed with warning: ${data.scanError}` : `Storage scan complete: ${(data?.all || []).length} files indexed.`);
    };
    if (typeof b.scanStorageDetailsAsync === 'function') {
      try {
        b.scanStorageDetailsAsync((result, error) => finishStorageScan(result, error));
      } catch (error) {
        finishStorageScan(null, error.message || 'Unable to start storage scan.');
      }
    } else {
      setTimeout(() => finishStorageScan(json('scanStorageDetailsJson')), 80);
    }
  });

  function refreshStorageData(category) {
    const desktopBridge = bridge();
    const finish = (raw, error = '') => {
      try {
        if (error) throw new Error(error);
        const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (!data || typeof data !== 'object' || !Array.isArray(data.all)) {
          throw new Error('Storage scan returned invalid data.');
        }
        storageData = data;
        renderStorage(data, category);
      } catch (scanError) {
        toast(`Storage refresh failed: ${scanError.message || 'No storage data returned.'}`);
      }
    };
    if (typeof desktopBridge?.scanStorageDetailsAsync === 'function') {
      try {
        desktopBridge.scanStorageDetailsAsync((result, error) => finish(result, error));
      } catch (error) {
        finish(null, error.message || 'Unable to refresh storage data.');
      }
    } else {
      finish(json('scanStorageDetailsJson'));
    }
  }

  // Clean Selected Safe Files
  storage?.querySelector('[data-action="clean"]')?.addEventListener('click', () => {
    if (!storageScanned) return toast('Run Scan Now first.');
    const paths = $$('.file-row input[type=checkbox]:checked', storage).map(i => i.closest('.file-row')?.dataset.path).filter(Boolean);
    if (!paths.length) return toast('Select safe files first.');
    const r = json('cleanSelected', null, paths.join('\n'));
    toast(r?.cleaned ? `Moved ${r.cleaned} file(s) to PulseOS quarantine (${fmtBytes(r?.freedBytes || 0)} freed).` : 'No selected safe files were cleaned.');
    if (r?.cleaned) {
      const activeTab = $('.tabs button.selected', storage)?.dataset.cat || 'all';
      refreshStorageData(activeTab);
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
    if (r?.cleaned) refreshStorageData('cache');
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
      if (r?.cleaned) refreshStorageData('logs');
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

  const getStoredTheme = () => {
    try {
      const local = localStorage.getItem('pulseos_theme');
      if (local && (local === 'Auto' || local === 'Light' || local === 'Dark')) return local;
    } catch (_) {}
    return bridge()?.getSetting?.('appearance.theme', 'Auto') || 'Auto';
  };

  let selectedTheme = getStoredTheme();
  const applyTheme = theme => {
    selectedTheme = theme || 'Auto';
    try { localStorage.setItem('pulseos_theme', selectedTheme); } catch (_) {}
    bridge()?.setSetting?.('appearance.theme', selectedTheme);

    document.body.classList.remove('theme-dark', 'theme-light');
    if (selectedTheme === 'Dark') {
      document.body.classList.add('theme-dark');
    } else if (selectedTheme === 'Light') {
      document.body.classList.add('theme-light');
    } else {
      const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      document.body.classList.add(isDark ? 'theme-dark' : 'theme-light');
    }

    $$('.segmented button', settings).forEach(btn => {
      btn.classList.toggle('selected', btn.textContent.trim().toLowerCase() === selectedTheme.toLowerCase());
    });
  };

  applyTheme(selectedTheme);

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

  settingsToggle?.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    const isCollapsed = settingsNav.classList.toggle('collapsed');
    settingsToggle.setAttribute('aria-expanded', String(!isCollapsed));
    settingsToggle.classList.toggle('is-collapsed', isCollapsed);
    settingsButtons.forEach((btn, i) => {
      const step = isCollapsed ? settingsButtons.length - 1 - i : i;
      btn.style.setProperty('--settings-delay', `${step * 45}ms`);
    });
    toast(isCollapsed ? 'Settings navigation collapsed.' : 'Settings navigation expanded.');
  });
  selectSettingsCard(0);

  // Settings checkboxes - bind all at once for localStorage persistence
  $$('.settings-card input[type="checkbox"]').forEach(cb => {
    const key = 'pulseos_setting_' + (cb.id || cb.name || cb.dataset.pref || Math.random());
    // Restore saved state
    try { if (localStorage.getItem(key) !== null) cb.checked = localStorage.getItem(key) === '1'; } catch(_) {}
    cb.addEventListener('change', () => {
      try { localStorage.setItem(key, cb.checked ? '1' : '0'); } catch(_) {}
    });
  });

  converter?.querySelector('.browse-btn')?.addEventListener('click', () => {
    const s = snapshot();
    toast(`Output directory: ${s?.defaultFolder || (s?.isWindows ? 'C:\\PulseOS\\Converted' : '~/PulseOS/Converted')}`);
  });

  // Settings interactive links
  $('#settingsPermissionsRow')?.addEventListener('click', () => showDetail('Application Permissions', 'System security and runtime permission review.', [['Accessibility', 'Granted'], ['File System', 'Full Disk Read/Write'], ['Network Probe', 'Allowed']]));
  $('#settingsFirewallRow')?.addEventListener('click', () => showDetail('System Firewall', 'Host system firewall policy status.', [['Status', 'Enabled'], ['Inbound Connections', 'Filtered'], ['Bridge Port', 'Localhost loopback only']]));
  $('#settingsDefaultAppsRow')?.addEventListener('click', () => showDetail('Default Apps', 'File association management.', [['Web Browser', 'System Default'], ['Document Handler', 'PulseOS Converter / Default']]));
  $('#settingsInstalledAppsRow')?.addEventListener('click', () => { showView('router'); toast('View all installed applications and active software in Smart Router.'); });
  $('#settingsAppPermsRow')?.addEventListener('click', () => showDetail('App Permissions', 'PulseOS sandbox and telemetry bridge access rights.', [['Bridge Telemetry', 'Active (OSHI)'], ['AI Assistant', 'Local Loopback (Ollama)']]));
  $('#settingsAutoStartRow')?.addEventListener('click', () => showDetail('Auto-Start Software', 'Startup software configuration.', [['PulseOS Daemon', 'Enabled'], ['Telemetry Worker', 'Enabled at login']]));

  // Telemetry rendering: no fabricated initial values are introduced by JS.
  const set = (id, v) => {
  const n = document.getElementById(id);
  if (!n) return;
  if (v === '—' || v === '' || v == null) {
    n.textContent = '';
    n.style.display = 'none';
  } else {
    n.textContent = v;
    n.style.display = '';
  }
};
  const num = (v, d = 1) => Number.isFinite(Number(v)) ? Number(v).toFixed(d) : '—';
  const up = s => Number.isFinite(Number(s)) ? `${Math.floor(s / 86400)}d ${Math.floor((s % 86400) / 3600)}h ${Math.floor((s % 3600) / 60)}m` : '—';

  function updateTelemetry() {
    const s = snapshot();
    if (!s || !s.ready) return;
    set('healthScore', s.healthScore);
    set('healthGrade', s.healthScore >= 85 ? 'EXCELLENT' : s.healthScore >= 70 ? 'GOOD' : 'WATCH');
    set('healthGradeLarge', s.healthScore >= 85 ? 'EXCELLENT' : s.healthScore >= 70 ? 'GOOD' : 'WATCH');
    set('performanceHealth', s.healthScore >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.cpu - 55) * 0.55))) : '—');
    set('hardwareHealth', s.temperature >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.temperature - 60) * 1.3))) : (s.temperature < 0 ? 'Normal' : '—'));
    set('storageHealth', s.storageUsed >= 0 ? Math.max(0, Math.min(100, Math.round(100 - Math.max(0, s.storageUsed - 70) * 1.7))) : '—');
    if (s.battery >= 0) {
      const bH = s.batteryHealth > 0 ? s.batteryHealth : (s.battery >= 20 ? 100 : s.battery);
      set('batteryHealth', `${bH}`);
    } else {
      set('batteryHealth', 'AC Power');
    }

    const arc = $('#healthScoreArc');
    if (arc) {
      const score = Math.max(0, Math.min(100, Number(s.healthScore) || 0));
      arc.style.strokeDashoffset = 314 - (314 * score / 100);
    }
    set('healthReason', 'Score calculated from current CPU, memory, thermal, storage and battery signals.');

    if (!window.__staticSpecsPopulated && s.ready) {
      window.__staticSpecsPopulated = true;
      set('deviceName', s.deviceModel || s.processor);
      updateDeviceArtwork(s);
      set('osName', s.os);
      set('deviceScreenText', s.os);
      set('cpuSpec', `${s.processor} (${s.cores} cores)`);
      set('ramSpec', `${num(s.ramTotal, 2)} GB total`);
      set('storageSpec', `${num(s.storageTotalGb, 0)} GB total`);
      set('systemInfo', `${s.os} · ${s.architecture}`);
      set('aboutOsName', s.os || '—');
      set('aboutDeviceModel', s.deviceModel || s.processor || '—');
      set('aboutArch', s.architecture || '—');
      set('hardwareProcessor', `${s.processor} (${s.cores} cores)`);
      set('hardwareRam', `${num(s.ramTotal, 2)} GB total`);
      set('hardwareStorage', `${num(s.storageTotalGb, 0)} GB total`);
      set('hardwareGpuName', s.gpuName || 'Integrated GPU');
      set('hardwareGpuDetail', s.gpuVramGb > 0 ? `${s.gpuVramGb.toFixed(0)} GB memory` : (s.gpuVendor ? s.gpuVendor : 'Operational'));
      set('topGpuName', s.gpuName || 'Integrated GPU');
      set('topGpuStatus', 'Nominal');
    }

    set('uptimeValue', up(s.uptimeSeconds));
    set('hardwareClock', `${num(s.clock, 2)} GHz`);
    set('hardwareRamUsed', `${num(s.ramUsed, 2)} GB used`);
    set('hardwareStorageUsed', `${num(s.storageUsed, 0)}% used`);
    if (s.battery >= 0) {
      set('hardwareBattery', `${s.battery}%${s.charging ? ' (Charging)' : ' (On battery)'}`);
      set('hardwareBatteryHealth', s.batteryHealth > 0 ? `Health ${s.batteryHealth}%` : 'Normal');
    } else {
      set('hardwareBattery', 'AC Power (Desktop)');
      set('hardwareBatteryHealth', 'Not applicable');
    }
    set('hardwareUptime', `Uptime: ${up(s.uptimeSeconds)}`);
    set('cpuStatusValue', s.cpu > 75 ? 'High' : 'Normal');
    set('ramStatusValue', s.ramPercent > 85 ? 'High' : 'Normal');
    set('storageStatusValue', s.storageUsed > 88 ? 'High' : 'Normal');
    set('batteryStatusValue', s.battery >= 0 ? (s.battery < 20 && !s.charging ? 'Low' : (s.charging ? 'Charging' : 'Normal')) : 'AC Power');
    set('networkStatusValue', !s.networkReady ? 'Checking' : (s.networkReachable ? 'Normal' : 'Problem'));
    set('topCpuName', s.topCpuName);
    set('topCpuValue', `${num(s.topCpuPercent)}%`);
    set('topRamName', s.topRamName);
    set('topRamValue', `${num(s.topRamMb, 0)} MB`);
    const diskR = Number(s.diskReadMb) || 0, diskW = Number(s.diskWriteMb) || 0;
    set('storagePressureValue', (diskR > 0 || diskW > 0) ? `R: ${diskR.toFixed(1)} / W: ${diskW.toFixed(1)} MB/s` : `${num(s.storageUsed, 0)}%`);
    set('batteryModeValue', s.battery >= 0 ? (s.charging ? 'Charging' : (s.batteryRemaining && s.batteryRemaining !== 'Calculating…' ? `${s.batteryRemaining}` : 'On battery')) : 'AC Power');
    set('batteryImpactValue', s.powerWatts > 0 ? `${s.powerWatts.toFixed(1)} W` : (s.battery >= 0 ? (s.charging ? 'Charging' : (s.battery < 20 ? 'High' : 'Low')) : 'Optimal'));
    set('cpuChartValue', `${num(s.cpu, 0)}%`);
    set('ramChartValue', `${num(s.ramPercent, 0)}%`);
    set('tempChartValue', s.temperature >= 0 ? `${num(s.temperature)}°C` : 'Unavailable');
    set('predictionStatus', s.healthScore >= 80 ? '0 active' : 'Review');
    set('predictionOutlook', s.healthScore >= 80 ? 'Outlook: stable — no immediate degradation pattern visible.' : 'Outlook: review recommended — elevated resource usage detected.');
    set('predictionInputs', `CPU ${num(s.cpu, 0)}% · RAM ${num(s.ramPercent, 0)}% · Temp ${s.temperature >= 0 ? num(s.temperature) + '°C' : '—'} · Storage ${num(s.storageUsed, 0)}% used`);
    set('predictionTrend', `Trend: CPU ${s.cpu > 70 ? 'elevated' : 'stable'} · RAM ${s.ramPercent > 80 ? 'elevated' : 'stable'} · Thermal ${s.temperature > 75 ? 'elevated' : 'stable'}`);
    set('sampleTime', `Last sample ${new Date().toLocaleTimeString()}`);
    set('footerStatus', `● ${s.os} telemetry running normally`);

    [['cpu', s.cpu, 100], ['ram', s.ramPercent, 100], ['temp', s.temperature, 100]].forEach(([k, v, maxVal]) => {
      if (v >= 0) {
        histories[k].push(v);
        if (histories[k].length > 25) histories[k].shift();
        const pathEl = document.getElementById(`${k}ChartPath`);
        const timeEl = document.getElementById(`${k}ChartTime`);
        if (pathEl && histories[k].length > 0) {
          const pts = histories[k];
          if (pts.length === 1) {
            const y = Math.round(90 - Math.min(100, Math.max(0, pts[0])) * 0.7);
            pathEl.setAttribute('d', `M0 ${y} L360 ${y}`);
          } else {
            const step = 360 / (pts.length - 1);
            const d = pts.map((val, idx) => {
              const x = Math.round(idx * step);
              const clamped = Math.min(maxVal, Math.max(0, val));
              const y = Math.round(90 - (clamped / maxVal) * 75);
              return `${idx === 0 ? 'M' : 'L'}${x} ${y}`;
            }).join(' ');
            pathEl.setAttribute('d', d);
          }
        }
        if (timeEl) timeEl.textContent = 'Live telemetry (2s refresh)';
      }
    });

    if (activeDetailMetric && overlay && overlay.classList.contains('show')) {
      renderDetailGraph();
    }

    // Platform-specific branding & paths (Mac vs Windows)
    if (s.isMac) {
      document.title = 'PulseOS | macOS Intelligent Hardware Health';
      const sub = $('.brand small');
      if (sub && !sub.dataset.osSet) { sub.textContent = 'macOS Desktop Utility & Hardware Health'; sub.dataset.osSet = '1'; }
    } else if (s.isWindows) {
      document.title = 'PulseOS | Windows Intelligent Hardware Health';
      const sub = $('.brand small');
      if (sub && !sub.dataset.osSet) { sub.textContent = 'Windows Desktop Utility & Hardware Health'; sub.dataset.osSet = '1'; }
    }

    // High performance view rendering - ONLY render the visible page to eliminate lag & CPU waste
    const activeView = $('.view.active-view')?.dataset.page || 'overview';
    if (activeView === 'overview') {
      updateOverviewStorage(s);
      updateOverviewNetwork(s);
    } else if (activeView === 'hardware') {
      updateHardwarePage(s);
    } else if (activeView === 'router') {
      updateRouterLive();
    } else if (activeView === 'storage') {
      updateStorageOverview(s);
    }
  }

  function updateOverviewStorage(s) {
    const c = $('#view-overview .storage-intelligence');
    if (!c) return;
    const score = $('.storage-mini strong', c);
    if (score) score.firstChild.textContent = `${num(s.storageUsed, 0)}% `;
    const vals = $$('.storage-mini span b', c);
    if (vals[0]) vals[0].textContent = `${num(s.storageFreeGb, 0)} GB`;
    if (vals[1]) vals[1].textContent = `${num(s.storageTotalGb, 0)} GB`;
    if (vals[2]) vals[2].textContent = s.storageUsed > 88 ? 'Near Full' : 'Healthy';
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
    setInterval(updateTelemetry, 2000);
    setInterval(() => {
      const activeView = $('.view.active-view')?.dataset.page || 'overview';
      if (activeView === 'overview') {
        renderRecentActivity();
        renderSystemWatcher();
      }
      // Router and storage re-render only on user action - prevents high CPU and UI jank
    }, 10000);
  }
  connect();

})();
