// Painel do administrador do NTPlay (antes ficava dentro do admin.html; separado para a página poder usar CSP sem 'unsafe-inline').
/* ================= base ================= */
const API = 'https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync';
const $ = (s, r = document) => r.querySelector(s), $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const DAY = 864e5;
const LS = (() => { try { return window.localStorage } catch (e) { return {} } })();
// a chave de administrador fica só enquanto esta aba estiver aberta (fechou a aba, pede de novo)
const SS = (() => { try { return window.sessionStorage } catch (e) { return {} } })();
try { delete LS.ntAdmKey } catch (e) { } // apaga a cópia antiga que ficava guardada para sempre
let ADM = '', USERS = [], FILTER = 'all';

function toast(m, er) { const t = $('#toast'); t.textContent = m; t.className = er ? 'er' : ''; clearTimeout(toast.t); toast.t = setTimeout(() => t.className = 'hide', 3200) }
const fmtD = t => new Date(t).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
const fmtDT = t => new Date(t).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
function ago(t) { if (!t) return 'nunca'; const s = (Date.now() - new Date(t)) / 1000; if (s < 90) return 'agora'; if (s < 3600) return Math.round(s / 60) + ' min atrás'; if (s < 86400) return Math.round(s / 3600) + ' h atrás'; return Math.round(s / 86400) + ' dias atrás' }
function daysLeft(u) { return u.expires_at ? (new Date(u.expires_at) - Date.now()) / DAY : -1 }
function status(u) { const d = daysLeft(u); if (d <= 0) return ['Vencida', 'r', 'exp']; if (d <= 7) return ['Vence em ' + Math.ceil(d) + ' d', 'y', 'soon']; if (d > 3650) return ['Vitalícia', 'g', 'ok']; return ['Ativa · ' + Math.floor(d) + ' d', 'g', 'ok'] }

async function call(body) {
  let r; try { r = await fetch(API, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) }) } catch (e) { throw new Error('Sem conexão com o servidor.') }
  let j = {}; try { j = await r.json() } catch (e) { }
  if (!r.ok || !j.ok) throw new Error(j.msg || ('Erro do servidor (' + r.status + ')'));
  return j;
}
// Modo local (servidores.html, <body data-local="1">): sem login e sem servidor. Os dados ficam só neste navegador
// (localStorage). Nada é enviado para o Supabase; o site não fica ligado ao painel principal.
const LOCAL = document.body.dataset.local == '1', LKEY = 'ntSrvLocal';
const cstore = () => { try { return SS[CLKEY] ? SS : LS } catch (e) { return LS } };
function localGet() { try { const o = JSON.parse(cstore()[LKEY] || 'null'); if (o && Array.isArray(o.groups)) return o } catch (e) { } return { groups: [], ts: 0 } }
function localPut(groups, ts) { try { cstore()[LKEY] = JSON.stringify({ groups, ts }) } catch (e) { throw new Error('Não deu para salvar neste navegador (memória cheia ou modo anônimo).') } }
/* Nuvem com senha (opcional): os dados vão trancados (AES-GCM com a senha) para uma conta NTPlay própria
   (ex.: "servidores"), criada no painel. O servidor só guarda o bloco trancado. A senha não fica salva:
   o aparelho guarda só as chaves derivadas dela (CLKEY), para não pedir de novo. */
const CLKEY = 'ntSrvCloud';
const cloud = () => { try { return JSON.parse(SS[CLKEY] || LS[CLKEY] || 'null') } catch (e) { return null } };
const devId = () => { let d = LS.ntSrvDev; if (!d) { d = gid() + gid(); try { LS.ntSrvDev = d } catch (e) { } } return d };
/* Formato v2 dos dados na nuvem (com código de recuperação):
   "v2." + JSON { wp, wr, d }
     d  = dados trancados com uma chave aleatória (DK)
     wp = DK trancada com a senha            → abre no dia a dia
     wr = DK trancada com o código de recuperação → abre se perder a senha
   Formato antigo (sem "v2."): dados trancados direto com a senha; é convertido sozinho na primeira abertura.
   O aparelho guarda auth + enc (da senha) + dk; nunca a senha nem o código. */
const cKeys = c => ({ auth: c.auth, enc: unb64(c.enc) });
const recKey = (u, code) => pbkdf(String(code).toUpperCase().replace(/[^A-Z0-9]/g, ''), 'ntplay-rec:' + normU(u));
function newRecCode() { const A = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', r = crypto.getRandomValues(new Uint8Array(20)); return [...r].map(x => A[x % A.length]).join('').match(/.{4}/g).join('-') }
const parseEnv = s => { if (typeof s != 'string' || !s.startsWith('v2.')) return null; try { return JSON.parse(s.slice(3)) } catch (e) { return null } };
const packEnv = e => 'v2.' + JSON.stringify(e);
async function unwrapDK(w, key) { const o = await decBlob(w, key); return o && o.dk ? unb64(o.dk) : null }
// lê a nuvem; devolve { groups, ts, env (v2) | old (formato antigo), dk }
async function cloudGet(c) {
  const j = await call({ a: 'get', u: c.u, k: c.auth, dev: devId(), dn: 'Site servidores' });
  if (!j.data) return { groups: null, ts: 0 };
  const env = parseEnv(j.data), ts = Number(j.ts) || 0;
  if (env) {
    const dk = c.dk ? unb64(c.dk) : await unwrapDK(env.wp, cKeys(c).enc);
    let d = dk && await decBlob(env.d, dk);
    if (!d && c.dk) { const dk2 = await unwrapDK(env.wp, cKeys(c).enc); d = dk2 && await decBlob(env.d, dk2); if (d) return { groups: d.srvGroups, ts, env, dk: dk2 } }
    if (!d || !Array.isArray(d.srvGroups)) throw new Error('Os dados da nuvem não abriram com esta senha.');
    return { groups: d.srvGroups, ts, env, dk };
  }
  const d = await decBlob(j.data, cKeys(c).enc); if (!d || !Array.isArray(d.srvGroups)) throw new Error('Os dados da nuvem não abriram com esta senha.');
  return { groups: d.srvGroups, ts, old: true };
}
const saveCloudCfg = c => { try { const st = c.rem === false ? SS : LS; (st == SS ? LS : SS).removeItem(CLKEY); st[CLKEY] = JSON.stringify(c) } catch (e) { } };
async function cloudPut(c, env, groups, ts) {
  const dk = unb64(c.dk);
  env = { wp: env.wp, wr: env.wr, d: await encBlob({ srvGroups: groups, lists: [], favs: [], groups: {}, prog: {}, ts }, dk) };
  await call({ a: 'put', u: c.u, k: c.auth, dev: devId(), dn: 'Site servidores', ts, data: packEnv(env) });
  return env;
}
// cria DK + código novos (primeira vez ou conversão do formato antigo) e grava; mostra o código
async function cloudInit(c, groups) {
  const dk = crypto.getRandomValues(new Uint8Array(32)), code = newRecCode();
  const env = { wp: await encBlob({ dk: b64(dk) }, cKeys(c).enc), wr: await encBlob({ dk: b64(dk) }, await recKey(c.u, code)) };
  c.dk = b64(dk); const ts = Date.now(); await cloudPut(c, env, groups || [], ts); saveCloudCfg(c);
  showRecCode(code, c.u); return ts;
}
function showRecCode(code, u) {
  modal(`<h3>Seu código de recuperação</h3>
    <p class="muted">Se um dia perder a senha da conta <b>${esc(u)}</b>, só este código destrava os dados. Ele aparece <b>uma vez só</b>. Anote no papel ou guarde fora deste aparelho.</p>
    <div class="cred" style="font-size:20px;text-align:center;letter-spacing:.06em">${esc(code)}</div>
    <p class="muted">Quem tiver o código e a conta consegue abrir os seus dados. Não mande para ninguém.</p>
    <label class="f" style="display:flex;align-items:center;gap:8px;color:var(--tx)"><input type="checkbox" data-f="ok">Anotei o código em lugar seguro</label>
    <div class="acts"><button class="bt" data-cp>Copiar</button><button class="bt" data-dl>Baixar .txt</button><button class="bt pk" data-ok disabled>Pronto</button></div>`, m => {
    $('[data-f="ok"]', m).onchange = e => $('[data-ok]', m).disabled = !e.target.checked;
    $('[data-cp]', m).onclick = async () => { try { await navigator.clipboard.writeText(code); toast('Código copiado.') } catch (e) { toast('Não deu para copiar; anote à mão.', 1) } };
    $('[data-dl]', m).onclick = () => { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(['NTPlay Servidores — código de recuperação\nConta: ' + u + '\nCódigo: ' + code + '\nCriado em: ' + new Date().toLocaleString('pt-BR') + '\n'], { type: 'text/plain' })); a.download = 'codigo-recuperacao-ntplay.txt'; document.body.appendChild(a); a.click(); a.remove() };
    $('[data-ok]', m).onclick = closeModal;
  });
  $('#modal').dataset.lock = 1;
}
let CMIG = false;
async function localAdm(op, x) {
  if (op == 'list') return { ok: true, users: [] };
  const c = cloud();
  if (op == 'gget') {
    if (c) {
      try {
        const r = await cloudGet(c);
        if (r.groups) {
          if (r.old && !CMIG) { CMIG = true; const ts = await cloudInit(c, r.groups); localPut(r.groups, ts); return { ok: true, groups: r.groups, ts } } // converte para o formato com código
          if (r.dk && b64(r.dk) != c.dk) { c.dk = b64(r.dk); saveCloudCfg(c) }
          localPut(r.groups, r.ts); return { ok: true, groups: r.groups, ts: r.ts };
        }
      } catch (e) { if (/incorretos|expirou|validade|abriram/i.test(e.message)) toast('Nuvem: ' + e.message, 1); else if (!localAdm.off) { localAdm.off = 1; toast('Sem internet: mostrando a última cópia deste aparelho.', 1) } }
    }
    const o = localGet(); return { ok: true, groups: o.groups, ts: o.ts };
  }
  if (op == 'gset') {
    let ts = Date.now();
    if (c) {
      const cur = await cloudGet(c); // confere se outro aparelho salvou antes
      if (cur.groups && cur.ts && cur.ts != x.prev) throw new Error('Outro aparelho alterou os dados. Clique em Atualizar e refaça a mudança.');
      if (cur.env && c.dk) await cloudPut(c, cur.env, x.groups, ts);
      else { CMIG = true; ts = await cloudInit(c, x.groups) }
      localAdm.off = 0;
    }
    localPut(x.groups, ts); return { ok: true, ts };
  }
  throw new Error('Não disponível neste site.');
}
function cloudBox() {
  const c = cloud();
  if (c) return modal(`<h3>Conta ${esc(c.u)}</h3><p class="muted">Os dados ficam salvos na nuvem, trancados com a sua senha. ${c.rem === false ? 'Este aparelho esquece o login quando o navegador fechar.' : 'Este aparelho lembra o login.'}</p>
    <div class="acts" style="justify-content:space-between"><button class="bt" data-rc>Gerar novo código de recuperação</button></div>
    <div class="acts"><button class="bt rd" data-off>Sair</button><button class="bt pk" data-no>Fechar</button></div>`, m => {
    $('[data-no]', m).onclick = closeModal;
    $('[data-rc]', m).onclick = async () => {
      closeModal(); if (!await confirmBox('Novo código de recuperação', 'O código antigo deixa de funcionar e um novo aparece uma vez só.', 'Gerar', 0)) return;
      try { const cur = await cloudGet(c); if (!cur.env) return toast('Abra o site de novo e tente mais uma vez.', 1); const code = newRecCode(), dk = unb64(c.dk);
        const ts = Date.now(); const env = { wp: cur.env.wp, wr: await encBlob({ dk: c.dk }, await recKey(c.u, code)) };
        await cloudPut(c, env, GROUPS, ts); GTS = ts; localPut(GROUPS, ts); showRecCode(code, c.u) } catch (e) { toast(e.message, 1) }
    };
    $('[data-off]', m).onclick = () => { closeModal(); gateLogout() };
  });
  modal(`<h3>Abrir de outros aparelhos</h3><p class="muted">Digite a conta e a senha do cofre (a conta criada no painel para este site). Este aparelho lembra e não pede de novo.</p>
    <label class="f">Conta<input class="in" data-f="u" value="servidores" autocomplete="username"></label>
    <label class="f">Senha<input class="in" data-f="p" type="password" autocomplete="current-password"></label>
    <p class="err" id="clE"></p>
    <div class="acts" style="justify-content:space-between"><button class="bt" data-fg style="border:0;background:none;color:var(--mut);text-decoration:underline;padding-left:0">Esqueci a senha</button><span style="flex:1"></span><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Ligar</button></div>`, m => {
    $('[data-no]', m).onclick = closeModal;
    $('[data-fg]', m).onclick = recoverBox;
    const go = async () => {
      const u = normU($('[data-f="u"]', m).value), p = $('[data-f="p"]', m).value, b = $('[data-ok]', m); $('#clE', m).textContent = '';
      if (!u || !p) return $('#clE', m).textContent = 'Preencha a conta e a senha.';
      b.disabled = true; b.innerHTML = '<span class="spin"></span> Ligando…';
      try {
        const k = await derive(u, p), c2 = { u, auth: k.auth, enc: b64(k.enc) }, r = await cloudGet(c2), here = localGet();
        const nHere = (here.groups.find(g => g.id == SRV_ID) || {}).servers?.reduce((a, s) => a + (s.plans || []).length, 0) || 0;
        if (r.dk) c2.dk = b64(r.dk);
        saveCloudCfg(c2); closeModal();
        if (r.groups && !r.old) {
          toast(nHere && JSON.stringify(here.groups) != JSON.stringify(r.groups) ? 'Nuvem ligada. Os dados da nuvem substituíram os deste aparelho.' : 'Nuvem ligada.');
          localPut(r.groups, r.ts); GLOADED = false; await loadGroups();
        } else { CMIG = true; const g = r.groups || (GROUPS.length ? GROUPS : here.groups), ts = await cloudInit(c2, g); localPut(g, ts); GLOADED = false; await loadGroups(); toast('Nuvem ligada.') }
        renderCloudBtn();
      } catch (e) { b.disabled = false; b.textContent = 'Ligar'; $('#clE', m).textContent = /incorretos/.test(e.message) ? 'Conta ou senha incorretas.' : e.message }
    };
    $('[data-ok]', m).onclick = go; $('[data-f="p"]', m).onkeydown = e => { if (e.key == 'Enter') go() }; $('[data-f="p"]', m).focus();
  });
}
// Esqueci a senha: o administrador define uma senha nova no painel (Contas → Redefinir senha) e aqui
// o código de recuperação destrava os dados, que passam a abrir com a senha nova.
function recoverBox() {
  modal(`<h3>Recuperar com o código</h3>
    <p class="muted"><b>1.</b> No painel principal (admin.html), aba <b>Contas</b>, conta <b>servidores</b>: use <b>Redefinir senha</b> e anote a senha nova.<br><b>2.</b> Aqui, digite a conta, a senha nova e o código de recuperação.</p>
    <label class="f">Conta<input class="in" data-f="u" value="servidores"></label>
    <label class="f">Senha nova<input class="in" data-f="p" type="password" autocomplete="new-password"></label>
    <label class="f">Código de recuperação<input class="in" data-f="c" placeholder="XXXX-XXXX-XXXX-XXXX-XXXX" style="font-family:ui-monospace,Consolas,monospace;text-transform:uppercase"></label>
    <p class="err" id="rcE"></p>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Recuperar</button></div>`, m => {
    $('[data-no]', m).onclick = closeModal;
    $('[data-ok]', m).onclick = async () => {
      const u = normU($('[data-f="u"]', m).value), p = $('[data-f="p"]', m).value, code = $('[data-f="c"]', m).value, b = $('[data-ok]', m), E = $('#rcE', m); E.textContent = '';
      if (!u || !p || code.replace(/[^A-Za-z0-9]/g, '').length < 20) return E.textContent = 'Preencha a conta, a senha nova e o código completo.';
      b.disabled = true; b.innerHTML = '<span class="spin"></span> Recuperando…';
      try {
        const k = await derive(u, p), c2 = { u, auth: k.auth, enc: b64(k.enc) };
        const j = await call({ a: 'get', u, k: k.auth, dev: devId(), dn: 'Site servidores' }), env = parseEnv(j.data);
        if (!j.data) throw new Error('A conta não tem dados salvos.');
        if (!env) throw new Error('Estes dados são do formato antigo, sem código de recuperação.');
        const dk = await unwrapDK(env.wr, await recKey(u, code)); if (!dk) throw new Error('Código de recuperação incorreto.');
        const d = await decBlob(env.d, dk); if (!d || !Array.isArray(d.srvGroups)) throw new Error('Os dados não abriram.');
        c2.dk = b64(dk); const ts = Date.now();
        await cloudPut(c2, { wp: await encBlob({ dk: c2.dk }, k.enc), wr: env.wr }, d.srvGroups, ts);
        saveCloudCfg(c2); localPut(d.srvGroups, ts); closeModal(); if (!$('#vGate').classList.contains('hide')) { $('#vGate').classList.add('hide'); start() } else { GLOADED = false; await loadGroups() } renderCloudBtn();
        toast('Recuperado. Os dados agora abrem com a senha nova; o código continua o mesmo.');
      } catch (e) { b.disabled = false; b.textContent = 'Recuperar'; E.textContent = /incorretos/.test(e.message) ? 'Conta ou senha nova incorretas. Confira se a senha foi redefinida no painel.' : e.message }
    };
  });
}
function renderCloudBtn() { renderHero(); const b = $('#bkC'); if (!b) return; const c = cloud(); b.querySelector('span').textContent = c ? 'Conta: ' + c.u : 'Conta'; b.classList.toggle('on2', !!c) }
const adm = (op, extra = {}) => LOCAL ? localAdm(op, extra) : call({ a: 'adm', adm: ADM, op, ...extra });

/* ================= criptografia (igual ao app: core/Sync) =================
   chave = PBKDF2-HMAC-SHA1(senha, "ntplay-auth:"/"ntplay-enc:" + usuário, 40000, 256 bits)
   dados = base64( iv[12] + AES-GCM(json) ) */
