# MOMENTUM for iPhone / iPad (native SwiftUI)

This is a **native** iOS app written in SwiftUI — not the Capacitor WebView shell in `apps/mobile/ios`. It keeps the same mental model as the rest of MOMENTUM (goals → milestones → tasks → visible next move) and stores everything locally, offline, on the device.

## Open and run

Requires macOS with Xcode 16 or newer (built and type-checked with Xcode 27.2 / iOS 26 SDK; deployment target is **iOS 16**).

```bash
open apps/ios/Momentum/Momentum.xcodeproj
```

In Xcode:

1. Select the **Momentum** scheme.
2. Choose an iPhone/iPad simulator or a connected device.
3. Press **▶ Run**. For a device, set **Signing & Capabilities → Team** to your Apple ID.

From the repository root you can also use:

```bash
npm run ios:native:open       # open the Xcode project
npm run ios:native:build      # build for the iOS simulator without signing
npm run ios:native:test       # build and run the unit tests on a booted simulator
```

## What is in the app

| Screen | What it does |
| --- | --- |
| Overview | Greeting, momentum signal (same formula as the web app), active goals, next best move, 7-day completion chart, deadlines, recent activity |
| Goals | Status/priority/category filters, sort, search, goal cards with live progress; archive and 30-day trash |
| Goal detail | Plan / Notes / Activity tabs, milestone rail, tasks and one level of subtasks, progress metrics, recurrence, complete/reopen/archive/trash |
| Planner | Focus queue, overdue tasks and tasks due this week across all active goals; quick task entry |
| Categories | Create, edit, delete categories with colors/icons and per-category goal stats |
| Settings | Theme (dark/light/system), accent presets, density, week start, reduce motion, display name, JSON backup export/import, sample workspace, erase |

Global search (magnifying-glass button) covers goal titles/descriptions/notes, tasks, subtasks, milestones and categories.

## Data model

`Momentum/Models/Domain.swift` mirrors `apps/web/src/state/types.ts`: `Goal`, `GoalTask`, `Milestone`, `Category`, `Activity`, `Recurrence`, `FinancePlan`, `AppSettings`, `Workspace`.

Progress and momentum use the same formulas as `apps/web/src/lib/progress.ts`:

- `taskPercent = completed / total`, `milestonePercent = completed / total`
- Mixed plan: `0.65 × taskPercent + 0.35 × milestonePercent`; completed goal = 100%
- Momentum: `0.72 × mean(goal progress) + 0.28 × velocity`, where velocity comes from tasks completed in the last 7 days

Recurring goals create the next occurrence on completion, shifting deadlines and milestones by the same calendar-day delta.

## Persistence and backup

`Momentum/Store/Persistence.swift` writes two JSON files atomically under Application Support/MOMENTUM:

- `momentum-workspace.json` — goals, categories, activities
- `momentum-settings.json` — appearance and preferences

Settings → **Export JSON backup** uses the same envelope shape as the web export (`app: "momentum"`, `version: 2`, `workspace`, `settings`), and **Import JSON backup** replaces the workspace after confirmation. Because this is a separate native store, iOS and the web/PWA do **not** share data automatically — move it with a JSON backup.

## Deliberate scope

The native app is offline by design. It does not include AI planning, accounts/OAuth, encrypted cloud sync, attachments, PDF export or a connection to the local Java service. Those features remain in the web app and the Capacitor shells (`apps/mobile/ios`, `apps/mobile/android`). No API keys or credentials are bundled.

## Tests

`MomentumTests/MomentumTests.swift` covers progress weighting, momentum, deadline labels, recurrence, store persistence and backup round-trips. Run them from the Momentum scheme's Test action in Xcode.
