-- enum_eq is not leakproof: under RLS, the enum filter cannot drive the index.
-- A text projection lets Postgres filter and order on an index before checking visible rows.
-- The existing RLS policies, subscription checks and playback authorization are unchanged.
alter table public.channels add column if not exists content_kind text generated always as (
 case content_type when 'movie'::public.content_type then 'movie'::text
 when 'series'::public.content_type then 'series'::text else 'live'::text end
) stored;
create index if not exists channels_home_kind_recent_idx on public.channels(content_kind,created_at desc,id desc) where is_active;
analyze public.channels;
