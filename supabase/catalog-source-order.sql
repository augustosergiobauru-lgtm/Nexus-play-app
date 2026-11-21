alter table public.channels add column if not exists source_position bigint;
create index if not exists channels_playlist_stream_hash_idx on public.channels(playlist_id,md5(stream_url));
create or replace function public.import_m3u_batch(p_playlist_id uuid,p_fingerprint text,p_offset integer,p_items jsonb,p_done boolean)
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

 if exists(select 1 from jsonb_array_elements(p_items) j where coalesce(j->>'title','')='' or coalesce(j->>'stream_url','') !~ '^https?://' or coalesce(j->>'content_type','') not in ('live','movie','series')) then raise exception 'INVALID_ITEM'; end if;
 insert into public.categories(name,content_type)
 select distinct coalesce(nullif(j->>'group',''),'Sem categoria'),(j->>'content_type')::public.content_type from jsonb_array_elements(p_items) j
 on conflict(name,content_type) do nothing;
 with payload as (
  select distinct on (j->>'stream_url') j, ordinal from jsonb_array_elements(p_items) with ordinality as rows(j,ordinal) order by j->>'stream_url',ordinal
 ), written as (
 insert into public.channels(id,playlist_id,category_id,title,stream_url,external_id,logo_url,content_type,source_position)
 select coalesce(existing.id,gen_random_uuid()),p_playlist_id,c.id,p.j->>'title',p.j->>'stream_url',p.j->>'external_id',p.j->>'logo_url',(p.j->>'content_type')::public.content_type,coalesce(nullif(p.j->>'source_position','')::bigint,p_offset+p.ordinal-1)
 from payload p join public.categories c on c.name=coalesce(nullif(p.j->>'group',''),'Sem categoria') and c.content_type=(p.j->>'content_type')::public.content_type
 left join lateral (select ch.id from public.channels ch where ch.playlist_id=p_playlist_id and md5(ch.stream_url)=md5(p.j->>'stream_url') and ch.stream_url=p.j->>'stream_url' order by ch.created_at,ch.id limit 1) existing on true
 on conflict(id) do update set category_id=excluded.category_id,title=excluded.title,external_id=excluded.external_id,logo_url=excluded.logo_url,content_type=excluded.content_type,source_position=excluded.source_position,updated_at=now()
 returning (xmax=0) as inserted
 )
 select count(*) filter(where inserted),count(*) filter(where not inserted) into n,u from written;

 insert into public.m3u_import_pages values(p_playlist_id,p_fingerprint,p_offset,jsonb_array_length(p_items),n,u,now());
 if p_done then update public.playlists set status='active',last_import_at=now(),last_check_at=now(),health_status='online',last_error=null where id=p_playlist_id; end if;
 return jsonb_build_object('inserted',n,'updated',u,'replayed',false);
end $$;
revoke all on function public.import_m3u_batch(uuid,text,integer,jsonb,boolean) from public,anon;
grant execute on function public.import_m3u_batch(uuid,text,integer,jsonb,boolean) to authenticated,service_role;

create index if not exists channels_source_position_idx on public.channels(source_position,created_at,id);
