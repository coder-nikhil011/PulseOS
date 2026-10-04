(function(){
  const ua=navigator.userAgent.toLowerCase();
  let platform='your device';
  let cardId='';
  if(ua.includes('windows')){platform='Windows';cardId='windows';}
  else if(ua.includes('android')){platform='Android';cardId='mobile-client';}
  else if(ua.includes('iphone')||ua.includes('ipad')){platform='iOS (no mobile app)';}
  else if(ua.includes('mac')){platform='macOS';cardId='macos';}
  else if(ua.includes('linux')){platform='Linux';cardId='linux';}

  document.getElementById('platformText').textContent='Detected platform: '+platform;
  const intro=document.getElementById('downloadIntro');
  if(cardId==='mobile-client'){
    intro.textContent='Detected Android. The Android-only mobile APK is highlighted below; desktop installers are available for Windows, macOS, and Linux.';
  }else if(platform.startsWith('iOS')){
    intro.textContent='PulseOS mobile is currently Android-only. Desktop installers are available for Windows, macOS, and Linux.';
  }else{
    intro.textContent='Detected '+platform+'. The recommended desktop installer is highlighted below. Downloaded desktop installers include Java and JavaFX, so no developer setup is required.';
  }
  if(cardId){
    const card=document.getElementById(cardId);
    card.classList.add('recommended');
    card.insertAdjacentHTML('afterbegin','<span class="recommended-label">Recommended for your device</span>');
  }
})();

document.querySelectorAll('.dash-click').forEach((el)=>{
  el.addEventListener('click',()=>el.classList.toggle('is-selected'));
});

const desktopContent=document.querySelector('.dash-content');
const desktopFrame=document.querySelector('.dash-window');
const desktopOverview=desktopContent?.innerHTML || '';
const desktopPages=['overview','hardware','router','storage','converter','settings'];
const phoneScreen=document.getElementById('phoneScreen');
const phoneFrame=document.getElementById('phoneDemo');
const mobilePages=['overview','hardware','router','storage','converter','settings'];
let desktopIndex=0;
let mobileIndex=0;

// Tour Configuration
const TOUR_INTERVAL = 5000; 
const tourSequence = [
  { dPage: 'overview', mPage: 'overview', dAction: null, text: 'Overview · health score, live metrics, trends and recent activity.' },
  { dPage: 'overview', mPage: 'overview', dAction: 'diagnosis', text: 'Running system diagnosis...' },
  { dPage: 'hardware', mPage: 'hardware', dAction: null, text: 'Hardware · detailed component health and sensor readings.' },
  { dPage: 'router', mPage: 'router', dAction: 'router-refresh', text: 'Router · monitoring resource-draining processes.' },
  { dPage: 'storage', mPage: 'storage', dAction: 'storage-scan', text: 'Storage · identifying safe cleanup candidates.' },
  { dPage: 'converter', mPage: 'converter', dAction: null, text: 'Converter · local-first file conversion workflows.' },
  { dPage: 'settings', mPage: 'settings', dAction: null, text: 'Settings · professional device health preferences.' },
];

let currentTourStep = 0;
let tourTimer = null;

function startTour() {
  if (tourTimer) clearTimeout(tourTimer);
  
  const step = tourSequence[currentTourStep % tourSequence.length];
  
  showDesktopPage(step.dPage, false);
  showPhonePage(step.mPage, false);
  
  const dCaption = document.getElementById('desktopTourCaption');
  if (dCaption) dCaption.textContent = step.text;
  const mCaption = document.getElementById('phoneTourCaption');
  if (mCaption) mCaption.textContent = step.text;

  if (step.dAction) {
    const btn = document.querySelector(`[data-demo-action="${step.dAction}"]`);
    if (btn) btn.click();
  }

  currentTourStep++;
  tourTimer = setTimeout(startTour, TOUR_INTERVAL);
}

function stopTour() {
  if (tourTimer) {
    clearTimeout(tourTimer);
    tourTimer = null;
  }
}

