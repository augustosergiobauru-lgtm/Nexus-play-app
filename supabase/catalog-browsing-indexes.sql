create index if not exists channels_active_type_title_idx on public.channels(content_type,title,id) where is_active;
create index if not exists channels_active_title_idx on public.channels(title,id) where is_active;
