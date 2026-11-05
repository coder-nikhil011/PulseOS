(() => {
  const processes = [
    { name: 'java', pid: 6284, cpu: 107.1, ramMb: 746, threads: 79, battery: 'High', status: 'Running', type: 'App' },
    { name: 'MetricSanAgent', pid: 1043, cpu: 28.9, ramMb: 291, threads: 9, battery: 'High', status: 'Running', type: 'Software' },
    { name: 'Antigravity Helper (Renderer)', pid: 1422, cpu: 14.3, ramMb: 351, threads: 18, battery: 'Normal', status: 'Running', type: 'Service' },
    { name: 'Google Chrome Helper (Renderer)', pid: 5743, cpu: 12.0, ramMb: 352, threads: 21, battery: 'Normal', status: 'Running', type: 'App' },
    { name: 'Google Chrome Helper (Alert)', pid: 1296, cpu: 11.5, ramMb: 449, threads: 23, battery: 'Normal', status: 'Running', type: 'App' },
    { name: 'Antigravity Helper', pid: 1308, cpu: 7.8, ramMb: 182, threads: 17, battery: 'Normal', status: 'Running', type: 'Service' }
  ];
  const now = Date.now();
  const recent = [
    { time: now, message: 'PulseOS dashboard started', detail: 'Live session' },
    { time: now - 30000, message: 'Telemetry engine initialized', detail: 'Live' },
    { time: now - 60000, message: 'System Watcher initialized', detail: 'Downloads monitoring' }
  ];
  const files = [
    { path: '/Users/nikhilpandit/Downloads/The.Matrix.1999.720p.BluRay.Hindi.English.DD.2.0.x264-ESubs.mkv', size: 1503238553, usedBeforeDays: 7, risk: 'No obvious risk · heuristic only', status: 'Review first' },
    { path: '/Users/nikhilpandit/Downloads/PulseOS/website/downloads/PulseOS-1.0.8.dmg', size: 155189248, usedBeforeDays: 29, risk: 'No obvious risk · heuristic only', status: 'Review first' },
    { path: '/Users/nikhilpandit/Downloads/PulseOS/website/downloads/PulseOS-1.0.8.exe', size: 120586240, usedBeforeDays: 29, risk: 'No obvious risk · heuristic only', status: 'Review first' },
    { path: '/Users/nikhilpandit/Downloads/PulseOS/website/downloads/PulseOS-1.0.8.deb', size: 100663296, usedBeforeDays: 29, risk: 'No obvious risk · heuristic only', status: 'Review first' },
    { path: '/Users/nikhilpandit/Downloads/PulseOS/website/downloads/PulseOS-2.0.4.apk', size: 58720256, usedBeforeDays: 0, risk: 'No obvious risk · heuristic only', status: 'Safe' },
    { path: '/Users/nikhilpandit/Downloads/PulseOS/website/downloads/PulseOS-android.apk', size: 58720256, usedBeforeDays: 0, risk: 'No obvious risk · heuristic only', status: 'Safe' },
    { path: '/Users/nikhilpandit/Downloads/SafeExamBrowser-3.7.dmg', size: 11534336, usedBeforeDays: 13, risk: 'Review · executable/script', status: 'Review' },
    { path: '/Users/nikhilpandit/Downloads/WhatsApp Video 2026-10-06 at 13.08.21.mp4', size: 10485760, usedBeforeDays: 27, risk: 'No obvious risk · heuristic only', status: 'Safe' },
    { path: '/Users/nikhilpandit/Downloads/mobile.zip', size: 6291456, usedBeforeDays: 27, risk: 'No obvious risk · heuristic only', status: 'Safe' }
  ];
  const storage = {
    all: files,
    large: files.slice(0, 6),
    logs: [
      { path: '/Users/nikhilpandit/Library/Logs/PulseOS/session.log', size: 5242880, usedBeforeDays: 2, status: 'Safe to clear' }
    ],
    cache: [
      { path: '/Users/nikhilpandit/Library/Caches/PulseOS/render-cache.tmp', size: 155648, usedBeforeDays: 1, status: 'Safe to clear' }
    ],
    temp: [
      { path: '/tmp/pulseos-worker.tmp', size: 155648, usedBeforeDays: 1, status: 'Safe to clear' }
    ],
    largeBytes: 1800000000,
    logBytes: 5242880,
    cacheBytes: 155648,
    tempBytes: 155648,
    scannedFiles: 1251,
    failedRoots: 0
  };
  const settings = new Map();
  const diagnosis = {
    ready: true,
    healthy: true,
    percentWorking: 100,
    drainPercent: 0,
    problem: 'Sample components are operating normally.',
    cause: 'Illustrative website telemetry only.',
    solution: 'Explore the six pages to preview PulseOS workflows.',
    fixRoute: 'route:hardware'
  };
  const json = value => JSON.stringify(value);

  window.pulseOSBridge = {
    start() {},
    stop() {},
    recordActivity() {},
    getSnapshotJson: () => json({
      ready: true,
      cpu: 53.0,
      ramUsed: 6.18,
      ramTotal: 8.00,
      ramPercent: 80.0,
      temperature: 82.0,
      battery: 59,
      charging: false,
      batteryHealth: 95,
      clock: 2.91,
      threads: 144,
      processes: 126,
      storageUsed: 40.0,
      storageFreeGb: 275.0,
      storageTotalGb: 460.0,
      healthScore: 96,
      uptimeSeconds: 18420,
      processor: 'Apple M4 Pro',
      deviceManufacturer: 'Apple',
      deviceModel: 'MacBook Pro',
      deviceImage: '../devices/macbook-pro.svg',
      cores: 16,
      os: 'macOS 27.2',
      architecture: 'aarch64',
      networkReady: true,
      networkReachable: true,
      dnsAvailable: true,
      latency: 20,
      networkRxMbps: 10.8,
      networkTxMbps: 10.0,
      topCpuName: 'java',
      topCpuPercent: 107.1,
      topRamName: 'MetricSanAgent',
      topRamMb: 1465
    }),
    getNetworkDetailsJson: () => json({
      network: 'Wi-Fi (Connected)', ip: '192.168.1.17', gateway: '192.168.1.1', dns: 'DNS available'
    }),
    getRecentActivityJson: () => json(recent),
    getSystemWatcherJson: () => json({
      watchPath: '/Users/nikhilpandit/Downloads',
      files: [
        {
          path: '/Users/nikhilpandit/Downloads/The.Matrix.1999.720p.BluRay.Hindi.English.DD.2.0.x264-ESubs.mkv',
          size: 1503238553,
          modified: now - 3600000 * 20,
          risk: 'No obvious risk · heuristic only'
        },
        {
          path: '/Users/nikhilpandit/Downloads/Experiments_7_and_8_Java_WS.pdf',
          size: 874000,
          modified: now - 3600000 * 7,
          risk: 'No obvious risk · heuristic only'
        },
        {
          path: '/Users/nikhilpandit/Downloads/WhatsApp Video 2026-10-06 at 13.08.21.mp4',
          size: 10485760,
          modified: now - 3600000 * 5,
          risk: 'No obvious risk · heuristic only'
        },
        {
          path: '/Users/nikhilpandit/Downloads/PulseOS_mobile_fixed.zip',
          size: 5242880,
          modified: now - 3600000 * 2,
          risk: 'No obvious risk · heuristic only'
        },
        {
          path: '/Users/nikhilpandit/Downloads/SafeExamBrowser-3.7.dmg',
          size: 11534336,
          modified: now - 3600000 * 1,
          risk: 'Review · executable/script'
        }
      ]
    }),
    getProcessJson: () => json(processes),
    scanProcessesJson: () => json(processes),
    scanStorageJson: () => json({ roots: ['Downloads', 'Desktop', 'Documents'], items: ['The.Matrix.1999.mkv · 1.4 GB', 'PulseOS-1.0.8.dmg · 148 MB'] }),
    scanStorageDetailsJson: () => json(storage),
    runDiagnosisJson: () => json(diagnosis),
    diagnoseComponent: () => json(diagnosis),
    fixComponent: () => json({ success: false, message: 'Preview only · no device action was run.' }),
    terminateProcess: () => json({ success: false, message: 'Preview only · no process was terminated.' }),
    cleanSelected: () => json({ cleaned: 0, freedBytes: 0, preview: true }),
    getConverterCapabilitiesJson: () => json({ ffmpeg: true, calibre: true, local: true }),
    chooseFile: () => '/Users/nikhilpandit/Downloads/Architecture-Specs.docx',
    chooseFiles: () => '/Users/nikhilpandit/Downloads/Architecture-Specs.docx',
    chooseDirectory: () => '~/PulseOS/Converted',
    convertFileAsync: () => 'demo-conversion',
    convertImageAsync: () => 'demo-conversion',
    getConversionResult: () => json({ status: 'done', output: 'Architecture-Specs.pdf', preview: true }),
    askAiAsync: () => 'demo-ai',
    getAiResult: () => json({ status: 'done', answer: 'Local AI: All 8 performance cores operating normally at 82°C. Dynamic frequency scaling active.' }),
    getSetting: (key, fallback) => settings.has(key) ? settings.get(key) : fallback,
    setSetting: (key, value) => settings.set(key, value),
    minimizeWindow() {},
    closeWindow() {}
  };

  document.addEventListener('DOMContentLoaded', () => {
    const badge = document.createElement('div');
    badge.className = 'website-demo-badge';
    badge.textContent = 'WEBSITE PREVIEW · SAMPLE DATA · NO DEVICE ACTIONS';
    document.body.appendChild(badge);
  });
})();
