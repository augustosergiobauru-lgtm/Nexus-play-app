-- Activate only together with an APK using register_nexus_device.
-- Existing test installations must be revoked once before enabling the switch.
CREATE OR REPLACE FUNCTION public.enforce_device_limit()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  IF NOT public.nexus_device_checks_enabled() THEN RETURN NEW; END IF;
  IF NEW.status = 'active' THEN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.customer_id::text, 0));
    IF EXISTS (SELECT 1 FROM public.devices d WHERE d.customer_id = NEW.customer_id
      AND d.status = 'active' AND d.id <> NEW.id) THEN
      RAISE EXCEPTION 'Limite de 1 dispositivo ativo atingido' USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

-- Narrow elevated operation: clients cannot insert device records directly under existing RLS.
-- Only the authenticated customer's first device can be enrolled, with a valid subscription.
-- Revoked identifiers cannot enroll themselves again. Device replacement remains administrative.
CREATE OR REPLACE FUNCTION public.register_nexus_device(p_device_identifier text, p_device_name text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE customer uuid; found_device public.devices%ROWTYPE;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED' USING ERRCODE='42501'; END IF;
  IF p_device_identifier IS NULL OR p_device_identifier !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
    RAISE EXCEPTION 'INVALID_DEVICE' USING ERRCODE='22023';
  END IF;
  SELECT c.id INTO customer FROM public.customers c WHERE c.auth_user_id=auth.uid() AND c.status='active' LIMIT 1;
  IF customer IS NULL OR NOT EXISTS (SELECT 1 FROM public.subscriptions s WHERE s.customer_id=customer
    AND s.status='active' AND s.starts_at<=now() AND s.expires_at>now()) THEN RETURN false; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(customer::text,0));
  SELECT * INTO found_device FROM public.devices WHERE customer_id=customer AND device_identifier=p_device_identifier LIMIT 1;
  IF FOUND THEN
    IF found_device.status <> 'active' THEN RETURN false; END IF;
    UPDATE public.devices SET last_seen_at=now() WHERE id=found_device.id;
    RETURN true;
  END IF;
  IF EXISTS (SELECT 1 FROM public.devices WHERE customer_id=customer AND status='active') THEN RETURN false; END IF;
  INSERT INTO public.devices(customer_id,device_identifier,device_name,platform,status,last_seen_at)
    VALUES(customer,p_device_identifier,left(coalesce(nullif(p_device_name,''),'Android'),100),'android','active',now());
  RETURN true;
END $$;
REVOKE ALL ON FUNCTION public.register_nexus_device(text,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_nexus_device(text,text) TO authenticated;
