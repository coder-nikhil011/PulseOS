/**
 * PulseOS Website Interactive Tour & Controller
 * Features autonomous desktop and mobile guided tours with virtual pointers,
 * feature spotlights, and interactive user controls.
 */
(() => {
  'use strict';

  // 1. Platform Detection
  const ua = navigator.userAgent.toLowerCase();
  let platform = 'your device';
  let cardId = '';
  if (ua.includes('windows')) { platform = 'Windows'; cardId = 'windows'; }
  else if (ua.includes('android')) { platform = 'Android'; cardId = 'mobile-client'; }
  else if (ua.includes('iphone') || ua.includes('ipad')) { platform = 'iOS'; }
  else if (ua.includes('mac')) { platform = 'macOS'; cardId = 'macos'; }
  else if (ua.includes('linux')) { platform = 'Linux'; cardId = 'linux'; }

  const platformEl = document.getElementById('platformText');
  if (platformEl) platformEl.textContent = 'Detected platform: ' + platform;

  const introEl = document.getElementById('downloadIntro');
  if (introEl) {
    if (cardId === 'mobile-client') {
      introEl.textContent = 'Detected Android. The Android mobile APK is highlighted below; desktop installers are available for Windows, macOS, and Linux.';
    } else if (platform === 'iOS') {
      introEl.textContent = 'PulseOS mobile is currently Android-only. Desktop installers are available for Windows, macOS, and Linux.';
    } else {
      introEl.textContent = `Detected ${platform}. The recommended desktop package is highlighted below. Bundled with Java runtime, no developer tools required.`;
    }
  }

  if (cardId) {
    const card = document.getElementById(cardId);
    if (card) {
      card.classList.add('recommended');
      card.insertAdjacentHTML('afterbegin', '<span class="recommended-label">★ Recommended for your device</span>');
    }
  }

  // =========================================================
  // 2. Desktop Showcase Controller
  // =========================================================
  const desktopPages = ['overview', 'hardware', 'router', 'storage', 'converter', 'settings'];
  let currentDesktopIndex = 0;
  let isDesktopTourPaused = false;
  const desktopIframe = document.getElementById('desktopIframe');
  const desktopTabs = document.querySelectorAll('#desktopPageTabs .page-pill');
  const desktopCaption = document.getElementById('desktopTourCaption');
  const desktopIndicator = document.getElementById('desktopPageIndicator');
  const btnPrevPage = document.getElementById('btnPrevPage');
  const btnNextPage = document.getElementById('btnNextPage');
  const btnToggleTour = document.getElementById('btnToggleTour');
  const tourToggleIcon = document.getElementById('tourToggleIcon');
  const tourToggleText = document.getElementById('tourToggleText');

  function updateDesktopPageUI(pageName) {
    const idx = desktopPages.indexOf(pageName);
    if (idx !== -1) currentDesktopIndex = idx;

    desktopTabs.forEach(tab => {
      tab.classList.toggle('active', tab.dataset.page === pageName);
    });

    if (desktopIndicator) {
      desktopIndicator.textContent = `SCREEN ${currentDesktopIndex + 1} OF 6: ${pageName.toUpperCase()}`;
    }
  }

  function commandDesktopPage(pageName) {
    updateDesktopPageUI(pageName);
    if (desktopIframe && desktopIframe.contentWindow) {
      try {
        desktopIframe.contentWindow.postMessage({ type: 'GOTO_PAGE', page: pageName }, '*');
      } catch (_) {}
    }
  }

  // Handle click on desktop page tabs
  desktopTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      commandDesktopPage(tab.dataset.page);
    });
  });

  // Prev / Next Page controls
  if (btnPrevPage) {
    btnPrevPage.addEventListener('click', () => {
      currentDesktopIndex = (currentDesktopIndex - 1 + desktopPages.length) % desktopPages.length;
      commandDesktopPage(desktopPages[currentDesktopIndex]);
    });
  }

  if (btnNextPage) {
    btnNextPage.addEventListener('click', () => {
      currentDesktopIndex = (currentDesktopIndex + 1) % desktopPages.length;
      commandDesktopPage(desktopPages[currentDesktopIndex]);
    });
  }

  // Pause / Resume Tour
  if (btnToggleTour) {
    btnToggleTour.addEventListener('click', () => {
      isDesktopTourPaused = !isDesktopTourPaused;
      if (tourToggleIcon) tourToggleIcon.textContent = isDesktopTourPaused ? '▶' : '⏸';
      if (tourToggleText) tourToggleText.textContent = isDesktopTourPaused ? 'Resume Tour' : 'Pause Tour';
      const modeIndicator = document.getElementById('tourModeIndicator');
      if (modeIndicator) modeIndicator.textContent = isDesktopTourPaused ? 'TOUR PAUSED' : 'VIRTUAL MOUSE ACTIVE';

      if (desktopIframe && desktopIframe.contentWindow) {
        desktopIframe.contentWindow.postMessage({ type: 'TOGGLE_PAUSE', paused: isDesktopTourPaused }, '*');
      }
    });
  }

  // Listen for progress messages from desktop tour inside iframe
  window.addEventListener('message', (event) => {
    if (!event.data || event.data.type !== 'PULSEOS_TOUR_UPDATE') return;
    const { page, title, desc } = event.data;
    if (page) updateDesktopPageUI(page);
    if (desktopCaption && title && desc) {
      desktopCaption.innerHTML = `<strong style="color:var(--accent)">${title}:</strong> ${desc}`;
    }
  });

  // =========================================================
  // 3. Mobile Autonomous Touch Tour Engine
  // =========================================================
  const mobilePages = ['overview', 'hardware', 'router', 'storage', 'converter', 'settings'];
  let currentMobileIndex = 0;
  let mobileTimer = null;
  let isMobileTourPaused = false;

  const phoneScreen = document.getElementById('phoneScreen');
  const mobileTabs = document.querySelectorAll('#mobilePageTabs .m-pill');
  const phoneNavBtns = document.querySelectorAll('#phoneBottomNav button');
  const phoneCaption = document.getElementById('phoneTourCaption');
  const touchPointer = document.getElementById('phoneTouchPointer');
  const mSpotlight = document.getElementById('phoneSpotlight');
  const mSpotlightTitle = document.getElementById('mSpotlightTitle');
  const mSpotlightDesc = document.getElementById('mSpotlightDesc');
  const btnMobilePrev = document.getElementById('btnMobilePrev');
  const btnMobileNext = document.getElementById('btnMobileNext');

  // Full HTML for all 6 mobile screens
  const MOBILE_SCREENS = {
    overview: `
      <div class="m-card">
        <div class="m-card-title">
          <span>PHONE HEALTH SCORE</span>
          <span style="color:#4ade80">● EXCELLENT</span>
        </div>
        <div class="m-score-circle" id="mHealthCircle">
          <strong>94</strong>
          <small>/100</small>
        </div>
        <button class="m-btn-primary" id="mBtnDiagnose" type="button">RUN DIAGNOSTIC CHECK</button>
      </div>

      <div class="m-grid-2">
        <div class="m-stat-box" id="mBatteryBox">
          <small>BATTERY</small>
          <b>91% Charging</b>
          <span style="font-size:8px;color:#4ade80">32°C · Good</span>
        </div>
        <div class="m-stat-box" id="mStorageBox">
          <small>STORAGE</small>
          <b>87 / 128 GB</b>
          <span style="font-size:8px;color:#94a3b8">41 GB Free</span>
        </div>
      </div>

      <div class="m-card">
        <div class="m-bar-wrap" id="mCpuBarWrap">
          <div class="m-bar-label">
            <span>Processor (8 Cores)</span>
            <b id="mCpuVal">38%</b>
          </div>
          <div class="m-bar-track"><div class="m-bar-fill" style="width:38%"></div></div>
        </div>
        <div class="m-bar-wrap" id="mRamBarWrap">
          <div class="m-bar-label">
            <span>Memory Pressure (16 GB)</span>
            <b>62%</b>
          </div>
          <div class="m-bar-track"><div class="m-bar-fill" style="width:62%;background:#38bdf8"></div></div>
        </div>
      </div>
    `,

    hardware: `
      <div class="m-card">
        <div class="m-card-title">
          <span>SUBSYSTEM AUDIT</span>
          <span id="mHardwareStatus" style="color:#4ade80">● ALL PASS</span>
        </div>
        <button class="m-btn-primary" id="mBtnAudit" type="button">RUN SENSOR DIAGNOSTICS →</button>
      </div>

      <div class="m-card" id="mTouchCard" style="display:flex;align-items:center;justify-content:space-between;padding:10px;">
        <div>
          <b style="font-size:11px;color:#fff;display:block">Multi-Touch &amp; Display</b>
          <small style="font-size:8px;color:#7d8b96">120 Hz AMOLED · Zero touch ghosting</small>
        </div>
        <span style="color:#4ade80;font-size:11px">● PASS</span>
      </div>

      <div class="m-card" id="mThermalCard" style="display:flex;align-items:center;justify-content:space-between;padding:10px;">
        <div>
          <b style="font-size:11px;color:#fff;display:block">Thermals &amp; Battery Cells</b>
          <small style="font-size:8px;color:#7d8b96">32°C Normal · 320 Cycles (96% Health)</small>
        </div>
        <span style="color:#4ade80;font-size:11px">● COOL</span>
      </div>

      <div class="m-card" style="display:flex;align-items:center;justify-content:space-between;padding:10px;">
        <div>
          <b style="font-size:11px;color:#fff;display:block">Cameras, Audio &amp; Sensors</b>
          <small style="font-size:8px;color:#7d8b96">Accelerometer, Gyro, Biometrics verified</small>
        </div>
        <span style="color:#4ade80;font-size:11px">● OK</span>
      </div>
    `,

    router: `
      <div class="m-card">
        <div class="m-card-title">
          <span>CONNECTION HEALTH</span>
          <span style="color:#4ade80">● STABLE</span>
        </div>
        <div style="display:flex;align-items:baseline;justify-content:space-between;margin:4px 0 10px;">
          <div>
            <b style="font-size:16px;color:#fff">14 ms</b>
            <small style="display:block;font-size:8px;color:#7d8b96">LATENCY (WI-FI 6)</small>
          </div>
          <div>
            <b style="font-size:16px;color:#38bdf8">0%</b>
            <small style="display:block;font-size:8px;color:#7d8b96">PACKET LOSS</small>
          </div>
        </div>
        <button class="m-btn-primary" id="mBtnOptimizeNet" type="button">OPTIMIZE CONNECTION</button>
      </div>

      <div class="m-card" id="mScreenTimeCard">
        <div class="m-card-title">
          <span>APP ACTIVITY &amp; SCREEN TIME</span>
          <small style="color:#94a3b8">3h 24m Today</small>
        </div>
        <div class="m-bar-wrap">
          <div class="m-bar-label"><span>Streaming Video</span><b>1h 42m</b></div>
          <div class="m-bar-track"><div class="m-bar-fill" style="width:76%;background:#f87171"></div></div>
        </div>
        <div class="m-bar-wrap">
          <div class="m-bar-label"><span>Browser</span><b>58m</b></div>
          <div class="m-bar-track"><div class="m-bar-fill" style="width:48%;background:#fbbf24"></div></div>
        </div>
        <div class="m-bar-wrap">
          <div class="m-bar-label"><span>Messaging</span><b>31m</b></div>
          <div class="m-bar-track"><div class="m-bar-fill" style="width:24%;background:#4ade80"></div></div>
        </div>
      </div>
    `,

    storage: `
      <div class="m-card">
        <div class="m-card-title">
          <span>DEVICE STORAGE</span>
          <span style="color:#00e5ff">68% USED</span>
        </div>
        <div style="font-size:22px;font-weight:800;color:#fff;margin:4px 0">
          87 GB <small style="font-size:11px;color:#7d8b96">of 128 GB</small>
        </div>
        <div class="m-bar-track" style="height:8px;margin:8px 0">
          <div class="m-bar-fill" style="width:68%;background:#00e5ff"></div>
        </div>
        <div style="display:flex;justify-content:space-between;font-size:9px;color:#94a3b8">
          <span>Apps: 48 GB</span>
          <span>Media: 32 GB</span>
          <span>Cache: 7 GB</span>
        </div>
      </div>

      <div class="m-card" id="mCacheCleanCard">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <div>
            <b style="font-size:11px;color:#fff;display:block">PulseOS Safe Cache</b>
            <small style="font-size:8px;color:#7d8b96">Safe developer &amp; app artifacts</small>
          </div>
          <span style="font-size:11px;font-weight:700;color:#00e5ff">42 MB</span>
        </div>
        <button class="m-btn-primary" id="mBtnCleanCache" type="button">REVIEW &amp; CLEAN SAFE CACHE</button>
      </div>
    `,

    converter: `
      <div class="m-card" id="mFilePickCard" style="border:1px dashed #00e5ff;text-align:center;padding:16px 10px;cursor:pointer">
        <span style="font-size:20px;color:#00e5ff;display:block">＋</span>
        <b id="mPickedFileName" style="font-size:11px;color:#fff;display:block;margin:4px 0">receipt-document.png</b>
        <small style="font-size:8px;color:#7d8b96">100% Local Processing · Zero Cloud Upload</small>
      </div>

      <div class="m-card">
        <div class="m-card-title">
          <span>CONVERT TO FORMAT</span>
          <span style="color:#00e5ff">PDF</span>
        </div>
        <button class="m-btn-primary" id="mBtnConvert" type="button">PREVIEW ON-DEVICE CONVERSION</button>
      </div>
    `,

    settings: `
      <div class="m-card">
        <div class="m-card-title">
          <span>SECURITY &amp; PRIVACY</span>
        </div>
        <div id="mLocalToggleRow" style="display:flex;align-items:center;justify-content:space-between;padding:6px 0;border-bottom:1px solid #1e2632">
          <div>
            <b style="font-size:11px;color:#fff;display:block">Local-First Processing</b>
            <small style="font-size:8px;color:#7d8b96">Keep telemetry 100% on device</small>
          </div>
          <input type="checkbox" id="mLocalFirstSwitch" checked style="accent-color:#00e5ff">
        </div>
        <div style="display:flex;align-items:center;justify-content:space-between;padding:6px 0;">
          <div>
            <b style="font-size:11px;color:#fff;display:block">Hardware Health Alerts</b>
            <small style="font-size:8px;color:#7d8b96">Notify on high thermal pressure</small>
          </div>
          <input type="checkbox" checked style="accent-color:#00e5ff">
        </div>
      </div>

      <div class="m-card">
        <b style="font-size:10.5px;color:#94a3b8;display:block;margin-bottom:6px">PULSEOS ANDROID CLIENT</b>
        <div style="display:flex;justify-content:space-between;font-size:10px;color:#cbd5e1">
          <span>Version</span><b>v2.0.4 (ARM64)</b>
        </div>
      </div>
    `
  };

  // Move Mobile Touch Pointer
  function moveTouchPointer(targetEl, callback) {
    if (!touchPointer || !targetEl || !phoneScreen) {
      if (callback) callback();
      return;
    }

    const phoneRect = phoneScreen.getBoundingClientRect();
    const targetRect = targetEl.getBoundingClientRect();

    const x = Math.round(targetRect.left - phoneRect.left + (targetRect.width / 2));
    const y = Math.round(targetRect.top - phoneRect.top + (targetRect.height / 2));

    touchPointer.style.left = `${Math.max(16, Math.min(x, phoneRect.width - 16))}px`;
    touchPointer.style.top = `${Math.max(16, Math.min(y, phoneRect.height - 16))}px`;

    setTimeout(() => {
      if (callback) callback();
    }, 1300);
  }

  // Simulate mobile tap animation
  function performTouchTap(targetEl, callback) {
    if (!touchPointer) return;
    touchPointer.classList.add('tapping');

    setTimeout(() => {
      if (targetEl) {
        try { targetEl.click(); } catch (_) {}
      }
      touchPointer.classList.remove('tapping');
      if (callback) callback();
    }, 320);
  }

  // Update mobile spotlight pill
  function setMobileSpotlight(title, desc) {
    if (mSpotlightTitle) mSpotlightTitle.textContent = title;
    if (mSpotlightDesc) mSpotlightDesc.textContent = desc;
    if (phoneCaption) phoneCaption.innerHTML = `<b style="color:var(--accent)">${title}:</b> ${desc}`;
    if (mSpotlight) {
      mSpotlight.classList.remove('visible');
      void mSpotlight.offsetWidth;
      mSpotlight.classList.add('visible');
    }
  }

  // Mobile Tour Step Scripts
  const MOBILE_TOUR_SCRIPTS = {
    overview: [
      {
        title: 'Device Health Score',
        desc: 'Android telemetry evaluates hardware, battery and storage health.',
        getTarget: () => document.getElementById('mBtnDiagnose') || document.getElementById('mHealthCircle'),
        action: (target, next) => {
          performTouchTap(target, () => {
            const circle = document.getElementById('mHealthCircle');
            if (circle) circle.style.borderColor = '#4ade80';
            setTimeout(next, 3800);
          });
        }
      },
      {
        title: 'Thermal & Battery Telemetry',
        desc: 'Live cell temperature and charging health readings directly from Android hardware.',
        getTarget: () => document.getElementById('mBatteryBox'),
        action: (target, next) => {
          performTouchTap(target, () => setTimeout(next, 3800));
        }
      },
      {
        title: 'Processor & Memory Load',
        desc: 'Monitors real-time 8-core CPU load and LPDDR memory pressure.',
        getTarget: () => document.getElementById('mCpuBarWrap'),
        action: (target, next) => {
          performTouchTap(target, () => setTimeout(next, 3800));
        }
      },
      {
        title: 'Advancing to Hardware Health',
        desc: 'Switching to Android component sensor diagnostics.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="hardware"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('hardware'));
        }
      }
    ],

    hardware: [
      {
        title: 'Sensor Diagnostics Sweep',
        desc: 'Checks multi-touch display, thermals, cameras, audio and biometrics.',
        getTarget: () => document.getElementById('mBtnAudit'),
        action: (target, next) => {
          performTouchTap(target, () => {
            const stat = document.getElementById('mHardwareStatus');
            if (stat) stat.textContent = '● AUDIT PASS';
            setTimeout(next, 4000);
          });
        }
      },
      {
        title: 'Multi-Touch & AMOLED Test',
        desc: 'Verifies 120Hz display refresh rate and multi-finger touch responsiveness.',
        getTarget: () => document.getElementById('mTouchCard'),
        action: (target, next) => {
          performTouchTap(target, () => setTimeout(next, 3800));
        }
      },
      {
        title: 'Advancing to Smart Router',
        desc: 'Switching to network latency and app screen time tracking.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="router"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('router'));
        }
      }
    ],

    router: [
      {
        title: 'Network Optimization',
        desc: 'Measures Wi-Fi ping latency and flushes Android network cache for smooth gaming.',
        getTarget: () => document.getElementById('mBtnOptimizeNet'),
        action: (target, next) => {
          performTouchTap(target, () => setTimeout(next, 4000));
        }
      },
      {
        title: 'App Screen Time Breakdown',
        desc: 'Tracks background screen time and battery consumption per app.',
        getTarget: () => document.getElementById('mScreenTimeCard'),
        action: (target, next) => {
          performTouchTap(target, () => setTimeout(next, 4000));
        }
      },
      {
        title: 'Advancing to Storage Healer',
        desc: 'Switching to Android cache analysis and space reclamation.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="storage"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('storage'));
        }
      }
    ],

    storage: [
      {
        title: 'Safe App Cache Cleanup',
        desc: 'Cleans safely identified temporary app cache without touching personal photos or files.',
        getTarget: () => document.getElementById('mBtnCleanCache'),
        action: (target, next) => {
          performTouchTap(target, () => {
            target.textContent = '✓ 42 MB RECLAIMED';
            target.style.background = '#4ade80';
            setTimeout(next, 4200);
          });
        }
      },
      {
        title: 'Advancing to Offline Converter',
        desc: 'Switching to on-device mobile converter studio.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="converter"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('converter'));
        }
      }
    ],

    converter: [
      {
        title: 'On-Device File Conversion',
        desc: 'Converts images and documents to PDF/PNG completely offline without uploading.',
        getTarget: () => document.getElementById('mBtnConvert'),
        action: (target, next) => {
          performTouchTap(target, () => {
            target.textContent = '✓ PDF Created (Offline)';
            target.style.background = '#4ade80';
            setTimeout(next, 4200);
          });
        }
      },
      {
        title: 'Advancing to Settings',
        desc: 'Switching to Android client privacy and health preferences.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="settings"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('settings'));
        }
      }
    ],

    settings: [
      {
        title: 'Local-First Privacy Governance',
        desc: 'Enforces on-device telemetry analysis with zero cloud transmission.',
        getTarget: () => document.getElementById('mLocalToggleRow'),
        action: (target, next) => {
          performTouchTap(target, () => {
            const sw = document.getElementById('mLocalFirstSwitch');
            if (sw) sw.checked = !sw.checked;
            setTimeout(next, 4000);
          });
        }
      },
      {
        title: 'Restarting Mobile Tour · Overview',
        desc: 'Returning to Mobile Overview screen.',
        getTarget: () => document.querySelector('#phoneBottomNav button[data-phone-page="overview"]'),
        action: (target, next) => {
          performTouchTap(target, () => switchMobileScreen('overview'));
        }
      }
    ]
  };

  let mobileStepIndex = 0;

  function runMobileTourStep() {
    if (isMobileTourPaused) return;

    const pageKey = mobilePages[currentMobileIndex];
    const steps = MOBILE_TOUR_SCRIPTS[pageKey];
    if (!steps || steps.length === 0) return;

    if (mobileStepIndex >= steps.length) {
      mobileStepIndex = 0;
    }

    const step = steps[mobileStepIndex];
    setMobileSpotlight(step.title, step.desc);

    const target = step.getTarget ? step.getTarget() : null;

    moveTouchPointer(target, () => {
      if (isMobileTourPaused) return;
      if (step.action) {
        step.action(target, () => {
          if (isMobileTourPaused) return;
          mobileStepIndex++;
          mobileTimer = setTimeout(runMobileTourStep, 350);
        });
      } else {
        mobileTimer = setTimeout(() => {
          if (isMobileTourPaused) return;
          mobileStepIndex++;
          runMobileTourStep();
        }, 2200);
      }
    });
  }

  function switchMobileScreen(pageName, manual = false) {
    if (mobileTimer) clearTimeout(mobileTimer);

    const idx = mobilePages.indexOf(pageName);
    if (idx !== -1) {
      currentMobileIndex = idx;
      mobileStepIndex = 0;
    }

    // Update screen content
    if (phoneScreen && MOBILE_SCREENS[pageName]) {
      phoneScreen.innerHTML = MOBILE_SCREENS[pageName];
      phoneScreen.scrollTop = 0;
    }

    // Update mobile pills
    mobileTabs.forEach(pill => {
      pill.classList.toggle('active', pill.dataset.phonePage === pageName);
    });

    // Update bottom nav
    phoneNavBtns.forEach(btn => {
      btn.classList.toggle('active', btn.dataset.phonePage === pageName);
    });

    // Reset touch pointer position to top center
    if (touchPointer) {
      touchPointer.style.left = '50%';
      touchPointer.style.top = '70px';
    }

    const steps = MOBILE_TOUR_SCRIPTS[pageName];
    if (steps && steps[0]) {
      setMobileSpotlight(steps[0].title, steps[0].desc);
    }

    mobileTimer = setTimeout(runMobileTourStep, 700);
  }

  // Handle click on mobile page pills
  mobileTabs.forEach(pill => {
    pill.addEventListener('click', () => {
      switchMobileScreen(pill.dataset.phonePage, true);
    });
  });

  // Handle click on phone bottom nav buttons
  phoneNavBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      switchMobileScreen(btn.dataset.phonePage, true);
    });
  });

  // Mobile Prev / Next
  if (btnMobilePrev) {
    btnMobilePrev.addEventListener('click', () => {
      currentMobileIndex = (currentMobileIndex - 1 + mobilePages.length) % mobilePages.length;
      switchMobileScreen(mobilePages[currentMobileIndex], true);
    });
  }

  if (btnMobileNext) {
    btnMobileNext.addEventListener('click', () => {
      currentMobileIndex = (currentMobileIndex + 1) % mobilePages.length;
      switchMobileScreen(mobilePages[currentMobileIndex], true);
    });
  }

  // Initialize both tours on load
  function initAll() {
    // Start mobile tour
    switchMobileScreen('overview');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAll);
  } else {
    initAll();
  }
})();
