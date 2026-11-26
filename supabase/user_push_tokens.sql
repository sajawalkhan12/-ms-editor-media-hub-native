-- FCM device token storage. This schema has already been applied to the production project.
create table if not exists public.user_push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  fcm_token text not null unique,
  device_name text not null default 'Android',
  updated_at timestamptz not null default now()
);
create index if not exists user_push_tokens_user_id_idx on public.user_push_tokens(user_id);
alter table public.user_push_tokens enable row level security;
drop policy if exists "Users manage their own push tokens" on public.user_push_tokens;
create policy "Users manage their own push tokens" on public.user_push_tokens
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
grant select, insert, update, delete on public.user_push_tokens to authenticated;
