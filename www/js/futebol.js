/* ⚽ Futebol: jogos do dia (placar da ESPN, sem chave) + canais da sua lista que passam os jogos.
   Carregado antes do app.js; só usa as funções do app dentro das funções abaixo. */
const FUT = { off: 0, data: {}, busy: {}, timer: null };
const FUT_L = [
  ['bra.1', 'Brasileirão Série A'], ['bra.2', 'Brasileirão Série B'], ['bra.3', 'Brasileirão Série C'], ['bra.copa_do_brazil', 'Copa do Brasil'],
  ['conmebol.libertadores', 'Libertadores'], ['conmebol.sudamericana', 'Sul-Americana'], ['conmebol.recopa', 'Recopa'],
  ['bra.camp.paulista', 'Paulistão'], ['bra.camp.carioca', 'Carioca'], ['bra.camp.mineiro', 'Mineiro'], ['bra.camp.gaucho', 'Gauchão'],
  ['fifa.world', 'Copa do Mundo'], ['fifa.worldq.conmebol', 'Eliminatórias'], ['fifa.cwc', 'Mundial de Clubes'], ['conmebol.america', 'Copa América'], ['fifa.friendly', 'Amistosos'],
  ['uefa.champions', 'Champions League'], ['uefa.europa', 'Europa League'], ['uefa.europa.conf', 'Conference League'], ['uefa.nations', 'Liga das Nações'],
  ['eng.1', 'Premier League'], ['esp.1', 'La Liga'], ['ita.1', 'Serie A (Itália)'], ['ger.1', 'Bundesliga'], ['fra.1', 'Ligue 1'], ['por.1', 'Liga Portugal'],
  ['ned.1', 'Eredivisie'], ['eng.fa', 'Copa da Inglaterra'], ['esp.copa_del_rey', 'Copa do Rei'], ['arg.1', 'Argentina'], ['ksa.1', 'Liga Saudita'], ['usa.1', 'MLS'], ['mex.1', 'Liga MX']
];
// canais de esporte na lista (pelo nome do canal ou do grupo)
const FUT_SP = /esporte|sport|futebol|futbol|soccer|premiere|\bespn|tnt sports|band ?sports|sportv|combate|caz[eé]|dazn|paramount|\bgoat\b|\bppv\b|jogos|campeonato|brasileir|libertadores|champions|copa do|nosso futebol|onefootball|conmebol|\beleven|bein|sky sport|tyc|fox sports|xsports|nsports|\bge\b.?tv/i;
const FUT_EV = /jogos|eventos?|ppv|futebol|campeonato|brasileir|premiere|copa|ao vivo agora/i;
const futDay = (off) => { const d = new Date(Date.now() + off * 864e5); return d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0'); };
const futSportCh = () => S.live.filter((e) => FUT_SP.test(e.title + ' ' + e.group));
function futTeam(c) {
  const t = c.team || {};
  return { n: t.shortDisplayName || t.displayName || t.name || '?', full: t.displayName || t.name || '', logo: t.logo || (t.logos && t.logos[0] && t.logos[0].href) || '', sc: c.score != null ? String(c.score) : '', win: !!c.winner, home: c.homeAway == 'home' };
}
async function futLoad(off, force) {
  const d = futDay(off), old = FUT.data[d];
  const live = old && old.games.some((g) => g.st == 'in');
  if (!force && old && Date.now() - old.ts < (live ? 60000 : 10 * 60000)) return old;
  if (FUT.busy[d]) return FUT.busy[d];
  FUT.busy[d] = (async () => {
    const res = await Promise.all(FUT_L.map(([id, nm], i) =>
      fetch('https://site.api.espn.com/apis/site/v2/sports/soccer/' + id + '/scoreboard?dates=' + d + '&lang=pt&region=br')
        .then((r) => (r.ok ? r.json() : null)).catch(() => null)
        .then((j) => ((j && j.events) || []).map((ev) => {
          const cp = (ev.competitions || [])[0] || {}, st = (ev.status || cp.status || {}), ty = st.type || {};
          const cs = (cp.competitors || []).map(futTeam), h = cs.find((x) => x.home) || cs[0] || {}, a = cs.find((x) => !x.home) || cs[1] || {};
          const tv = [].concat(...(cp.broadcasts || []).map((b) => b.names || []), ...(cp.geoBroadcasts || []).map((b) => [b.media && b.media.shortName].filter(Boolean)));
          const lg = (j.leagues || [])[0] || {};
          return { id: ev.id, li: i, lg: nm, lgLogo: (lg.logos && lg.logos[0] && lg.logos[0].href) || '', t: new Date(ev.date), st: ty.state || 'pre', sn: ty.name || '', clock: st.displayClock || '', h, a, venue: (cp.venue && cp.venue.fullName) || '', city: (cp.venue && cp.venue.address && cp.venue.address.city) || '', tv: [...new Set(tv)] };
        }))));
    const seen = new Set(), games = [];
    res.flat().forEach((g) => { if (!seen.has(g.id)) { seen.add(g.id); games.push(g); } });
    games.sort((x, y) => x.li - y.li || x.t - y.t);
    const out = { ts: Date.now(), games, err: res.every((r) => !r.length) && !games.length ? 'sem jogos ou sem conexão' : '' };
    FUT.data[d] = out; delete FUT.busy[d];
    return out;
  })();
  return FUT.busy[d];
}
function futStatus(g) {
  if (g.st == 'in') return '<span class="fl1">● AO VIVO' + (g.clock && g.clock != "0'" ? ' ' + esc(g.clock) : '') + '</span>';
  if (/POSTPONED/.test(g.sn)) return 'Adiado';
  if (/CANCELED|CANCELLED/.test(g.sn)) return 'Cancelado';
  if (g.st == 'post') return /PEN|SHOOTOUT/.test(g.sn) ? 'Encerrado (pênaltis)' : 'Encerrado';
  return g.t.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}
// canais da lista para este jogo: primeiro os que têm os dois times no nome (canais de evento), depois um dos times
const futKey = (s) => norm(s).replace(/\b(fc|sc|ec|cf|ac|afc|club|clube|de|da|do|futebol|football|esporte|sport)\b/g, ' ').replace(/\s+/g, ' ').trim();
function futChannels(g, sp) {
  const ks = (t) => [...new Set([futKey(t.n), futKey(t.full)].filter((k) => k.length >= 3))];
  const H = ks(g.h), A = ks(g.a), has = (n, K) => K.some((k) => (' ' + n + ' ').includes(' ' + k + ' '));
  const both = [], one = [];
  for (const e of sp) {
    const n = futKey(e.title);
    const h = has(n, H), a = has(n, A);
    if (h && a) both.push(e); else if ((h || a) && FUT_EV.test(e.group + ' ' + e.title)) one.push(e);
  }
  const tvk = g.tv.map(futKey).filter((k) => k.length >= 3), byTv = tvk.length ? sp.filter((e) => tvk.some((k) => futKey(e.title).startsWith(k))).slice(0, 3) : [];
  return [...new Set([...both, ...one, ...byTv])].slice(0, 6);
}
function futCard(g, sp) {
  const ch = futChannels(g, sp);
  const tm = (t) => `<div class="ftm${t.win ? ' w' : ''}">${t.logo ? `<img loading="lazy" referrerpolicy="no-referrer" src="${esc(t.logo)}" onerror="this.remove()">` : '<i></i>'}<span>${esc(t.n)}</span><b>${g.st == 'pre' ? '' : esc(t.sc)}</b></div>`;
  return `<div class="fc${g.st == 'in' ? ' on' : ''}"><div class="fst">${futStatus(g)}</div>${tm(g.h)}${tm(g.a)}
    <div class="fw">${esc([g.venue, g.city].filter(Boolean).join(' • '))}${g.tv.length ? '<br>TV: ' + esc(g.tv.join(', ')) : ''}</div>
    <div class="fch">${ch.length ? ch.map((e) => `<button class="chip fk" data-k="${esc(e.key)}" title="${esc(e.group)}">▶ ${esc(e.title)}</button>`).join('') : '<small>Procure nos canais de esporte abaixo</small>'}</div></div>`;
}
function futPage() {
  const d = futDay(FUT.off), D = FUT.data[d], sp = futSportCh();
  const days = [[-1, 'Ontem'], [0, 'Hoje'], [1, 'Amanhã'], [2, new Date(Date.now() + 2 * 864e5).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit' })]];
  let h = `<div class="fut"><div class="fh"><h2 class="pgt">⚽ Jogos ${FUT.off == 0 ? 'de hoje' : FUT.off == -1 ? 'de ontem' : FUT.off == 1 ? 'de amanhã' : ''}<small>${((x) => x[0].toUpperCase() + x.slice(1))(new Date(Date.now() + FUT.off * 864e5).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }))}</small></h2>
    <div class="fd">${days.map(([o, n]) => `<button class="ch2${FUT.off == o ? ' on' : ''}" data-fo="${o}">${esc(n)}</button>`).join('')}<button class="ch2" data-fr="1" title="Atualizar placares">↻</button></div></div>`;
  if (!D) { h += '<div class="hint">Buscando os jogos na internet…</div>'; futLoad(FUT.off).then(() => { if (S.view == 'browse' && S.tab == 'FUTEBOL') renderBrowse(); }); }
  else if (!D.games.length) h += `<div class="empty">Nenhum jogo encontrado ${FUT.off == 0 ? 'hoje' : 'neste dia'} nos campeonatos acompanhados${D.err ? ' (' + esc(D.err) + ')' : ''}.</div>`;
  else {
    const lv = D.games.filter((g) => g.st == 'in').length;
    h += `<div class="fsum">${D.games.length} jogos${lv ? ` • <b class="fl1">● ${lv} ao vivo agora</b>` : ''} • atualizado às ${new Date(D.ts).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} • fonte: ESPN</div>`;
    const by = new Map();
    D.games.forEach((g) => { if (!by.has(g.lg)) by.set(g.lg, []); by.get(g.lg).push(g); });
    for (const [lg, a] of by) h += `<section class="flg"><div class="flh">${a[0].lgLogo ? `<img src="${esc(a[0].lgLogo)}" referrerpolicy="no-referrer" onerror="this.remove()">` : ''}<b>${esc(lg)}</b><span>${a.length} jogo${a.length > 1 ? 's' : ''}</span></div><div class="fgr">${a.map((g) => futCard(g, sp)).join('')}</div></section>`;
  }
  const ev = sp.filter((e) => FUT_EV.test(e.group)), rest = sp.filter((e) => !FUT_EV.test(e.group));
  h += '</div>' + row('Canais de jogos e eventos', ev.slice(0, 40).map(wcard), ev.length + ' canais') + row('Canais de esporte', rest.slice(0, 40).map(wcard), rest.length + ' canais');
  if (!sp.length && S.all.length) h += '<div class="empty">Não encontrei canais de esporte na sua lista (Premiere, SporTV, ESPN, TNT Sports…).</div>';
  clearInterval(FUT.timer);
  if (D && D.games.some((g) => g.st == 'in')) FUT.timer = setInterval(() => { if (S.view != 'browse' || S.tab != 'FUTEBOL' || S.q) return clearInterval(FUT.timer); futLoad(FUT.off, true).then(() => renderBrowse()); }, 60000);
  return h;
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-fo],[data-fr]');
  if (!b) return;
  if (b.dataset.fo != null) FUT.off = +b.dataset.fo;
  const d = futDay(FUT.off);
  if (b.dataset.fr) delete FUT.data[d];
  renderBrowse();
  if (b.dataset.fr) toast('Atualizando placares…');
});
