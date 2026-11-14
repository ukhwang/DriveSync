-- DriveSync initial schema (기획서 3-② 핵심 데이터 모델 6 entities + support tables)
-- Every row is owned by a sales rep (auth.users) and protected by RLS.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- profiles / consent
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  -- 기획서 3-⑥: explicit consent before customer data is sent to the external LLM.
  llm_consent_at timestamptz,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- core entities
create table public.customers (
  id text primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  company_name text not null,
  contact_name text not null,
  contact_email text not null,
  history_summary text not null default '',
  created_at timestamptz not null default now()
);

create table public.visits (
  id text primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  customer_id text not null references public.customers (id) on delete cascade,
  purpose text not null,
  scheduled_at timestamptz not null,
  place_name text not null,
  latitude double precision not null,
  longitude double precision not null
);

create type public.session_phase as enum (
  'idle', 'prepare_session', 'driving_limited', 'paused_by_policy', 'arrival_pending',
  'parked_review', 'awaiting_approval', 'executing', 'completed', 'error'
);

create table public.sessions (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  visit_id text not null references public.visits (id) on delete cascade,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  eta_tier text check (eta_tier in ('short', 'medium', 'long')),
  phase public.session_phase not null default 'prepare_session'
);

create table public.notes (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  session_id uuid not null references public.sessions (id) on delete cascade,
  text text not null,
  input_method text not null check (input_method in ('voice', 'text')),
  phase public.session_phase not null,
  created_at timestamptz not null default now()
);

create table public.insights (
  visit_id text primary key references public.visits (id) on delete cascade,
  owner_id uuid not null references auth.users (id) on delete cascade,
  payload jsonb not null,            -- validated by Zod (InsightSchema) before insert
  created_at timestamptz not null default now()
);

create type public.action_status as enum ('draft', 'review', 'approved', 'executed', 'failed', 'retry');

create table public.actions (
  id text primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  session_id uuid not null,
  customer_id text not null references public.customers (id) on delete cascade,
  type text not null check (type in ('email')),
  draft jsonb not null,
  draft_version int not null default 1,
  status public.action_status not null default 'draft',
  idempotency_key text,
  failure_reason text,
  updated_at timestamptz not null default now()
);

-- One row per idempotency key: the database itself makes double-sending impossible.
create table public.action_executions (
  idempotency_key text primary key,
  action_id text not null references public.actions (id) on delete cascade,
  owner_id uuid not null references auth.users (id) on delete cascade,
  status text not null check (status in ('pending', 'executed', 'failed')),
  provider_message_id text,
  result_log text,
  created_at timestamptz not null default now(),
  finished_at timestamptz
);

-- ---------------------------------------------------------------- rate limiting
create table public.ai_usage (
  id bigint generated always as identity primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  function_name text not null,
  created_at timestamptz not null default now()
);
create index ai_usage_owner_fn_time on public.ai_usage (owner_id, function_name, created_at desc);

-- Atomically checks and records one call. Returns true when the call is allowed.
create or replace function public.consume_ai_quota(p_function text, p_limit int, p_window_seconds int)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  used int;
begin
  if auth.uid() is null then
    return false;
  end if;
  perform pg_advisory_xact_lock(hashtext(auth.uid()::text || p_function));
  select count(*) into used
    from public.ai_usage
   where owner_id = auth.uid()
     and function_name = p_function
     and created_at > now() - make_interval(secs => p_window_seconds);
  if used >= p_limit then
    return false;
  end if;
  insert into public.ai_usage (owner_id, function_name) values (auth.uid(), p_function);
  return true;
end;
$$;
revoke all on function public.consume_ai_quota(text, int, int) from public;
grant execute on function public.consume_ai_quota(text, int, int) to authenticated;

-- ---------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.customers enable row level security;
alter table public.visits enable row level security;
alter table public.sessions enable row level security;
alter table public.notes enable row level security;
alter table public.insights enable row level security;
alter table public.actions enable row level security;
alter table public.action_executions enable row level security;
alter table public.ai_usage enable row level security;

create policy "own profile" on public.profiles
  for all using (id = auth.uid()) with check (id = auth.uid());

create policy "own customers" on public.customers
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "own visits" on public.visits
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "own sessions" on public.sessions
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "own notes" on public.notes
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "own insights" on public.insights
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- Actions: the client may read and delete its own rows. Status changes go through the
-- execute-action function (service role), so a client cannot mark an action "executed".
create policy "read own actions" on public.actions
  for select using (owner_id = auth.uid());
create policy "delete own actions" on public.actions
  for delete using (owner_id = auth.uid());
create policy "read own executions" on public.action_executions
  for select using (owner_id = auth.uid());
-- ai_usage: no client policies at all (only the security definer function writes it).

-- New users get a profile row automatically.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id) values (new.id) on conflict do nothing;
  return new;
end;
$$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();