function showDesktopPage(page, manual=false){
  if(!desktopContent || !desktopPages.includes(page)) return;
  if (manual) stopTour(); 
  
  desktopIndex=desktopPages.indexOf(page);
  desktopContent.innerHTML=page==='overview' ? desktopOverview : desktopPanels[page];
  desktopContent.classList.remove('demo-page-enter');
  void desktopContent.offsetWidth;
  desktopContent.classList.add('demo-page-enter');
  document.querySelectorAll('[data-demo-page]').forEach((tab)=>{
    const active=tab.dataset.demoPage===page;
    tab.classList.toggle('active',active);
    tab.setAttribute('aria-pressed',String(active));
  });
  const position=document.querySelector('.demo-auto-status');
  if(position) position.textContent=`AUTO TOUR · ${desktopIndex+1} / 6`;
  const heading=desktopContent.querySelector('.dash-pagehead h3');
  if(heading && page==='overview') heading.textContent='Device Health & Healing Center';
}

function showPhonePage(page,manual=false){
  if(!phoneScreen || !mobilePages.includes(page)) return;
  if (manual) stopTour(); 
  
  mobileIndex=mobilePages.indexOf(page);
  phoneScreen.innerHTML=phonePanels[page];
  phoneScreen.classList.remove('phone-page-enter');
  void phoneScreen.offsetWidth;
  phoneScreen.classList.add('phone-page-enter');
  document.querySelectorAll('[data-phone-page]').forEach((tab)=>{
    const active=tab.dataset.phonePage===page;
    tab.classList.toggle('active',active);
    tab.setAttribute('aria-current',active ? 'page' : 'false');
  });
}

function notifyDemo(message){
  let toast=document.getElementById('previewToast');
  if(!toast){
    toast=document.createElement('div');
    toast.id='previewToast';
    toast.className='preview-toast';
    toast.setAttribute('role','status');
    document.body.appendChild(toast);
  }
  toast.textContent=message;
  toast.classList.add('visible');
  window.clearTimeout(toast.timer);
  toast.timer=window.setTimeout(()=>toast.classList.remove('visible'),2400);
}

document.addEventListener('click',(event)=>{
  const desktopTab=event.target.closest('[data-demo-page]');
  if(desktopTab){
    showDesktopPage(desktopTab.dataset.demoPage,true);
    return;
  }
  const phoneTab=event.target.closest('[data-phone-page]');
  if(phoneTab){
    showPhonePage(phoneTab.dataset.phonePage,true);
    return;
  }
  const detail=event.target.closest('[data-inspect],[data-phone-detail]');
  if(detail){
    notifyDemo(detail.dataset.inspect || detail.dataset.phoneDetail);
    return;
  }
  const action=event.target.closest('[data-demo-action]');
  if(action){
    const type=action.dataset.demoAction;
    if(type==='storage-clean'){
      const selected=[...desktopContent.querySelectorAll('.demo-file-list input:checked')].map((input)=>input.value);
      const message=document.getElementById('desktopMessage');
      if(message) message.textContent=selected.length ? `Preview only: ${selected.join(', ')} would be reviewed before cleanup.` : 'Select one or more sample rows to review.';
    }else if(type==='convert'){
      const file=document.getElementById('desktopFile');
      const message=document.getElementById('desktopMessage');
      const format=document.getElementById('desktopFormat')?.value || 'PDF';
      if(message) message.textContent=file?.files?.length ? `Preview complete: ${file.files[0].name} → ${format}. No file was changed.` : `Choose a file to preview conversion to ${format}.`;
    }else if(type==='router-refresh'){
      const message=document.getElementById('desktopMessage');
      if(message) message.textContent='Sample refreshed just now · connection stable · 126 processes.';
    }else if(type==='storage-scan'){
      const message=document.getElementById('desktopMessage');
      if(message) message.textContent='Sample scan complete · 2 review candidates · active work remains protected.';
    }else if(type==='refresh'){
      notifyDemo('Sample telemetry refreshed just now.');
    }else if(type==='diagnosis'){
      notifyDemo('Sample check complete · no critical issues in this demo.');
    }
    return;
  }
  const phoneAction=event.target.closest('[data-phone-action]');
  if(phoneAction){
    const message=document.getElementById('phoneMessage');
    const type=phoneAction.dataset.phoneAction;
    if(type==='convert'){
      const file=document.getElementById('phoneFile');
      const format=document.getElementById('phoneFormat')?.value || 'PDF';
      if(message) message.textContent=file?.files?.length ? `Preview complete: ${file.files[0].name} → ${format}. No upload.` : `Choose a file to preview ${format} conversion.`;
    }else if(type==='cleanup'){
      if(message) message.textContent='Preview only · 42 MB of app cache selected for review. Nothing was deleted.';
    }else if(type==='data'){
      if(message) message.textContent='In the Android app, this opens your phone’s Data usage settings.';
    }else if(type==='usage'){
      if(message) message.textContent='In the Android app, grant optional Usage Access to view app screen time.';
    }else if(type==='refresh'){
      if(message) message.textContent='Phone sample readings refreshed just now.';
    }
  }
});