const te = new TextEncoder(), td = new TextDecoder();
const b64 = a => btoa(String.fromCharCode(...a));
const b64u = a => b64(a).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64 = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=')), c => c.charCodeAt(0));
const normU = u => String(u || '').trim().toLowerCase();
async function pbkdf(pass, salt) {
  const k = await crypto.subtle.importKey('raw', te.encode(pass), 'PBKDF2', false, ['deriveBits']);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-1', salt: te.encode(salt), iterations: 40000 }, k, 256));
}
async function derive(user, pass) { const u = normU(user); return { auth: b64u(await pbkdf(pass, 'ntplay-auth:' + u)), enc: await pbkdf(pass, 'ntplay-enc:' + u) } }
async function encBlob(obj, enc) {
  const iv = crypto.getRandomValues(new Uint8Array(12)), key = await crypto.subtle.importKey('raw', enc, 'AES-GCM', false, ['encrypt']);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, te.encode(JSON.stringify(obj))));
  const all = new Uint8Array(12 + ct.length); all.set(iv); all.set(ct, 12); return b64(all);
}
async function decBlob(s, enc) {
  try { const all = unb64(s), key = await crypto.subtle.importKey('raw', enc, 'AES-GCM', false, ['decrypt']); return JSON.parse(td.decode(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: all.slice(0, 12) }, key, all.slice(12)))) } catch (e) { return null }
}
const emptyPayload = () => ({ lists: [], favs: [], groups: {}, prog: {}, ts: 0 });
async function putPayload(user, keys, payload) { payload.ts = Date.now(); return call({ a: 'put', u: normU(user), k: keys.auth, data: await encBlob(payload, keys.enc), ts: payload.ts }) }
function xtreamUrl(host, user, pass) { let h = host.trim().replace(/\/+$/, ''); if (!h.includes('://')) h = 'http://' + h; return h + '/get.php?username=' + encodeURIComponent(user.trim()) + '&password=' + encodeURIComponent(pass.trim()) + '&type=m3u_plus&output=ts' }
function hostOf(url) { try { return new URL(url).host } catch (e) { return url.slice(0, 40) } }

/* ================= editor de listas (usado em Nova conta e na aba Listas) ================= */
function listEditor(listEl, addEl, lists, onChange) {
  let mode = 'm3u';
  function draw() {
    listEl.innerHTML = lists.length ? lists.map((l, i) => `<div class="li"><button class="sw ${l.active !== false ? 'on' : ''}" data-i="${i}" title="Usar esta lista" aria-label="Usar esta lista"></button><div class="n"><b>${esc(l.name || 'Lista')}</b><small>${esc(hostOf(l.url))}</small></div><button class="bt sm rd" data-del="${i}">Remover</button></div>`).join('') : '<p class="muted">Nenhuma lista.</p>';
    $$('.sw', listEl).forEach(b => b.onclick = () => { const l = lists[+b.dataset.i]; l.active = l.active === false; draw(); onChange && onChange() });
    $$('[data-del]', listEl).forEach(b => b.onclick = () => { lists.splice(+b.dataset.del, 1); draw(); onChange && onChange() });
  }
  function drawAdd() {
    addEl.innerHTML = `<div class="grid" style="gap:8px;background:#ffffff06;border:1px solid var(--line);border-radius:12px;padding:12px">
      <div class="seg"><button data-m="m3u" class="${mode == 'm3u' ? 'on' : ''}">Link M3U</button><button data-m="xt" class="${mode == 'xt' ? 'on' : ''}">Xtream</button></div>
      <input class="in" data-k="name" placeholder="Nome (ex.: Lista principal)">
      ${mode == 'm3u' ? '<input class="in" data-k="url" placeholder="http://… link da lista M3U">' : '<input class="in" data-k="host" placeholder="Servidor (ex.: http://servidor.com:8080)"><div class="row"><input class="in" data-k="xu" placeholder="Usuário Xtream" autocomplete="off"><input class="in" data-k="xp" placeholder="Senha Xtream" autocomplete="off"></div>'}
      <div><button class="bt sm" data-add>+ Adicionar lista</button></div></div>`;
    $$('[data-m]', addEl).forEach(b => b.onclick = () => { mode = b.dataset.m; drawAdd() });
    $('[data-add]', addEl).onclick = () => {
      const v = k => ($(`[data-k="${k}"]`, addEl) || {}).value || '';
      let url = mode == 'm3u' ? v('url').trim() : (v('host') && v('xu') && v('xp') ? xtreamUrl(v('host'), v('xu'), v('xp')) : '');
      if (!/^https?:\/\/\S+$/i.test(url)) return toast(mode == 'm3u' ? 'Cole um link que comece com http:// ou https://' : 'Preencha servidor, usuário e senha do Xtream.', 1);
      if (lists.some(l => l.url == url)) return toast('Essa lista já está na conta.', 1);
      lists.push({ name: v('name').trim() || 'Lista ' + (lists.length + 1), url, active: true }); draw(); drawAdd(); onChange && onChange();
    };
  }
  draw(); drawAdd();
  return { draw };
}

/* ================= modal ================= */
function modal(html, bind) { $('#mdBody').innerHTML = html; $('#modal').classList.remove('hide'); bind && bind($('#mdBody')) }
function closeModal() { $('#modal').classList.add('hide'); delete $('#modal').dataset.lock }
$('#modal').onclick = e => { if (e.target.id == 'modal' && !$('#modal').dataset.lock) closeModal() };
function confirmBox(title, text, okLabel, danger) { return new Promise(res => modal(`<h3>${esc(title)}</h3><p class="muted">${esc(text)}</p><div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt ${danger ? 'rd' : 'pk'}" data-ok>${esc(okLabel)}</button></div>`, m => { $('[data-no]', m).onclick = () => { closeModal(); res(false) }; $('[data-ok]', m).onclick = () => { closeModal(); res(true) } })) }
function askBox(title, text, fields, okLabel) {
  return new Promise(res => modal(`<h3>${esc(title)}</h3>${text ? `<p class="muted">${esc(text)}</p>` : ''}${fields.map(f => `<label class="f">${esc(f.label)}<input class="in" data-f="${f.k}" type="${f.type || 'text'}" value="${esc(f.v || '')}" ${f.max ? `maxlength="${f.max}"` : ''}></label>`).join('')}<div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>${esc(okLabel)}</button></div>`, m => {
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { const o = {}; $$('[data-f]', m).forEach(i => o[i.dataset.f] = i.value); closeModal(); res(o) };
    const first = $('[data-f]', m); first && first.focus();
  }))
}

/* ================= navegação ================= */
// servidores.html é o mesmo painel com <body data-only="srv,ind,fin,com">: só essas abas, mesmos dados.
const ONLY = (document.body.dataset.only || '').split(',').filter(Boolean);
const TABS = ['geral', 'contas', 'clientes', 'sigma', 'srv', 'ind', 'fin', 'com', 'listas', 'uso'].filter(t => !ONLY.length || ONLY.includes(t));
if (ONLY.length) {
  $$('.nav[data-tab]').forEach(b => { if (!TABS.includes(b.dataset.tab)) b.classList.add('hide') }); // escondidos, não apagados: o código ainda atualiza os números deles
  $$('main > section').forEach(s => { if (!TABS.includes(s.id.slice(2))) { s.classList.add('hide'); s.dataset.off = 1 } });
  $$('.brand b span').forEach(x => x.textContent = 'Servidores');
  const k = $('#lKey'); if (k) k.closest('label').classList.add('hide');
}
function go(tab) {
  $$('.nav[data-tab]').forEach(b => b.classList.toggle('on', b.dataset.tab == tab));
  TABS.forEach(t => $('#t-' + t).classList.toggle('hide', t != tab));
  if (tab == 'uso') usoStart();
  if (tab == 'sigma') sigmaStart();
  if (['srv', 'ind', 'fin', 'com'].includes(tab)) srvStart();
  if (tab == 'clientes') setTimeout(drawTimeline, 0);
  try { history.replaceState(null, '', '#' + tab) } catch (e) { }
  window.scrollTo(0, 0);
}
$$('.nav[data-tab]').forEach(b => b.onclick = () => go(b.dataset.tab));
document.addEventListener('click', e => { const b = e.target.closest('[data-go="novo"]'); if (b) { go('contas'); openNovo() } });

/* ================= dados das contas ================= */
async function loadUsers() {
  $('#upd').textContent = 'atualizando…';
  try { const j = await adm('list'); USERS = j.users || []; renderAll(); $('#upd').textContent = 'atualizado ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) }
  catch (e) { $('#upd').textContent = ''; toast(e.message, 1); if (/administrador/i.test(e.message)) logout() }
}
$('#bRef').onclick = loadUsers;
function renderAll() { renderGeral(); renderContas(); renderClientes(); fillAccSelect(); if (GLOADED) renderGrp() }

/* ================= sigma e grupos =================
   Grupos ficam no servidor (op gget/gset). Cada membro é
   { id, src:'nt', u } (conta NTPlay; vencimento vem de USERS) ou
   { id, src:'sg', u, exp:'AAAA-MM-DD', note } (cliente do painel Sigma, cadastrado à mão). */
const GCOLS = ['#d0477b', '#5c9bd1', '#3daa78', '#d39b32', '#9a86c9', '#d55a55', '#4fb3b8'];
let GROUPS = [], GTS = 0, GLOADED = false, GBUSY = false;
const gid = () => Math.random().toString(36).slice(2, 10);
const clone = o => JSON.parse(JSON.stringify(o));
const SVG_ED = '<svg class="i" viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>';
const SVG_DEL = '<svg class="i" viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/></svg>';
const SVG_X = '<svg class="i" viewBox="0 0 24 24" style="width:14px;height:14px"><path d="M18 6L6 18M6 6l12 12"/></svg>';

function sigmaStart() { if (!GLOADED) loadGroups(); else renderGroups() }
let GSEL = (() => { try { return LS.gSel || '' } catch (e) { return '' } })();
const ALERT_DAYS = 7, ALERT_PAST = 30; // alerta: vence em até 7 dias ou venceu há até 30 dias

async function loadGroups() {
  if (!GLOADED) $('#gBody').innerHTML = '<p class="muted"><span class="spin"></span> Carregando grupos…</p>';
  try { const j = await adm('gget'); GROUPS = Array.isArray(j.groups) ? j.groups : []; GTS = j.ts || 0; GLOADED = true; renderGrp() }
  catch (e) { $('#gBody').innerHTML = $('#sBody').innerHTML = `<p class="err">${esc(e.message)}</p>`; if (/administrador/i.test(e.message)) logout() }
}
$('#gRef').onclick = () => { loadUsers(); loadGroups() };

async function saveGroups(next, okMsg) {
  if (GBUSY) { toast('Aguarde, salvando…'); return false }
  GBUSY = true;
  try { const j = await adm('gset', { groups: next, prev: GTS }); GROUPS = next; GTS = j.ts; renderGrp(); if (okMsg) toast(okMsg); return true }
  catch (e) { toast(e.message, 1); return false }
  finally { GBUSY = false }
}

// tempo que falta, em texto claro
function remText(d) {
  if (d > 3650) return 'Vitalícia';
  if (d <= 0) { const p = Math.floor(-d); return p < 1 ? 'Venceu hoje' : 'Venceu há ' + p + (p == 1 ? ' dia' : ' dias') }
  if (d < 1) return 'Vence hoje';
  if (d < 2) return 'Vence amanhã';
  const n = Math.floor(d); return 'Faltam ' + n + ' dias';
}
const remCls = d => d <= 0 ? 'r' : d <= 3 ? 'r' : d <= ALERT_DAYS ? 'y' : 'g';

function memberInfo(m) {
  if (m.src == 'nt') {
    const u = USERS.find(x => x.username == m.u);
    if (!u) return { lab: 'Conta apagada', cls: 'r', d: -1e9, sub: 'não existe mais no NTPlay', none: 1 };
    const d = u.expires_at ? daysLeft(u) : 1e9;
    return { lab: u.expires_at ? remText(d) : 'Sem validade', cls: u.expires_at ? remCls(d) : 'n', d, sub: [u.note, u.expires_at && d <= 3650 ? 'vence ' + fmtD(u.expires_at) : ''].filter(Boolean).join(' · ') };
  }
  const sp = srvPlanText(m);
  if (!m.exp) return { lab: 'Sem vencimento', cls: 'n', d: 1e9, sub: [sp, m.note].filter(Boolean).join(' · ') };
  const end = new Date(m.exp + 'T23:59:59-03:00').getTime(), d = (end - Date.now()) / DAY;
  return { lab: remText(d), cls: remCls(d), d, sub: [sp, 'vence ' + fmtD(end), m.note].filter(Boolean).join(' · ') };
}
const inAlert = i => !i.none && i.d <= ALERT_DAYS && i.d > -ALERT_PAST;

function renderGrp() { renderGroups(); renderSrv(); renderInd(); renderFin(); renderCom(); renderHero() }
/* banner do topo (só no servidores.html): saudação, data, resumo do dia e atalhos */
function renderHero() {
  const el = $('#hero'); if (!el || !ONLY.length || !GLOADED) return;
  const rows = allRows(), now = new Date(), hr = +now.toLocaleString('en-US', { hour: 'numeric', hour12: false, timeZone: SP });
  const hi = hr < 12 ? 'Bom dia' : hr < 18 ? 'Boa tarde' : 'Boa noite';
  const dia = now.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: SP });
  let rcv = 0, venc = 0, soon = 0; rows.forEach(({ p }) => { const k = rowInfo(p).k; if (!p.paid) rcv += netOf(p); if (k == 'exp') venc++; if (k == 'soon') soon++ });
  const t = todayISO(), avisar = waDue().filter(x => x.p.warned != t).length, nSv = srvList().filter(s => (s.plans || []).length).length;
  const c = LOCAL && cloud();
  el.innerHTML = `<div>
      <div class="eb"><img src="img/icon.png" alt="">NTPlay Servidores<span class="pill ${c ? 'on' : ''}">${LOCAL ? (c ? 'Salvo na nuvem' : 'Salvo só neste aparelho') : 'Painel'}</span></div>
      <h2>${hi}!</h2>
      <div class="ds">${esc(dia.charAt(0).toUpperCase() + dia.slice(1))} · ${rows.length} cliente${rows.length == 1 ? '' : 's'} em ${nSv} servidor${nSv == 1 ? '' : 'es'}</div>
      <div class="ha"><button class="bt pk" data-h="add"><svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>Novo cliente</button>
        <button class="bt ${avisar ? 'wab' : ''}" data-h="wa">${SVG_WA}${avisar ? 'Avisar ' + avisar + ' no WhatsApp' : 'Avisos em dia'}</button>
        ${LOCAL ? '<button class="bt" data-h="bk"><svg class="i" viewBox="0 0 24 24"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg>Salvar cópia</button>' : ''}</div>
    </div>
    <div class="hm">
      <div class="yl"><b>${brl(rcv)}</b><small>a receber</small></div>
      <div class="${venc ? 'rd' : ''}"><b>${venc}</b><small>vencido${venc == 1 ? '' : 's'}</small></div>
      <div class="${soon ? 'yl' : ''}"><b>${soon}</b><small>vencem em 7 dias</small></div>
      <div class="${avisar ? 'gr' : ''}"><b>${avisar}</b><small>para avisar hoje</small></div>
    </div>`;
  el.classList.remove('hide');
}
function heroAdd() {
  const SV = srvList(); if (!SV.length) return toast('Cadastre um servidor primeiro.', 1);
  modal(`<h3>Novo cliente</h3><label class="f">Em qual servidor?<select class="in" data-f="s">${SV.map(x => `<option value="${esc(x.id)}">${esc(x.name)}</option>`).join('')}</select></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Continuar</button></div>`, m => {
    $('[data-no]', m).onclick = closeModal;
    $('[data-ok]', m).onclick = () => { const id = $('[data-f="s"]', m).value; closeModal(); go('srv'); setTimeout(() => { const b = $$('#t-srv [data-sa="padd"]').find(x => x.dataset.s == id); if (b) b.click() }, 50) };
  });
}
$('#hero').onclick = e => {
  const b = e.target.closest('[data-h]'); if (!b) return;
  if (b.dataset.h == 'add') heroAdd();
  if (b.dataset.h == 'bk') backupSave();
  if (b.dataset.h == 'wa') { go('srv'); setTimeout(() => $('#sWa').scrollIntoView({ behavior: 'smooth', block: 'start' }), 50) }
};
function renderGroups() {
  if (!GLOADED) return;
  const GR = realGroups(GROUPS);
  // alerta + selo no menu
  const al = [];
  GR.forEach(g => (g.members || []).forEach(m => { const i = memberInfo(m); if (inAlert(i)) al.push({ g, m, i }) }));
  al.sort((a, b) => a.i.d - b.i.d);
  const nExp = al.filter(x => x.i.d <= 0).length;
  const nb = $('#nGrp'); nb.textContent = al.length; nb.className = 'bdg ' + (nExp ? 'r' : 'y') + (al.length ? '' : ' hide');
  $('#gAlert').innerHTML = al.length ? `<div class="card galert ${nExp ? 'bad' : ''}"><div class="gah"><svg class="i" viewBox="0 0 24 24" style="color:var(--${nExp ? 'rd' : 'yl'})"><path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01"/></svg>
      <h3>Atenção aos vencimentos</h3><span class="muted">${al.length - nExp} vence${al.length - nExp == 1 ? '' : 'm'} em até ${ALERT_DAYS} dias${nExp ? ` · <b style="color:var(--rd)">${nExp} vencido${nExp > 1 ? 's' : ''}</b>` : ''}</span></div>
      <div class="gai">${al.map(({ g, m, i }) => `<button class="gm ${i.cls}" data-gs="${esc(g.id)}" style="text-align:left;cursor:pointer"><span class="src ${m.src == 'nt' ? 'nt' : 'sg'}">${m.src == 'nt' ? 'NTPLAY' : 'SIGMA'}</span><div class="n"><b>${esc(m.u)}</b><small>${esc(g.name)}${i.sub ? ' · ' + esc(i.sub) : ''}</small></div><span class="bdg ${i.cls}">${esc(i.lab)}</span></button>`).join('')}</div></div>` : '';

  if (!GR.length) { $('#gTabs').innerHTML = ''; $('#gBody').innerHTML = '<div class="empty card">Nenhum grupo ainda. Crie o primeiro em <b>Novo grupo</b>.</div>'; return }
  if (!GR.some(g => g.id == GSEL)) GSEL = GR[0].id;
  // abas dos grupos
  $('#gTabs').innerHTML = GR.map(g => {
    const inf = (g.members || []).map(memberInfo), w = inf.filter(inAlert), wr = w.some(i => i.d <= 0);
    return `<button class="gtab ${g.id == GSEL ? 'on' : ''}" data-gs="${esc(g.id)}" style="--c:${esc(g.color || GCOLS[0])}"><i></i>${esc(g.name)}<span class="bdg n">${inf.length}</span>${w.length ? `<span class="w ${wr ? 'r' : ''}" title="${w.length} com vencimento próximo"></span>` : ''}</button>`;
  }).join('');
  // grupo aberto: NTPlay e Sigma em colunas separadas
  const g = GR.find(x => x.id == GSEL), mem = (g.members || []).map(m => ({ m, i: memberInfo(m) })).sort((a, b) => a.i.d - b.i.d);
  const col = (src, title, add) => {
    const rows = mem.filter(x => (x.m.src == 'nt') == (src == 'nt'));
    return `<div class="card"><div class="gch"><span class="src ${src}">${src == 'nt' ? 'NTPLAY' : 'SIGMA'}</span><b>${title}</b><span class="bdg n">${rows.length}</span><span class="sp"></span><button class="bt sm" data-ga="${add}" data-g="${esc(g.id)}">+ Adicionar</button></div>
      <div class="lst">${rows.length ? rows.map(({ m, i }) => `<div class="gm ${i.cls == 'g' || i.cls == 'n' ? '' : i.cls}"><div class="n"><b>${esc(m.u)}</b><small>${esc(i.sub) || '&nbsp;'}</small></div><span class="bdg ${i.cls}">${esc(i.lab)}</span>
        ${m.src == 'sg' ? `<button class="x" data-ga="edsg" data-g="${esc(g.id)}" data-m="${esc(m.id)}" title="Editar" aria-label="Editar">${SVG_ED}</button>` : ''}
        <button class="x" data-ga="rm" data-g="${esc(g.id)}" data-m="${esc(m.id)}" title="Tirar do grupo" aria-label="Tirar do grupo">${SVG_X}</button></div>`).join('') : `<p class="muted">Nenhum cliente ${src == 'nt' ? 'NTPlay' : 'Sigma'} neste grupo.</p>`}</div></div>`;
  };
  $('#gBody').innerHTML = `<div class="ghd" style="--c:${esc(g.color || GCOLS[0])}"><h2><i></i>${esc(g.name)}</h2><span class="sp"></span>
      <button class="bt sm" data-ga="edit" data-g="${esc(g.id)}">${SVG_ED}Editar grupo</button><button class="bt sm rd" data-ga="del" data-g="${esc(g.id)}">${SVG_DEL}Apagar</button></div>
    <div class="gcolw">${col('nt', 'Clientes NTPlay', 'addnt')}${col('sg', 'Clientes Sigma', 'addsg')}</div>
    <p class="muted" style="margin-top:10px">Os clientes Sigma são cadastrados à mão: quando renovar no painel, atualize o vencimento aqui no lápis. No lápis também dá para escolher o servidor, o plano e o valor cobrado.</p>`;
}

