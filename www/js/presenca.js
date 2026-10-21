/* Presença: avisa ao painel do desenvolvedor (admin.html) que este aparelho está usando o NTPlay.
   Vai criptografado (ECDH P-256 + AES-GCM) — só o painel, com a chave privada, consegue ler.
   Envia: nome do aparelho, plataforma, versão, se está assistindo e as listas (nome, servidor, itens, vencimento, telas).
   NÃO envia usuário, senha nem o link completo das listas. Pode ser desligado em Listas M3U → Preferências. */
const PRES = { pub: 'BHI9Apeb-q1NfFyzK1M50pJL7IWEGH0pJbwT0FXFtTD6B4986Gp-qjGP0IeKLIEMFH_UfcvuDGTL3GqAs3k8g4A', topic: 'ntplay-pres-381affd473dafb5cac', t: null, last: 0 };
const pB64 = (a) => btoa(String.fromCharCode(...new Uint8Array(a))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const pUnb64 = (s) => { s = s.replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '='; return Uint8Array.from(atob(s), (c) => c.charCodeAt(0)); };
function presDev() { let d = LS.ntDev; if (!d) { d = 'd' + [...crypto.getRandomValues(new Uint8Array(8))].map((x) => x.toString(16).padStart(2, '0')).join(''); LS.ntDev = d; } return d; }
function presPlat() {
  if (window.bobNative) return 'pc';
  try { if (window.Capacitor && Capacitor.getPlatform) { const p = Capacitor.getPlatform(); if (p != 'web') return document.documentElement.classList.contains('tv') ? 'androidtv' : p; } } catch (e) {}
  return /Android|iPhone|iPad/i.test(navigator.userAgent) ? 'web-celular' : 'web';
}
async function presSeal(obj) {
  const C = crypto.subtle, pub = await C.importKey('raw', pUnb64(PRES.pub), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const eph = await C.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
  const sh = await C.deriveBits({ name: 'ECDH', public: pub }, eph.privateKey, 256);
  const key = await C.importKey('raw', await C.digest('SHA-256', sh), 'AES-GCM', false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await C.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(obj)));
  return 'ntp1.' + pB64(await C.exportKey('raw', eph.publicKey)) + '.' + pB64(iv) + '.' + pB64(ct);
}
async function presSend(off) {
  if (LS.ntPres == '0' || !window.crypto || !crypto.subtle) return;
  try {
    const all = ((await dbAll().catch(() => [])) || []).slice(0, 12), act = getActive();
    const ls = all.map((o) => { const a = ACCT[o.id] || {}; return { n: o.name, h: o.url ? hostOf(o.url) : 'arquivo', c: o.count, on: act.includes(o.id) ? 1 : 0, e: a.exp || 0, s: a.status || '', m: a.max || 0, a: a.act || 0 }; });
    const x = S.cur, w = x && S.view == 'player' ? { k: x.e.kind, g: x.e.kind == 'l' ? x.e.group : '' } : null;
    const msg = await presSeal({ id: presDev(), nm: LS.ntDevName || '', p: presPlat(), v: window.NT_VER || '', os: navigator.userAgent.replace(/^.*?\((.*?)\).*$/, '$1').slice(0, 80), t: Date.now(), off: off ? 1 : 0, w, ls });
    PRES.last = Date.now();
    await fetch('https://ntfy.sh/' + PRES.topic, { method: 'POST', body: msg, keepalive: !!off, headers: { 'X-Firebase': 'no' } });
  } catch (e) {}
}
function presStart() {
  clearInterval(PRES.t);
  if (LS.ntPres == '0') return;
  setTimeout(() => presSend(), 6000);
  PRES.t = setInterval(() => presSend(), 5 * 60000);
}
// avisa logo ao começar a assistir (no máximo 1 vez por minuto)
v.addEventListener('playing', () => { if (Date.now() - PRES.last > 60000) presSend(); });
addEventListener('beforeunload', () => presSend(1));
if (window.bobNative && bobNative.version) bobNative.version().then((x) => { window.NT_VER = x; }).catch(() => {});
$('#ppres').checked = LS.ntPres != '0';
$('#ppres').onchange = (e) => { LS.ntPres = e.target.checked ? '1' : '0'; presStart(); };
$('#pdev').value = LS.ntDevName || '';
$('#pdev').onchange = (e) => { LS.ntDevName = e.target.value.trim().slice(0, 40); presSend(); };
presStart();
