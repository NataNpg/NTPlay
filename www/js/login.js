/* Login do NTPlay (mesma conta da TV Android). A senha nunca é guardada nem enviada: dela sai uma chave de login
   (PBKDF2, igual à TV) e só essa chave vai ao servidor. O app só carrega depois que o servidor confirma a conta. */
(function () {
  const API = 'https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync';
  const LSK = 'ntAcc', DEV = 'ntDev';
  const SCRIPTS = ['config.js', 'js/native.js', 'js/futebol.js', 'js/app.js', 'js/presenca.js'];
  const ls = { get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }, set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }, del(k) { try { localStorage.removeItem(k); } catch (e) {} } };
  const b64u = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  async function authKey(user, pass) {
    const k = await crypto.subtle.importKey('raw', new TextEncoder().encode(pass), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-1', salt: new TextEncoder().encode('ntplay-auth:' + user), iterations: 40000 }, k, 256);
    return b64u(bits);
  }
  function devId() { let d = ls.get(DEV); if (!d) { d = [...crypto.getRandomValues(new Uint8Array(8))].map((x) => x.toString(16).padStart(2, '0')).join(''); ls.set(DEV, d); } return d; }
  function devName() { const p = navigator.userAgent; return (/Windows/i.test(p) ? 'PC Windows' : /Android/i.test(p) ? 'Celular Android' : /iPhone|iPad/i.test(p) ? 'iPhone/iPad' : /Mac/i.test(p) ? 'Mac' : 'Navegador') + (window.bobNative ? ' (app)' : ''); }
  async function check(user, ak) {
    try {
      const r = await fetch(API, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ a: 'get', u: user, k: ak, dev: devId(), dn: devName() }) });
      const j = await r.json().catch(() => ({}));
      if (r.ok && j.ok) return { ok: true, exp: j.exp || 0 };
      return { ok: false, net: false, msg: j.msg || 'Erro do servidor (' + r.status + ')' };
    } catch (e) { return { ok: false, net: true, msg: 'Sem conexão com o servidor. Verifique a internet.' }; }
  }
  let booted = false;
  function boot() {
    if (booted) return; booted = true;
    SCRIPTS.forEach((s) => { const e = document.createElement('script'); e.src = s; e.async = false; document.body.appendChild(e); });
  }
  function lock(msg) { ls.del(LSK); if (msg) ls.set('ntLockMsg', msg); location.reload(); }
  function warn(exp) {
    const old = document.getElementById('ntwarn'); if (old) old.remove();
    const d = Math.ceil((exp - Date.now()) / 86400000);
    if (!exp || d < 0 || d > 7) return;
    const e = document.createElement('div'); e.id = 'ntwarn';
    e.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:9998;background:#b45f06;color:#fff;font:600 12px system-ui,Segoe UI,sans-serif;text-align:center;padding:5px 30px 5px 10px';
    e.textContent = '⚠ Seu acesso ' + (d === 0 ? 'vence hoje' : 'vence em ' + d + ' dia' + (d === 1 ? '' : 's')) + '. Fale com quem forneceu o acesso para renovar.';
    const x = document.createElement('span'); x.textContent = '✕'; x.style.cssText = 'position:absolute;right:10px;cursor:pointer'; x.onclick = () => e.remove(); e.appendChild(x);
    document.body.appendChild(e);
  }
  function watch(acc) {
    warn(acc.exp);
    const run = async () => { const r = await check(acc.u, acc.k); if (r.ok) { acc.exp = r.exp; acc.t = Date.now(); ls.set(LSK, JSON.stringify(acc)); warn(acc.exp); } else if (!r.net) lock(r.msg); };
    setInterval(run, 5 * 60 * 1000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) run(); });
  }
  const css = '#ntlg{position:fixed;inset:0;z-index:99999;background:#080b1a;display:flex;align-items:center;justify-content:center;font-family:system-ui,Segoe UI,sans-serif;color:#e9ecff}#ntlg .c{width:min(380px,92vw);background:#121735;border:1px solid #2a3470;border-radius:16px;padding:28px}#ntlg img{width:64px;height:64px;display:block;margin:0 auto 10px}#ntlg h1{margin:0 0 4px;font-size:22px;text-align:center}#ntlg p{margin:0 0 16px;color:#9aa3d6;font-size:13px;text-align:center}#ntlg input{width:100%;box-sizing:border-box;margin:6px 0;padding:12px;border-radius:10px;border:1px solid #2a3470;background:#0b0f26;color:#fff;font-size:15px}#ntlg input:focus{outline:2px solid #3a6df0}#ntlg button{width:100%;margin-top:10px;padding:12px;border:0;border-radius:10px;background:#3a6df0;color:#fff;font-size:15px;font-weight:700;cursor:pointer}#ntlg button:disabled{opacity:.6}#ntlg .e{color:#ff8a8a;font-size:13px;min-height:18px;margin-top:8px;text-align:center}#ntlg .w{margin-top:14px;font-size:12px;color:#9aa3d6;text-align:center}#ntlg .w a{color:#3ce0e6}';
  function show(msg) {
    const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
    const d = document.createElement('div'); d.id = 'ntlg';
    d.innerHTML = '<form class="c" autocomplete="off"><img src="img/icon.png" alt=""><h1>NTPlay</h1><p>Entre com o usuário e a senha da sua conta.</p><input id="ntu" placeholder="Usuário" autocapitalize="off" spellcheck="false"><input id="ntp" type="password" placeholder="Senha"><button type="submit">Entrar</button><div class="e" id="nte"></div><div class="w">Não tem conta? Peça pelo <a href="https://wa.me/5542998040495" target="_blank" rel="noopener">WhatsApp (42) 99804-0495</a></div></form>';
    document.body.appendChild(d);
    const u = d.querySelector('#ntu'), p = d.querySelector('#ntp'), e = d.querySelector('#nte'), b = d.querySelector('button');
    if (msg) e.textContent = msg;
    window.addEventListener('keydown', (ev) => { if (d.isConnected && /^Arrow/.test(ev.key) && d.contains(ev.target)) ev.stopImmediatePropagation(); }, true);
    setTimeout(() => u.focus(), 50);
    d.querySelector('form').onsubmit = async (ev) => {
      ev.preventDefault();
      const user = u.value.trim().toLowerCase(), pass = p.value;
      if (!user || !pass) { e.textContent = 'Digite usuário e senha.'; return; }
      b.disabled = true; e.textContent = 'Entrando…';
      let k; try { k = await authKey(user, pass); } catch (x) { e.textContent = 'Este navegador não suporta o login seguro.'; b.disabled = false; return; }
      const r = await check(user, k);
      if (!r.ok) { e.textContent = r.msg; b.disabled = false; return; }
      const acc = { u: user, k, exp: r.exp, t: Date.now() };
      ls.set(LSK, JSON.stringify(acc)); d.remove(); st.remove(); boot(); watch(acc);
    };
  }
  async function start() {
    const lm = ls.get('ntLockMsg'); ls.del('ntLockMsg');
    let acc = null; try { acc = JSON.parse(ls.get(LSK) || 'null'); } catch (e) {}
    if (acc && acc.u && acc.k) {
      const r = await check(acc.u, acc.k);
      if (r.ok) { acc.exp = r.exp; acc.t = Date.now(); ls.set(LSK, JSON.stringify(acc)); boot(); watch(acc); return; }
      if (r.net && acc.exp > Date.now()) { boot(); watch(acc); return; } // sem internet: vale a última validade confirmada
      if (r.net) return show('Sem conexão e a última validade confirmada já passou. Conecte-se à internet.');
      ls.del(LSK); return show(r.msg);
    }
    show(lm);
  }
  window.ntLogout = () => lock('');
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