function groupBox(g) {
  let color = (g && g.color) || GCOLS[realGroups(GROUPS).length % GCOLS.length];
  return new Promise(res => modal(`<h3>${g ? 'Editar grupo' : 'Novo grupo'}</h3>
    <label class="f">Nome<input class="in" data-f="name" maxlength="40" value="${esc(g ? g.name : '')}" placeholder="ex.: Revenda João"></label>
    <div class="f" style="display:grid;gap:5px;font-size:12px;color:var(--mut);font-weight:600">Cor<div class="gcols">${GCOLS.map(c => `<button style="--c:${c}" data-c="${c}" class="${c == color ? 'on' : ''}" aria-label="Cor ${c}"></button>`).join('')}</div></div>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    $$('[data-c]', m).forEach(b => b.onclick = () => { color = b.dataset.c; $$('[data-c]', m).forEach(x => x.classList.toggle('on', x == b)) });
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { const name = $('[data-f="name"]', m).value.trim(); if (!name) return toast('Dê um nome ao grupo.', 1); closeModal(); res({ name, color }) };
    $('[data-f="name"]', m).focus();
  }));
}

function sigmaBox(mem) {
  const SV = srvList(), cur = mem || {};
  const planOpts = sid => { const sv = SV.find(x => x.id == sid); return '<option value="">—</option>' + (sv ? (sv.plans || []).map(p => `<option value="${esc(p.id)}">${esc(p.name)}${p.price != null ? ' · ' + brl(p.price) : ''}</option>`).join('') : '') };
  return new Promise(res => modal(`<h3>${mem ? 'Editar cliente Sigma' : 'Cliente do painel Sigma'}</h3>
    <p class="muted">Copie do painel o usuário e o vencimento do cliente.</p>
    <label class="f">Usuário no painel<input class="in" data-f="u" maxlength="60" value="${esc(cur.u || '')}" autocomplete="off"></label>
    <div class="row"><label class="f">Servidor<select class="in" data-f="srv"><option value="">—</option>${SV.map(x => `<option value="${esc(x.id)}">${esc(x.name)}</option>`).join('')}</select></label>
      <label class="f">Cliente no servidor<select class="in" data-f="plan"></select></label></div>
    <div class="row"><label class="f">Valor cobrado (R$)<input class="in" data-f="price" inputmode="decimal" value="${cur.price != null ? esc(String(cur.price).replace('.', ',')) : ''}" placeholder="usa o do plano"></label>
      <label class="f">Vencimento<input class="in" type="date" data-f="exp" value="${esc(cur.exp || '')}"></label></div>
    <label class="f">Comentário<textarea class="in" data-f="note" rows="2" maxlength="200" placeholder="WhatsApp, aparelho, combinado de pagamento…">${esc(cur.note || '')}</textarea></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    const fs = $('[data-f="srv"]', m), fp = $('[data-f="plan"]', m), fv = $('[data-f="price"]', m), fe = $('[data-f="exp"]', m);
    const hint = () => { const p = planOf(fs.value, fp.value); fv.placeholder = p && p.price != null ? 'plano: ' + brl(p.price) : 'opcional' };
    fs.value = cur.srv || ''; fp.innerHTML = planOpts(fs.value); fp.value = cur.plan || ''; hint();
    fs.onchange = () => { fp.innerHTML = planOpts(fs.value); hint() };
    fp.onchange = () => { hint(); const p = planOf(fs.value, fp.value); if (p && p.months && !fe.value) { const d = new Date(); d.setMonth(d.getMonth() + Number(p.months)); fe.value = d.toLocaleDateString('en-CA', { timeZone: SP }) } };
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => {
      const u = $('[data-f="u"]', m).value.trim(); if (!u) return toast('Informe o usuário do cliente.', 1);
      const price = fv.value.trim() ? money(fv.value) : null; if (fv.value.trim() && price == null) return toast('Valor inválido. Use, por exemplo, 35 ou 35,90.', 1);
      closeModal(); res({ u, srv: fs.value, plan: fp.value, price, exp: fe.value, note: $('[data-f="note"]', m).value.trim() });
    };
    $('[data-f="u"]', m).focus();
  }));
}

function pickNtBox(g) {
  const have = new Set((g.members || []).filter(x => x.src == 'nt').map(x => x.u));
  const free = USERS.filter(u => !have.has(u.username));
  if (!free.length) { toast(USERS.length ? 'Todas as contas NTPlay já estão neste grupo.' : 'Nenhuma conta NTPlay carregada.', 1); return Promise.resolve(null) }
  return new Promise(res => modal(`<h3>Adicionar contas NTPlay a "${esc(g.name)}"</h3>
    <input class="in" data-f="q" placeholder="Buscar…">
    <div class="lst" style="max-height:46vh;overflow:auto">${free.map(u => { const s = status(u); return `<label class="gm" data-u="${esc(u.username)}" style="cursor:pointer"><input type="checkbox" value="${esc(u.username)}"><div class="n"><b>${esc(u.username)}</b><small>${esc(u.note || '')}</small></div><span class="bdg ${s[1]}">${esc(s[0])}</span></label>` }).join('')}</div>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Adicionar</button></div>`, m => {
    $('[data-f="q"]', m).oninput = e => { const q = e.target.value.toLowerCase(); $$('[data-u]', m).forEach(l => l.classList.toggle('hide', !l.textContent.toLowerCase().includes(q))) };
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { const sel = $$('input[type=checkbox]:checked', m).map(i => i.value); if (!sel.length) return toast('Marque pelo menos uma conta.', 1); closeModal(); res(sel) };
    $('[data-f="q"]', m).focus();
  }));
}

$('#gNew').onclick = async () => { const o = await groupBox(); if (!o) return; const next = clone(GROUPS); const id = gid(); next.push({ id, name: o.name, color: o.color, members: [] }); GSEL = id; saveGroups(next, 'Grupo criado.') };

$('#t-sigma').onclick = async e => {
  const t = e.target.closest('[data-gs]'); if (t) { GSEL = t.dataset.gs; try { LS.gSel = GSEL } catch (er) { } renderGroups(); if (t.closest('#gAlert')) $('#gBody').scrollIntoView({ behavior: 'smooth' }); return }
  const b = e.target.closest('[data-ga]'); if (!b) return;
  const act = b.dataset.ga, next = clone(GROUPS), g = next.find(x => x.id == b.dataset.g); if (!g) return;
  g.members = g.members || [];
  if (act == 'edit') { const o = await groupBox(g); if (!o) return; Object.assign(g, o); return saveGroups(next, 'Grupo salvo.') }
  if (act == 'del') { if (!await confirmBox('Apagar grupo', `O grupo "${g.name}" será apagado. As contas NTPlay não são afetadas.`, 'Apagar', 1)) return; return saveGroups(next.filter(x => x.id != g.id), 'Grupo apagado.') }
  if (act == 'rm') { g.members = g.members.filter(x => x.id != b.dataset.m); return saveGroups(next) }
  if (act == 'addnt') { const sel = await pickNtBox(g); if (!sel) return; sel.forEach(u => g.members.push({ id: gid(), src: 'nt', u })); return saveGroups(next, sel.length + (sel.length > 1 ? ' contas adicionadas.' : ' conta adicionada.')) }
  if (act == 'addsg') {
    const o = await sigmaBox(); if (!o) return;
    if (g.members.some(x => x.src == 'sg' && x.u.toLowerCase() == o.u.toLowerCase())) return toast('Esse cliente Sigma já está no grupo.', 1);
    g.members.push({ id: gid(), src: 'sg', ...o }); return saveGroups(next, 'Cliente Sigma adicionado.');
  }
  if (act == 'edsg') { const mem = g.members.find(x => x.id == b.dataset.m); if (!mem) return; const o = await sigmaBox(mem); if (!o) return; Object.assign(mem, o); return saveGroups(next, 'Cliente atualizado.') }
};

/* ================= servidores =================
   Ficam no mesmo registro dos grupos (op gget/gset), num item especial que a aba Grupos ignora:
   { id:'__srv', kind:'srv', servers:[ { id, name, url, note, plans:[ { id, name, months, cost, price } ] } ] }
   cost = quanto você paga no painel; price = quanto cobra do cliente. Cliente Sigma guarda srv, plan e price (valor próprio, opcional).
   Assim não foi preciso mexer na função ntsync do Supabase. */
const SRV_ID = '__srv';
const DEFAULT_SRV = [
  ['coliseutv', 'Coliseutv', 'https://coliseutv.com/'], ['cineflix', 'Cineflix', 'https://cineflix.3xt.fun/'],
  ['playdragon', 'Playdragon', 'https://playdragon.sigma.st/'], ['wpsistemas', 'Wpsistemas', 'https://wpsistemas.online/'],
  ['playtvstreaming', 'Playtvstreaming', 'https://playtvstreaming.sigma.st/'], ['problack', 'Problack', 'https://problack.sigma.st/'],
  ['clickmaxx', 'Painel.clickmaxx', 'https://painel.clickmaxx.sbs/'], ['netplay', 'Netplay', 'https://netplay.mplll.com/'],
  ['souiptv', 'Painel.souiptv', 'https://painel.souiptv.com/']
].map(([id, name, url]) => ({ id, name, url, note: '', plans: [] }));
const realGroups = gs => gs.filter(g => g.kind != 'srv');
const srvList = () => { const r = GROUPS.find(g => g.id == SRV_ID); return r ? (r.servers || []) : clone(DEFAULT_SRV) };
function withSrv(next, fn) { return withRec(next, r => fn(r.servers)) }
const planOf = (sid, pid) => { const sv = srvList().find(x => x.id == sid); return sv && (sv.plans || []).find(p => p.id == pid) };
const brl = v => v == null || v === '' || isNaN(v) ? '—' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
function money(t) { let x = String(t).replace(/R\$|\s/g, ''); if (!x) return null; if (x.includes(',')) x = x.replace(/\./g, '').replace(',', '.'); const n = Number(x); return isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : null }
// texto curto do servidor/plano/valor de um cliente Sigma (aparece na aba Grupos)
function srvPlanText(m) {
  if (!m.srv) return m.price != null ? brl(m.price) : '';
  const sv = srvList().find(x => x.id == m.srv), p = planOf(m.srv, m.plan), v = m.price != null ? m.price : p && p.price;
  return [sv ? sv.name : 'servidor removido', p ? p.name : '', v != null ? brl(v) : ''].filter(Boolean).join(' ');
}
// clientes Sigma de cada servidor (de todos os grupos)
function srvMembers(sid) { const out = []; realGroups(GROUPS).forEach(g => (g.members || []).forEach(m => { if (m.src == 'sg' && m.srv == sid) out.push({ g, m }) })); return out }
/* registro completo: servers + expenses (despesas) + notes (comentários) + cfg.refDisc (desconto por indicação).
   Linha de cliente também guarda disc (desconto fixo R$), ref (id da linha de quem indicou) e paid (recebido no ciclo atual). */
const REC = () => GROUPS.find(g => g.id == SRV_ID) || {};
function withRec(next, fn) { let r = next.find(g => g.id == SRV_ID); if (!r) { r = { id: SRV_ID, kind: 'srv', servers: clone(DEFAULT_SRV) }; next.push(r) } r.servers = r.servers || []; r.expenses = r.expenses || []; r.notes = r.notes || []; r.cfg = r.cfg || {}; fn(r); return next }
// índice das linhas, refeito só quando os dados mudam (GROUPS vira outro array a cada carga/salvamento).
// Sem ele, cada linha varria a lista inteira e o site ficava lento com milhares de clientes.
let RC = null;
function rowCache() {
  if (RC && RC.g === GROUPS) return RC;
  const rows = srvList().flatMap(sv => (sv.plans || []).map(p => ({ sv, p }))), byId = new Map(), refs = new Map();
  rows.forEach(x => { byId.set(x.p.id, x); if (x.p.ref) { if (!refs.has(x.p.ref)) refs.set(x.p.ref, []); refs.get(x.p.ref).push(x) } });
  return RC = { g: GROUPS, rows, byId, refs };
}
const allRows = () => rowCache().rows;
const rowById = id => rowCache().byId.get(id);
// valor pago nos painéis digitado à mão (por mês, tudo junto). null = somar o "Você paga" de cada cliente.
const panelCost = () => { const v = (REC().cfg || {}).panelCost; return v == null || v === '' ? null : Number(v) };
const refDisc = () => Number((REC().cfg || {}).refDisc) || 0;
const rowOn = p => !p.exp || (new Date(p.exp + 'T23:59:59-03:00') - Date.now()) > 0;
// indicados de um cliente que estão com o plano em dia (cada um dá refDisc de desconto a quem indicou)
const refsOf = id => (rowCache().refs.get(id) || []).filter(x => rowOn(x.p));
const refDiscOf = p => refsOf(p.id).length * refDisc();
const discOf = p => Math.min(Number(p.price) || 0, (Number(p.disc) || 0) + refDiscOf(p));
const netOf = p => Math.max(0, (Number(p.price) || 0) - discOf(p));
const SVG_WA = '<svg class="i" viewBox="0 0 24 24"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/><path d="M9 9.5c.5 2 2 3.6 4.5 4.5"/></svg>';
const SVG_MV = '<svg class="i" viewBox="0 0 24 24"><path d="M4 8h14M14 4l4 4-4 4M20 16H6M10 12l-4 4 4 4"/></svg>';
const SVG_INFO = '<svg class="i" viewBox="0 0 24 24"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h4"/></svg>';
const INFO_MAX = 8000;
const perMonth = (v, months) => v == null ? 0 : v / Math.max(1, Number(months) || 1);

function srvStart() { if (!GLOADED) { $('#sBody').innerHTML = '<p class="muted"><span class="spin"></span> Carregando…</p>'; loadGroups() } else renderGrp() }
// Cada linha da tabela de um servidor é um cliente: { id, name, months, start, exp, cost, price, note }.
// (fica no campo "plans" por compatibilidade com o que já estava salvo)
let SQ = '', SF = 'all';
const todayISO = () => new Date().toLocaleDateString('en-CA', { timeZone: SP });
function addMonths(iso, n) { const d = new Date(iso + 'T12:00:00'); d.setMonth(d.getMonth() + Number(n || 1)); return d.toISOString().slice(0, 10) }
const fmtS = iso => iso ? iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(2, 4) : '—';
const fmtISO = iso => iso ? iso.split('-').reverse().join('/') : '—';
function rowInfo(p) {
  if (!p.exp) return { lab: 'Sem data', cls: 'n', d: 1e9, k: 'none' };
  const d = (new Date(p.exp + 'T23:59:59-03:00') - Date.now()) / DAY;
  return { lab: remText(d), cls: remCls(d), d, k: d <= 0 ? 'exp' : d <= ALERT_DAYS ? 'soon' : 'ok' };
}
/* ---- aviso de vencimento por WhatsApp ----
   O site não envia sozinho (é uma página estática, sem servidor de envio): monta a mensagem e abre o WhatsApp
   com tudo preenchido, você só aperta enviar. A linha guarda wa (telefone) e warned (data do último aviso). */