document.addEventListener('change',(event)=>{
  if(event.target.id==='desktopFile'){
    const label=document.getElementById('desktopFileName');
    if(label) label.textContent=event.target.files?.[0]?.name || 'Choose a sample file';
  }
  if(event.target.id==='phoneFile'){
    const label=document.getElementById('phoneFileName');
    if(label) label.textContent=event.target.files?.[0]?.name || 'Choose a file';
  }
  if(event.target.matches('[data-demo-setting]')){
    const state=event.target.checked ? 'on' : 'off';
    const message=document.getElementById('desktopMessage');
    if(message) message.textContent=`Preview preference changed: ${event.target.dataset.demoSetting} is ${state}.`;
  }
});

desktopContent?.addEventListener('click',(event)=>{
  const timeFilter=event.target.closest('.time-filters button');
  if(timeFilter){
    desktopContent.querySelectorAll('.time-filters button').forEach((button)=>button.classList.toggle('active',button===timeFilter));
    notifyDemo(`Showing illustrative ${timeFilter.textContent} trend.`);
  }
});

phoneScreen?.addEventListener('click',(event)=>{
  const refresh=event.target.closest('[data-phone-action="refresh"]');
  if(refresh){
    const message=document.getElementById('phoneMessage');
    if(message) message.textContent='Phone sample readings refreshed just now.';
  }
});

