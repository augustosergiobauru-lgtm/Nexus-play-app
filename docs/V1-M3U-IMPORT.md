# Nexus Play V1: M3U and Xtream import

## Deployed backend
Project: ypjajibbcyopkfrenibb. Function: import-m3u. JWT validation plus an Auth getUser and profiles.role=owner check protect administrative calls. No V2 app was created.

The importer supports ready M3U files and Xtream server/username/password connections. The latter uses player_api.php for live streams, VOD and series episode catalogs.

Administrative configure_xtream requests store credentials in m3u_xtream_sources. That table has RLS and no anon/authenticated access; only service_role can read it. Credentials never enter Android source, catalog metadata, public errors or repository files. A source authorization record is bound to the hash of the playlist source URL.

## Pagination
POST with playlist_id, limit (1–200), offset and, on continuation, fingerprint.
For Xtream also pass section (live/movie/series), category_index and series_index.
Follow next_offset inside a category/show; when it is null, continue at offset 0 using next_section, next_category_index and next_series_index. done=true ends traversal.

Live/movie catalogs are fetched per category: the actual provider VOD response exceeded the 16 MiB bound when requested globally. Series import reads one show's episodes at a time.
HTTP sources require explicit configuration approval. HTTPS certificate checks remain enabled. No silent protocol or port changes are performed.

## Database behavior
import_m3u_batch validates source authorization, serializes imports by playlist, verifies cursor continuity and records page replay. Categories and channels are written with set operations; exact stream URL matching uses an additional hash index for large catalogs. Existing records are updated, never deleted. Existing inactive channels remain inactive.
Successful completion updates status and last_import_at; errors are sanitized.

## Android V1
NexusCatalog queries by content type and title on the server, with paginated results. Search resets pagination and ignores stale responses.
Catalog titles/cards request resolve_nexus_playback before opening the existing NexusPlaybackScreen.
The SECURITY INVOKER resolver checks current user, active customer, unexpired subscription, approved installation and available channel.
ExoPlayer and LibVLC remain in place, with shared controls, mini/fullscreen layout and fallback. HTTP URLs and cross-protocol CDN redirects are supported because the actual provider responds with HTTPS-to-HTTP redirects.

## Actual validation, 2026-11-20
- Provider login returned HTTP 200, auth=1, Active following explicit user permission.
- Persisted provider counts at this checkpoint: 2,165 live channels and 18,185 movies.
- Provider series catalog: 9,797 shows; episode download/import is ongoing at this checkpoint.
- Original 31 M3U/Xtream tests passed locally and in GitHub CI; two more tests cover category pagination.
- Database checks passed for batch pagination/replay, anonymous rejection, eligible account playback resolution, unapproved devices and missing subscriptions.
- Earlier Android build and emulator tests for public MP4/HLS passed.
- The current commit adds a real HTTP redirect instrumentation test and needs a fresh build/emulator run.
- Provider playback probes returned a CDN redirect for the movie and HTTP 429 for one live sample. These probes do not establish decoded playback of actual provider media.
- Real provider import in this session is executed through the deployed database batch RPC using the same parser/source mapping, with authenticated provider fetches from the execution environment. An admin-session invocation of the deployed Edge HTTP entrypoint has not been exercised.

No provider credentials or raw catalogs are committed. Consolidated initial schema and follow-up SQL files describe changes already applied to this project; do not reapply them blindly.