const WA_DEF = 'Olá {nome}! Seu plano vence {quando} ({vence}). Valor da renovação: {valor}. Para renovar é só responder esta mensagem.';
const waMsgT = () => (REC().cfg || {}).waMsg || WA_DEF;
const waDays = () => Number((REC().cfg || {}).waDays) || 3;
function waNum(t) { let d = String(t || '').replace(/\D/g, ''); if (!d) return ''; if (d.length <= 11) d = '55' + d.replace(/^0+/, ''); return d }
function waText(p, sv) {
  const i = rowInfo(p), dd = Math.floor(i.d);
  const quando = !p.exp ? 'em breve' : dd < 0 ? 'venceu há ' + (-dd) + (dd == -1 ? ' dia' : ' dias') : dd == 0 ? 'hoje' : dd == 1 ? 'amanhã' : 'em ' + dd + ' dias';
  return waMsgT().replace(/\{nome\}/g, p.name.split(' ')[0]).replace(/\{nomecompleto\}/g, p.name).replace(/\{vence\}/g, fmtISO(p.exp)).replace(/\{quando\}/g, quando)
    .replace(/\{dias\}/g, String(Math.max(0, dd))).replace(/\{valor\}/g, brl(netOf(p))).replace(/\{servidor\}/g, sv ? sv.name : '');
}
const waLink = (p, sv) => 'https://wa.me/' + waNum(p.wa) + '?text=' + encodeURIComponent(waText(p, sv));
// quem avisar: vence em até waDays() dias ou venceu há até 15 dias
const waDue = () => allRows().filter(({ p }) => p.exp && (() => { const d = Math.floor(rowInfo(p).d); return d <= waDays() && d > -15 })()).sort((a, b) => rowInfo(a.p).d - rowInfo(b.p).d);
function renderWa() {
  const due = waDue(), t = todayISO(), pend = due.filter(x => x.p.warned != t);
  const nb = $('.nav[data-tab="srv"]'); let bd = nb && nb.querySelector('.bdg'); if (nb && !bd) { bd = document.createElement('span'); nb.appendChild(bd) }
  if (bd) { bd.className = 'bdg y'; bd.textContent = pend.length || '' }
  if (pend.length && !renderWa.shown) { renderWa.shown = 1; toast(pend.length + ' cliente(s) para avisar no WhatsApp.') }
  return `<div class="card wa ${due.length ? '' : 'ok'}"><div class="gah"><svg class="i" viewBox="0 0 24 24" style="color:var(--gr)"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/><path d="M9 9.5c.5 2 2 3.6 4.5 4.5"/></svg>
    <h3>Avisos de vencimento</h3><span class="muted">${due.length ? `${pend.length} para avisar hoje · vence em até ${waDays()} dias ou já venceu` : 'Ninguém vencendo nos próximos ' + waDays() + ' dias.'}</span><span class="sp"></span><button class="bt sm" data-sa="wacfg">${SVG_ED}Mensagem</button></div>
    ${due.length ? `<div class="gai">${due.map(({ sv, p }) => { const i = rowInfo(p), done = p.warned == t;
      return `<div class="gm ${done ? '' : i.cls}"><div class="n"><b>${esc(p.name)}</b><small>${esc(sv.name)} · ${esc(i.lab)} · ${brl(netOf(p))}${p.warned ? ' · avisado ' + (done ? 'hoje' : fmtISO(p.warned)) : ''}</small></div>
        ${p.wa ? `<a class="bt sm ${done ? '' : 'wab'}" href="${esc(waLink(p, sv))}" target="_blank" rel="noopener" data-wa="${esc(sv.id)}|${esc(p.id)}">${done ? 'Enviar de novo' : 'Enviar WhatsApp'}</a>` : `<button class="bt sm" data-sa="pedit" data-s="${esc(sv.id)}" data-p="${esc(p.id)}">+ WhatsApp</button>`}</div>` }).join('')}</div>` : ''}</div>`;
}
const withSrvList = next => { let l; withSrv(next, x => l = x); return l };
function moveBox(p, sv, others) {
  const i = rowInfo(p), left = p.exp ? Math.max(0, Math.floor(i.d)) : null;
  return new Promise(res => modal(`<h3>Mudar ${esc(p.name)} de servidor</h3>
    <p class="muted">Hoje está em <b>${esc(sv.name)}</b>${p.exp ? `, vence ${fmtISO(p.exp)} (${esc(i.lab.toLowerCase())})` : ''}.</p>
    <label class="f">Vai para<select class="in" data-f="to">${others.map(x => `<option value="${esc(x.id)}">${esc(x.name)}</option>`).join('')}</select></label>
    <label class="f">Data da mudança<input class="in" type="date" data-f="d" value="${todayISO()}"></label>
    <label class="f" style="display:flex;align-items:flex-start;gap:8px;color:var(--tx)"><input type="radio" name="mk" value="1" checked style="margin-top:3px"><span>Manter o vencimento${left != null ? ` — os ${left} dia(s) que faltam passam para o servidor novo` : ''}</span></label>
    <label class="f" style="display:flex;align-items:flex-start;gap:8px;color:var(--tx)"><input type="radio" name="mk" value="0" style="margin-top:3px"><span>Começar do zero — novo período de ${p.months || 1} mês a partir da data da mudança</span></label>
    <p class="muted" id="mvP"></p>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Mudar</button></div>`, m => {
    const f = k => $(`[data-f="${k}"]`, m), keep = () => $('input[name="mk"]:checked', m).value == '1';
    const prev = () => { const e = keep() ? p.exp : addMonths(f('d').value || todayISO(), p.months || 1); $('#mvP', m).textContent = e ? 'No servidor novo vai vencer em ' + fmtISO(e) + '.' : '' };
    $$('input[name="mk"]', m).forEach(r => r.onchange = prev); f('d').oninput = prev; prev();
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { if (!f('d').value) return toast('Informe a data da mudança.', 1); closeModal(); res({ to: f('to').value, d: f('d').value, keep: keep() }) };
  }));
}
/* Informações de acesso: o texto que o painel do servidor gera ("Detalhes do Cliente"), colado como está. */
function infoBox(p, sv) {
  return new Promise(res => modal(`<h3>Informações de ${esc(p.name)}</h3>
    <p class="muted">Cole aqui os detalhes que o painel ${esc(sv.name)} mostra ao criar ou renovar o cliente (usuário, senha, apps, links M3U…).${p.infoAt ? ' Atualizado em ' + fmtDT(p.infoAt) + '.' : ''}</p>
    <textarea class="in mono" data-f="t" rows="14" maxlength="${INFO_MAX}" placeholder="Cole aqui o texto do painel…">${esc(p.info || '')}</textarea>
    <div class="row" style="justify-content:space-between;align-items:center"><span class="muted" id="ifN"></span></div>
    <div class="acts"><button class="bt" data-cp>Copiar</button><button class="bt wab" data-wa>${SVG_WA}Enviar no WhatsApp</button><span style="flex:1"></span><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    const t = $('[data-f="t"]', m), n = () => $('#ifN', m).textContent = t.value.length + ' de ' + INFO_MAX + ' letras'; t.oninput = n; n();
    $('[data-cp]', m).onclick = async () => { try { await navigator.clipboard.writeText(t.value); toast('Copiado.') } catch (e) { t.select(); document.execCommand('copy'); toast('Copiado.') } };
    $('[data-wa]', m).onclick = async () => { const v = t.value.trim(); closeModal(); const o = await waSendBox(p, sv, 'info', v); res(v != (p.info || '') ? v : null); if (o && o.wa != (p.wa || '')) toast('Abra o lápis do cliente para salvar o número.') };
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { closeModal(); res(t.value.trim()) };
    t.focus();
  }));
}
/* Enviar WhatsApp: escolhe a mensagem (vencimento, informações de acesso ou livre), edita e abre o WhatsApp. */
function waSendBox(p, sv, kind, infoNow) {
  const texts = { venc: waText(p, sv), info: infoNow != null ? infoNow : (p.info || ''), livre: '' };
  let k = kind || 'venc';
  return new Promise(res => modal(`<h3>WhatsApp de ${esc(p.name)}</h3>
    <label class="f">Número do cliente<input class="in" data-f="n" inputmode="tel" maxlength="20" value="${esc(p.wa || '')}" placeholder="(11) 98765-4321"></label>
    <div class="seg" id="wsK"><button data-k="venc">Aviso de vencimento</button><button data-k="info">Informações de acesso</button><button data-k="livre">Mensagem livre</button></div>
    <textarea class="in" data-f="m" rows="9" maxlength="${INFO_MAX}"></textarea>
    <p class="muted" id="wsH"></p>
    <div class="acts"><button class="bt" data-no>Cancelar</button><a class="bt wab" data-wgo target="_blank" rel="noopener">${SVG_WA}Abrir WhatsApp</a></div>`, m => {
    const num = $('[data-f="n"]', m), msg = $('[data-f="m"]', m), go = $('[data-wgo]', m);
    const upd = () => { const d = waNum(num.value); go.href = d.length >= 12 ? 'https://wa.me/' + d + '?text=' + encodeURIComponent(msg.value) : '#'; go.classList.toggle('off', d.length < 12) };
    const pick = x => { texts[k] = msg.value; k = x; msg.value = texts[k]; $$('#wsK button', m).forEach(b => b.classList.toggle('on', b.dataset.k == k));
      $('#wsH', m).textContent = k == 'info' && !texts.info ? 'Este cliente ainda não tem informações salvas. Cole o texto no botão de documento da linha dele.' : k == 'venc' ? 'Muda o texto padrão em "Mensagem", no quadro Avisos de vencimento.' : ''; upd() };
    $$('#wsK button', m).forEach(b => b.onclick = () => pick(b.dataset.k)); msg.oninput = upd; num.oninput = upd;
    msg.value = texts[k]; pick(k);
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    go.onclick = e => { if (go.classList.contains('off')) { e.preventDefault(); return toast('Coloque o número com DDD.', 1) } if (!msg.value.trim()) { e.preventDefault(); return toast('A mensagem está vazia.', 1) } setTimeout(() => { closeModal(); res({ wa: num.value.trim(), warn: k == 'venc' }) }, 50) };
    msg.focus();
  }));
}
function waBox() {
  return new Promise(res => modal(`<h3>Mensagem de aviso</h3><p class="muted">Use: {nome} (primeiro nome), {nomecompleto}, {quando} (ex.: "em 3 dias"), {vence} (data), {dias}, {valor} (já com desconto), {servidor}.</p>
    <label class="f">Mensagem<textarea class="in" data-f="m" rows="5" maxlength="600">${esc(waMsgT())}</textarea></label>
    <label class="f">Avisar quantos dias antes<input class="in" data-f="d" type="number" min="0" max="30" value="${waDays()}"></label>
    <div class="acts"><button class="bt" data-r>Voltar ao padrão</button><span class="sp" style="flex:1"></span><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    $('[data-r]', m).onclick = () => $('[data-f="m"]', m).value = WA_DEF;
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { closeModal(); res({ m: $('[data-f="m"]', m).value.trim() || WA_DEF, d: Math.max(0, Math.min(30, parseInt($('[data-f="d"]', m).value) || 0)) }) };
  }));
}
function renderSrv() {
  if (!GLOADED) return;
  const SV = srvList(), q = SQ.toLowerCase();
  $('#sWa').innerHTML = renderWa();
  let nCli = 0, tCob = 0, tPag = 0, nExp = 0, nSoon = 0, tDes = 0, tRec = 0;
  SV.forEach(sv => (sv.plans || []).forEach(p => { const i = rowInfo(p); nCli++; tCob += netOf(p); tDes += discOf(p); tPag += p.cost || 0; if (!p.paid) tRec += netOf(p); if (i.k == 'exp') nExp++; if (i.k == 'soon') nSoon++ }));
  const cards = SV.map(sv => {
    const all = (sv.plans || []).map(p => ({ p, i: rowInfo(p) }));
    const rows = all.filter(({ p, i }) => (SF == 'all' || i.k == SF || (SF == 'rcv' && !p.paid)) && (!q || (p.name + ' ' + (p.note || '')).toLowerCase().includes(q))).sort((a, b) => a.i.d - b.i.d);
    if ((q || SF != 'all') && !rows.length) return '';
    const sum = f => all.reduce((n, x) => n + (f(x.p) || 0), 0), sc = sum(p => p.price), sp = sum(p => p.cost), sd = sum(discOf), sn = sum(netOf), sr = sum(p => p.paid ? 0 : netOf(p));
    const ex = all.filter(x => x.i.k == 'exp').length;
    // com o valor dos painéis digitado (tudo junto), "Você paga" e "Sobra" por cliente somem da tabela
    const pc = panelCost() == null, C = (h) => pc ? h : '';
    const table = rows.length ? `<div class="twrap"><table class="ktab rt"><thead><tr><th>Cliente</th><th>Lançado</th><th>Vence</th><th>Situação</th>${C('<th class="r">Você paga</th>')}<th class="r">Cobra</th><th class="r">Desconto</th><th class="r">Recebe</th>${C('<th class="r">Sobra</th>')}<th>Pagamento</th><th>Comentário</th><th></th></tr></thead><tbody>${rows.map(({ p, i }) => {
      const ds = discOf(p), nt = netOf(p), mg = nt - (p.cost || 0), rf = p.ref && rowById(p.ref), ni = refsOf(p.id).length;
      const mv = (p.moves || []).slice(-1)[0], sub = [p.months > 1 ? p.months + ' meses' : '', mv ? 'veio de ' + mv.fromName + ' em ' + fmtS(mv.d) : '', rf ? 'indicado por ' + rf.p.name : '', ni ? 'indicou ' + ni : ''].filter(Boolean).join(' · ');
      return `<tr><td class="nm"><b style="font-weight:500">${esc(p.name)}</b>${sub ? `<small class="muted" style="display:block">${esc(sub)}</small>` : ''}</td><td data-l="Lançado">${fmtS(p.start)}</td><td data-l="Vence">${fmtS(p.exp)}</td><td data-l="Situação"><span class="bdg ${i.cls}">${esc(i.lab)}</span></td>
        ${C(`<td class="r" data-l="Você paga">${brl(p.cost)}</td>`)}<td class="r" data-l="Cobra">${brl(p.price)}</td><td class="r" data-l="Desconto" title="${ni ? 'inclui ' + brl(refDiscOf(p)) + ' por indicação' : ''}">${ds ? '−' + brl(ds) : '—'}</td><td class="r" data-l="Recebe"><b>${brl(nt)}</b></td>${C(`<td class="r ${mg < 0 ? 'neg' : ''}" data-l="Sobra">${brl(mg)}</td>`)}
        <td data-l="Pagamento"><button class="bdg ${p.paid ? 'g' : 'y'}" data-sa="ppay" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="Clique para trocar">${p.paid ? 'Recebido' : 'A receber'}</button></td>
        <td class="cmt" data-l="Comentário" title="${esc(p.note || '')}">${p.note ? esc(p.note) : '<span class="muted">—</span>'}</td>
        <td class="r ac" style="white-space:nowrap"><button class="ia ${p.wa ? 'wai' : ''}" data-sa="pwa" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="${p.wa ? 'WhatsApp ' + esc(p.wa) : 'WhatsApp (sem número)'}">${SVG_WA}</button><button class="ia ${p.info ? 'inf' : ''}" data-sa="pinfo" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="${p.info ? 'Informações de acesso' : 'Colar informações de acesso'}">${SVG_INFO}</button><button class="ia" data-sa="pren" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="Renovar (+${p.months || 1} mês)">${svgI('renew')}</button><button class="ia" data-sa="pmove" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="Mudar de servidor">${SVG_MV}</button><button class="ia" data-sa="pedit" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="Editar">${SVG_ED}</button><button class="ia" data-sa="pdel" data-s="${esc(sv.id)}" data-p="${esc(p.id)}" title="Apagar">${SVG_DEL}</button></td></tr>`;
    }).join('')}</tbody><tfoot><tr><td colspan="4" class="nm"><b>Total · ${all.length} cliente${all.length == 1 ? '' : 's'}</b>${ex ? ` <span class="bdg r">${ex} vencido${ex > 1 ? 's' : ''}</span>` : ''}</td>${C(`<td class="r" data-l="Você paga"><b>${brl(sp)}</b></td>`)}<td class="r" data-l="Cobra"><b>${brl(sc)}</b></td><td class="r" data-l="Desconto"><b>${sd ? '−' + brl(sd) : '—'}</b></td><td class="r" data-l="Recebe"><b>${brl(sn)}</b></td>${C(`<td class="r ${sn - sp < 0 ? 'neg' : ''}" data-l="Sobra"><b>${brl(sn - sp)}</b></td>`)}<td colspan="3" data-l="A receber">${sr ? `<span class="muted">a receber</span> <b>${brl(sr)}</b>` : ''}</td></tr></tfoot></table></div>`
      : '<p class="muted" style="padding:10px 0">Nenhum cliente neste servidor. Use <b>+ Cliente</b>.</p>';
    return `<div class="sv"><div class="svh"><h3>${esc(sv.name)}</h3>${sv.url ? `<a class="u" href="${esc(sv.url)}" target="_blank" rel="noopener">${esc(hostOf(sv.url))}</a>` : ''}<span class="bdg n">${all.length} cliente${all.length == 1 ? '' : 's'}</span><span class="sp"></span>
        ${sv.url ? `<a class="bt sm" href="${esc(sv.url)}" target="_blank" rel="noopener">Abrir painel</a>` : ''}<button class="bt sm pk" data-sa="padd" data-s="${esc(sv.id)}">+ Cliente</button><button class="bt sm" data-sa="edit" data-s="${esc(sv.id)}">${SVG_ED}Editar</button><button class="bt sm rd" data-sa="del" data-s="${esc(sv.id)}" title="Apagar servidor">${SVG_DEL}</button></div>
      ${sv.note ? `<div class="svc">${esc(sv.note)}</div>` : ''}
      <div class="svb">${table}</div></div>`;
  }).join('');
  $('#sStats').innerHTML = `<div class="stats">${[['', nCli, 'clientes em ' + SV.length + (SV.length == 1 ? ' servidor' : ' servidores')], ['gr', brl(tCob), 'total a cobrar (já com descontos)'], ['', brl(tDes), 'descontos'], ['pc', brl(panelCost() ?? tPag), panelCost() != null ? 'pago nos painéis por mês (digitado)' : 'pago nos painéis (clique para digitar)'], [tCob - (panelCost() ?? tPag) < 0 ? 'rd' : 'pk', brl(tCob - (panelCost() ?? tPag)), 'sobra'], [tRec ? 'yl' : '', brl(tRec), 'a receber'], [nExp ? 'rd' : '', nExp, 'vencidos'], [nSoon ? 'yl' : '', nSoon, 'vencem em ' + ALERT_DAYS + ' dias']].map(x => x[0] == 'pc' ? `<button class="st stb" data-sa="pcost" title="Digitar o valor pago nos painéis"><b>${x[1]}</b><small>${x[2]} ${SVG_ED}</small></button>` : `<div class="st ${x[0]}"><b>${x[1]}</b><small>${x[2]}</small></div>`).join('')}</div>
    <div class="bar"><input class="in" id="sQ" placeholder="Buscar cliente ou comentário…" value="${esc(SQ)}">${[['all', 'Todos'], ['exp', 'Vencidos'], ['soon', 'Vencendo'], ['ok', 'Em dia'], ['none', 'Sem data'], ['rcv', 'A receber']].map(([k, l]) => `<button class="ch ${SF == k ? 'on' : ''}" data-sf="${k}">${l}</button>`).join('')}<button class="bt" id="sCsv">Exportar CSV</button><button class="bt" id="sBk" title="Baixa um arquivo com os servidores e clientes (backup ou para abrir no outro site)">Salvar cópia</button></div>`;
  $('#sBody').innerHTML = cards || `<div class="empty card">${q || SF != 'all' ? 'Nenhum cliente encontrado.' : 'Nenhum servidor. Cadastre em <b>Novo servidor</b>.'}</div>`;
  const qi = $('#sQ'); qi.oninput = () => { SQ = qi.value; const pos = qi.selectionStart; renderSrv(); const n = $('#sQ'); n.focus(); n.setSelectionRange(pos, pos) };
  $$('[data-sf]').forEach(b => b.onclick = () => { SF = b.dataset.sf; renderSrv() });
  $('#sCsv').onclick = srvCsv; $('#sBk').onclick = backupSave;
}
function srvCsv() {
  const q = v => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"', num = v => v == null ? '' : String(v).replace('.', ',');
  const lines = [['servidor', 'cliente', 'meses', 'lancado', 'vence', 'situacao', 'voce_paga', 'cobra', 'desconto', 'recebe', 'pagamento', 'indicado_por', 'comentario'].join(';')];
  srvList().forEach(sv => (sv.plans || []).forEach(p => lines.push([sv.name, p.name, p.months || 1, fmtISO(p.start), fmtISO(p.exp), rowInfo(p).lab, num(p.cost), num(p.price), num(discOf(p)), num(netOf(p)), p.paid ? 'recebido' : 'a receber', p.ref && rowById(p.ref) ? rowById(p.ref).p.name : '', p.note || ''].map(q).join(';'))));
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
  a.download = 'ntplay-servidores-' + todayISO() + '.csv'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

function srvBox(sv) {
  return new Promise(res => modal(`<h3>${sv ? 'Editar servidor' : 'Novo servidor'}</h3>
    <label class="f">Nome<input class="in" data-f="name" maxlength="40" value="${esc(sv ? sv.name : '')}" placeholder="ex.: Playdragon"></label>
    <label class="f">Endereço do painel<input class="in" data-f="url" maxlength="200" value="${esc(sv ? sv.url : '')}" placeholder="https://…"></label>
    <label class="f">Comentário<textarea class="in" data-f="note" rows="3" maxlength="400" placeholder="Contato do revendedor, qualidade, regras de crédito…">${esc(sv ? sv.note || '' : '')}</textarea></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => {
      const name = $('[data-f="name"]', m).value.trim(); let url = $('[data-f="url"]', m).value.trim();
      if (!name) return toast('Dê um nome ao servidor.', 1);
      if (url && !/^https?:\/\//i.test(url)) url = 'https://' + url;
      if (url && !/^https?:\/\/[^\s/]+\.[^\s]+$/i.test(url)) return toast('Endereço inválido.', 1);
      closeModal(); res({ name, url, note: $('[data-f="note"]', m).value.trim() });
    };
    $('[data-f="name"]', m).focus();
  }));
}
function planBox(p) {
  const v = x => x == null ? '' : String(x).replace('.', ',');
  const st = p && p.start || todayISO(), mo = p && p.months || 1, ex = p ? p.exp || '' : addMonths(st, mo);
  return new Promise(res => modal(`<h3>${p ? 'Editar cliente' : 'Novo cliente no servidor'}</h3>
    <div class="row"><label class="f">Nome do cliente<input class="in" data-f="name" maxlength="40" value="${esc(p ? p.name : '')}" placeholder="ex.: Rita de Cássia"></label>
      <label class="f" style="flex:0 1 100px">Meses<input class="in" data-f="months" type="number" min="1" max="36" value="${esc(mo)}"></label></div>
    <div class="row"><label class="f">Lançado em<input class="in" data-f="start" type="date" value="${esc(st)}"></label>
      <label class="f">Vence em<input class="in" data-f="exp" type="date" value="${esc(ex)}"></label></div>
    <div class="row"><label class="f">Você paga no painel (R$)<input class="in" data-f="cost" inputmode="decimal" value="${esc(v(p && p.cost))}" placeholder="ex.: 12,00"></label>
      <label class="f">Cobra do cliente (R$)<input class="in" data-f="price" inputmode="decimal" value="${esc(v(p && p.price))}" placeholder="ex.: 35,00"></label></div>
    <div class="row"><label class="f">Desconto fixo (R$)<input class="in" data-f="disc" inputmode="decimal" value="${esc(v(p && p.disc))}" placeholder="opcional"></label>
      <label class="f">Indicado por<select class="in" data-f="ref"><option value="">Ninguém</option>${allRows().filter(x => !p || x.p.id != p.id).sort((a, b) => a.p.name.localeCompare(b.p.name)).map(x => `<option value="${esc(x.p.id)}" ${p && p.ref == x.p.id ? 'selected' : ''}>${esc(x.p.name)} (${esc(x.sv.name)})</option>`).join('')}</select></label></div>
    <label class="f" style="display:flex;align-items:center;gap:8px;color:var(--tx)"><input type="checkbox" data-f="paid" ${p && p.paid ? 'checked' : ''}>Já recebi o pagamento deste ciclo</label>
    <label class="f">WhatsApp do cliente<input class="in" data-f="wa" inputmode="tel" maxlength="20" value="${esc(p ? p.wa || '' : '')}" placeholder="ex.: (11) 98765-4321"></label>
    <label class="f">Comentário<textarea class="in" data-f="note" rows="2" maxlength="300" placeholder="Forma de pagamento, aparelho…">${esc(p ? p.note || '' : '')}</textarea></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    const f = k => $(`[data-f="${k}"]`, m); let touched = !!(p && p.exp);
    f('exp').oninput = () => touched = true;
    const auto = () => { if (!touched && f('start').value) f('exp').value = addMonths(f('start').value, parseInt(f('months').value) || 1) };
    f('start').oninput = auto; f('months').oninput = auto;
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => {
      const g = k => f(k).value.trim(), name = g('name'), months = Math.max(1, Math.min(36, parseInt(g('months')) || 1));
      if (!name) return toast('Informe o nome do cliente.', 1);
      const cost = g('cost') ? money(g('cost')) : null, price = g('price') ? money(g('price')) : null, disc = g('disc') ? money(g('disc')) : null;
      if ((g('cost') && cost == null) || (g('price') && price == null) || (g('disc') && disc == null)) return toast('Valor inválido. Use, por exemplo, 35 ou 35,90.', 1);
      if (g('wa') && waNum(g('wa')).length < 12) return toast('WhatsApp incompleto. Coloque DDD + número.', 1);
      if (g('start') && g('exp') && g('exp') < g('start')) return toast('O vencimento está antes do lançamento.', 1);
      closeModal(); res({ name, months, start: g('start'), exp: g('exp'), cost, price, disc, ref: f('ref').value, wa: g('wa'), paid: f('paid').checked, note: g('note') });
    };
    f('name').focus();
  }));
}