const desktopPanels={
  hardware:`<div class="demo-page-enter"><div class="demo-pagehead"><div><span>COMPONENT HEALTH</span><h3>Hardware Health</h3><p>Illustrative device readings · captured just now</p></div><button class="demo-action" data-demo-action="diagnosis">Run sample check</button></div><div class="demo-component-grid"><button class="demo-component" data-inspect="Processor"><span>CPU · PROCESSOR</span><strong>38%</strong><small>8 cores · 3.1 GHz · Normal</small></button><button class="demo-component" data-inspect="Memory"><span>RAM · MEMORY</span><strong>62%</strong><small>9.9 / 16 GB · Stable</small></button><button class="demo-component" data-inspect="Battery"><span>BATTERY &amp; POWER</span><strong>91%</strong><small>Charging · 32 °C · Good</small></button><button class="demo-component" data-inspect="Thermals"><span>THERMALS</span><strong>54 °C</strong><small>Fans responding normally</small></button><button class="demo-component" data-inspect="Graphics"><span>GPU · GRAPHICS</span><strong>12%</strong><small>Integrated · Sensors limited</small></button><button class="demo-component" data-inspect="Storage health"><span>SSD · STORAGE</span><strong>28%</strong><small>Used · Capacity available</small></button><button class="demo-component" data-inspect="Network adapter"><span>NETWORK</span><strong>Online</strong><small>Latency 18 ms · Stable</small></button><button class="demo-component" data-inspect="Peripherals"><span>PERIPHERALS</span><strong>4 devices</strong><small>Display, audio, USB</small></button></div><p class="demo-footnote">Sample readings are for product preview only; real sensor access depends on your device.</p></div>`,
  router:`<div class="demo-page-enter"><div class="demo-pagehead"><div><span>PROCESS &amp; NETWORK</span><h3>Smart Router</h3><p>See what is using resources and review connection health.</p></div><button class="demo-action" data-demo-action="router-refresh">↻ Refresh sample</button></div><div class="demo-stat-strip"><div><small>CONNECTION</small><b>Online · Wi-Fi</b></div><div><small>LATENCY</small><b>18 ms</b></div><div><small>ACTIVE PROCESSES</small><b>126</b></div><div><small>TOP CPU</small><b>Browser · 14%</b></div></div><div class="demo-table-wrap"><table class="demo-table"><thead><tr><th>Application</th><th>CPU</th><th>Memory</th><th>Impact</th><th></th></tr></thead><tbody><tr><td>Browser</td><td>14%</td><td>2.4 GB</td><td><span class="demo-tag amber">Moderate</span></td><td><button data-inspect="Browser process">Details</button></td></tr><tr><td>Code Editor</td><td>8%</td><td>1.8 GB</td><td><span class="demo-tag green">Low</span></td><td><button data-inspect="Code Editor process">Details</button></td></tr><tr><td>Cloud Sync</td><td>3%</td><td>420 MB</td><td><span class="demo-tag green">Low</span></td><td><button data-inspect="Cloud Sync process">Details</button></td></tr></tbody></table></div><p class="demo-message" id="desktopMessage">Sample process list. No process controls are executed in this preview.</p></div>`,
  storage:`<div class="demo-page-enter"><div class="demo-pagehead"><div><span>SPACE &amp; CLEANUP</span><h3>Storage Healer</h3><p>Review candidates first; nothing is removed without confirmation.</p></div><button class="demo-action" data-demo-action="storage-scan">Scan sample</button></div><div class="demo-storage-meter"><div><strong>68%</strong><span>used · 156 GB free of 512 GB</span></div><div class="demo-meter"><i style="width:68%"></i></div></div><div class="demo-file-list"><label><input type="checkbox" value="Build cache"> <span><b>Build cache</b><small>Developer artifacts · 2.8 GB · Not recently used</small></span><em>Review</em></label><label><input type="checkbox" value="App temporary files"> <span><b>App temporary files</b><small>Temporary data · 640 MB · Safe candidate</small></span><em>Review</em></label><label><input type="checkbox" value="Downloads"> <span><b>Downloads</b><small>Personal files · 4.1 GB · User review required</small></span><em>Protected</em></label></div><div class="demo-action-row"><button class="demo-action" data-demo-action="storage-clean">Review selected</button><span class="demo-message" id="desktopMessage">Demo only · your files are never accessed.</span></div></div>`,
  converter:`<div class="demo-page-enter"><div class="demo-pagehead"><div><span>LOCAL FILE WORKFLOWS</span><h3>Offline Converter</h3><p>Choose a file and preview a conversion flow. This website demo never uploads it.</p></div><span class="demo-tag green">ON DEVICE</span></div><label class="demo-drop"><input id="desktopFile" type="file"><span class="demo-drop-icon">＋</span><b id="desktopFileName">Choose a sample file</b><small>Images · documents · archives · media</small></label><div class="demo-form-row"><label>Convert to<select id="desktopFormat"><option>PDF</option><option>PNG</option><option>JPG</option><option>ZIP</option><option>MP4</option></select></label><button class="demo-action" data-demo-action="convert">Preview conversion</button></div><p class="demo-message" id="desktopMessage">Select any local file to see a simulated conversion result.</p></div>`,
  settings:`<div class="demo-page-enter"><div class="demo-pagehead"><div><span>PREFERENCES</span><h3>Settings</h3><p>Sample controls show how device preferences are organized.</p></div><span class="demo-tag green">LOCAL-FIRST</span></div><div class="demo-setting-list"><label><span><b>Local processing</b><small>Keep device analysis on this computer</small></span><input type="checkbox" checked data-demo-setting="Local processing"></label><label><span><b>Health alerts</b><small>Notify when a supported issue is observed</small></span><input type="checkbox" checked data-demo-setting="Health alerts"></label><label><span><b>Protect active projects</b><small>Exclude work in progress from cleanup suggestions</small></span><input type="checkbox" checked data-demo-setting="Protect active projects"></label><label><span><b>Downloads organizer</b><small>Preview file organization rules</small></span><input type="checkbox" data-demo-setting="Downloads organizer"></label></div><p class="demo-message" id="desktopMessage">Settings are illustrative and only affect this preview.</p></div>`
};

