-- limite de tentativas (chave de admin e login de cliente), usado pela função ntsync
create table if not exists public.ntplay_attempts (
  k text primary key,
  n integer not null default 0,
  until bigint not null default 0
);
alter table public.ntplay_attempts enable row level security;
revoke all on public.ntplay_attempts from anon, authenticated;

create or replace function public.ntplay_hit(p_k text, p_win bigint, p_now bigint)
returns integer language sql security definer set search_path = public as $$
  insert into ntplay_attempts as t (k, n, until) values (p_k, 1, p_now + p_win)
  on conflict (k) do update
    set n = case when t.until <= p_now then 1 else t.n + 1 end,
        until = p_now + p_win
  returning n;
$$;
revoke execute on function public.ntplay_hit(text, bigint, bigint) from public, anon, authenticated;
grant execute on function public.ntplay_hit(text, bigint, bigint) to service_role;
