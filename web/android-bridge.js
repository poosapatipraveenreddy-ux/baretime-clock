/* BareTime Android bridge.
 * Does nothing in a normal browser. Inside the Android app it adds the
 * "Apply to widget" buttons and makes share links point at the real website.
 * MIN_BRIDGE: the oldest app "bridge level" this site version works with.
 * Raise it only when this file starts using a new app feature. */
const MIN_BRIDGE = 2;
(() => {
  const A = window.BareTimeAndroid;
  if (!A) return;
  try { window.BARETIME_BASE = A.siteUrl() || null; } catch (e) { /* ignore */ }

  const css = document.createElement('style');
  css.textContent = `
    .wbar{display:flex;gap:8px;padding:10px 16px 12px;border-bottom:1px solid var(--line)}
    .wbar button{flex:1;height:40px;border-radius:10px;border:1px solid var(--line);background:var(--field);
      font-weight:600;cursor:pointer}
    .wbar button.main{background:linear-gradient(135deg,var(--accent),var(--accent-2));border-color:transparent}
    #btnFs,#btnInstall,#appInstall{display:none!important}`;
  document.head.append(css);

  function say(msg) {
    const t = document.getElementById('toast'); if (!t) return;
    t.replaceChildren(Object.assign(document.createElement('span'), { textContent: msg }));
    t.classList.add('show');
    clearTimeout(say.t); say.t = setTimeout(() => t.classList.remove('show'), 4200);
  }

  function apply() {
    let json = null;
    try { json = localStorage.getItem('baretime.settings.v1'); } catch (e) { /* ignore */ }
    if (!json) json = '{}';
    const n = A.applyToWidget(json);
    if (n < 0) return say('Could not save the design.');
    if (n === 0) {
      say('Saved. Now add the BareTime widget to your home screen.');
      try { A.pinWidget(); } catch (e) { /* ignore */ }
    } else {
      say(n === 1 ? 'Widget updated.' : n + ' widgets updated.');
    }
  }

  function mount() {
    const panel = document.getElementById('panel'), nav = document.getElementById('tabnav');
    if (!panel || !nav || document.querySelector('.wbar')) return;
    const bar = document.createElement('div'); bar.className = 'wbar';
    const a = document.createElement('button'); a.type = 'button'; a.className = 'main'; a.textContent = 'Apply to widget';
    const b = document.createElement('button'); b.type = 'button'; b.textContent = 'Add widget';
    a.onclick = apply;
    b.onclick = () => { try { if (!A.pinWidget()) say('Long-press your home screen, then Widgets → BareTime.'); } catch (e) { /* ignore */ } };
    bar.append(a, b);
    if (typeof A.openScreenSaverSettings === 'function') {
      const c = document.createElement('button'); c.type = 'button'; c.textContent = 'Screen saver';
      c.onclick = () => { try { A.openScreenSaverSettings(); say('Choose "BareTime clock", then tap Start now.'); } catch (e) { /* ignore */ } };
      bar.append(c);
    }
    panel.insertBefore(bar, nav);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount();
})();
