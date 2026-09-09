(function(){
  const ua=navigator.userAgent.toLowerCase();
  let platform='your device';
  let cardId='';
  if(ua.includes('windows')){platform='Windows';cardId='windows';}
  else if(ua.includes('android'))platform='Android';
  else if(ua.includes('iphone')||ua.includes('ipad'))platform='iOS';
  else if(ua.includes('mac')){platform='macOS';cardId='macos';}
  else if(ua.includes('linux')){platform='Linux';cardId='linux';}

  document.getElementById('platformText').textContent='Detected platform: '+platform;
  document.getElementById('downloadIntro').textContent='Detected '+platform+'. The recommended installer is highlighted below. Downloaded desktop installers include Java and JavaFX, so no developer setup is required.';
  if(cardId){
    const card=document.getElementById(cardId);
    card.classList.add('recommended');
    card.insertAdjacentHTML('afterbegin','<span class="recommended-label">Recommended for your device</span>');
  }
})();

document.querySelectorAll('.dash-click').forEach((el)=>{
  el.addEventListener('click',()=>el.classList.toggle('is-selected'));
});
