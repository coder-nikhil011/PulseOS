/**
 * PulseOS Autonomous Interactive Desktop Tour
 * Calm, gradual, and easy-to-follow feature demonstration.
 * Features realistic slow cursor gliding, gentle clicks with ripples,
 * generous pauses to read explanations, and zero modal obscuration.
 */
(() => {
  'use strict';

  const PAGES = ['overview', 'hardware', 'router', 'storage', 'converter', 'settings'];
  let currentPageIndex = 0;
  let currentStepIndex = 0;
  let isTourPaused = false;
  let tourTimer = null;
  let cursorEl = null;
  let spotlightEl = null;
  let titleEl = null;
  let descEl = null;
  let rippleEl = null;

  // Prevent dialogs from obscuring the dashboard cards during the tour
  function forceCloseModals() {
    document.body.classList.add('tour-active');
    const overlay = document.getElementById('detailOverlay');
    if (overlay) {
      overlay.classList.remove('show');
      overlay.style.setProperty('display', 'none', 'important');
      overlay.style.setProperty('opacity', '0', 'important');
      overlay.style.setProperty('pointer-events', 'none', 'important');
    }
  }

  // Initialize Virtual Cursor DOM
  function initCursor() {
    if (document.getElementById('virtualCursor')) {
      cursorEl = document.getElementById('virtualCursor');
      spotlightEl = document.getElementById('cursorSpotlight');
      titleEl = document.getElementById('spotlightTitle');
      descEl = document.getElementById('spotlightDesc');
      rippleEl = document.getElementById('cursorRipple');
      return;
    }

    const wrap = document.createElement('div');
    wrap.id = 'virtualCursor';
    wrap.className = 'virtual-cursor';
    wrap.innerHTML = `
      <div class="cursor-pointer-wrap" id="cursorPointerWrap">
        <svg class="mouse-pointer-svg" viewBox="0 0 26 26" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 2L21 14.5L13 16.2L17.2 24.2L13.2 25.8L9 17.8L4 23V2Z" fill="#00E5FF" stroke="#080c14" stroke-width="2" stroke-linejoin="round"/>
        </svg>
        <div class="cursor-ripple" id="cursorRipple"></div>
      </div>
      <div class="cursor-spotlight-pill" id="cursorSpotlight">
        <span class="spotlight-tag">FEATURE SPOTLIGHT</span>
        <strong class="spotlight-title" id="spotlightTitle">PulseOS Feature</strong>
        <p class="spotlight-desc" id="spotlightDesc">Feature description</p>
      </div>
    `;
    document.body.appendChild(wrap);

    cursorEl = wrap;
    spotlightEl = document.getElementById('cursorSpotlight');
    titleEl = document.getElementById('spotlightTitle');
    descEl = document.getElementById('spotlightDesc');
    rippleEl = document.getElementById('cursorRipple');

    cursorEl.style.left = '260px';
    cursorEl.style.top = '100px';
  }

  // Smooth, slow cursor glide (1.4s transition + 600ms hover pause)
  function moveCursorTo(element, callback, shouldScroll = false) {
    if (!cursorEl) initCursor();
    forceCloseModals();

    if (!element) {
      if (callback) setTimeout(callback, 500);
      return;
    }

    if (shouldScroll) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else if (PAGES[currentPageIndex] === 'overview' && !shouldScroll) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      document.documentElement.scrollTop = 0;
    }

    // Allow a tiny moment for smooth scroll to engage before calculating position
    setTimeout(() => {
      const rect = element.getBoundingClientRect();
      const targetX = Math.round(rect.left + Math.min(rect.width * 0.4, 45));
      const targetY = Math.round(rect.top + Math.min(rect.height * 0.4, 25));

      cursorEl.style.left = `${Math.max(16, targetX)}px`;
      cursorEl.style.top = `${Math.max(16, targetY)}px`;

      // Position spotlight pill so it never clips off-screen
      if (spotlightEl) {
        if (targetX > window.innerWidth - 340) {
          spotlightEl.classList.add('flip-left');
        } else {
          spotlightEl.classList.remove('flip-left');
        }
        if (targetY > window.innerHeight - 170) {
          spotlightEl.classList.add('flip-top');
        } else {
          spotlightEl.classList.remove('flip-top');
        }
      }

      // Calm arrival pause: 1.4s travel time + 0.6s hover time
      setTimeout(() => {
        if (callback) callback();
      }, 1600);
    }, 80);
  }

  // Slow, deliberate click
  function triggerClick(element, callback, allowRealClick = false) {
    if (!cursorEl) return;
    const pointer = cursorEl.querySelector('#cursorPointerWrap');
    if (pointer) pointer.classList.add('clicking');
    if (rippleEl) {
      rippleEl.classList.remove('animate');
      void rippleEl.offsetWidth;
      rippleEl.classList.add('animate');
    }

    setTimeout(() => {
      if (element) {
        element.classList.add('demo-active-card');
        element.classList.add('demo-active-click');
        if (allowRealClick) {
          try { element.click(); } catch (_) {}
        }
        setTimeout(() => {
          element.classList.remove('demo-active-click');
          element.classList.remove('demo-active-card');
        }, 1200);
      }
      if (pointer) pointer.classList.remove('clicking');
      forceCloseModals();
      if (callback) callback();
    }, 450);
  }

  // Update spotlight contents
  function setSpotlight(title, desc) {
    if (!titleEl || !descEl) return;
    titleEl.textContent = title;
    descEl.textContent = desc;
    if (spotlightEl) {
      spotlightEl.classList.remove('visible');
      void spotlightEl.offsetWidth;
      spotlightEl.classList.add('visible');
    }

    try {
      window.parent.postMessage({
        type: 'PULSEOS_TOUR_UPDATE',
        page: PAGES[currentPageIndex],
        pageIndex: currentPageIndex,
        title: title,
        desc: desc
      }, '*');
    } catch (_) {}
  }

  // Calm, relaxed tour scripts (4.5 to 5.5 seconds per step)
  const TOUR_SCRIPTS = {
    overview: [
      {
        title: 'Device Health Score (96/100 · EXCELLENT)',
        desc: 'Comprehensive score evaluating CPU, RAM, battery, thermal and storage baselines.',
        getTarget: () => document.querySelector('#view-overview .health-score-panel') || document.querySelector('.score-visual'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Live Device Snapshot',
        desc: 'Detected profile: MacBook Pro with 16-Core Apple M4 Pro, 8 GB RAM, and macOS 27.2.',
        getTarget: () => document.querySelector('#view-overview .snapshot-panel') || document.getElementById('deviceIllustration'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Quick Health Status Checks',
        desc: 'Instant verification: CPU, memory, storage, battery and network all operating normally.',
        getTarget: () => document.querySelector('#view-overview .quick-status') || document.getElementById('cpuStatusRow'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Live Telemetry Waves',
        desc: 'Continuous sparkline telemetry for CPU Load (53%), Memory Pressure (80%), and Temp (82°C).',
        getTarget: () => document.querySelector('#view-overview .live-performance .range-buttons button:nth-child(2)') || document.querySelector('#view-overview .live-performance'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: 'Top Resource Drainers',
        desc: 'Identifies active background drainers: java (107.1% CPU) and MetricSanAgent (1465 MB RAM).',
        getTarget: () => document.querySelector('#view-overview .drainers'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'System Watcher & Safety Heuristics',
        desc: 'Monitors Downloads folder for risky files and scripts with heuristic safety scoring.',
        shouldScroll: true,
        getTarget: () => document.querySelector('#view-overview .system-watcher'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 5200);
          }, false);
        }
      },
      {
        title: 'Advancing to Hardware Health',
        desc: 'Switching to deep hardware sensor diagnostics and component status.',
        getTarget: () => document.querySelector('button[data-view="hardware"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('hardware');
          }, true);
        }
      }
    ],

    hardware: [
      {
        title: 'Subsystem Diagnostic Sweep',
        desc: 'Audits CPU, GPU, SSD, battery cells, fans and sensors across all 16 components.',
        getTarget: () => document.querySelector('#view-hardware .hardware-diagnosis-bar button'),
        action: (target, next) => {
          triggerClick(target, () => {
            const stat = document.getElementById('hardwareDiagnosisStatus');
            if (stat) {
              stat.textContent = '● Diagnostic sweep complete: 16 of 16 components PASS (100% efficiency)';
              stat.style.color = '#388e3c';
            }
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: 'Processor (CPU) Sensor Telemetry',
        desc: 'Monitors Apple M4 Pro (16 cores), 2.91 GHz clock, and thermal states.',
        getTarget: () => document.querySelector('#healthList .health-item:first-child'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Battery Longevity & Power Delivery',
        desc: '59% battery on charge, 95% health retention, and 0d 5h operational uptime.',
        getTarget: () => document.querySelector('#healthList .health-item:nth-child(5)') || document.querySelector('#healthList .health-item:nth-child(2)'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Advancing to Smart Router',
        desc: 'Switching to live process and network activity monitoring.',
        getTarget: () => document.querySelector('button[data-view="router"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('router');
          }, true);
        }
      }
    ],

    router: [
      {
        title: 'Network Health & Connection Details',
        desc: 'Wi-Fi 192.168.1.17: 10.8 Mbps Download, 10.0 Mbps Upload, 20 ms low latency.',
        getTarget: () => document.querySelector('#view-router .router-hero-row'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Active Process Resource Scanner',
        desc: 'Samples running background tasks to catch excessive CPU and battery drain.',
        getTarget: () => document.querySelector('#view-router .network-scan button[data-action="scan"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: 'Running Services & Filter Tabs',
        desc: 'Sorts running processes by CPU drain. Filter by Apps, Software, or Services.',
        getTarget: () => document.querySelector('#processFilterTabs button:nth-child(3)') || document.querySelector('#view-router .data-table'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: 'Advancing to Storage Healer',
        desc: 'Switching to AI-guarded disk cleanup and space reclamation.',
        getTarget: () => document.querySelector('button[data-view="storage"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('storage');
          }, true);
        }
      }
    ],

    storage: [
      {
        title: 'AI-Guarded Storage Scan',
        desc: 'Scans 460.4 GB disk: 186.0 GB used (40%), 274.4 GB free, 1,251 files indexed.',
        getTarget: () => document.querySelector('#view-storage .action-row button[data-action="scan"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: 'Storage Distribution & Categories',
        desc: 'Visual categorisation: Large Files (1.8 GB), Log Files (5 MB), Cache (152 KB).',
        getTarget: () => document.querySelector('#view-storage .category-panel'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, false);
        }
      },
      {
        title: 'Candidate Review & Verification',
        desc: 'Review verified safe cleaning candidates without touching personal user files.',
        getTarget: () => document.querySelector('#view-storage .file-table .file-row:first-child input[type="checkbox"]') || document.querySelector('#view-storage .file-table'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, true);
        }
      },
      {
        title: 'Advancing to Offline Converter',
        desc: 'Switching to private on-device document and media converter.',
        getTarget: () => document.querySelector('button[data-view="converter"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('converter');
          }, true);
        }
      }
    ],

    converter: [
      {
        title: 'Universal Drag & Drop Zone',
        desc: 'Supports documents, images, video and audio with batch offline processing.',
        getTarget: () => document.querySelector('#view-converter .converter-tabs button:nth-child(4)') || document.querySelector('#view-converter .drop-zone'),
        action: (target, next) => {
          triggerClick(target, () => {
            const drop = document.querySelector('#view-converter .drop-zone');
            if (drop) {
              const h2 = drop.querySelector('h2');
              if (h2) h2.textContent = 'Selected: Architecture-Specs.docx (2.4 MB)';
            }
            setTimeout(next, 5000);
          }, true);
        }
      },
      {
        title: '100% Offline & Private Processing',
        desc: 'Zero cloud upload. Your documents and files never leave your device.',
        getTarget: () => document.querySelector('#btnConvertNow') || document.querySelector('#view-converter .primary-button'),
        action: (target, next) => {
          triggerClick(target, () => {
            const drop = document.querySelector('#view-converter .drop-zone');
            if (drop) {
              const h2 = drop.querySelector('h2');
              if (h2) h2.innerHTML = '<span style="color:#2e7d32;font-weight:700">✓ Converted to PDF (Saved locally in 0.6s)</span>';
            }
            setTimeout(next, 5200);
          }, false);
        }
      },
      {
        title: 'Advancing to Settings',
        desc: 'Switching to theme, privacy and preference controls.',
        getTarget: () => document.querySelector('button[data-view="settings"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('settings');
          }, true);
        }
      }
    ],

    settings: [
      {
        title: 'Theme Customization (Dark / Light)',
        desc: 'Toggle between modern Dark and crisp Light interface themes instantly.',
        getTarget: () => document.querySelector('#view-settings .segmented button:first-child') || document.querySelector('#view-settings .segmented button'),
        action: (target, next) => {
          triggerClick(target, () => {
            document.body.classList.toggle('theme-dark');
            setTimeout(next, 4800);
          }, true);
        }
      },
      {
        title: 'Local-First Privacy Controls',
        desc: 'Strict governance over device telemetry. Zero telemetry leaves your machine.',
        getTarget: () => document.querySelector('#view-settings .settings-card:nth-child(3) input[type="checkbox"]') || document.querySelector('#view-settings .settings-card input[type="checkbox"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            setTimeout(next, 4800);
          }, true);
        }
      },
      {
        title: 'Completing Tour · Returning to Overview',
        desc: 'Restarting continuous tour with Device Health Overview.',
        getTarget: () => document.querySelector('button[data-view="overview"]'),
        action: (target, next) => {
          triggerClick(target, () => {
            switchPage('overview');
          }, true);
        }
      }
    ]
  };

  // Run the next step in the tour
  function executeCurrentStep() {
    if (isTourPaused) return;

    forceCloseModals();

    const pageKey = PAGES[currentPageIndex];
    const steps = TOUR_SCRIPTS[pageKey];
    if (!steps || steps.length === 0) return;

    if (currentStepIndex >= steps.length) {
      currentStepIndex = 0;
    }

    const step = steps[currentStepIndex];
    setSpotlight(step.title, step.desc);

    const target = step.getTarget ? step.getTarget() : null;

    moveCursorTo(target, () => {
      if (isTourPaused) return;
      if (step.action) {
        step.action(target, () => {
          if (isTourPaused) return;
          currentStepIndex++;
          tourTimer = setTimeout(executeCurrentStep, 900);
        });
      } else {
        tourTimer = setTimeout(() => {
          if (isTourPaused) return;
          currentStepIndex++;
          executeCurrentStep();
        }, 4800);
      }
    }, Boolean(step.shouldScroll));
  }

  // Switch to a specific page
  function switchPage(pageName, manual = false) {
    if (tourTimer) clearTimeout(tourTimer);

    forceCloseModals();

    const idx = PAGES.indexOf(pageName);
    if (idx !== -1) {
      currentPageIndex = idx;
      currentStepIndex = 0;
    }

    const navBtn = document.querySelector(`button[data-view="${pageName}"]`);
    if (navBtn) {
      navBtn.click();
    } else {
      document.querySelectorAll('.view').forEach(v => {
        v.classList.toggle('active-view', v.dataset.page === pageName);
      });
      document.querySelectorAll('.nav-item').forEach(n => {
        n.classList.toggle('active', n.dataset.view === pageName);
      });
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
    document.documentElement.scrollTop = 0;

    const steps = TOUR_SCRIPTS[pageName];
    if (steps && steps[0]) {
      setSpotlight(steps[0].title, steps[0].desc);
    }

    tourTimer = setTimeout(executeCurrentStep, 1100);
  }

  // Handle messages from parent window
  window.addEventListener('message', (event) => {
    if (!event.data) return;
    if (event.data.type === 'GOTO_PAGE') {
      switchPage(event.data.page, true);
    } else if (event.data.type === 'TOGGLE_PAUSE') {
      isTourPaused = Boolean(event.data.paused);
      if (!isTourPaused) {
        executeCurrentStep();
      }
    }
  });

  window.addEventListener('hashchange', () => {
    const hash = location.hash.slice(1);
    if (PAGES.includes(hash)) {
      switchPage(hash, true);
    }
  });

  function startTour() {
    initCursor();
    forceCloseModals();
    const hash = location.hash.slice(1);
    if (PAGES.includes(hash)) {
      currentPageIndex = PAGES.indexOf(hash);
    }
    setTimeout(executeCurrentStep, 1400);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startTour);
  } else {
    startTour();
  }
})();
