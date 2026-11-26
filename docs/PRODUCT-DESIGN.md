# Product and delivery notes

The Android app and the browser PWA share the Coral Studio palette and emphasis on keeping the work itself visible. They do not share an interaction model.

- **Android:** follow Android's [Material adaptive layout guidance](https://developer.android.com/codelabs/adaptive-material-guidance), [adaptive quality criteria](https://developer.android.com/develop/adaptive-apps/quality-guidelines/adaptive-app-quality/tier-2), and [accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps). Keep system file entry, sharing, and Back. Give the frequent action priority, keep clear touch targets, and use a narrower single-column presentation when large text needs it.
- **Web:** a compact source/preview workflow for small screens and the library beside the workbench when room permits. Imports and drafts are browser-local. The browser edition deliberately does not make Android format-parity claims.
- **Project page:** use [LocalSend](https://github.com/localsend/localsend)'s independently readable language pages, screenshots, and cross-platform download structure; use [Excalidraw](https://github.com/excalidraw/excalidraw)'s separation of the online tool, local-first features, and developer instructions. No third-party imagery or copy is reused.

The marketing site and workbench are separate paths under the existing HTTPS site. GitHub Pages deploys only the explicit web subtree: no Android build settings, unpublished drafts, or repository-wide files. The demo links lead to different real narrated files rather than changing only the captions.

## Web acceptance

- Open both locales; check navigation, quick start, the correct narration and caption downloads, and Android release links.
- Start from a clean browser at the deployed project subpath and verify app-shell load, representative file import, source edits, preview, export, draft reload, small-screen switching, and offline reload after service worker activation.
- Verify untrusted document boundaries separately. Do not treat a Chromium mobile viewport as proof of installation on physical iOS hardware.
