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
const adm = (op, extra = {}) => call({ a: 'adm', adm: ADM, op, ...extra });

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
function closeModal() { $('#modal').classList.add('hide') }
$('#modal').onclick = e => { if (e.target.id == 'modal') closeModal() };
function confirmBox(title, text, okLabel, danger) { return new Promise(res => modal(`<h3>${esc(title)}</h3><p class="muted">${esc(text)}</p><div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt ${danger ? 'rd' : 'pk'}" data-ok>${esc(okLabel)}</button></div>`, m => { $('[data-no]', m).onclick = () => { closeModal(); res(false) }; $('[data-ok]', m).onclick = () => { closeModal(); res(true) } })) }
function askBox(title, text, fields, okLabel) {
  return new Promise(res => modal(`<h3>${esc(title)}</h3>${text ? `<p class="muted">${esc(text)}</p>` : ''}${fields.map(f => `<label class="f">${esc(f.label)}<input class="in" data-f="${f.k}" type="${f.type || 'text'}" value="${esc(f.v || '')}" ${f.max ? `maxlength="${f.max}"` : ''}></label>`).join('')}<div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>${esc(okLabel)}</button></div>`, m => {
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { const o = {}; $$('[data-f]', m).forEach(i => o[i.dataset.f] = i.value); closeModal(); res(o) };
    const first = $('[data-f]', m); first && first.focus();
  }))
}

/* ================= navegação ================= */
const TABS = ['geral', 'contas', 'clientes', 'sigma', 'listas', 'uso'];
function go(tab) {
  $$('.nav[data-tab]').forEach(b => b.classList.toggle('on', b.dataset.tab == tab));
  TABS.forEach(t => $('#t-' + t).classList.toggle('hide', t != tab));
  if (tab == 'uso') usoStart();
  if (tab == 'sigma') sigmaStart();
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
function renderAll() { renderGeral(); renderContas(); renderClientes(); fillAccSelect(); if (GLOADED) renderGroups() }

/* ================= sigma e grupos =================
   Grupos ficam no servidor (op gget/gset). Cada membro é
   { id, src:'nt', u } (conta NTPlay; vencimento vem de USERS) ou
   { id, src:'sg', u, exp:'AAAA-MM-DD', note } (cliente do painel Sigma, cadastrado à mão). */
const GCOLS = ['#e93a7d', '#60a5fa', '#34d399', '#fbbf24', '#a78bfa', '#f87171', '#22d3ee'];
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
  try { const j = await adm('gget'); GROUPS = Array.isArray(j.groups) ? j.groups : []; GTS = j.ts || 0; GLOADED = true; renderGroups() }
  catch (e) { $('#gBody').innerHTML = `<p class="err">${esc(e.message)}</p>`; if (/administrador/i.test(e.message)) logout() }
}
$('#gRef').onclick = () => { loadUsers(); loadGroups() };

async function saveGroups(next, okMsg) {
  if (GBUSY) { toast('Aguarde, salvando…'); return false }
  GBUSY = true;
  try { const j = await adm('gset', { groups: next, prev: GTS }); GROUPS = next; GTS = j.ts; renderGroups(); if (okMsg) toast(okMsg); return true }
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
  if (!m.exp) return { lab: 'Sem vencimento', cls: 'n', d: 1e9, sub: m.note || '' };
  const end = new Date(m.exp + 'T23:59:59-03:00').getTime(), d = (end - Date.now()) / DAY;
  return { lab: remText(d), cls: remCls(d), d, sub: [m.note, 'vence ' + fmtD(end)].filter(Boolean).join(' · ') };
}
const inAlert = i => !i.none && i.d <= ALERT_DAYS && i.d > -ALERT_PAST;

