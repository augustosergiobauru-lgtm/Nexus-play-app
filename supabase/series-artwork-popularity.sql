-- Add only cached metadata; existing RLS and read-only client grants remain in effect.
alter table public.series_catalog add column if not exists poster_url text;
alter table public.series_catalog add column if not exists popularity_score integer;
alter table public.series_catalog add column if not exists popularity_source_url text;
create index if not exists series_catalog_popularity_idx on public.series_catalog(popularity_score desc nulls last,title,id) where is_active;
notify pgrst, 'reload schema';