$('#sRef').onclick = () => { loadUsers(); loadGroups() };
$('#sNew').onclick = async () => {
  const o = await srvBox(); if (!o) return;
  const id = (o.name.toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '').slice(0, 20) || 'srv') + '-' + gid().slice(0, 4);
  saveGroups(withSrv(clone(GROUPS), l => l.push({ id, ...o, plans: [] })), 'Servidor adicionado.');
};
$('#t-srv').onclick = async e => {
  const w = e.target.closest('[data-wa]');
  if (w) { const [sid, pid] = w.dataset.wa.split('|'), t = todayISO(); // deixa o link abrir o WhatsApp e marca como avisado
    const r = rowById(pid); if (r && r.p.warned != t) setTimeout(() => saveRec(rc => { const sv = rc.servers.find(x => x.id == sid), p = sv && (sv.plans || []).find(x => x.id == pid); if (p) p.warned = t }, 'Marcado como avisado hoje.'), 300); return }
  const b = e.target.closest('[data-sa]'); if (!b) return;
  if (b.dataset.sa == 'wacfg') { const o = await waBox(); if (!o) return; return saveRec(r => { r.cfg.waMsg = o.m == WA_DEF ? '' : o.m; r.cfg.waDays = o.d }, 'Mensagem salva.') }
  if (b.dataset.sa == 'pcost') {
    const cur = panelCost(), o = await askBox('Pago nos painéis', 'Quanto você paga por mês nos painéis, somando todos. Deixe vazio para voltar a somar o "Você paga" de cada cliente.', [{ k: 'v', label: 'Valor por mês (R$)', v: cur != null ? String(cur).replace('.', ',') : '' }], 'Salvar');
    if (!o) return; const t = o.v.trim(), n = t ? money(t) : null; if (t && n == null) return toast('Valor inválido. Use, por exemplo, 150 ou 150,00.', 1);
    return saveRec(r => r.cfg.panelCost = n, n == null ? 'Voltou a somar o valor de cada cliente.' : 'Valor dos painéis salvo.');
  }
  const act = b.dataset.sa, next = clone(GROUPS); let sv;
  withSrv(next, l => { sv = l.find(x => x.id == b.dataset.s) }); if (!sv) return;
  sv.plans = sv.plans || [];
  if (act == 'edit') { const o = await srvBox(sv); if (!o) return; Object.assign(sv, o); return saveGroups(next, 'Servidor salvo.') }
  if (act == 'del') {
    const n = srvMembers(sv.id).length;
    if (!await confirmBox('Apagar servidor', `"${sv.name}" e os planos dele serão apagados.${n ? ' ' + n + ' cliente(s) ligado(s) a ele ficam marcados como "servidor removido".' : ''}`, 'Apagar', 1)) return;
    return saveGroups(withSrv(next, l => l.splice(l.indexOf(sv), 1)), 'Servidor apagado.');
  }
  if (act == 'padd') { const o = await planBox(); if (!o) return; sv.plans.push({ id: gid(), ...o }); return saveGroups(next, 'Cliente adicionado.') }
  const p = sv.plans.find(x => x.id == b.dataset.p); if (!p) return;
  if (act == 'pedit') { const o = await planBox(p); if (!o) return; Object.assign(p, o); return saveGroups(next, 'Cliente salvo.') }
  if (act == 'pren') {
    const base = p.exp && p.exp > todayISO() ? p.exp : todayISO(), nx = addMonths(base, p.months || 1);
    if (!await confirmBox('Renovar ' + p.name, `Vencimento passa de ${fmtISO(p.exp)} para ${fmtISO(nx)} (+${p.months || 1} mês).`, 'Renovar')) return;
    p.exp = nx; p.renewed = todayISO(); p.paid = false; delete p.warned; return saveGroups(next, 'Renovado até ' + fmtISO(nx) + '. Marcado como a receber.');
  }
  if (act == 'pmove') {
    const others = withSrvList(next).filter(x => x.id != sv.id); if (!others.length) return toast('Cadastre outro servidor primeiro.', 1);
    const o = await moveBox(p, sv, others); if (!o) return;
    const dest = others.find(x => x.id == o.to); dest.plans = dest.plans || [];
    sv.plans = sv.plans.filter(x => x != p);
    p.moves = (p.moves || []).concat({ d: o.d, from: sv.id, fromName: sv.name, to: dest.id, keep: o.keep });
    if (!o.keep) { p.start = o.d; p.exp = addMonths(o.d, p.months || 1) }
    dest.plans.push(p);
    return saveGroups(next, p.name + ' foi para ' + dest.name + (p.exp ? ', vence ' + fmtISO(p.exp) : '') + '.');
  }
  if (act == 'pinfo') { const o = await infoBox(p, sv); if (o == null) return; p.info = o; p.infoAt = Date.now(); return saveGroups(next, o ? 'Informações salvas.' : 'Informações apagadas.') }
  if (act == 'pwa') { const o = await waSendBox(p, sv); if (!o) return; if (o.wa != (p.wa || '') || o.warn) { p.wa = o.wa; if (o.warn) p.warned = todayISO(); return saveGroups(next, o.warn ? 'Marcado como avisado hoje.' : 'WhatsApp salvo.') } return }
  if (act == 'ppay') { p.paid = !p.paid; if (p.paid) p.paidAt = todayISO(); return saveGroups(next, p.paid ? p.name + ': recebido.' : p.name + ': a receber.') }
  if (act == 'pdel') { if (!await confirmBox('Apagar cliente', `"${p.name}" será tirado de ${sv.name}.`, 'Apagar', 1)) return; sv.plans = sv.plans.filter(x => x != p); return saveGroups(next, 'Cliente apagado.') }
};

/* ================= indicações, financeiro e comentários =================
   Usam o mesmo registro dos servidores (REC): cfg.refDisc, expenses e notes. */
const MES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const mKey = iso => iso.slice(0, 7);
const mLab = k => MES[+k.slice(5, 7) - 1] + '/' + k.slice(2, 4);
function monthKeys(from, n) { const out = []; let d = from.slice(0, 7) + '-01'; for (let i = 0; i < n; i++) { out.push(mKey(d)); d = addMonths(d, 1) } return out }
function saveRec(fn, msg) { return saveGroups(withRec(clone(GROUPS), fn), msg) }

// --- indicações ---
function renderInd() {
  if (!GLOADED || !$('#iBody')) return;
  const rows = allRows(), refd = rows.filter(x => x.p.ref && rowById(x.p.ref)), v = refDisc();
  const by = new Map(); refd.forEach(x => { const k = x.p.ref; if (!by.has(k)) by.set(k, []); by.get(k).push(x) });
  const ind = [...by.entries()].map(([id, l]) => ({ r: rowById(id), l, on: l.filter(x => rowOn(x.p)).length })).sort((a, b) => b.l.length - a.l.length);
  const tot = ind.reduce((n, x) => n + x.on * v, 0);
  $('#iBody').innerHTML = `<div class="stats">${[['', refd.length, 'indicações'], ['', ind.length, 'clientes que indicaram'], ['gr', refd.filter(x => rowOn(x.p)).length, 'indicados em dia'], ['yl', brl(tot), 'desconto dado por mês']].map(x => `<div class="st ${x[0]}"><b>${x[1]}</b><small>${x[2]}</small></div>`).join('')}</div>
  <div class="grid g2" style="align-items:start">
    <div class="card"><h3>Desconto por indicação</h3><p class="hint">Quem indicou ganha este desconto em cada renovação, para cada indicado que está com o plano em dia. Se o indicado vencer, o desconto para.</p>
      <div class="row" style="align-items:center"><input class="in" id="iVal" inputmode="decimal" value="${v ? String(v).replace('.', ',') : ''}" placeholder="ex.: 5,00" style="flex:1 1 120px"><button class="bt pk" id="iSave" style="flex:0 0 auto">Salvar</button></div>
      <p class="muted" style="margin-top:10px">Para marcar quem indicou, edite o cliente na aba Servidores (campo <b>Indicado por</b>).</p></div>
    <div class="card"><h3>Quem mais indicou</h3><p class="hint">Indicações por cliente.</p>${ind.length ? hbars(ind.slice(0, 10).map(x => [x.r.p.name, x.l.length])) : '<p class="muted">Nenhuma indicação ainda.</p>'}</div>
  </div>
  <div class="card" style="margin-top:12px"><h3>Indicadores</h3><p class="hint">Desconto que cada um recebe por mês com o valor atual.</p>
    ${ind.length ? `<div class="twrap"><table class="ktab"><thead><tr><th>Cliente</th><th>Servidor</th><th>Indicou</th><th class="r">Em dia</th><th class="r">Desconto</th><th class="r">Cobra</th><th class="r">Recebe</th></tr></thead><tbody>${ind.map(x => `<tr><td><b style="font-weight:500">${esc(x.r.p.name)}</b></td><td>${esc(x.r.sv.name)}</td><td class="muted">${x.l.map(y => esc(y.p.name)).join(', ')}</td><td class="r">${x.on}</td><td class="r">${brl(x.on * v)}</td><td class="r">${brl(x.r.p.price)}</td><td class="r"><b>${brl(netOf(x.r.p))}</b></td></tr>`).join('')}</tbody></table></div>` : '<p class="muted">Ninguém indicou ainda.</p>'}</div>
  <div class="card" style="margin-top:12px"><h3>Todas as indicações</h3>
    ${refd.length ? `<div class="twrap"><table class="ktab"><thead><tr><th>Indicado</th><th>Servidor</th><th>Indicado por</th><th>Desde</th><th>Situação</th></tr></thead><tbody>${refd.sort((a, b) => (b.p.start || '').localeCompare(a.p.start || '')).map(x => { const i = rowInfo(x.p); return `<tr><td>${esc(x.p.name)}</td><td>${esc(x.sv.name)}</td><td>${esc(rowById(x.p.ref).p.name)}</td><td>${fmtISO(x.p.start)}</td><td><span class="bdg ${i.cls}">${esc(i.lab)}</span></td></tr>` }).join('')}</tbody></table></div>` : '<p class="muted">Nenhuma.</p>'}</div>`;
  $('#iSave').onclick = () => { const t = $('#iVal').value.trim(), n = t ? money(t) : 0; if (n == null) return toast('Valor inválido.', 1); saveRec(r => r.cfg.refDisc = n, 'Desconto por indicação salvo.') };
}

// --- gráficos simples em SVG ---
function hbars(rows, fmt = x => x, color = '#5c9bd1') {
  const max = Math.max(1, ...rows.map(r => r[1]));
  return `<div class="hb">${rows.map(r => `<div class="hbr"><span title="${esc(r[0])}">${esc(r[0])}</span><div><i style="width:${(r[1] / max * 100).toFixed(1)}%;background:${r[2] || color}"></i></div><b>${esc(fmt(r[1]))}</b></div>`).join('')}</div>`;
}
function gbars(keys, series) {
  const W = 760, H = 250, pl = 58, pr = 10, pt = 14, pb = 28, iw = W - pl - pr, ih = H - pt - pb;
  const max = Math.max(1, ...series.flatMap(s => keys.map(k => s.v[k] || 0)));
  const y = v => pt + ih - v / max * ih, gw = iw / keys.length, bw = Math.min(22, (gw - 12) / series.length);
  const ticks = [0, max / 2, max];
  return `<svg viewBox="0 0 ${W} ${H}" class="gch2" role="img" aria-label="Valores por mês">
    ${ticks.map(t => `<line x1="${pl}" x2="${W - pr}" y1="${y(t)}" y2="${y(t)}" stroke="#ffffff12"/><text x="${pl - 6}" y="${y(t) + 4}" text-anchor="end" fill="var(--mut)" font-size="11">${brl(t).replace(',00', '')}</text>`).join('')}
    ${keys.map((k, i) => { const x0 = pl + i * gw + (gw - bw * series.length) / 2;
      return series.map((s, j) => { const v = s.v[k] || 0; return v ? `<rect x="${(x0 + j * bw).toFixed(1)}" y="${y(v).toFixed(1)}" width="${(bw - 2).toFixed(1)}" height="${(y(0) - y(v)).toFixed(1)}" rx="1.5" fill="${s.c}"><title>${mLab(k)} · ${s.n}: ${brl(v)}</title></rect>` : '' }).join('') + `<text x="${(pl + i * gw + gw / 2).toFixed(1)}" y="${H - 9}" text-anchor="middle" fill="var(--mut)" font-size="11">${mLab(k)}</text>` }).join('')}
  </svg><div class="lgd">${series.map(s => `<span><i style="background:${s.c}"></i>${esc(s.n)}</span>`).join('')}</div>`;
}
function donutSvg(parts, center, sub) {
  const R = 70, C = 2 * Math.PI * R, tot = parts.reduce((n, p) => n + p[1], 0) || 1; let off = 0;
  const arcs = parts.filter(p => p[1] > 0).map(p => { const len = p[1] / tot * C, a = `<circle cx="90" cy="90" r="${R}" fill="none" stroke="${p[2]}" stroke-width="16" stroke-dasharray="${Math.max(0, len - 2)} ${C}" stroke-dashoffset="${-off}" transform="rotate(-90 90 90)"><title>${esc(p[0])}: ${brl(p[1])}</title></circle>`; off += len; return a }).join('');
  return `<div class="donut"><svg viewBox="0 0 180 180" style="width:min(100%,180px)"><circle cx="90" cy="90" r="${R}" fill="none" stroke="#ffffff0d" stroke-width="16"/>${arcs}<text x="90" y="92" text-anchor="middle" fill="var(--tx)" font-size="17" font-weight="600">${esc(center)}</text><text x="90" y="110" text-anchor="middle" fill="var(--mut)" font-size="11">${esc(sub)}</text></svg>
    <div class="lg">${parts.map(p => `<span><i style="background:${p[2]}"></i>${esc(p[0])} <b>${brl(p[1])}</b></span>`).join('')}</div></div>`;
}

