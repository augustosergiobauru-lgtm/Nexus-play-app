-- Run after distributing an APK that supports register_nexus_device and Android ID v2.
-- Remove only the four legacy Redmi test registrations, leaving the first real login to enroll.
DO $$
DECLARE removed integer;
BEGIN
  IF (SELECT count(*) FROM public.devices WHERE status='active') <> 4 THEN
    RAISE EXCEPTION 'Device state changed: inspect active registrations before enabling';
  END IF;
  DELETE FROM public.devices
  WHERE status='active' AND platform='android' AND last_seen_at IS NULL
    AND device_name IN (
      'Redmi Note 13 - instalação atual',
      'Redmi Note 13 - instalação f77d2db0',
      'Redmi Note 13 - instalação f7283e2b',
      'Redmi Note 13 - teste sem limite'
    );
  GET DIAGNOSTICS removed = ROW_COUNT;
  IF removed <> 4 THEN RAISE EXCEPTION 'Unexpected legacy device registrations'; END IF;
END $$;
CREATE OR REPLACE FUNCTION public.nexus_device_checks_enabled()
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$ SELECT true $$;
REVOKE ALL ON FUNCTION public.nexus_device_checks_enabled() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.nexus_device_checks_enabled() TO authenticated;
