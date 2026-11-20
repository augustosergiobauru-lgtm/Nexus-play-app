# Nexus Play V1 dual player

Base: nexus/auth-foundation (the existing black/gold V1 with its existing login,
subscription and device gate). main at 07cde00 was still the OpenTV entry point.
The main retry-counter fix is retained. No V2 code, database migrations, admin
panel changes, Supabase changes or credentials are introduced.

NexusPlayer owns one session across mini/fullscreen layout changes. ExoPlayer
is first; fatal errors or 30 seconds without playback progress try LibVLC once.
A second engine failure shows a retry button rather than looping. Retry creates
a new bounded attempt. HTTPS validation remains in place. VOD position, paused
intent, volume and speed transfer between engines; live sources start at the live
edge and seek controls depend on the source's actual seekability. Audio/subtitle
choices are refreshed from each engine (track IDs cannot transfer across engines).
Both engines use the same play/pause, seek bar, skip, mute, audio, subtitles and
speed controls. App backgrounding pauses and disposal releases native resources.
The legacy OpenTV PlayerController is unchanged by this feature.

Validation workflow: V1 dual player validation. Instrumentation plays the public
Google Big Buck Bunny MP4 and Mux x36xhzz HLS samples with each engine, injects
errors through the production fallback method, checks pause/mute/speed, seek,
resizing the same surface, terminal error, retry and cleanup. It does not use a
customer account. Sources are publisher-provided public testing material:
https://github.com/muxinc/stream-examples
https://test-streams.mux.dev/
https://peach.blender.org/about/
https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4

An emulator pass validates these samples, not every provider codec or a physical
Redmi/Android TV. Live DVR availability and available audio/subtitle tracks depend
on the source and engine. LibVLC is pinned to 3.6.5 (Maven Central), retaining its
license notices in the dependency; project remains GPLv3.