// --- financeiro ---
let FEXP = false;
const CATS = ['Crédito de painel', 'Internet', 'Anúncio', 'Equipamento', 'Outros'];
function finData(keys) {
  const z = () => Object.fromEntries(keys.map(k => [k, 0])), rec = z(), cost = z(), disc = z(), exp = z(), first = keys[0], last = keys[keys.length - 1];
  allRows().forEach(({ p }) => {
    const nt = netOf(p), mo = Math.max(1, +p.months || 1);
    if (!p.exp) { keys.forEach(k => { rec[k] += nt / mo; cost[k] += (p.cost || 0) / mo; disc[k] += discOf(p) / mo }); return }
    // "paid" vale para o ciclo atual (lançado até o vencimento). Não pago = cobrar já (mês atual).
    // Vencido: a renovação é esperada agora (uma vez). Em dia: renovações a partir do vencimento.
    if (p.exp < todayISO()) { rec[first] += nt; cost[first] += p.cost || 0; disc[first] += discOf(p); return }
    if (!p.paid) rec[first] += nt;
    for (let d = p.exp, g = 0; mKey(d) <= last && g < 60; g++, d = addMonths(d, mo)) {
      const k = mKey(d); if (!(k in rec)) continue;
      rec[k] += nt; cost[k] += p.cost || 0; disc[k] += discOf(p);
    }
  });
  (REC().expenses || []).forEach(e => { const k = mKey(e.date || ''); if (k in exp) exp[k] += e.value || 0 });
  const pc = panelCost(); if (pc != null) keys.forEach(k => cost[k] = pc);
  return { rec, cost, disc, exp };
}
function renderFin() {
  if (!GLOADED || !$('#fBody')) return;
  const keys = monthKeys(todayISO(), 6), cur = keys[0], D = finData(keys), rows = allRows(), E = REC().expenses || [];
  const late = rows.filter(x => !x.p.paid && x.p.exp && x.p.exp < todayISO()), paidNow = rows.filter(x => x.p.paid).reduce((n, x) => n + netOf(x.p), 0);
  const rcvAll = rows.filter(x => !x.p.paid).reduce((n, x) => n + netOf(x.p), 0), lateV = late.reduce((n, x) => n + netOf(x.p), 0);
  const res = D.rec[cur] - D.cost[cur] - D.exp[cur];
  // despesas dos últimos 6 meses (o que já foi gasto)
  const back = monthKeys(addMonths(todayISO().slice(0, 7) + '-01', -5), 6), eb = Object.fromEntries(back.map(k => [k, 0]));
  E.forEach(e => { const k = mKey(e.date || ''); if (k in eb) eb[k] += e.value || 0 });
  const bySrv = srvList().map(sv => [sv.name, (sv.plans || []).reduce((n, p) => n + (p.paid ? 0 : netOf(p)), 0)]).filter(x => x[1] > 0).sort((a, b) => b[1] - a[1]);
  const sorted = [...E].sort((a, b) => (b.date || '').localeCompare(a.date || '')), shown = FEXP ? sorted : sorted.slice(0, 12);
  $('#fBody').innerHTML = `<div class="stats">${[['yl', brl(rcvAll), 'a receber (todos)'], [lateV ? 'rd' : '', brl(lateV), late.length + ' atrasado(s)'], ['gr', brl(paidNow), 'já recebido (ciclo atual)'], ['', brl(D.cost[cur]), 'custo dos painéis em ' + mLab(cur)], ['', brl(D.exp[cur]), 'despesas em ' + mLab(cur)], ['', brl(D.disc[cur]), 'descontos em ' + mLab(cur)], [res < 0 ? 'rd' : 'pk', brl(res), 'resultado previsto em ' + mLab(cur)]].map(x => `<div class="st ${x[0]}"><b>${x[1]}</b><small>${x[2]}</small></div>`).join('')}</div>
  <div class="card"><h3>Próximos 6 meses</h3><p class="hint">Pelo vencimento de cada cliente: quanto entra, quanto vai para os painéis, despesas lançadas e descontos dados. Pagamentos atrasados entram no mês atual.</p>
    ${gbars(keys, [{ n: 'A receber', c: '#3daa78', v: D.rec }, { n: 'Custo dos painéis', c: '#5c9bd1', v: D.cost }, { n: 'Despesas', c: '#d55a55', v: D.exp }, { n: 'Descontos', c: '#d39b32', v: D.disc }])}</div>
  <div class="grid g2" style="margin-top:12px;align-items:start">
    <div class="card"><h3>Para onde vai o dinheiro em ${mLab(cur)}</h3><p class="hint">Do valor cheio cobrado no mês.</p>${donutSvg([['Custo dos painéis', D.cost[cur], '#5c9bd1'], ['Despesas', D.exp[cur], '#d55a55'], ['Descontos', D.disc[cur], '#d39b32'], ['Sobra', Math.max(0, res), '#3daa78']], brl(D.rec[cur] + D.disc[cur]).replace(',00', ''), 'cobrado no mês')}</div>
    <div class="card"><h3>A receber por servidor</h3><p class="hint">Clientes ainda marcados como "A receber".</p>${bySrv.length ? hbars(bySrv, brl, '#3daa78') : '<p class="muted">Nada a receber.</p>'}
      <h3 style="margin-top:18px">Despesas nos últimos 6 meses</h3>${hbars(back.map(k => [mLab(k), eb[k]]), brl, '#d55a55')}</div>
  </div>
  <div class="card" style="margin-top:12px"><h3>Despesas</h3><p class="hint">Gastos que não são o crédito de cada cliente: internet, anúncios, compra de créditos em lote, etc.</p>
    <div class="row" style="margin-bottom:12px"><label class="f" style="flex:0 1 150px">Data<input class="in" type="date" id="eD" value="${todayISO()}"></label><label class="f" style="flex:2 1 200px">Descrição<input class="in" id="eT" maxlength="80" placeholder="ex.: 100 créditos Playdragon"></label>
      <label class="f">Categoria<select class="in" id="eC">${CATS.map(c => `<option>${c}</option>`).join('')}</select></label><label class="f" style="flex:0 1 130px">Valor (R$)<input class="in" id="eV" inputmode="decimal" placeholder="0,00"></label><button class="bt pk" id="eAdd" style="flex:0 0 auto">Lançar</button></div>
    ${E.length ? `<div class="twrap"><table class="ktab"><thead><tr><th>Data</th><th>Descrição</th><th>Categoria</th><th class="r">Valor</th><th></th></tr></thead><tbody>${shown.map(e => `<tr><td>${fmtISO(e.date)}</td><td>${esc(e.desc)}</td><td class="muted">${esc(e.cat || '')}</td><td class="r">${brl(e.value)}</td><td class="r"><button class="ia" data-ed="${esc(e.id)}" title="Apagar">${SVG_DEL}</button></td></tr>`).join('')}</tbody><tfoot><tr><td colspan="3"><b>Total lançado</b></td><td class="r"><b>${brl(E.reduce((n, e) => n + (e.value || 0), 0))}</b></td><td></td></tr></tfoot></table></div>${sorted.length > 12 ? `<button class="bt sm" id="eMore" style="margin-top:8px">${FEXP ? 'Mostrar menos' : 'Mostrar todas (' + sorted.length + ')'}</button>` : ''}` : '<p class="muted">Nenhuma despesa lançada.</p>'}</div>`;
  $('#eAdd').onclick = () => {
    const date = $('#eD').value, desc = $('#eT').value.trim(), value = money($('#eV').value);
    if (!date || !desc) return toast('Preencha a data e a descrição.', 1); if (!value) return toast('Informe o valor.', 1);
    saveRec(r => r.expenses.push({ id: gid(), date, desc, cat: $('#eC').value, value }), 'Despesa lançada.');
  };
  $$('[data-ed]', $('#fBody')).forEach(b => b.onclick = async () => { if (!await confirmBox('Apagar despesa', 'Esta despesa será apagada.', 'Apagar', 1)) return; saveRec(r => r.expenses = r.expenses.filter(e => e.id != b.dataset.ed), 'Despesa apagada.') });
  const mb = $('#eMore'); if (mb) mb.onclick = () => { FEXP = !FEXP; renderFin() };
}

// --- comentários ---
function renderCom() {
  if (!GLOADED || !$('#cmBody')) return;
  const N = [...(REC().notes || [])].sort((a, b) => b.t - a.t), SV = srvList();
  const aboutLab = a => { if (!a) return 'Geral'; const [k, id] = a.split(':'); if (k == 's') { const s = SV.find(x => x.id == id); return s ? 'Servidor ' + s.name : 'servidor removido' } const r = rowById(id); return r ? r.p.name + ' (' + r.sv.name + ')' : 'cliente removido' };
  const inRows = allRows().filter(x => x.p.note).map(x => ({ who: x.p.name + ' (' + x.sv.name + ')', text: x.p.note, s: x.sv.id, p: x.p.id }));
  const inSrv = SV.filter(s => s.note).map(s => ({ who: 'Servidor ' + s.name, text: s.note, s: s.id }));
  $('#cmBody').innerHTML = `<div class="card"><h3>Novo comentário</h3><p class="hint">Anotações com data: combinados, problemas de servidor, cobranças, lembretes.</p>
    <div class="grid" style="gap:8px"><textarea class="in" id="cmT" rows="3" maxlength="600" placeholder="Escreva aqui…"></textarea>
    <div class="row" style="align-items:center"><label class="f" style="flex:1 1 220px">Sobre<select class="in" id="cmA"><option value="">Geral</option>${SV.map(s => `<option value="s:${esc(s.id)}">Servidor ${esc(s.name)}</option>`).join('')}${allRows().sort((a, b) => a.p.name.localeCompare(b.p.name)).map(x => `<option value="p:${esc(x.p.id)}">${esc(x.p.name)} (${esc(x.sv.name)})</option>`).join('')}</select></label><button class="bt pk" id="cmAdd" style="flex:0 0 auto">Salvar comentário</button></div></div></div>
  <div class="bar" style="margin-top:14px"><input class="in" id="cmQ" placeholder="Buscar nos comentários…"></div>
  <div class="card"><h3>Histórico</h3><div class="cml" id="cmL">${N.length ? N.map(n => `<div class="cmi" data-txt="${esc((n.text + ' ' + aboutLab(n.about)).toLowerCase())}"><div class="cmh"><b>${esc(aboutLab(n.about))}</b><span class="muted">${fmtDT(n.t)}</span><span class="sp"></span><button class="x" data-cd="${esc(n.id)}" title="Apagar">${SVG_X}</button></div><p>${esc(n.text)}</p></div>`).join('') : '<p class="muted">Nenhum comentário ainda.</p>'}</div></div>
  <div class="card" style="margin-top:12px"><h3>Comentários fixos dos servidores e clientes</h3><p class="hint">O que está no campo Comentário de cada um. Para mudar, use Editar na aba Servidores.</p>
    <div class="cml">${inSrv.concat(inRows).map(c => `<div class="cmi" data-txt="${esc((c.text + ' ' + c.who).toLowerCase())}"><div class="cmh"><b>${esc(c.who)}</b></div><p>${esc(c.text)}</p></div>`).join('') || '<p class="muted">Nenhum.</p>'}</div></div>`;
  $('#cmAdd').onclick = () => { const text = $('#cmT').value.trim(); if (!text) return toast('Escreva o comentário.', 1); saveRec(r => r.notes.push({ id: gid(), t: Date.now(), text, about: $('#cmA').value }), 'Comentário salvo.') };
  $('#cmQ').oninput = e => { const q = e.target.value.toLowerCase(); $$('.cmi', $('#cmBody')).forEach(d => d.classList.toggle('hide', !d.dataset.txt.includes(q))) };
  $$('[data-cd]', $('#cmBody')).forEach(b => b.onclick = async () => { if (!await confirmBox('Apagar comentário', 'Este comentário será apagado.', 'Apagar', 1)) return; saveRec(r => r.notes = r.notes.filter(n => n.id != b.dataset.cd), 'Comentário apagado.') });
}

/* ================= aba clientes (gráficos) ================= */
let KF = 'all', KR = 30, KANIM = false;
const SP = 'America/Sao_Paulo';
const dayKey = t => new Date(t).toLocaleDateString('en-CA', { timeZone: SP });
const onlineNow = u => Object.values(u.devices || {}).filter(d => Date.now() - Number(d.t) < 10 * 60000).length;
const ICO = {
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/>',
  ok: '<path d="M22 11.1V12a10 10 0 1 1-5.9-9.1"/><path d="M22 4L12 14l-3-3"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  x: '<circle cx="12" cy="12" r="9"/><path d="M15 9l-6 6M9 9l6 6"/>',
  wifi: '<path d="M5 12.6a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0M12 20h.01"/>',
  renew: '<path d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5"/>', list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>', more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
};
const svgI = k => `<svg class="i" viewBox="0 0 24 24">${ICO[k]}</svg>`;

