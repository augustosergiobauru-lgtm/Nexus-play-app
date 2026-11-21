-- Store the parsed show title once rather than reparsing every episode on the phone.
create or replace function public.nexus_series_title(raw_title text) returns text
language sql immutable strict parallel safe set search_path = '' as $$
 select coalesce(nullif(btrim(regexp_replace(raw_title,
 '(?i)\m(S[0-9]{1,3}[[:space:]_.-]*E(P)?[[:space:]]*[0-9]{1,4}|[0-9]{1,3}x[0-9]{1,4}|(temporada|temp\.?|T)[[:space:]]*[0-9]{1,3}[[:space:]_.-]*(epis[oó]dio|ep\.?|E)[[:space:]]*[0-9]{1,4})\M.*$', ''), ' -|:.'), ''), btrim(raw_title))
$$;
alter table public.channels add column if not exists series_title text generated always as (case when content_type = 'series'::public.content_type then public.nexus_series_title(title) end) stored;

create index if not exists channels_series_lookup_idx on public.channels(playlist_id, series_title) where is_active and content_kind='series';
create index if not exists channels_kind_title_idx on public.channels(content_kind,title,id) where is_active;
create index if not exists channels_kind_source_idx on public.channels(content_kind,source_position,created_at,id) where is_active;
create table public.series_catalog (
 id text primary key, playlist_id uuid not null references public.playlists(id) on delete cascade,
 title text not null, content_type text not null default 'series', content_kind text not null default 'series',
 category_id uuid references public.categories(id) on delete set null, logo_url text,
 created_at timestamptz, source_position bigint, is_active boolean not null default true,
 unique(playlist_id,title)
);
alter table public.series_catalog enable row level security;
create policy series_owner_all on public.series_catalog for all to authenticated using ((select public.is_owner())) with check ((select public.is_owner()));
create policy series_read on public.series_catalog for select to authenticated using (
 (select public.is_owner()) or (is_active and public.is_playlist_active(playlist_id) and exists (
 select 1 from public.subscriptions s where s.customer_id=(select public.current_customer_id())
 and s.status='active' and s.starts_at<=now() and s.expires_at>now())));
revoke all on public.series_catalog from anon,authenticated;
grant select on public.series_catalog to authenticated;
-- The importer already writes channels. Maintain one small metadata row per show.
create schema if not exists nexus_internal;
revoke all on schema nexus_internal from public,anon,authenticated;
create or replace function nexus_internal.nexus_sync_series_catalog() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if TG_OP <> 'DELETE' and NEW.content_kind='series' and NEW.is_active then
 insert into public.series_catalog(id,playlist_id,title,category_id,logo_url,created_at,source_position)
 values(NEW.playlist_id::text || ':' || NEW.series_title,NEW.playlist_id,NEW.series_title,NEW.category_id,NEW.logo_url,NEW.created_at,NEW.source_position)
 on conflict(playlist_id,title) do update set
 logo_url=coalesce(nullif(series_catalog.logo_url,''),excluded.logo_url),
 created_at=greatest(series_catalog.created_at,excluded.created_at),
 source_position=least(series_catalog.source_position,excluded.source_position),category_id=excluded.category_id;
 end if;
 if TG_OP <> 'INSERT' and OLD.content_kind='series' then
 if TG_OP='DELETE' or NEW.series_title is distinct from OLD.series_title or NEW.playlist_id is distinct from OLD.playlist_id or not NEW.is_active then
 delete from public.series_catalog sc where sc.playlist_id=OLD.playlist_id and sc.title=OLD.series_title
 and not exists(select 1 from public.channels c where c.playlist_id=OLD.playlist_id and c.series_title=OLD.series_title and c.content_kind='series' and c.is_active);
 end if;
 end if;
 return null;
end $$;
-- Trigger-only function: no client execution or direct writes to the cache.
revoke all on function nexus_internal.nexus_sync_series_catalog() from public,anon,authenticated;
create trigger nexus_series_catalog_changed after insert or update or delete on public.channels for each row execute function nexus_internal.nexus_sync_series_catalog();
insert into public.series_catalog(id,playlist_id,title,category_id,logo_url,created_at,source_position)
select playlist_id::text || ':' || series_title,playlist_id,series_title,
 (array_agg(category_id order by source_position nulls last,id))[1],
 (array_agg(logo_url order by source_position nulls last,id) filter(where nullif(logo_url,'') is not null))[1],
 max(created_at),min(source_position)
from public.channels where content_kind='series' and is_active group by playlist_id,series_title;
create index series_catalog_recent_idx on public.series_catalog(created_at desc,id desc) where is_active;
create index series_catalog_title_idx on public.series_catalog(title,id) where is_active;
create index if not exists channels_series_title_order_idx on public.channels(series_title,title,id) where is_active and content_kind='series';
analyze public.series_catalog;
analyze public.channels;
notify pgrst,'reload schema';
