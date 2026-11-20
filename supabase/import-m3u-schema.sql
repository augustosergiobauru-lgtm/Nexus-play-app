
create table public.m3u_authorizations (
 playlist_id uuid primary key references public.playlists(id) on delete cascade,
 source_url_hash text not null,
 evidence text not null check(length(evidence)>=12),
 approved_by uuid references auth.users(id),
 approved_at timestamptz not null default now()
);
alter table public.m3u_authorizations enable row level security;
grant select,insert,update on public.m3u_authorizations to authenticated;
grant all on public.m3u_authorizations to service_role;
create policy m3u_authorizations_owner on public.m3u_authorizations for all to authenticated using(public.is_owner()) with check(public.is_owner());
create table public.m3u_import_pages (
 playlist_id uuid not null references public.playlists(id),
 fingerprint text not null,
 page_offset integer not null,
 item_count integer not null,
 inserted_count integer not null,
 updated_count integer not null,
 imported_at timestamptz not null default now(),
 primary key(playlist_id,fingerprint,page_offset)
);
alter table public.m3u_import_pages enable row level security;
grant select on public.m3u_import_pages to authenticated;
grant all on public.m3u_import_pages to service_role;
create policy m3u_import_pages_owner on public.m3u_import_pages for select to authenticated using(public.is_owner());
create function public.import_m3u_batch(p_playlist_id uuid,p_fingerprint text,p_offset integer,p_items jsonb,p_done boolean)
returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
declare item jsonb; cid uuid; chid uuid; n int:=0; u int:=0; total int; url text;
begin
 if current_user not in ('postgres','service_role') and not public.is_owner() then raise exception 'ADMIN_REQUIRED'; end if;
 if p_offset<0 or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)>200 then raise exception 'INVALID_BATCH'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_playlist_id::text,0));
 select source_url into strict url from public.playlists where id=p_playlist_id for update;
 if not exists(select 1 from public.m3u_authorizations where playlist_id=p_playlist_id and source_url_hash=encode(sha256(convert_to(url,'UTF8')),'hex')) then raise exception 'SOURCE_NOT_AUTHORIZED'; end if;
 if exists(select 1 from public.m3u_import_pages where playlist_id=p_playlist_id and fingerprint=p_fingerprint and page_offset=p_offset) then
 if not exists(select 1 from public.m3u_import_pages where playlist_id=p_playlist_id and fingerprint=p_fingerprint and page_offset=p_offset and item_count=jsonb_array_length(p_items)) then raise exception 'BATCH_SIZE_CHANGED'; end if;
 return jsonb_build_object('replayed',true,'inserted',0,'updated',0);
 end if;
 select coalesce(sum(item_count),0) into total from public.m3u_import_pages where playlist_id=p_playlist_id and fingerprint=p_fingerprint;
 if total<>p_offset then raise exception 'INVALID_CURSOR'; end if;
 for item in select value from jsonb_array_elements(p_items) loop
  if coalesce(item->>'title','')='' or (item->>'stream_url') !~ '^https?://' or (item->>'content_type') not in ('live','movie','series') then raise exception 'INVALID_ITEM'; end if;
  insert into public.categories(name,content_type) values(coalesce(nullif(item->>'group',''),'Sem categoria'),(item->>'content_type')::public.content_type)
   on conflict(name,content_type) do update set name=excluded.name returning id into cid;
  select id into chid from public.channels where playlist_id=p_playlist_id and stream_url=item->>'stream_url' order by created_at,id limit 1;
  if chid is null then
   insert into public.channels(playlist_id,category_id,title,stream_url,external_id,logo_url,content_type)
    values(p_playlist_id,cid,item->>'title',item->>'stream_url',item->>'external_id',item->>'logo_url',(item->>'content_type')::public.content_type);
   n:=n+1;
  else
   update public.channels set category_id=cid,title=item->>'title',external_id=item->>'external_id',logo_url=item->>'logo_url',content_type=(item->>'content_type')::public.content_type,updated_at=now() where id=chid;
   u:=u+1;
  end if;
 end loop;
 insert into public.m3u_import_pages values(p_playlist_id,p_fingerprint,p_offset,jsonb_array_length(p_items),n,u,now());
 if p_done then update public.playlists set status='active',last_import_at=now(),last_check_at=now(),health_status='online',last_error=null where id=p_playlist_id; end if;
 return jsonb_build_object('inserted',n,'updated',u,'replayed',false);
end $$;
revoke all on function public.import_m3u_batch(uuid,text,integer,jsonb,boolean) from public,anon;
grant execute on function public.import_m3u_batch(uuid,text,integer,jsonb,boolean) to authenticated,service_role;
