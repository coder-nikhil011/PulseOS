(() => {
  const processes = [
    { name: 'PulseOS Demo Browser', pid: 4012, cpu: 14.2, ramMb: 2460, type: 'App' },
    { name: 'PulseOS Demo Editor', pid: 2988, cpu: 8.1, ramMb: 1820, type: 'Software' },
    { name: 'PulseOS Demo Sync', pid: 1764, cpu: 3.4, ramMb: 430, type: 'Service' },
    { name: 'PulseOS Demo System', pid: 1024, cpu: 1.2, ramMb: 290, type: 'Service' }
  ];
  const now = Date.now();
  const recent = [
    { time: now, message: 'Telemetry sample refreshed', detail: 'Website demo data' },
    { time: now - 60000, message: 'Hardware status checked', detail: 'All sample components healthy' },
    { time: now - 180000, message: 'Storage scan complete', detail: '3 sample files indexed' }
  ];
  const files = [
    { path: 'C:/Users/Demo/Downloads/old-build.zip', size: 2860000000, usedBeforeDays: 38 },
    { path: 'C:/Users/Demo/AppData/Local/Temp/render.tmp', size: 671000000, usedBeforeDays: 5 },
    { path: 'C:/Users/Demo/Projects/PulseOS/logs/session.log', size: 48000000, usedBeforeDays: 2 }
  ];
  const storage = {
    all: files,
    large: [files[0]],
    logs: [files[2]],
    cache: [files[1]],
    temp: [files[1]],
    largeBytes: files[0].size,
    logBytes: files[2].size,
    cacheBytes: files[1].size,
    tempBytes: files[1].size,
    scannedFiles: 3,
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
      cpu: 38.2,
      ramUsed: 9.9,
      ramTotal: 16,
      ramPercent: 62,
      temperature: 54,
      battery: 91,
      charging: true,
      batteryHealth: 96,
      clock: 3.1,
      threads: 248,
      processes: 126,
      storageUsed: 68,
      storageFreeGb: 156,
      storageTotalGb: 512,
      healthScore: 91,
      uptimeSeconds: 189000,
      processor: 'PulseOS Demo Processor',
      deviceManufacturer: 'PulseOS',
      deviceModel: 'Demo Workstation',
      deviceImage: '../devices/generic-laptop.svg',
      cores: 8,
      os: 'Windows 11',
      architecture: 'x64',
      networkReady: true,
      networkReachable: true,
      dnsAvailable: true,
      latency: 18,
      networkRxMbps: 24.3,
      networkTxMbps: 4.8,
      topCpuName: 'Demo Browser',
      topCpuPercent: 14.2,
      topRamName: 'Demo Editor',
      topRamMb: 1820
    }),
    getNetworkDetailsJson: () => json({
      network: 'Demo Wi-Fi', ip: '192.168.1.42', gateway: '192.168.1.1', dns: 'Available'
    }),
    getRecentActivityJson: () => json(recent),
    getSystemWatcherJson: () => json({
      watchPath: 'C:/Users/Demo/Downloads',
      files: [{
        path: 'C:/Users/Demo/Downloads/sample-report.pdf',
        size: 2840000,
        modified: now - 240000,
        risk: 'No obvious risk · sample'
      }]
    }),
    getProcessJson: () => json(processes),
    scanProcessesJson: () => json(processes),
    scanStorageJson: () => json({ roots: ['Downloads', 'Desktop', 'Documents'], items: ['old-build.zip · 2724 MB'] }),
    scanStorageDetailsJson: () => json(storage),
    runDiagnosisJson: () => json(diagnosis),
    diagnoseComponent: () => json(diagnosis),
    fixComponent: () => json({ success: false, message: 'Preview only · no device action was run.' }),
    terminateProcess: () => json({ success: false, message: 'Preview only · no process was terminated.' }),
    cleanSelected: () => json({ cleaned: 0, freedBytes: 0, preview: true }),
    getConverterCapabilitiesJson: () => json({ ffmpeg: true, calibre: true, local: true }),
    chooseFile: () => 'C:/Demo/sample-photo.jpg',
    chooseFiles: () => 'C:/Demo/sample-photo.jpg',
    chooseDirectory: () => 'C:/Demo/Converted',
    convertFileAsync: () => 'demo-conversion',
    convertImageAsync: () => 'demo-conversion',
    getConversionResult: () => json({ status: 'done', output: 'sample-photo_converted.png', preview: true }),
    askAiAsync: () => 'demo-ai',
    getAiResult: () => json({ status: 'done', answer: 'Website preview: this is sample text, not a live device analysis.' }),
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
