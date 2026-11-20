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
