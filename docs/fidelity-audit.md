# MetroFlow fidelity audit

MetroFlow Native 1.0.2 is a working Spotify-style shell, not a pixel-identical reproduction. Source review and public reference images do not establish how the current APK renders on a device. This audit did not control the phone or desktop.

References inspected on 18 November 2026:

- [Spotify desktop redesign, 20 June 2023](https://newsroom.spotify.com/2023-06-20/spotify-desktop-experience-redesign-your-library-now-playing-views-customize/), including its desktop screenshot.
- [Spotify adaptive tablet design, 16 April 2026](https://newsroom.spotify.com/2026-04-16/new-tablet-app-experience/), including portrait and landscape screenshots.
- [Spotify design history, 23 April 2026](https://newsroom.spotify.com/2026-04-23/spotify-design-history/), documenting the Spotify Mix typeface.

The references are different versions and platforms. A pixel comparison needs an agreed current Spotify mobile/web reference, matching viewport, theme, text scale, page, playback state and content, alongside screenshots of MetroFlow on the same target sizes.

## Remaining differences

MetroFlow uses Material typography and its own vector icons rather than Spotify Mix and the full Spotify icon system. The extra source control and protected MetroFuse Settings/Plugins routes affect header structure. Search currently lists provider catalog results rather than Spotify genre/category tiles. Library currently exposes tracks rather than Spotify's saved-collection filters, sorting and pinned collection layouts. The player lacks Spotify's artwork-derived background treatment, lyrics/video panels, volume/device selectors and exposed like/shuffle/repeat active states. Sidebar sizing and breakpoints have not been measured against the web layout; Android tablets also do not reproduce Spotify's adaptive parallel player presentation.

## Narrow follow-up prepared

The current source removes duplicate desktop brand text, uses circular Home/search surfaces, and reports an empty filtered Home rather than leaving blank space. Quick Home cards now reject empty provider items before rendering and distinguish content kinds with identical IDs; previously those cases could crash or drop valid cards. These changes preserve the existing provider/player services. They have targeted build/test coverage but are not published as a further plugin release without visual verification.
