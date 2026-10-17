# Weekly Summary Reminder Implementation Plan

> **For agentic workers:** Use this plan as the task-level execution map; implement it sequentially with tests before production code.

**Goal:** Remind the user at a separately configured weekly time to create the latest eligible app summary that has not yet been saved.

**Architecture:** Persist reminder enablement and local day/time independently from the existing email schedule. The scheduler evaluates the latest eligible Friday, and the existing owner lock protects the summary-exists check and owner+Friday notification deduplication. The bell and subscribed push notification route to the matching weekly preview and its existing explicit create flow.

**Tech Stack:** Java 21, Spring Boot 3.5, Gradle, MariaDB, Flyway, Vue 3 Options API, PrimeVue, Playwright.

**Spec:** Personal Todo task #463, “I need a reminder to save the weekly summary,” selected 2026-10-17.

## Global Constraints

- Reminder defaults to enabled Monday at 08:00 Europe/Madrid and remains independent of the email schedule.
- At most one reminder exists per owner and Friday; repeated ticks and dismiss/open actions do not recreate it.
- Do not replay a backlog, generate a summary automatically, invoke Coach, save a reflection, or resend email.
- Saving manually or through scheduled email suppresses/removes the corresponding reminder.
- Disabling the reminder clears its pending weekly-summary reminders; schedule edits affect the next scheduled occurrence.
- Preserve current authenticated owner boundaries, notification behavior, and existing summary warnings and explicit Save anyway flow.

## Review Focus

- A scheduler retry or restart never creates a second notification for the same owner and Friday; keep the same dedup key without clearing dismissal.
- A summary created by the same-time scheduled email or manual save leaves no stale reminder.
- A reminder opens the correct Friday preview and Create action without making an automatic write or dismissing on open.
- A reminder schedule remains active with email disabled and respects Madrid local time across DST and weekly boundaries.
- User preference changes and notification cleanup affect only the authenticated owner; verify settings persistence and narrow/desktop layout.

---

### Task 1: Persist the independent reminder schedule

**Files:**
- Modify: `backend/src/main/java/com/jllado/weightcontrol/domain/User.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/api/dto/WeeklySummaryDtos.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/api/WeeklySummaryController.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/service/WeeklySummaryService.java`
- Create: append-only migration under `backend/src/main/resources/db/migration/`
- Test: `WeeklySummarySchedulePersistenceTest`, `WeeklySummaryControllerTest`
- Modify: `src/components/WeeklySummarySettings.vue`, `src/services/WeeklySummaryService.js`
- Test: `tests/e2e/weekly-summary-settings.spec.js`

**Interfaces:**
- Preserve the existing weekly email schedule fields and endpoints.
- Extend the weekly summary schedule response/update contract with reminder enabled, weekday, and local time fields.
- Default reminder settings to enabled, Monday, and 08:00 without changing email preferences.

- [x] Add persistence tests proving default values, custom values across reload, and unchanged email preference values.
- [x] Run `scripts/check.sh backend test --tests '*WeeklySummarySchedulePersistenceTest' --tests '*WeeklySummaryControllerTest'`; confirm the new assertions fail because the reminder fields are absent.
- [x] Add the migration, entity fields, request/response DTO values, and owner-scoped update/read behavior; keep the existing lock and validation conventions.
- [x] Extend the settings form with an independently enabled reminder day/time; verify save and reload in the existing settings E2E suite at mobile and desktop widths.
- [x] Run those focused backend and frontend tests.

### Task 2: Create and retire one reminder per eligible Friday

**Files:**
- Modify: `backend/src/main/java/com/jllado/weightcontrol/domain/InAppNotificationType.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/service/InAppNotificationService.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/api/dto/InAppNotificationDtos.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/service/WeeklySummaryScheduler.java`
- Modify: `backend/src/main/java/com/jllado/weightcontrol/service/WeeklySummaryService.java`
- Test: `WeeklySummaryServiceTest`, `InAppNotificationServiceTest`, and notification controller tests

**Interfaces:**
- Add a weekly-summary notification type with owner+Friday identity and the Friday's local notification date.
- The reminder service evaluates one latest eligible Friday at its due Madrid-local schedule; it does not enumerate missed historical weeks.
- Existing notification bell delivery and subscribed push delivery consume the persisted notification through the current pipeline.

- [x] Add failing service tests for due/missing summary creates one notification, already-saved summary creates none, reminder disabled creates none, not-due time creates none, email disabled still permits the reminder, and repeated scheduler ticks remain deduplicated after dismiss.
- [x] Add lifecycle tests for manual summary create, scheduled-email create at the same time, and disabling preferences clearing only that owner's pending weekly-summary reminders.
- [x] Add notification DTO/repository tests for persistence after its reminder Friday, route payload, dismissal retention, and owner boundaries.
- [x] Run the targeted backend test families and confirm failures are caused by missing reminder behavior.
- [x] Implement due evaluation under the existing owner lock; preserve the unique owner+dedup key and persistent notification semantics. Coordinate summary creation and reminder cleanup in the established transaction boundary.
- [x] Rerun focused tests, including scheduled email retry, Madrid DST, concurrency, and weekly boundary tests.

### Task 3: Open the matching preview and explicit creation flow

**Files:**
- Modify: `backend/src/main/java/com/jllado/weightcontrol/api/dto/InAppNotificationDtos.java`
- Modify: `src/components/NotificationBell.vue`
- Modify: `src/components/WeeklySummaries.vue`
- Modify: route configuration only if the existing weekly summary route cannot accept the reminder destination
- Test: `tests/e2e/login-and-push.spec.js`, `tests/e2e/weekly-summaries.spec.js`

**Interfaces:**
- The notification action URL identifies the summary page and exact `weekEnd` for preview.
- Opening notification preserves it; only explicit dismissal or successful summary save removes it.
- The existing Create flow remains the only operation that writes the summary and retains its warning and Save anyway behavior.

- [x] Add a browser test that opens the selected Friday preview and verifies creation remains explicit with no automatic POST.
- [x] Add a browser test that cancelling Create leaves no create request; opening via the bell keeps the notification pending and explicit dismissal removes it.
- [x] Verify saving the requested summary clears the matching reminder through the persisted lifecycle test.
- [x] Implement the notification route handling and selected-week state using existing router query conventions.
- [x] Run the notification and weekly summary E2E suites at supported widths; verify bell and push route behavior without sending a real push.

### Task 4: Verify and deliver

- [x] Run sequentially: focused backend suites from task #463, `scripts/check.sh frontend lint`, the combined settings/summary/notification E2E filter, and `git diff --check`.
- [x] Inspect migration compatibility, notification DTO route, no-backlog behavior, email/reminder timing interaction, and the mobile/desktop settings screenshots.
- [x] Obtain independent read-only review; correct supported findings and rerun affected checks.
- [ ] Build release artifacts before pushing, merge/push master, deploy with the documented helper, and independently verify production frontend/backend identity and readiness.
- [ ] Finalize Todo #463 only after production verification passes.
