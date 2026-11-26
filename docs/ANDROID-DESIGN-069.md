# Android interface review – Code Canvas 0.6.9 candidate

| Screen | Review finding | Change |
| --- | --- | --- |
| Library | Repeated high-contrast overflow buttons competed with thumbnails and cut long filenames into narrow fragments. Search and creation actions looked mechanically similar. | Give filenames the full card width, use a quiet artwork-corner overflow with a full touch target, move timestamps to list/details, make search and creation distinct. |
| Add | The system popup listed two bare actions with no explanation. | Use a Code Canvas bottom sheet titled “Create or import”, with icons and short descriptions for New and Import, retaining the same routes. The main Paste action remains separate. |
| File catalog | “Other” was visually stranded; the floating Paste action drew above the scrim, and the close target was too small. | Balanced format filters, visible close/selection states and a modal surface that disables underlying controls and accessibility focus. Preserve current catalog items, filtering and Back behavior. |
| Settings | Theme repeated its state; network text described defaults even when the current toggle was on. | Group settings by purpose, use one theme selection indicator and state-specific connection explanation. Keep stored preferences and all existing actions. |
| Code search and dialogs | Several common buttons only measured 30–40 dp, and enlarging them in one row could squeeze the search field. | Use 48 dp targets and reflow the search actions below the input at compact widths. |

## Selected text

The app registers the standard Android text-selection action. Compatible apps may show “Open in Code Canvas” under the text menu’s overflow. The selected characters become a new local draft and open in the Code tab; the source app’s text is not modified. The initial editor session does not run selected scripts or load external resources. This action does not make apps with their own custom selection menu display Android actions; system Share is the fallback.

References: [Android touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views), [Android text-selection action](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT).

## Validation

- 282 JVM tests and 45 Android emulator tests passed across the redesign, search, dialogs and preview.
- A separate test package selected synthetic text using Android's native selection toolbar. Its More menu showed Open in Code Canvas, which opened the exact original string in the Code editor. Back returned to the source app with unchanged text.
