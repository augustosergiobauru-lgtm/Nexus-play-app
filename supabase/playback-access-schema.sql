create function public.resolve_nexus_playback(p_channel_id uuid,p_device_identifier text)
returns table(access_url text)
language plpgsql security invoker set search_path=public,pg_temp as $$
declare customer uuid;
begin
 if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
 select c.id into customer from public.customers c where c.auth_user_id=auth.uid() and c.status='active' limit 1;
 if customer is null or not exists(select 1 from public.subscriptions s where s.customer_id=customer and s.status='active' and s.starts_at<=now() and s.expires_at>now()) then
  raise exception 'SUBSCRIPTION_REQUIRED' using errcode='42501';
 end if;
 if not exists(select 1 from public.devices d where d.customer_id=customer and d.device_identifier=p_device_identifier and d.status='active') then
  raise exception 'DEVICE_NOT_AUTHORIZED' using errcode='42501';
 end if;
 return query select ch.stream_url from public.channels ch where ch.id=p_channel_id and ch.is_active and public.is_playlist_active(ch.playlist_id);
 if not found then raise exception 'CONTENT_UNAVAILABLE' using errcode='42501'; end if;
end $$;
revoke all on function public.resolve_nexus_playback(uuid,text) from public,anon;
grant execute on function public.resolve_nexus_playback(uuid,text) to authenticated;