function renderClientes() {
  const tot = USERS.length, act = USERS.filter(u => daysLeft(u) > 0), soon = USERS.filter(u => { const d = daysLeft(u); return d > 0 && d <= 7 }), exp = USERS.filter(u => daysLeft(u) <= 0);
  const onl = USERS.reduce((n, u) => n + onlineNow(u), 0);
  const pct = tot ? Math.round(act.length / tot * 100) : 0;
  $('#kSub').textContent = new Date().toLocaleDateString('pt-BR', { timeZone: SP, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  $('#kKpis').innerHTML = [
    ['#8a9199', 'Total de contas', tot, 'cadastradas'],
    ['#3daa78', 'Ativos', act.length, pct + '% do total'],
    ['#d39b32', 'Vencem em 7 dias', soon.length, soon.length ? 'renovar' : 'nenhum'],
    ['#d55a55', 'Expirados', exp.length, 'bloqueados no app'],
    ['#5c9bd1', 'Online agora', onl, 'últimos 10 min']
  ].map(k => `<div class="kpi" style="--c:${k[0]}"><small><i></i>${k[1]}</small><b>${k[2]}</b><em>${k[3]}</em></div>`).join('');
  drawDonut([['Ativos', act.length - soon.length, '#3daa78'], ['Vencendo', soon.length, '#d39b32'], ['Expirados', exp.length, '#d55a55']], tot);
  drawTimeline();
  drawRows();
  KANIM = true;
}

function drawDonut(parts, tot) {
  const R = 78, C = 2 * Math.PI * R, live = parts.filter(p => p[1] > 0), gap = live.length > 1 ? 2 : 0;
  let off = 0, arcs = '';
  live.forEach(p => {
    const len = Math.max(0, p[1] / tot * C - gap);
    arcs += `<circle cx="100" cy="100" r="${R}" fill="none" stroke="${p[2]}" stroke-width="16" stroke-dasharray="${KANIM ? len : 0} ${C}" data-len="${len}" stroke-dashoffset="${-off}" transform="rotate(-90 100 100)" style="transition:stroke-dasharray .5s ease-out"><title>${p[0]}: ${p[1]}</title></circle>`;
    off += p[1] / tot * C;
  });
  $('#kDonut').innerHTML = `<svg viewBox="0 0 200 200" role="img" aria-label="Status das contas">
    <circle cx="100" cy="100" r="${R}" fill="none" stroke="#ffffff0d" stroke-width="16"/>${arcs}
    <text x="100" y="100" text-anchor="middle" fill="var(--tx)" font-size="34" font-weight="600">${tot}</text>
    <text x="100" y="122" text-anchor="middle" fill="var(--mut)" font-size="12">contas</text></svg>
    <div class="lg">${parts.map(p => `<span><i style="background:${p[2]}"></i>${p[0]} <b>${p[1]}</b></span>`).join('')}</div>`;
  if (!KANIM && !matchMedia('(prefers-reduced-motion: reduce)').matches) requestAnimationFrame(() => requestAnimationFrame(() => $$('#kDonut circle[data-len]').forEach(c => c.setAttribute('stroke-dasharray', c.dataset.len + ' ' + C))));
  else $$('#kDonut circle[data-len]').forEach(c => c.setAttribute('stroke-dasharray', c.dataset.len + ' ' + C));
}

function drawTimeline() {
  const box = $('#kTime'); if (!box) return;
  const W = Math.max(300, box.clientWidth || 640), H = Math.round(Math.min(260, Math.max(190, W * .55))), pl = 34, pr = 14, pt = 18, pb = 34;
  const week = KR > 31, step = week ? 7 : 1, n = Math.ceil(KR / step);
  const t0 = new Date(dayKey(Date.now()) + 'T00:00:00-03:00').getTime();
  const B = Array.from({ length: n }, (_, i) => ({ s: t0 + i * step * DAY, names: [] }));
  USERS.forEach(u => { if (!u.expires_at) return; const e = new Date(u.expires_at).getTime(); if (e < t0) return; const i = Math.floor((e - t0) / (step * DAY)); if (i < n) B[i].names.push(u.username) });
  let acc = exp0(); B.forEach(b => { acc += b.names.length; b.acc = acc; b.n = b.names.length });
  function exp0() { return USERS.filter(u => u.expires_at && new Date(u.expires_at) < Date.now()).length }
  const max = Math.max(1, ...B.map(b => b.acc)), iw = W - pl - pr, ih = H - pt - pb, bw = iw / n;
  const y = v => pt + ih - v / max * ih, x = i => pl + i * bw;
  const ticks = [...new Set([0, Math.round(max / 2), max])];
  const lab = i => { const d = new Date(B[i].s); if (i == 0) return 'Hoje'; return d.toLocaleDateString('pt-BR', { timeZone: SP, day: '2-digit', month: '2-digit' }) };
  const every = Math.ceil(n / Math.max(2, Math.floor(iw / 56)));
  const line = B.map((b, i) => (i ? `L${x(i).toFixed(1)},${y(B[i - 1].acc).toFixed(1)}L${x(i).toFixed(1)},${y(b.acc).toFixed(1)}` : `M${x(0).toFixed(1)},${y(b.acc).toFixed(1)}`) + `L${(x(i) + bw).toFixed(1)},${y(b.acc).toFixed(1)}`).join('');
  const area = line + `L${(x(n - 1) + bw).toFixed(1)},${y(0)}L${x(0).toFixed(1)},${y(0)}Z`;
  const bwi = Math.min(26, Math.max(4, bw * .58));
  box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Vencimentos por ${week ? 'semana' : 'dia'}">
    ${ticks.map(t => `<line x1="${pl}" x2="${W - pr}" y1="${y(t)}" y2="${y(t)}" stroke="#ffffff12" ${t ? 'stroke-dasharray="3 4"' : ''}/><text x="${pl - 8}" y="${y(t) + 4}" text-anchor="end" fill="var(--mut)" font-size="11">${t}</text>`).join('')}
    <path d="${area}" fill="#5c9bd114"/><path d="${line}" fill="none" stroke="#5c9bd1" stroke-width="1.5" stroke-linejoin="round"/>
    ${B.map((b, i) => b.n ? `<rect x="${(x(i) + (bw - bwi) / 2).toFixed(1)}" y="${y(b.n).toFixed(1)}" width="${bwi.toFixed(1)}" height="${(y(0) - y(b.n)).toFixed(1)}" rx="2" fill="#d0477b"/>` : '').join('')}
    ${B.map((b, i) => i % every == 0 ? `<text x="${(x(i) + bw / 2).toFixed(1)}" y="${H - 12}" text-anchor="middle" fill="var(--mut)" font-size="11">${lab(i)}</text>` : '').join('')}
    <line id="khl" x1="0" x2="0" y1="${pt}" y2="${y(0)}" stroke="#ffffff40" visibility="hidden"/>
    ${B.map((b, i) => `<rect class="hz" data-i="${i}" x="${x(i)}" y="${pt}" width="${bw}" height="${ih}" fill="transparent"/>`).join('')}
  </svg><div class="lg muted" style="display:flex;gap:14px;margin-top:6px"><span><i style="display:inline-block;width:10px;height:10px;border-radius:3px;background:#d0477b;margin-right:5px"></i>vencem no ${week ? 'semana' : 'dia'}</span><span><i style="display:inline-block;width:14px;height:2px;background:#5c9bd1;vertical-align:middle;margin-right:5px"></i>total vencido até a data (sem renovar)</span></div><div class="ktip hide" id="ktip"></div>`;
  const tip = $('#ktip'), hl = $('#khl');
  $$('.hz', box).forEach(r => {
    r.onmouseenter = () => { const i = +r.dataset.i, b = B[i], d = new Date(b.s);
      const per = week ? 'Semana de ' + d.toLocaleDateString('pt-BR', { timeZone: SP, day: '2-digit', month: '2-digit' }) : (i == 0 ? 'Hoje' : d.toLocaleDateString('pt-BR', { timeZone: SP, weekday: 'short', day: '2-digit', month: '2-digit' }));
      tip.innerHTML = `<b>${esc(per)}</b>${b.n ? b.n + ' vence(m): ' + esc(b.names.join(', ')) : 'Nenhum vencimento'}<br><span class="muted">acumulado: ${b.acc}</span>`;
      const cx = x(i) + bw / 2; hl.setAttribute('x1', cx); hl.setAttribute('x2', cx); hl.setAttribute('visibility', 'visible');
      const k = box.clientWidth / W; tip.style.left = Math.min(Math.max(cx * k, 90), box.clientWidth - 90) + 'px'; tip.style.top = (y(Math.max(b.acc, b.n)) * k) + 'px'; tip.classList.remove('hide') };
    r.onmouseleave = () => { tip.classList.add('hide'); hl.setAttribute('visibility', 'hidden') };
  });
}
$$('#kRange button').forEach(b => b.onclick = () => { KR = +b.dataset.r; $$('#kRange button').forEach(x => x.classList.toggle('on', x == b)); drawTimeline() });
let kRT; addEventListener('resize', () => { clearTimeout(kRT); kRT = setTimeout(() => { if (!$('#t-clientes').classList.contains('hide')) drawTimeline() }, 150) });

function drawRows() {
  const q = ($('#kQ').value || '').toLowerCase();
  const rows = USERS.filter(u => (KF == 'all' || status(u)[2] == KF) && (!q || (u.username + ' ' + (u.note || '')).toLowerCase().includes(q))).sort((a, b) => daysLeft(a) - daysLeft(b));
  const col = { g: '#3daa78', y: '#d39b32', r: '#d55a55' };
  $('#kRows').innerHTML = rows.length ? rows.map(u => {
    const s = status(u), d = daysLeft(u), nd = Object.keys(u.devices || {}).length, on = onlineNow(u);
    const life = d > 3650, w = life ? 100 : Math.max(0, Math.min(100, d / 30 * 100));
    return `<tr><td><div class="cl"><div class="av">${esc(u.username[0])}</div><div><b>${esc(u.username)}</b><small class="${u.note ? 'nt' : ''}">${u.note ? esc(u.note) : 'Desde ' + fmtD(u.created_at)}</small></div></div></td>
      <td>${ago(u.last_seen)}</td>
      <td><span class="dot ${on ? 'on' : ''}"></span><b>${on} online</b> <span class="muted">· ${nd} aparelho(s)</span></td>
      <td><span class="bdg ${s[1]}">${d <= 0 ? 'Expirado' : d <= 7 ? 'Vencendo' : 'Ativo'}</span></td>
      <td>${life ? 'Vitalícia' : d <= 0 ? 'Venceu' : Math.ceil(d) + ' dia(s)'}<div class="pg"><i style="width:${w}%;background:${col[s[1]]}"></i></div></td>
      <td><b>${u.expires_at ? fmtD(u.expires_at) : '—'}</b></td>
      <td class="r"><button class="ia" data-renew="${esc(u.username)}" title="Renovar">${svgI('renew')}</button><button class="ia" data-lists="${esc(u.username)}" title="Listas">${svgI('list')}</button><button class="ia" data-more="${esc(u.username)}" title="Mais ações">${svgI('more')}</button></td></tr>`
  }).join('') : '<tr><td colspan="7" class="empty">Nenhum cliente encontrado.</td></tr>';
}
$('#kQ').oninput = drawRows;
$$('.ch[data-k]').forEach(b => b.onclick = () => { KF = b.dataset.k; $$('.ch[data-k]').forEach(x => x.classList.toggle('on', x == b)); drawRows() });
$('#kCsv').onclick = () => {
  const q = v => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"';
  const lines = [['usuario', 'nota', 'criada', 'vencimento', 'dias_restantes', 'status', 'ultimo_acesso', 'aparelhos'].join(';')].concat(USERS.map(u => [u.username, u.note || '', fmtD(u.created_at), u.expires_at ? fmtD(u.expires_at) : '', Math.ceil(daysLeft(u)), status(u)[0], u.last_seen ? fmtDT(u.last_seen) : '', Object.keys(u.devices || {}).length].map(q).join(';')));
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
  a.download = 'ntplay-clientes-' + dayKey(Date.now()) + '.csv'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
};

function renderGeral() {
  const act = USERS.filter(u => daysLeft(u) > 0), soon = USERS.filter(u => { const d = daysLeft(u); return d > 0 && d <= 7 }), exp = USERS.filter(u => daysLeft(u) <= 0);
  const on24 = USERS.filter(u => u.last_seen && Date.now() - new Date(u.last_seen) < DAY).length;
  const devs = USERS.reduce((n, u) => n + Object.keys(u.devices || {}).length, 0);
  $('#gStats').innerHTML = [['gr', act.length, 'contas ativas'], ['yl', soon.length, 'vencem em 7 dias'], ['rd', exp.length, 'vencidas'], ['pk', on24, 'usaram nas últimas 24 h'], ['', devs, 'aparelhos vinculados']].map(s => `<div class="st ${s[0]}"><b>${s[1]}</b><small>${s[2]}</small></div>`).join('');
  $('#nCont').textContent = soon.length + exp.length ? soon.length + exp.length : '';
  const att = [...exp, ...soon].sort((a, b) => daysLeft(a) - daysLeft(b));
  $('#gSub').textContent = att.length ? att.length + (att.length > 1 ? ' contas precisam' : ' conta precisa') + ' de atenção.' : 'Tudo em dia. Nenhuma conta vencendo nos próximos 7 dias.';
  $('#gAtt').innerHTML = att.length ? att.map(u => { const s = status(u); return `<div class="li"><div class="n"><b>${esc(u.username)}</b><small>vence ${fmtD(u.expires_at)}${u.note ? ' · ' + esc(u.note) : ''}</small></div><span class="bdg ${s[1]}">${s[0]}</span><button class="bt sm" data-renew="${esc(u.username)}">Renovar</button></div>` }).join('') : '<p class="muted">Nada pendente.</p>';
  const last = [...USERS].filter(u => u.last_seen).sort((a, b) => new Date(b.last_seen) - new Date(a.last_seen)).slice(0, 6);
  $('#gLast').innerHTML = last.length ? last.map(u => `<div class="li"><div class="av" style="width:28px;height:28px;font-size:12px">${esc(u.username[0])}</div><div class="n"><b>${esc(u.username)}</b><small>${Object.keys(u.devices || {}).length} aparelho(s)</small></div><span class="muted">${ago(u.last_seen)}</span></div>`).join('') : '<p class="muted">Ninguém entrou ainda.</p>';
}

function renderContas() {
  const q = ($('#cQ').value || '').toLowerCase();
  const rows = USERS.filter(u => (FILTER == 'all' || status(u)[2] == FILTER) && (!q || (u.username + ' ' + (u.note || '')).toLowerCase().includes(q))).sort((a, b) => daysLeft(a) - daysLeft(b));
  $('#cList').innerHTML = rows.length ? rows.map(u => {
    const s = status(u), devs = Object.entries(u.devices || {}).sort((a, b) => b[1].t - a[1].t);
    return `<div class="acc ${s[1] == 'y' ? 'warn' : s[1] == 'r' ? 'bad' : ''}">
      <div class="ah"><div class="av">${esc(u.username[0])}</div><div class="n"><b>${esc(u.username)}</b><small>${esc(u.note || 'sem nota')}</small></div><span class="bdg ${s[1]}">${s[0]}</span></div>
      <div class="kv"><span>Vence</span><b>${u.expires_at ? fmtD(u.expires_at) : '—'}</b><span>Último uso</span><b>${ago(u.last_seen)}</b><span>Criada</span><b>${fmtD(u.created_at)}</b></div>
      <div class="devs">${devs.length ? devs.map(([id, d]) => `<span class="tg" title="${esc(id)}">${esc(d.n || 'aparelho')} · ${ago(d.t)}</span>`).join('') : '<span class="tg">nenhum aparelho</span>'}</div>
      <div class="acts">
        <button class="bt sm pk" data-renew="${esc(u.username)}">Renovar</button>
        <button class="bt sm" data-lists="${esc(u.username)}">Listas</button>
        <button class="bt sm" data-more="${esc(u.username)}">Mais…</button>
      </div></div>`
  }).join('') : '<div class="empty">Nenhuma conta encontrada.</div>';
}
$('#cQ').oninput = renderContas;
$$('.ch[data-f]').forEach(b => b.onclick = () => { FILTER = b.dataset.f; $$('.ch[data-f]').forEach(x => x.classList.toggle('on', x == b)); renderContas() });

document.addEventListener('click', async e => {
  const r = e.target.closest('[data-renew]'), l = e.target.closest('[data-lists]'), m = e.target.closest('[data-more]');
  if (r) renewFlow(r.dataset.renew);
  if (l) { go('listas'); $('#lsU').value = l.dataset.lists; resetListas(); $('#lsP').focus() }
  if (m) moreFlow(m.dataset.more);
});

async function renewFlow(u) {
  modal(`<h3>Renovar ${esc(u)}</h3><p class="muted">Soma os dias a partir do vencimento atual (ou de hoje, se já venceu).</p>
    <div class="seg" id="rSeg"><button data-d="30" class="on">+30 dias</button><button data-d="90">+90</button><button data-d="180">+180</button><button data-d="365">+1 ano</button></div>
    <label class="f">Ou defina a data exata<input class="in" type="date" id="rDate"></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Renovar</button></div>`, mm => {
    let d = 30; $$('#rSeg button', mm).forEach(b => b.onclick = () => { d = +b.dataset.d; $$('#rSeg button', mm).forEach(x => x.classList.toggle('on', x == b)); $('#rDate', mm).value = '' });
    $('[data-no]', mm).onclick = closeModal;
    $('[data-ok]', mm).onclick = async () => {
      const dt = $('#rDate', mm).value; closeModal();
      try { if (dt) await adm('setexp', { u, date: dt }); else await adm('renew', { u, days: d }); toast('Conta ' + u + ' renovada.'); loadUsers() } catch (er) { toast(er.message, 1) }
    };
  });
}

function moreFlow(u) {
  const user = USERS.find(x => x.username == u) || {};
  modal(`<h3>${esc(u)}</h3><div class="grid" style="gap:8px">
    <button class="bt" data-a="note">Editar nota</button>
    <button class="bt" data-a="pass">Redefinir senha</button>
    <button class="bt" data-a="dev">Desvincular aparelhos (${Object.keys(user.devices || {}).length})</button>
    <button class="bt rd" data-a="revoke">Suspender acesso agora</button>
    <button class="bt rd" data-a="del">Apagar conta</button></div>
    <div class="acts"><button class="bt" data-no>Fechar</button></div>`, mm => {
    $('[data-no]', mm).onclick = closeModal;
    $$('[data-a]', mm).forEach(b => b.onclick = async () => {
      const a = b.dataset.a; closeModal();
      try {
        if (a == 'note') { const o = await askBox('Nota de ' + u, '', [{ k: 'note', label: 'Nota', v: user.note || '', max: 80 }], 'Salvar'); if (!o) return; await adm('note', { u, note: o.note }) }
        if (a == 'pass') {
          const o = await askBox('Nova senha para ' + u, 'As listas e favoritos atuais ficam ilegíveis com a senha nova. Depois de salvar, abra a aba Listas com a senha nova para colocar as listas de novo.', [{ k: 'p', label: 'Nova senha', v: genPass() }], 'Redefinir'); if (!o || !o.p) return;
          const k = await derive(u, o.p); await adm('setpass', { u, k: k.auth });
          await putPayload(u, k, emptyPayload());
          modal(`<h3>Senha redefinida</h3><p class="muted">Envie ao cliente:</p><div class="cred">Usuário: ${esc(u)}\nSenha: ${esc(o.p)}</div><div class="acts"><button class="bt" data-c>Copiar</button><button class="bt pk" data-l>Colocar listas</button></div>`, m2 => {
            $('[data-c]', m2).onclick = () => copy('Usuário: ' + u + '\nSenha: ' + o.p);
            $('[data-l]', m2).onclick = () => { closeModal(); go('listas'); $('#lsU').value = u; $('#lsP').value = o.p; openListas() };
          }); loadUsers(); return;
        }
        if (a == 'dev') { if (!await confirmBox('Desvincular aparelhos', 'A lista de aparelhos de ' + u + ' será zerada. O cliente continua podendo entrar.', 'Desvincular')) return; await adm('cleardev', { u }) }
        if (a == 'revoke') { if (!await confirmBox('Suspender ' + u, 'O acesso vence agora. Você pode reativar depois com Renovar.', 'Suspender', 1)) return; await adm('revoke', { u }) }
        if (a == 'del') { if (!await confirmBox('Apagar ' + u, 'A conta e os dados salvos dela (listas, favoritos, progresso) serão apagados para sempre.', 'Apagar', 1)) return; await adm('delete', { u }) }
        toast('Feito.'); loadUsers();
      } catch (er) { toast(er.message, 1) }
    });
  });
}

function copy(t) { (navigator.clipboard ? navigator.clipboard.writeText(t) : Promise.reject()).then(() => toast('Copiado.')).catch(() => toast('Não deu para copiar; selecione o texto.', 1)) }
function genPass() { const a = 'abcdefghjkmnpqrstuvwxyz23456789'; const r = crypto.getRandomValues(new Uint8Array(10)); return [...r].map(x => a[x % a.length]).join('') }

/* ================= nova conta ================= */
let NEW_LISTS = [], NEW_DAYS = 30;
function openNovo() {
  $('#cNovo').classList.remove('hide'); $('#nDone').classList.add('hide'); $('#nMsg').textContent = '';
  ['nU', 'nNote'].forEach(i => $('#' + i).value = ''); $('#nP').value = genPass(); NEW_LISTS = [];
  listEditor($('#nLists'), $('#nAddW'), NEW_LISTS); $('#nU').focus();
}
$('#nCancel').onclick = () => $('#cNovo').classList.add('hide');
$('#nGen').onclick = () => $('#nP').value = genPass();
$$('#nSeg button').forEach(b => b.onclick = () => { NEW_DAYS = +b.dataset.d; $$('#nSeg button').forEach(x => x.classList.toggle('on', x == b)); $('#nDateW').classList.toggle('hide', NEW_DAYS != 0) });
$('#nGo').onclick = async () => {
  const u = normU($('#nU').value), p = $('#nP').value, note = $('#nNote').value.trim(), date = $('#nDate').value;
  $('#nMsg').textContent = '';
  if (!/^[a-z0-9._-]{3,32}$/.test(u)) return $('#nMsg').textContent = 'Usuário: 3 a 32 letras minúsculas, números, ponto, _ ou -.';
  if (p.length < 6) return $('#nMsg').textContent = 'Use uma senha de pelo menos 6 caracteres.';
  if (!NEW_DAYS && !date) return $('#nMsg').textContent = 'Escolha a data de vencimento.';
  const btn = $('#nGo'); btn.disabled = true; btn.innerHTML = '<span class="spin"></span> Criando…';
  try {
    const k = await derive(u, p);
    await adm('create', { u, k: k.auth, note, ...(NEW_DAYS ? { days: NEW_DAYS } : { date }) });
    let extra = '';
    if (NEW_LISTS.length) { try { const pl = emptyPayload(); pl.lists = NEW_LISTS.map(l => ({ name: l.name, url: l.url, active: l.active !== false })); await putPayload(u, k, pl); extra = '\nListas: ' + NEW_LISTS.length + ' já configurada(s)' } catch (er) { extra = '\n(Conta criada, mas as listas não foram salvas: ' + er.message + ')' } }
    const txt = 'Usuário: ' + u + '\nSenha: ' + p;
    $('#nDone').innerHTML = `<p class="ok" style="margin-bottom:8px">Conta criada. Envie ao cliente:</p><div class="cred">${esc(txt + extra)}</div><div style="margin-top:8px"><button class="bt sm" id="nCopy">Copiar dados</button></div>`;
    $('#nDone').classList.remove('hide'); $('#nCopy').onclick = () => copy(txt);
    loadUsers();
  } catch (er) { $('#nMsg').textContent = er.message }
  btn.disabled = false; btn.textContent = 'Criar conta';
};

/* ================= aba listas ================= */
let LS_STATE = null;
function fillAccSelect() { const cur = $('#lsU').value; $('#lsU').innerHTML = USERS.map(u => `<option value="${esc(u.username)}">${esc(u.username)}${u.note ? ' — ' + esc(u.note) : ''}</option>`).join(''); if (cur) $('#lsU').value = cur }
function resetListas() { LS_STATE = null; $('#lsP').value = ''; $('#lsMsg').textContent = ''; $('#lsList').innerHTML = ''; $('#lsAddW').innerHTML = ''; $('#lsSaveW').classList.add('hide'); $('#lsTitle').textContent = 'Listas'; $('#lsHint').textContent = 'Abra uma conta para ver as listas dela.'; steps(1) }
function steps(n) { [1, 2, 3].forEach(i => $('#s' + i).classList.toggle('on', i <= n)) }
$('#lsU').onchange = resetListas;
$('#lsP').onkeydown = e => { if (e.key == 'Enter') openListas() };
$('#lsOpen').onclick = openListas;
async function openListas() {
  const u = $('#lsU').value, p = $('#lsP').value; $('#lsMsg').textContent = '';
  if (!u || !p) return $('#lsMsg').textContent = 'Escolha a conta e digite a senha do cliente.';
  const btn = $('#lsOpen'); btn.disabled = true; btn.innerHTML = '<span class="spin"></span> Abrindo…';
  try {
    const k = await derive(u, p), j = await call({ a: 'get', u, k: k.auth });
    let payload = emptyPayload(), fresh = false;
    if (j.data) { const d = await decBlob(j.data, k.enc); if (!d) { btn.disabled = false; btn.textContent = 'Abrir listas'; const go2 = await confirmBox('Dados ilegíveis', 'A senha está certa, mas os dados salvos foram gravados com outra senha (provavelmente antes de uma redefinição). Começar do zero apaga favoritos e progresso antigos dessa conta.', 'Começar do zero', 1); if (!go2) return; fresh = true } else payload = Object.assign(emptyPayload(), d) }
    payload.lists = (payload.lists || []).map(l => ({ name: l.name || 'Lista', url: l.url, active: l.active !== false }));
    LS_STATE = { u, k, payload, dirty: fresh };
    $('#lsTitle').textContent = 'Listas de ' + u;
    $('#lsHint').textContent = 'Ligue/desligue, remova ou adicione. Favoritos e progresso do cliente são mantidos.';
    listEditor($('#lsList'), $('#lsAddW'), payload.lists, () => { LS_STATE.dirty = true; steps(3); $('#lsSaveMsg').textContent = 'Alterações não salvas.' });
    $('#lsSaveW').classList.remove('hide'); $('#lsSaveMsg').textContent = fresh ? 'Salve para gravar com a senha atual.' : ''; steps(fresh ? 3 : 2);
  } catch (er) { $('#lsMsg').textContent = er.message }
  btn.disabled = false; btn.textContent = 'Abrir listas';
}
$('#lsSave').onclick = async () => {
  if (!LS_STATE) return; const btn = $('#lsSave'); btn.disabled = true; btn.innerHTML = '<span class="spin"></span> Salvando…';
  try { await putPayload(LS_STATE.u, LS_STATE.k, LS_STATE.payload); LS_STATE.dirty = false; $('#lsSaveMsg').textContent = 'Salvo ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) + '. O app aplica na próxima vez que sincronizar.'; toast('Listas salvas na conta ' + LS_STATE.u + '.') }
  catch (er) { toast(er.message, 1) }
  btn.disabled = false; btn.textContent = 'Salvar na conta';
};

/* ================= aba uso (telemetria anônima via ntfy) ================= */
const TOPIC = 'ntplay-pres-381affd473dafb5cac', ONLINE = 7 * 60000, RET = 180 * DAY;
let UKEY = null, UDATA = [], UT = null, lastRx = 0, ustarted = false;
const DB = new Promise((ok, no) => { try { const r = indexedDB.open('ntadmin', 2); r.onupgradeneeded = () => { if (!r.result.objectStoreNames.contains('ev')) r.result.createObjectStore('ev', { keyPath: 'k' }) }; r.onsuccess = () => ok(r.result); r.onerror = () => no(r.error) } catch (e) { no(e) } });
const tx = (m, f) => DB.then(db => new Promise((ok, no) => { const t = db.transaction('ev', m), r = f(t.objectStore('ev')); t.oncomplete = () => ok(r && r.result); t.onerror = () => no(t.error) }));
const dbAll = () => tx('readonly', s => s.getAll()), dbPut = evs => tx('readwrite', s => { evs.forEach(e => s.put(e)) });
const dbDelOld = cut => tx('readwrite', s => { const rq = s.openCursor(); rq.onsuccess = e => { const c = e.target.result; if (!c) return; if (c.value.t < cut) c.delete(); c.continue() } });
async function loadUKey(txt) { const j = JSON.parse(txt); return crypto.subtle.importKey('jwk', j.prv || j, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']) }
async function open1(m) { const p = m.split('.'); if (p[0] != 'ntp1' || p.length != 4) return null; const C = crypto.subtle;
  const eph = await C.importKey('raw', unb64(p[1]), { name: 'ECDH', namedCurve: 'P-256' }, false, []); const sh = await C.deriveBits({ name: 'ECDH', public: eph }, UKEY, 256);
  const k = await C.importKey('raw', await C.digest('SHA-256', sh), 'AES-GCM', false, ['decrypt']);
  return JSON.parse(td.decode(await C.decrypt({ name: 'AES-GCM', iv: unb64(p[2]) }, k, unb64(p[3])))) }
async function fetchNew() { const since = lastRx ? Math.floor(lastRx / 1000) : Math.floor((Date.now() - 12 * 36e5) / 1000);
  const r = await fetch('https://ntfy.sh/' + TOPIC + '/json?poll=1&since=' + since, { cache: 'no-store' }); if (!r.ok) throw Error('HTTP ' + r.status);
  const neu = []; for (const l of (await r.text()).split('\n')) { if (!l.trim()) continue; let j; try { j = JSON.parse(l) } catch (e) { continue } if (j.event != 'message' || !j.message) continue;
    try { const o = await open1(j.message.trim()); if (o && o.id && o.t) { const e = { k: o.id + '|' + o.t, id: o.id, p: o.p || '?', v: o.v || '', t: o.t, rx: j.time * 1000 }; neu.push(e); if (e.rx > lastRx) lastRx = e.rx } } catch (e) { } }
  if (neu.length) await dbPut(neu); return neu.length }
const PL = { pc: 'PC Windows', android: 'Android', ios: 'iPhone/iPad', androidtv: 'Android TV (web)', tv: 'Android TV', web: 'Navegador', 'web-celular': 'Navegador (celular)', '?': 'Desconhecido' };
const dkey = t => { const d = new Date(t); return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate() };
const nD = n => { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + n); return d };
function lastById(evs) { const m = new Map(); evs.forEach(e => { const o = m.get(e.id); if (!o || e.t > o.t) m.set(e.id, e) }); return m }
function bars(rows) { const max = Math.max(1, ...rows.map(r => r[1])); return '<div class="chart">' + rows.map(r => `<div class="col"><span class="v">${r[1] || ''}</span><div class="bk"><div class="br${r[2] ? ' hl' : ''}" style="height:${(r[1] / max * 100).toFixed(1)}%"></div></div><span class="x">${esc(r[0])}</span></div>`).join('') + '</div>' }
function perDay(evs, i) { const d0 = nD(-i).getTime(), d1 = nD(-i + 1).getTime(), s = new Set(); evs.forEach(e => { if (e.t >= d0 && e.t < d1) s.add(e.id) }); return s.size }
function usoRender() {
  const now = Date.now(), evs = UDATA, last = lastById(evs), on = [...last.values()].filter(e => now - e.t < ONLINE);
  const c30 = []; for (let i = 0; i < 30; i++) c30.push(perDay(evs, i));
  const avg7 = Math.round(c30.slice(0, 7).reduce((a, b) => a + b, 0) / 7), peak = Math.max(0, ...c30);
  let h = `<div class="stats">${[['gr', on.length, 'usando agora'], ['', c30[0], 'aparelhos hoje'], ['', avg7, 'média/dia (7 dias)'], ['pk', peak, 'pico num dia (30 dias)']].map(s => `<div class="st ${s[0]}"><b>${s[1]}</b><small>${s[2]}</small></div>`).join('')}</div>`;
  const days = []; for (let i = 13; i >= 0; i--) days.push([nD(-i).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }), perDay(evs, i), i == 0]);
  h += '<h2 class="sec">Aparelhos por dia</h2>' + bars(days) + '<p class="muted" style="margin-top:6px">Aparelhos diferentes que usaram o app em cada dia (últimos 14 dias).</p>';
  const hrs = Array.from({ length: 24 }, () => new Set()), w7 = now - 7 * DAY, hn = new Date().getHours();
  evs.forEach(e => { if (e.t >= w7) hrs[new Date(e.t).getHours()].add(e.id + '|' + dkey(e.t)) });
  h += '<h2 class="sec">Horário de pico <span class="muted">(7 dias)</span></h2>' + bars(hrs.map((s, i) => [String(i).padStart(2, '0') + 'h', s.size, i == hn]));
  const pm = new Map(), vm = new Map(), t0 = nD(0).getTime();
  [...last.values()].forEach(e => { if (e.t >= t0) { pm.set(e.p, (pm.get(e.p) || 0) + 1); if (e.v) vm.set(e.v, (vm.get(e.v) || 0) + 1) } });
  if (pm.size) h += '<h2 class="sec">Plataformas <span class="muted">(hoje)</span></h2><div class="srv">' + [...pm].sort((a, b) => b[1] - a[1]).map(([k, n]) => `<div><span>${esc(PL[k] || k)}</span><b>${n}</b></div>`).join('') + '</div>';
  if (vm.size) h += '<h2 class="sec">Versões <span class="muted">(hoje)</span></h2><div class="srv">' + [...vm].sort((a, b) => b[1] - a[1]).map(([k, n]) => `<div><span>v${esc(k)}</span><b>${n}</b></div>`).join('') + '</div>';
  h += `<p class="muted" style="margin-top:20px">Os aparelhos enviam só uma marca de uso com um identificador que muda todo dia. O histórico fica neste navegador (até ${RET / DAY} dias) e o serviço de avisos guarda só ~12 h, então abra esta aba de vez em quando para não haver buracos.</p>`;
  $('#uBody').innerHTML = h;
}
async function usoRefresh() { $('#uUpd').textContent = 'atualizando…'; try { await fetchNew(); await dbDelOld(Date.now() - RET); UDATA = await dbAll(); usoRender(); $('#uUpd').textContent = 'atualizado ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) } catch (e) { $('#uUpd').textContent = 'erro: ' + e.message; if (UDATA.length) usoRender() } }
function usoAskKey(err) {
  $('#uBody').innerHTML = `<div class="card" style="max-width:560px"><h3>Chave de uso</h3><p class="hint">Cole o conteúdo de <b>ntplay-painel-chave.json</b> para ver os números de uso.</p><textarea class="in" id="uK" rows="4" style="font:12px ui-monospace,monospace"></textarea><div class="row" style="margin-top:10px;align-items:center"><input type="file" id="uKF" accept=".json,.txt" class="muted"><button class="bt pk" id="uGo" style="flex:0 0 auto">Usar chave</button></div>${err ? `<p class="err" style="margin-top:8px">${esc(err)}</p>` : ''}</div>`;
  $('#uKF').onchange = async e => { const f = e.target.files[0]; if (f) $('#uK').value = await f.text() };
  $('#uGo').onclick = async () => { try { UKEY = await loadUKey($('#uK').value.trim()); LS.ntAdminKey = $('#uK').value.trim(); ustarted = false; usoStart() } catch (e) { usoAskKey('Chave inválida.') } };
}
async function usoStart() {
  if (ustarted) return; if (!UKEY) { const k = LS.ntAdminKey; if (k) { try { UKEY = await loadUKey(k) } catch (e) { } } }
  if (!UKEY) return usoAskKey(); ustarted = true;
  try { UDATA = await dbAll() } catch (e) { } await usoRefresh(); clearInterval(UT); UT = setInterval(() => { if (!$('#t-uso').classList.contains('hide')) usoRefresh() }, 30000);
}

/* ================= entrar / sair ================= */
$('#lKeyF').onchange = async e => { const f = e.target.files[0]; if (f) $('#lKey').value = await f.text() };
$('#lGo').onclick = async () => {
  const a = $('#lAdm').value.trim(), k = $('#lKey').value.trim(); $('#lErr').textContent = '';
  if (!a) return $('#lErr').textContent = 'Cole a chave de administrador.';
  ADM = a; try { await adm('list') } catch (e) { ADM = ''; return $('#lErr').textContent = e.message }
  if (k) { try { UKEY = await loadUKey(k); LS.ntAdminKey = k } catch (e) { return $('#lErr').textContent = 'A chave de uso é inválida (a de administrador está certa).' } }
  SS.ntAdmKey = a; start();
};
$('#lAdm').onkeydown = e => { if (e.key == 'Enter') $('#lGo').click() };
function logout() { try { delete SS.ntAdmKey } catch (e) { } ADM = ''; GROUPS = []; GTS = 0; GLOADED = false; ustarted = false; clearInterval(UT); $('#vApp').classList.add('hide'); $('#vLogin').classList.remove('hide') }
$('#bOut').onclick = logout;
function start() { $('#vLogin').classList.add('hide'); $('#vApp').classList.remove('hide'); loadUsers().then(loadGroups); const t = (location.hash || '').slice(1); go(TABS.includes(t) ? t : TABS[0]) }
// os dois sites (painel e servidores) usam os mesmos dados: a cada 30 s confere se algo mudou no outro e atualiza,
// sem atrapalhar quem está digitando ou com uma janela aberta.
setInterval(async () => {
  if (!ADM || !GLOADED || GBUSY || document.hidden || !$('#modal').classList.contains('hide')) return;
  const a = document.activeElement; if (a && /INPUT|TEXTAREA|SELECT/.test(a.tagName) && a.value) return;
  try { const j = await adm('gget'); if ((j.ts || 0) != GTS) { GROUPS = Array.isArray(j.groups) ? j.groups : []; GTS = j.ts || 0; renderGrp() } } catch (e) { }
}, 30000);
/* ---- cópia de segurança (arquivo .json): backup e para levar os dados de um aparelho para outro ---- */
function backupSave() {
  const r = GROUPS.find(g => g.id == SRV_ID); if (!r) return toast('Ainda não há dados para salvar.', 1);
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([JSON.stringify({ ntplay: 'servidores', v: 1, t: Date.now(), data: r }, null, 1)], { type: 'application/json' }));
  a.download = 'ntplay-servidores-' + todayISO() + '.json'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  try { LS.ntSrvBackup = String(Date.now()) } catch (e) { } toast('Cópia salva nos Downloads.');
}
function backupOpen() {
  const i = document.createElement('input'); i.type = 'file'; i.accept = '.json,application/json';
  i.onchange = async () => {
    const f = i.files[0]; if (!f) return; let o; try { o = JSON.parse(await f.text()) } catch (e) { return toast('Arquivo inválido.', 1) }
    const r = o && o.ntplay == 'servidores' && o.data && o.data.id == SRV_ID ? o.data : null; if (!r) return toast('Este arquivo não é uma cópia dos servidores.', 1);
    const n = (r.servers || []).reduce((a, s) => a + (s.plans || []).length, 0);
    if (!await confirmBox('Abrir cópia', `A cópia de ${fmtDT(o.t)} tem ${n} cliente(s). Ela substitui tudo o que está neste site agora.`, 'Substituir', 1)) return;
    const next = clone(GROUPS).filter(g => g.id != SRV_ID); next.push(r); saveGroups(next, 'Cópia aberta.');
  };
  i.click();
}
if (LOCAL) {
  $('#bOut').classList.add('hide');
  const sp = $('.side .sp');
  sp.insertAdjacentHTML('beforebegin', `<button class="nav" id="bkC"><svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5"/></svg><span>Conta</span></button><button class="nav" id="bkS"><svg class="i" viewBox="0 0 24 24"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg>Salvar cópia</button><button class="nav" id="bkO"><svg class="i" viewBox="0 0 24 24"><path d="M12 21V9M7 14l5-5 5 5M5 3h14"/></svg>Abrir cópia</button>`);
  $('#bkS').onclick = backupSave; $('#bkO').onclick = backupOpen; $('#bkC').onclick = cloudBox; renderCloudBtn();
  $('.side .sp').insertAdjacentHTML('afterend', `<button class="nav" id="gtOut"><svg class="i" viewBox="0 0 24 24"><path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/></svg>Sair</button>`); $('#gtOut').onclick = gateLogout;
  // lembrete: sem cópia há mais de 7 dias
  setTimeout(() => { const b = +(LS.ntSrvBackup || 0); if (allRows().length && Date.now() - b > 7 * DAY) { if (!cloud()) toast('Faz mais de 7 dias da última cópia. Use "Salvar cópia".') } }, 4000);
}
/* ---- tela de entrada do servidores.html: usuário + senha da conta do cofre; sem isso o site não abre ----
   "Lembrar" marcado: fica salvo neste aparelho (localStorage). Desmarcado: só até fechar o navegador (sessionStorage). */
function gateShow() {
  $('#vApp').classList.add('hide'); $('#vGate').classList.remove('hide');
  const u = $('#gtU'); if (!u.value) u.value = LS.ntSrvUser || ''; (u.value ? $('#gtP') : u).focus();
}
async function gateLogin(e) {
  e.preventDefault();
  const u = normU($('#gtU').value), p = $('#gtP').value, rem = $('#gtR').checked, b = $('#gtGo'), E = $('#gtE'); E.textContent = '';
  if (!u || !p) return E.textContent = 'Digite o usuário e a senha.';
  b.disabled = true; b.innerHTML = '<span class="spin"></span>';
  try {
    const k = await derive(u, p), c = { u, auth: k.auth, enc: b64(k.enc), rem }, r = await cloudGet(c);
    if (r.dk) c.dk = b64(r.dk);
    const here = localGet(); // dados antigos só deste aparelho, se houver
    saveCloudCfg(c); if (!rem) { try { LS.removeItem(LKEY) } catch (e) { } }
    try { LS.ntSrvUser = u } catch (e) { }
    $('#gtP').value = ''; $('#vGate').classList.add('hide'); start();
    if (!r.groups || r.old) { CMIG = true; const g = r.groups || here.groups, ts = await cloudInit(c, g); localPut(g, ts); GLOADED = false; await loadGroups() }
    else localPut(r.groups, r.ts);
  } catch (err) { E.textContent = /incorretos/.test(err.message) ? 'Usuário ou senha incorretos.' : /expirou|validade/i.test(err.message) ? 'Esta conta venceu. Renove no painel principal.' : err.message }
  b.disabled = false; b.textContent = 'Continuar';
}
async function gateLogout() {
  if (!await confirmBox('Sair', 'Este aparelho esquece o login e a cópia dos dados. Tudo continua salvo na nuvem; para voltar, entre com usuário e senha.', 'Sair', 1)) return;
  [LS, SS].forEach(st => { try { st.removeItem(CLKEY); st.removeItem(LKEY) } catch (e) { } }); location.reload();
}
if (LOCAL) {
  $('#gtF').onsubmit = gateLogin; $('#gtFg').onclick = recoverBox;
  $('#gtEye').onclick = () => { const p = $('#gtP'); p.type = p.type == 'password' ? 'text' : 'password'; p.focus() };
}
(function boot() { if (LOCAL) { ADM = 'local'; return cloud() ? start() : gateShow() } const a = SS.ntAdmKey; if (a) { ADM = a; start() } else $('#vLogin').classList.remove('hide') })();