const phonePanels={
  overview:`<div class="phone-page-enter"><div class="phone-greeting"><span>YOUR DEVICE</span><b>Looking healthy <i>●</i></b></div><div class="phone-health"><div class="phone-ring"><strong>87</strong><small>HEALTH</small></div><div><b>Good condition</b><small>Updated just now</small><button data-phone-page="hardware">View components →</button></div></div><div class="phone-metrics"><button data-phone-page="hardware"><small>PROCESSOR</small><b>38%</b><span>Normal</span></button><button data-phone-page="hardware"><small>MEMORY</small><b>62%</b><span>9.9 / 16 GB</span></button><button data-phone-page="hardware"><small>BATTERY</small><b>91%</b><span>Charging</span></button><button data-phone-page="storage"><small>STORAGE</small><b>68%</b><span>156 GB free</span></button></div><div class="phone-alert"><span>✓</span><div><b>No active warnings</b><small>Phone checks are based on available signals.</small></div></div></div>`,
  hardware:`<div class="phone-page-enter"><div class="phone-page-title"><small>PHONE COMPONENTS</small><b>Hardware</b></div><div class="phone-list"><button data-phone-detail="Battery · 91% · Charging · 32 °C"><span>▰</span><div><b>Battery</b><small>91% · Charging · 32 °C</small></div><em>›</em></button><button data-phone-detail="Display · 120 Hz · Multi-touch available"><span>▣</span><div><b>Display &amp; touch</b><small>120 Hz · Multi-touch available</small></div><em>›</em></button><button data-phone-detail="Processor · 8 cores · 38% sample usage"><span>⌁</span><div><b>Processor</b><small>8 cores · 38% sample usage</small></div><em>›</em></button><button data-phone-detail="Cameras · front, rear, flash"><span>◎</span><div><b>Camera</b><small>Front · rear · flash</small></div><em>›</em></button><button data-phone-detail="Audio · microphone and 2 outputs"><span>♫</span><div><b>Microphone &amp; speaker</b><small>Mic · earpiece · speaker</small></div><em>›</em></button><button data-phone-detail="Sensors · accelerometer, gyro, proximity"><span>✣</span><div><b>Sensors</b><small>Accelerometer · gyro · proximity</small></div><em>›</em></button><button data-phone-detail="GPS · NFC · Bluetooth · biometrics"><span>⌖</span><div><b>Connections &amp; security</b><small>GPS · NFC · Bluetooth · fingerprint</small></div><em>›</em></button></div></div>`,
  router:`<div class="phone-page-enter"><div class="phone-page-title"><small>CONNECTION &amp; APP ACTIVITY</small><b>Router</b></div><div class="phone-network"><span>●</span><div><b>Wi-Fi connected</b><small>Home network · Internet available</small></div><em>Stable</em></div><div class="phone-mini-stats"><div><small>LATENCY</small><b>18 ms</b></div><div><small>SCREEN TIME</small><b>3h 24m</b></div></div><div class="phone-app-usage"><div><b>App activity</b><button data-phone-action="usage">Details</button></div><p><span>Video</span><i><b style="width:76%"></b></i><em>1h 42m</em></p><p><span>Browser</span><i><b style="width:52%"></b></i><em>58m</em></p><p><span>Messages</span><i><b style="width:28%"></b></i><em>31m</em></p></div><p class="phone-note" id="phoneMessage">Screen-time sample · Usage Access is optional in the real app.</p></div>`,
  storage:`<div class="phone-page-enter"><div class="phone-page-title"><small>SPACE &amp; APP CACHE</small><b>Storage</b></div><div class="phone-storage"><div><strong>68%</strong><span>used</span></div><b>87 GB <small>of 128 GB</small></b><i><b style="width:68%"></b></i><small>41 GB available</small></div><div class="phone-list compact"><div><span>▦</span><div><b>PulseOS cache</b><small>Safe to review · 42 MB</small></div><em>42 MB</em></div><div><span>▣</span><div><b>Other apps</b><small>Open Android storage settings</small></div><em>›</em></div></div><button class="phone-primary" data-phone-action="cleanup">Review safe cache</button><p class="phone-note" id="phoneMessage">Only sample data is shown. Personal files are never touched.</p></div>`,
  converter:`<div class="phone-page-enter"><div class="phone-page-title"><small>ON-DEVICE WORKFLOW</small><b>Convert</b></div><label class="phone-pick"><input id="phoneFile" type="file"><span>＋</span><b id="phoneFileName">Choose a file</b><small>Selected files stay on this device</small></label><label class="phone-select">Convert to<select id="phoneFormat"><option>PDF</option><option>PNG</option><option>JPG</option><option>ZIP</option></select></label><button class="phone-primary" data-phone-action="convert">Preview conversion</button><p class="phone-note" id="phoneMessage">Sample workflow · no file is uploaded by this website.</p></div>`,
  settings:`<div class="phone-page-enter"><div class="phone-page-title"><small>PHONE PREFERENCES</small><b>Settings</b></div><div class="phone-settings"><label><span><b>Local-first</b><small>Keep analysis on device</small></span><input type="checkbox" checked></label><label><span><b>Health alerts</b><small>Supported device signals</small></span><input type="checkbox" checked></label></div><div class="phone-list compact"><button data-phone-action="data"><span>⇵</span><div><b>Data usage</b><small>Mobile and Wi-Fi usage</small></div><em>›</em></button><button data-phone-action="usage"><span>◷</span><div><b>App screen time</b><small>Usage Access setting</small></div><em>›</em></button><button data-phone-detail="Battery, display, sound, storage, location and permissions open your Android settings in the real app."><span>⚙</span><div><b>Phone settings</b><small>Battery · display · sound · more</small></div><em>›</em></button></div><p class="phone-note" id="phoneMessage">Interactive sample only · no Android settings are opened.</p></div>`
};

