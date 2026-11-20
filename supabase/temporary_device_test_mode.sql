-- Temporary test mode: set SELECT false to SELECT true in this function to restore device checks.
CREATE OR REPLACE FUNCTION public.nexus_device_checks_enabled()
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$ SELECT false $$;
REVOKE ALL ON FUNCTION public.nexus_device_checks_enabled() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.nexus_device_checks_enabled() TO authenticated;
CREATE OR REPLACE FUNCTION public.enforce_device_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
                                                                                                                                                                      declare
                                                                                                                                                                        active_count integer;
                                                                                                                                                                        begin
 if not public.nexus_device_checks_enabled() then return new; end if;
                                                                                                                                                                          if new.status = 'active' then
                                                                                                                                                                              perform pg_advisory_xact_lock(hashtextextended(new.customer_id::text, 0));

                                                                                                                                                                                  select count(*) into active_count
                                                                                                                                                                                      from public.devices
                                                                                                                                                                                          where customer_id = new.customer_id
                                                                                                                                                                                                and status = 'active'
                                                                                                                                                                                                      and id <> coalesce(new.id, '00000000-0000-0000-0000-000000000000'::uuid);

                                                                                                                                                                                                          if active_count >= 3 then
                                                                                                                                                                                                                raise exception 'Limite de 3 dispositivos ativos atingido';
                                                                                                                                                                                                                    end if;
                                                                                                                                                                                                                      end if;

                                                                                                                                                                                                                        return new;
                                                                                                                                                                                                                        end;
                                                                                                                                                                                                                        $function$;
CREATE OR REPLACE FUNCTION public.resolve_nexus_playback(p_channel_id uuid, p_device_identifier text)
 RETURNS TABLE(access_url text)
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare customer uuid;
begin
 if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
 select c.id into customer from public.customers c where c.auth_user_id=auth.uid() and c.status='active' limit 1;
 if customer is null or not exists(select 1 from public.subscriptions s where s.customer_id=customer and s.status='active' and s.starts_at<=now() and s.expires_at>now()) then
  raise exception 'SUBSCRIPTION_REQUIRED' using errcode='42501';
 end if;
 if public.nexus_device_checks_enabled() and not exists(select 1 from public.devices d where d.customer_id=customer and d.device_identifier=p_device_identifier and d.status='active') then
  raise exception 'DEVICE_NOT_AUTHORIZED' using errcode='42501';
 end if;
 return query select ch.stream_url from public.channels ch where ch.id=p_channel_id and ch.is_active and public.is_playlist_active(ch.playlist_id);
 if not found then raise exception 'CONTENT_UNAVAILABLE' using errcode='42501'; end if;
end $function$
;

-- Restore approvals AND the 3-device limit (also used by new APKs):
-- CREATE OR REPLACE FUNCTION public.nexus_device_checks_enabled()
-- RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$ SELECT true $$;
