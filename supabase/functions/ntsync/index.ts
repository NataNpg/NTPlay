import { createClient } from "jsr:@supabase/supabase-js@2";

// NTPlay: acesso por conta (criada pelo administrador, com validade) + sincronização de um blob JÁ CRIPTOGRAFADO no aparelho.
// O servidor nunca vê a senha nem o conteúdo.
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const H = { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type, authorization" };
const out = (o: unknown, s = 200) => new Response(JSON.stringify(o), { status: s, headers: H });
const hex = (b: ArrayBuffer) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
const sha = async (s: string) => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
const same = (a: string, b: string) => { if (a.length !== b.length) return false; let r = 0; for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i); return r === 0; };
const MAX_DATA = 2_000_000;
const MAX_GROUPS = 500_000;
const DAY = 86_400_000;
const ADMIN = Deno.env.get("NT_ADMIN_KEY") ?? "";
const userOk = (u: string) => /^[a-z0-9._-]{3,32}$/.test(u);
const fmt = (ms: number) => new Date(ms).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });
// "AAAA-MM-DD" -> fim desse dia no horário de Brasília (23:59:59 -03:00)
const endOfDay = (d: string) => /^\d{4}-\d{2}-\d{2}$/.test(d) ? new Date(d + "T23:59:59-03:00").getTime() : NaN;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return out({});
  if (req.method !== "POST") return out({ err: "metodo" }, 405);
  let b: any;
  try { b = await req.json(); } catch { return out({ err: "json" }, 400); }
  const a = String(b.a ?? "");

  // ---------------- painel do administrador ----------------
  if (a === "adm") {
    if (ADMIN.length < 16 || !same(String(b.adm ?? ""), ADMIN)) return out({ err: "admin", msg: "Chave de administrador incorreta." }, 401);
    const op = String(b.op ?? "");
    const u = String(b.u ?? "").trim().toLowerCase();
    const days = Math.max(1, Math.min(3650, Math.floor(Number(b.days) || 0)));
    if (op === "list") {
      const { data } = await db.from("ntplay_accounts").select("username,expires_at,note,created_at,last_seen,devices,max_devices").order("created_at", { ascending: false });
      return out({ ok: true, users: data ?? [] });
    }
    // grupos do painel (clientes NTPlay + clientes do painel Sigma, cadastrados à mão)
    if (op === "gget") {
      const { data } = await db.from("ntplay_admin_kv").select("value,ts").eq("key", "groups").maybeSingle();
      return out({ ok: true, groups: data?.value ?? [], ts: Number(data?.ts ?? 0) });
    }
    if (op === "gset") {
      const g = b.groups;
      if (!Array.isArray(g)) return out({ err: "dados", msg: "Grupos inválidos." }, 400);
      if (JSON.stringify(g).length > MAX_GROUPS) return out({ err: "grande", msg: "Grupos grandes demais." }, 413);
      const { data: cur } = await db.from("ntplay_admin_kv").select("ts").eq("key", "groups").maybeSingle();
      if (cur && Number(cur.ts) !== Number(b.prev ?? 0)) return out({ err: "conflito", msg: "Os grupos foram alterados em outro aparelho. Clique em Atualizar e refaça a mudança." }, 409);
      const ts = Date.now();
      const { error } = await db.from("ntplay_admin_kv").upsert({ key: "groups", value: g, ts });
      if (error) return out({ err: "db", msg: "Erro ao salvar os grupos." }, 500);
      return out({ ok: true, ts });
    }
    if (!userOk(u)) return out({ err: "usuario", msg: "Usuário: 3 a 32 letras/números (. _ - permitidos)." }, 400);
    if (op === "create") {
      const k = String(b.k ?? "");
      if (k.length < 20 || k.length > 128) return out({ err: "dados", msg: "Dados incompletos." }, 400);
      let exp = Date.now() + days * DAY;
      if (b.date) { exp = endOfDay(String(b.date)); if (!(exp > Date.now())) return out({ err: "data", msg: "Escolha uma data de validade no futuro." }, 400); }
      else if (!b.days) return out({ err: "dados", msg: "Informe a duração ou a data." }, 400);
      const salt = crypto.randomUUID();
      const { error } = await db.from("ntplay_accounts").insert({ username: u, salt, hash: await sha(salt + k), expires_at: new Date(exp).toISOString(), note: String(b.note ?? "").slice(0, 80) });
      if (error) return out({ err: "existe", msg: "Este usuário já existe." }, 409);
      return out({ ok: true });
    }
    const { data: row } = await db.from("ntplay_accounts").select("expires_at").eq("username", u).maybeSingle();
    if (!row) return out({ err: "nao", msg: "Usuário não encontrado." }, 404);
    if (op === "renew") {
      const cur = row.expires_at ? new Date(row.expires_at).getTime() : 0;
      const base = Math.max(Date.now(), cur);
      await db.from("ntplay_accounts").update({ expires_at: new Date(base + days * DAY).toISOString() }).eq("username", u);
      return out({ ok: true });
    }
    if (op === "setexp") {
      const exp = endOfDay(String(b.date ?? ""));
      if (isNaN(exp)) return out({ err: "data", msg: "Data inválida." }, 400);
      await db.from("ntplay_accounts").update({ expires_at: new Date(exp).toISOString() }).eq("username", u);
      return out({ ok: true });
    }
    if (op === "note") { await db.from("ntplay_accounts").update({ note: String(b.note ?? "").slice(0, 80) }).eq("username", u); return out({ ok: true }); }
    if (op === "setmax") { const n = Math.max(0, Math.min(20, Math.floor(Number(b.max) || 0))); await db.from("ntplay_accounts").update({ max_devices: n }).eq("username", u); return out({ ok: true, max: n }); }
    if (op === "cleardev") { await db.from("ntplay_accounts").update({ devices: {} }).eq("username", u); return out({ ok: true }); }
    if (op === "setpass") {
      const k = String(b.k ?? "");
      if (k.length < 20 || k.length > 128) return out({ err: "dados" }, 400);
      const salt = crypto.randomUUID();
      await db.from("ntplay_accounts").update({ salt, hash: await sha(salt + k), fails: 0, locked_until: null }).eq("username", u);
      return out({ ok: true });
    }
    if (op === "revoke") { await db.from("ntplay_accounts").update({ expires_at: new Date(Date.now() - 1000).toISOString() }).eq("username", u); return out({ ok: true }); }
    if (op === "delete") { await db.from("ntplay_accounts").delete().eq("username", u); return out({ ok: true }); }
    return out({ err: "acao" }, 400);
  }

  // ---------------- aplicativo ----------------
  const u = String(b.u ?? "").trim().toLowerCase();
  const k = String(b.k ?? "");
  if (!userOk(u)) return out({ err: "usuario", msg: "Usuário ou senha incorretos." }, 401);
  if (k.length < 20 || k.length > 128) return out({ err: "chave", msg: "Usuário ou senha incorretos." }, 401);
  if (a === "reg") return out({ err: "fechado", msg: "Contas são criadas pelo administrador. Peça seu acesso." }, 403);

  const { data: row } = await db.from("ntplay_accounts").select("*").eq("username", u).maybeSingle();
  if (!row) return out({ err: "login", msg: "Usuário ou senha incorretos." }, 401);
  if (row.locked_until && new Date(row.locked_until).getTime() > Date.now()) return out({ err: "bloqueado", msg: "Muitas tentativas. Tente de novo em alguns minutos." }, 429);
  const ok = same(await sha(row.salt + k), row.hash);
  if (!ok) {
    const f = (row.fails ?? 0) + 1;
    await db.from("ntplay_accounts").update(f >= 8 ? { fails: 0, locked_until: new Date(Date.now() + 15 * 60_000).toISOString() } : { fails: f }).eq("username", u);
    return out({ err: "login", msg: "Usuário ou senha incorretos." }, 401);
  }
  const exp = row.expires_at ? new Date(row.expires_at).getTime() : 0;
  if (!exp || exp < Date.now()) return out({ err: "expirado", msg: exp ? "Seu acesso expirou em " + fmt(exp) + ". Fale com quem forneceu o acesso para renovar." : "Acesso sem validade definida. Fale com quem forneceu o acesso." }, 403);

  // aparelhos que usam esta conta: { id: { t: último contato (ms), n: nome } } (guarda 30 dias, no máximo 20)
  const upd: Record<string, unknown> = { fails: 0, locked_until: null, last_seen: new Date().toISOString() };
  const dev = String(b.dev ?? "").replace(/[^a-zA-Z0-9]/g, "").slice(0, 32);
  if (!dev && Number(row.max_devices ?? 0) > 0) return out({ err: "limite", msg: "Atualize o aplicativo para continuar usando esta conta." }, 403);
  if (dev) {
    const now = Date.now();
    const m: Record<string, { t: number; n: string }> = {};
    const old = (row.devices ?? {}) as Record<string, { t: number; n: string }>;
    for (const [id, v] of Object.entries(old)) if (v && now - Number(v.t) < 30 * DAY) m[id] = v;
    // limite de aparelhos (0 = sem limite): aparelho novo é recusado quando a conta já está cheia
    const lim = Number(row.max_devices ?? 0);
    if (lim > 0 && !m[dev] && Object.keys(m).length >= lim) return out({ err: "limite", msg: "Esta conta já está sendo usada em " + lim + " aparelho(s). Peça ao administrador para liberar um aparelho." }, 403);
    m[dev] = { t: now, n: String(b.dn ?? "").slice(0, 40) };
    const ids = Object.keys(m).sort((x, y) => m[y].t - m[x].t).slice(0, 20);
    const keep: Record<string, { t: number; n: string }> = {};
    for (const id of ids) keep[id] = m[id];
    upd.devices = keep;
  }
  await db.from("ntplay_accounts").update(upd).eq("username", u);

  if (a === "get") return out({ ok: true, data: row.data, ts: row.ts, exp });
  if (a === "put") {
    const d = String(b.data ?? "");
    if (d.length > MAX_DATA) return out({ err: "grande", msg: "Dados grandes demais." }, 413);
    const ts = Number(b.ts) || Date.now();
    await db.from("ntplay_accounts").update({ data: d, ts }).eq("username", u);
    return out({ ok: true, ts, exp });
  }
  return out({ err: "acao" }, 400);
});