showDesktopPage('overview');
showPhonePage('overview');
startTour();

function formatDownloadBytes(bytes){
  if(bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

document.querySelectorAll('a.download-button[download]').forEach((link)=>{
  const status=document.createElement('small');
  status.className='download-status';
  status.setAttribute('aria-live','polite');
  link.insertAdjacentElement('afterend', status);
  link.addEventListener('click', async (event)=>{
    if(!window.ReadableStream) return;
    event.preventDefault();
    link.setAttribute('aria-busy','true');
    status.textContent='Preparing download…';
    try{
      const response=await fetch(link.href);
      if(!response.ok || !response.body) throw new Error('Download unavailable');
      const total=Number(response.headers.get('content-length')) || 0;
      const reader=response.body.getReader();
      const chunks=[];
      let received=0;
      while(true){
        const result=await reader.read();
        if(result.done) break;
        chunks.push(result.value);
        received+=result.value.length;
        status.textContent=total
          ? `Downloaded ${formatDownloadBytes(received)} / ${formatDownloadBytes(total)} · Remaining ${formatDownloadBytes(total-received)}`
          : `Downloaded ${formatDownloadBytes(received)}`;
      }
      const blob=new Blob(chunks, {type: response.headers.get('content-type') || 'application/octet-stream'});
      const url=URL.createObjectURL(blob);
      const trigger=document.createElement('a');
      trigger.href=url;
      trigger.download=link.href.split('/').pop() || 'PulseOS-download';
      trigger.click();
      URL.revokeObjectURL(url);
      status.textContent=total
        ? `Complete · ${formatDownloadBytes(received)}`
        : `Complete · ${formatDownloadBytes(received)}`;
    }catch(error){
      status.textContent='Download started by browser';
      window.location.href=link.href;
    }finally{
      link.removeAttribute('aria-busy');
    }
  });
});
