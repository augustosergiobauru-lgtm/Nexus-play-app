create table public.m3u_xtream_sources (
 playlist_id uuid primary key references public.playlists(id) on delete cascade,
 source_url text not null,
 username text not null,
 password text not null,
 allow_insecure_http boolean not null default false,
 updated_at timestamptz not null default now()
);
alter table public.m3u_xtream_sources enable row level security;
revoke all on public.m3u_xtream_sources from public,anon,authenticated;
grant all on public.m3u_xtream_sources to service_role;