function renderGroups() {
  if (!GLOADED) return;
  // alerta + selo no menu
  const al = [];
  GROUPS.forEach(g => (g.members || []).forEach(m => { const i = memberInfo(m); if (inAlert(i)) al.push({ g, m, i }) }));
  al.sort((a, b) => a.i.d - b.i.d);
  const nExp = al.filter(x => x.i.d <= 0).length;
  const nb = $('#nGrp'); nb.textContent = al.length; nb.className = 'bdg ' + (nExp ? 'r' : 'y') + (al.length ? '' : ' hide');
  $('#gAlert').innerHTML = al.length ? `<div class="card galert ${nExp ? 'bad' : ''}"><div class="gah"><svg class="i" viewBox="0 0 24 24" style="color:var(--${nExp ? 'rd' : 'yl'})"><path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01"/></svg>
      <h3>Atenção aos vencimentos</h3><span class="muted">${al.length - nExp} vence${al.length - nExp == 1 ? '' : 'm'} em até ${ALERT_DAYS} dias${nExp ? ` · <b style="color:var(--rd)">${nExp} vencido${nExp > 1 ? 's' : ''}</b>` : ''}</span></div>
      <div class="gai">${al.map(({ g, m, i }) => `<button class="gm ${i.cls}" data-gs="${esc(g.id)}" style="text-align:left;cursor:pointer"><span class="src ${m.src == 'nt' ? 'nt' : 'sg'}">${m.src == 'nt' ? 'NTPLAY' : 'SIGMA'}</span><div class="n"><b>${esc(m.u)}</b><small>${esc(g.name)}${i.sub ? ' · ' + esc(i.sub) : ''}</small></div><span class="bdg ${i.cls}">${esc(i.lab)}</span></button>`).join('')}</div></div>` : '';

  if (!GROUPS.length) { $('#gTabs').innerHTML = ''; $('#gBody').innerHTML = '<div class="empty card">Nenhum grupo ainda. Crie o primeiro em <b>Novo grupo</b>.</div>'; return }
  if (!GROUPS.some(g => g.id == GSEL)) GSEL = GROUPS[0].id;
  // abas dos grupos
  $('#gTabs').innerHTML = GROUPS.map(g => {
    const inf = (g.members || []).map(memberInfo), w = inf.filter(inAlert), wr = w.some(i => i.d <= 0);
    return `<button class="gtab ${g.id == GSEL ? 'on' : ''}" data-gs="${esc(g.id)}" style="--c:${esc(g.color || GCOLS[0])}"><i></i>${esc(g.name)}<span class="bdg n">${inf.length}</span>${w.length ? `<span class="w ${wr ? 'r' : ''}" title="${w.length} com vencimento próximo"></span>` : ''}</button>`;
  }).join('');
  // grupo aberto: NTPlay e Sigma em colunas separadas
  const g = GROUPS.find(x => x.id == GSEL), mem = (g.members || []).map(m => ({ m, i: memberInfo(m) })).sort((a, b) => a.i.d - b.i.d);
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
    <p class="muted" style="margin-top:10px">Os clientes Sigma são cadastrados à mão: quando renovar no painel Sigma, atualize o vencimento aqui no lápis.</p>`;
}

function groupBox(g) {
  let color = (g && g.color) || GCOLS[GROUPS.length % GCOLS.length];
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
  return new Promise(res => modal(`<h3>${mem ? 'Editar cliente Sigma' : 'Cliente do painel Sigma'}</h3>
    <p class="muted">Copie do painel Sigma o usuário e o vencimento do cliente.</p>
    <label class="f">Usuário no Sigma<input class="in" data-f="u" maxlength="60" value="${esc(mem ? mem.u : '')}" autocomplete="off"></label>
    <label class="f">Vencimento (opcional)<input class="in" type="date" data-f="exp" value="${esc(mem ? mem.exp || '' : '')}"></label>
    <label class="f">Nota (opcional)<input class="in" data-f="note" maxlength="80" value="${esc(mem ? mem.note || '' : '')}" placeholder="ex.: WhatsApp, plano, aparelho"></label>
    <div class="acts"><button class="bt" data-no>Cancelar</button><button class="bt pk" data-ok>Salvar</button></div>`, m => {
    $('[data-no]', m).onclick = () => { closeModal(); res(null) };
    $('[data-ok]', m).onclick = () => { const u = $('[data-f="u"]', m).value.trim(); if (!u) return toast('Informe o usuário do Sigma.', 1); closeModal(); res({ u, exp: $('[data-f="exp"]', m).value, note: $('[data-f="note"]', m).value.trim() }) };
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
    ['#a78bfa', 'users', 'Total de contas', tot, 'cadastradas'],
    ['#34d399', 'ok', 'Clientes ativos', act.length, pct + '% do total'],
    ['#fbbf24', 'clock', 'Vencendo (≤7 dias)', soon.length, soon.length ? 'renove logo' : 'nenhuma pendente'],
    ['#f87171', 'x', 'Expirados', exp.length, 'bloqueados no app'],
    ['#60a5fa', 'wifi', 'Aparelhos online', onl, 'últimos 10 min']
  ].map(k => `<div class="kpi" style="--c:${k[0]}"><div class="ic">${svgI(k[1])}</div><small>${k[2]}</small><b>${k[3]}</b><em>${k[4]}</em></div>`).join('');
  drawDonut([['Ativos', act.length - soon.length, '#34d399', '#10b981'], ['Vencendo', soon.length, '#fbbf24', '#f59e0b'], ['Expirados', exp.length, '#fb7185', '#e11d48']], tot);
  drawTimeline();
  drawRows();
  KANIM = true;
}

function drawDonut(parts, tot) {
  const R = 78, C = 2 * Math.PI * R, live = parts.filter(p => p[1] > 0), gap = live.length > 1 ? 10 : 0;
  let off = 0, defs = '', arcs = '';
  live.forEach((p, i) => {
    const len = Math.max(0, p[1] / tot * C - gap);
    defs += `<linearGradient id="dg${i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${p[2]}"/><stop offset="1" stop-color="${p[3]}"/></linearGradient>`;
    arcs += `<circle cx="100" cy="100" r="${R}" fill="none" stroke="url(#dg${i})" stroke-width="18" stroke-linecap="round" stroke-dasharray="${KANIM ? len : 0} ${C}" data-len="${len}" stroke-dashoffset="${-off}" transform="rotate(-90 100 100)" filter="url(#glow)" style="transition:stroke-dasharray .9s cubic-bezier(.2,.8,.2,1)"><title>${p[0]}: ${p[1]}</title></circle>`;
    off += p[1] / tot * C;
  });
  $('#kDonut').innerHTML = `<svg viewBox="0 0 200 200" role="img" aria-label="Status das contas">
    <defs>${defs}<filter id="glow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>
    <circle cx="100" cy="100" r="${R}" fill="none" stroke="#ffffff10" stroke-width="18"/>${arcs}
    <text x="100" y="98" text-anchor="middle" fill="var(--tx)" font-size="40" font-weight="800">${tot}</text>
    <text x="100" y="122" text-anchor="middle" fill="var(--mut)" font-size="12">contas</text></svg>
    <div class="lg">${parts.map(p => `<span><i style="background:linear-gradient(135deg,${p[2]},${p[3]})"></i>${p[0]} <b>${p[1]}</b></span>`).join('')}</div>`;
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
    <defs><linearGradient id="tb" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff7aae"/><stop offset="1" stop-color="#7c3aed"/></linearGradient>
    <linearGradient id="ta" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#60a5fa" stop-opacity=".28"/><stop offset="1" stop-color="#60a5fa" stop-opacity="0"/></linearGradient></defs>
    ${ticks.map(t => `<line x1="${pl}" x2="${W - pr}" y1="${y(t)}" y2="${y(t)}" stroke="#ffffff12" ${t ? 'stroke-dasharray="3 4"' : ''}/><text x="${pl - 8}" y="${y(t) + 4}" text-anchor="end" fill="var(--mut)" font-size="11">${t}</text>`).join('')}
    <path d="${area}" fill="url(#ta)"/><path d="${line}" fill="none" stroke="#60a5fa" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
    ${B.map((b, i) => b.n ? `<rect x="${(x(i) + (bw - bwi) / 2).toFixed(1)}" y="${y(b.n).toFixed(1)}" width="${bwi.toFixed(1)}" height="${(y(0) - y(b.n)).toFixed(1)}" rx="${Math.min(6, bwi / 2)}" fill="url(#tb)"/>` : '').join('')}
    ${B.map((b, i) => i % every == 0 ? `<text x="${(x(i) + bw / 2).toFixed(1)}" y="${H - 12}" text-anchor="middle" fill="var(--mut)" font-size="11">${lab(i)}</text>` : '').join('')}
    <line id="khl" x1="0" x2="0" y1="${pt}" y2="${y(0)}" stroke="#ffffff40" visibility="hidden"/>
    ${B.map((b, i) => `<rect class="hz" data-i="${i}" x="${x(i)}" y="${pt}" width="${bw}" height="${ih}" fill="transparent"/>`).join('')}
  </svg><div class="lg muted" style="display:flex;gap:14px;margin-top:6px"><span><i style="display:inline-block;width:10px;height:10px;border-radius:3px;background:linear-gradient(#ff7aae,#7c3aed);margin-right:5px"></i>vencem no ${week ? 'semana' : 'dia'}</span><span><i style="display:inline-block;width:14px;height:2px;background:#60a5fa;vertical-align:middle;margin-right:5px"></i>total vencido até a data (sem renovar)</span></div><div class="ktip hide" id="ktip"></div>`;
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
  const col = { g: '#34d399', y: '#fbbf24', r: '#f87171' };
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
  $('#gLast').innerHTML = last.length ? last.map(u => `<div class="li"><div class="av" style="width:30px;height:30px;font-size:12px">${esc(u.username[0])}</div><div class="n"><b>${esc(u.username)}</b><small>${Object.keys(u.devices || {}).length} aparelho(s)</small></div><span class="muted">${ago(u.last_seen)}</span></div>`).join('') : '<p class="muted">Ninguém entrou ainda.</p>';
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
function start() { $('#vLogin').classList.add('hide'); $('#vApp').classList.remove('hide'); loadUsers().then(loadGroups); const t = (location.hash || '').slice(1); go(TABS.includes(t) ? t : 'geral') }
(function boot() { const a = SS.ntAdmKey; if (a) { ADM = a; start() } else $('#vLogin').classList.remove('hide') })();
