# V1 M3U importer

Deployed to project ypjajibbcyopkfrenibb as import-m3u, version 1, JWT verification enabled.
No Android files were changed.

## Authorization
The request needs a valid Supabase user access token. The function validates it with Auth and checks profiles.role = owner using a server-side client.
A row in m3u_authorizations must reference the playlist and SHA256 of its current source_url, with evidence of distribution rights. Changing the URL invalidates the approval.
The provider "Fornecedor teste 1" has no approval: its stored https://example.com/teste.m3u returned HTTP 404.

## Invocation
POST /functions/v1/import-m3u
Authorization: Bearer <admin user access token>
Content-Type: application/json

Body: {"playlist_id":"<uuid>","offset":0,"limit":200}
Continue with next_offset and fingerprint from the response until done=true.
Keep the same limit while retrying a page. SOURCE_CHANGED_RESTART requires offset=0.
The connector does not provide an admin user session or an Edge invocation tool; the authenticated deployed HTTP path has not been exercised in this session.

## Behavior
M3U EXTINF, tvg-id, tvg-logo, group-title, EXTGRP, BOM, CRLF, relative URLs.
Heuristic live/movie/series classification from group, title and URL; ambiguous items default to live.
HLS segment manifests and HTML pages are rejected.
HTTPS public source, DNS filtering, no redirects, 20 second fetch timeout, 16 MiB limit, 200 entries/page.
Atomic RPC batch, per-playlist lock, URL deduplication, audited page replay, source fingerprint.
No channels are deleted or deactivated by an import. Existing channels match by playlist_id + stream_url.
last_import_at is set only after the final batch; errors are sanitized.

## Verified results — 2026-11-20
- 13 local parser and HTTP handler tests passed (Auth and REST mocked).
- Deployed HTTP request without auth: 401 UNAUTHORIZED_NO_AUTH_HEADER.
- Public fixture downloaded from immutable GitHub URL and parsed.
- Media sample returned HTTP 200, video/mp4.
- Public rights evidence: https://peach.blender.org/about/ (CC BY 3.0; attribution in imported title).
- Actual database RPC: first insert 1; second content fingerprint update 1/insert 0; page replay insert 0/update 0.
- Actual database transaction test: 200 + 1 items and page replay passed, test rows rolled back.
- Persistent catalog: 1 movie in Filmes livres. Original provider: 0 contents, status error, last_import_at null.

## APK compatibility limits
NexusCatalog.kt reads id,title,content_type,category_id with pagination; imported fields and enum match.
NexusHomeScreen.kt currently only makes series titles clickable to metadata details; live/movie titles do not invoke NexusPlaybackScreen.
Playback of imported catalog items is therefore not implemented by this backend-only PR. The existing ExoPlayer/LibVLC diagnostic player remains untouched.
No physical APK playback test was performed.

## Database rollout
supabase/import-m3u-schema.sql is the consolidated initial schema for this feature. It was applied through three incremental managed migrations during this session. Do not reapply it to this project.
Authorization and page audit tables have RLS. No service keys or provider credentials are committed.

## Xtream compatibility update
The screenshots show server/username/password credentials, rather than a ready M3U file. The existing V1 OpenTV dependency already includes an XtreamApi client; its protocol behavior was reviewed instead of copying any other app binary.

The deployed importer now supports Xtream player_api.php:
- Administrative configure_xtream action with xtream.server, xtream.username, xtream.password, optional allow_insecure_http and authorization_reference.
- Credentials stored in m3u_xtream_sources, RLS enabled, no anon/authenticated grants; only service_role can access.
- Source URL remains credential-free. Changing it invalidates the stored connection binding.
- section=live → get_live_streams, movie → get_vod_streams, series → get_series plus get_series_info.
- Series are sorted by ID and paginated one show at a time; each show's episodes are imported in batches of at most 200.
- Use next_offset within a section/show. On completing a page chain, follow next_section and next_series_index at offset=0. done=true ends the full traversal.
- The final section updates last_import_at; partial pages do not announce full completion.
- HTTP requires an explicit allow_insecure_http flag. HTTPS on port 80 is preserved as entered; no automatic downgrade or certificate bypass.
- TS is the default live format; M3U8 is selected when the provider advertises only M3U8.
- Login/account errors are sanitized and provider URLs with credentials are not returned or logged.

Validation: 13 M3U tests + 18 Xtream tests passed locally with synthetic credentials.
No-credential root connectivity probes: first HTTPS server HTTP 200; second HTTP server timeout; second HTTPS:80 server HTTP 503. Root probes do not validate the accounts or catalogs.
A credentialed provider test was blocked by automatic approval review because external destinations were not yet verified and explicit consent to send the pictured secrets was required. No provider catalog result is available; do not report a successful provider import.
Provider secrets have not been committed or configured by this update.
The Android click-to-play limitation documented above remains.

## V1 catalog-to-player connection
The branch now connects channel/movie/episode titles and home catalog cards to the existing NexusPlaybackScreen and its unchanged ExoPlayer/LibVLC implementation.
NexusPlaybackAccess requests resolve_nexus_playback with the current session and persistent installation identifier.
The SQL function runs as SECURITY INVOKER, requires auth.uid(), an active customer, an unexpired active subscription, an active device belonging to that customer, and an available channel from an active playlist.
No provider credentials are embedded in Android source or added to catalog metadata.

Actual database checks:
- Unauthenticated resolution rejected.
- Existing eligible account/device resolved the public sample.
- Unapproved device rejected.
- Tests ran within a reverted transaction, with no user/device/subscription records changed.

Provider test retry after the user's broad permission was again rejected by automatic review: end-user-authored permission must explicitly identify the pictured credentials and external destination. No credentialed provider result is available, and no workaround was attempted.

Latest Android changes await GitHub compilation and player validation before merge. Earlier M3U/Xtream CI completed successfully.
