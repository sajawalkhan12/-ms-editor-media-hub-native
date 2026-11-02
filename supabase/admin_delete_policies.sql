-- Run this once in Supabase Dashboard > SQL Editor.
-- Admin-only deletion for shared website + Android app data.
-- Does not delete or modify any existing rows.

alter table public.media_comments enable row level security;
alter table public.media_items enable row level security;

drop policy if exists "Media admins can delete comments" on public.media_comments;
create policy "Media admins can delete comments"
on public.media_comments
for delete
to authenticated
using (public.is_media_admin());

drop policy if exists "Media admins can delete media items" on public.media_items;
create policy "Media admins can delete media items"
on public.media_items
for delete
to authenticated
using (public.is_media_admin());
