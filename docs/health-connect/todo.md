# Weight Control Android and Health Connect TODO

This checklist implements the [Weight Control Android and Health Connect plan](plan.md) in dependency order. Complete and validate one group before starting the next. A completed engineering group does not authorize Play Store publication or production deployment.

## 1. Confirm product and platform contracts

Dependencies: none.

- [ ] Choose and record the permanent Android application ID and user-facing store name.
- [ ] Confirm Capacitor as the native container with a small repository-owned Kotlin Health Connect plugin.
- [ ] Inventory every browser capability used by Weight Control: login, API transport, push, notification actions, app links, photos, camera/files, clipboard, external links, storage, PWA updates, and offline behavior.
- [ ] Define the private feasibility slice as foreground sleep synchronization from Health Connect.
- [ ] Define the beta data-type order: sleep/overnight vitals, activity, exercise sessions, weight, then blood pressure.
- [ ] Define the support floor as Android 9 and record the tested Android/Health Connect versions.
- [ ] Decide the production API origin, app-link domains, OAuth client layout, signing ownership, and secret-handling boundaries.
- [ ] Review current Capacitor, Android Gradle Plugin, AndroidX Health Connect, Credential Manager, and Google Play requirements; pin selected versions in configuration.
- [ ] Write the version and command sources of truth into `docs/project-guide.md` before implementation expands repository boundaries.
- [ ] Record baseline screenshots and behavior for login, Settings, notifications, progress-photo upload, and representative core routes.
- [ ] Document out-of-scope behavior: no separate companion, no Google Fit API, no Health Connect writes, no raw continuous-heart-rate upload, and no publication.

Definition of done: the permanent package identity, native architecture, MVP record types, compatibility floor, and parity inventory are explicit enough to prevent generated scaffolding or permissions from deciding product behavior.

Validation:

```bash
git diff --check
```

## 2. Create the native application shell

Dependencies: group 1.

- [ ] Add pinned Capacitor dependencies and scripts to `package.json`.
- [ ] Add `capacitor.config.*` with the permanent app ID, Weight Control name, bundled output directory, Android HTTPS scheme, and approved navigation policy.
- [ ] Generate and check in the `android/` project while excluding generated build and local-machine files.
- [ ] Configure release-independent application icons, splash assets, versioning, and accessible application labels.
- [ ] Add `src/native/PlatformService.js` as the single platform-capability boundary.
- [ ] Keep same-origin `/api` requests in browsers and introduce a configured API base URL for the native client.
- [ ] Disable service-worker registration in the native container while preserving current browser/PWA behavior.
- [ ] Route external URLs to the system browser and verified Weight Control links into Vue routes.
- [ ] Verify progress-photo selection/capture and upload in the native shell.
- [ ] Add Android build, lint, and test operations to `scripts/check.sh` under the shared worktree lock.
- [ ] Update dependency notices and checked-in licensing artifacts for new production dependencies.
- [ ] Add a smoke test proving one APK contains the Vue assets and can render authenticated and unauthenticated routes without loading the frontend remotely.

Definition of done: one debug Weight Control APK renders the existing Vue application from bundled assets, retains the PWA build for browsers, and builds only through the repository check wrapper.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh android test
scripts/check.sh android lint
scripts/check.sh android assembleDebug
```

## 3. Add commercial-grade native authentication

Dependencies: group 2.

- [ ] Register separate Google OAuth clients for the web application and Android package/signing certificates while retaining the backend audience contract.
- [ ] Add native Google sign-in through Android Credential Manager.
- [ ] Extract shared Google ID-token verification and user resolution from the existing web login service.
- [ ] Add a native login exchange returning a short-lived access token and rotating refresh credential.
- [ ] Add append-only Flyway persistence for hashed, installation-scoped refresh credentials and revocation metadata.
- [ ] Add bearer-user authentication to normal application endpoints without changing Coach Action or push-release authentication.
- [ ] Store refresh credentials with Android Keystore-backed secure storage and avoid persistent access-token storage where practical.
- [ ] Add access-token refresh, single-installation logout, all-installations revocation, expiry, rotation, and replay handling.
- [ ] Add an API transport adapter that attaches native bearer tokens while leaving web cookies unchanged.
- [ ] Clear native credentials and local pending sync state on logout and account change.
- [ ] Keep the current email allow-list for a private feasibility build only.
- [ ] Replace the hard-coded email restriction with an explicit registration/onboarding policy before external testing.
- [ ] Add authentication controller, service, security-filter, credential-rotation, and ownership tests.
- [ ] Verify login, refresh, logout, reinstall, revoked credentials, and account switching on a real device.

Definition of done: the Android client authenticates without an embedded web login, normal APIs accept revocable mobile credentials, browser login remains unchanged, and no commercial build contains the hard-coded single-user restriction.

Validation:

```bash
scripts/check.sh backend test
scripts/check.sh frontend lint
scripts/check.sh android test
scripts/check.sh android lint
```

## 4. Build the Health Connect bridge

Dependencies: groups 2 and 3.

- [ ] Add the pinned AndroidX Health Connect client and required manifest queries.
- [ ] Implement native availability states for unsupported Android, missing/outdated Health Connect, and available Health Connect.
- [ ] Implement a repository-owned Kotlin Capacitor plugin with typed methods for availability, granted permissions, permission requests, settings navigation, initial reads, changes, and disconnect cleanup.
- [ ] Add sleep-session and sleep-stage reads for the initial slice.
- [ ] Add heart-rate and RMSSD HRV reads limited to sleep intervals and calculate only the aggregates required by Weight Control.
- [ ] Preserve record IDs, source versions/modified times, instants, offsets, and data origins.
- [ ] Page reads and bound each native response and backend batch.
- [ ] Persist per-record-group change tokens and unsent batches in installation-scoped native storage.
- [ ] Handle Health Connect update and deletion changes without treating an expired token as an empty successful sync.
- [ ] Add JavaScript DTO mapping in `src/native/HealthConnectService.js` without leaking Capacitor imports into ordinary components.
- [ ] Add Health Connect fixture/unit tests for units, stages, overlapping midnight, daylight-saving changes, pagination, updates, deletions, and partial permissions.
- [ ] Add Android instrumentation tests for system permission grant, partial grant, denial, revocation, and settings return.
- [ ] Confirm Oura-originated sleep records are readable on a real Android device.

Definition of done: the Weight Control Android app can read typed, source-preserving sleep data from Health Connect in the foreground and return deterministic normalized records to Vue without uploading them yet.

Validation:

```bash
scripts/check.sh android test
scripts/check.sh android connectedCheck
scripts/check.sh android lint
```

## 5. Add provider-neutral backend synchronization

Dependencies: groups 3 and 4.

- [ ] Finalize typed synchronization DTOs and a schema-version policy; do not accept arbitrary record JSON.
- [ ] Add append-only Flyway migrations for mobile installations, health data sources, idempotent batches, imported sleep, and external record provenance.
- [ ] Enforce unique `(user, platform, origin, record type, external ID)` identity and `(installation, idempotency key)` batch identity.
- [ ] Add `DeviceSyncController`, focused DTOs, `DeviceSyncService`, repositories, and domain models using normal Spring layering.
- [ ] Derive ownership exclusively from the bearer-authenticated user and installation.
- [ ] Validate the complete bounded batch and apply supported changes transactionally.
- [ ] Implement idempotent creation, update, deletion, retry, and rejected-entry reporting.
- [ ] Model imported sleep values as partial where Health Connect can omit them; keep unknown values distinct from zero.
- [ ] Preserve manual sleep entries and implement source-labelled imported alternatives rather than overwriting them.
- [ ] Add a deterministic preferred-source rule and a user selection for dates with competing imported sleep sessions.
- [ ] Recalculate affected summaries and personal records only after batch commit.
- [ ] Add retention, disconnect, and delete-imported-data services.
- [ ] Keep source package names, external IDs, installation IDs, tokens, and payloads out of ordinary logs and API responses that do not require them.
- [ ] Add MariaDB migration tests and controller/service tests for user isolation, replay, partial values, conflicts, updates, deletions, rollback, and malicious cross-user identifiers.
- [ ] Upload the real-device Oura sleep slice twice and prove the second upload creates no duplicate.

Definition of done: foreground Health Connect sleep synchronization is resumable and idempotent, imported records retain provenance, manual records remain intact, and Oura credentials are not stored by Weight Control.

Validation:

```bash
scripts/check.sh backend test
scripts/check.sh android test
scripts/check.sh frontend lint
```

## 6. Add the Health data settings experience

Dependencies: groups 4 and 5.

- [ ] Add a Health data panel to the existing Settings screen using the nearest established panel and asynchronous-action patterns.
- [ ] On Android, show Health Connect availability, requested/granted types, source labels, import range, last successful sync, latest actionable error, and `Sync now`.
- [ ] Add actions to request permissions, open Health Connect settings, select a preferred source, disconnect, and delete imported data.
- [ ] Explain each requested data type's concrete user benefit before opening the system permission screen.
- [ ] Treat denial and partial permission as normal visible states and do not repeatedly prompt.
- [ ] On the web, explain that Health Connect requires the Android version without showing nonfunctional permission controls.
- [ ] Use `ActionButton` for synchronization/deletion mutations and `SaveFields` for editable connection settings.
- [ ] Keep sync state available after route changes and prevent duplicate concurrent synchronization.
- [ ] Add concise source labels to imported sleep history and details.
- [ ] Add loading, empty, partial, offline, conflict, success, and deletion-confirmation states.
- [ ] Add Playwright coverage using a native-capability fixture for unavailable, disconnected, partial permission, syncing, success, conflict, and error states.
- [ ] Verify the interface at 376px, 390–393px, 575px, 640px, 960px, and 1280px with intentional wrapping and no page overflow.
- [ ] Capture and visually compare Settings and sleep-history screenshots against the repository baseline.

Definition of done: users understand whether synchronization is available, control permissions and retained data, can synchronize without leaving Weight Control, and can identify imported data and its source.

Validation:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend playwright test --grep "Health data"
scripts/check.sh android connectedCheck
```

## 7. Expand to activity and additional measurements

Dependencies: group 6; complete one record family at a time.

- [ ] Add steps, active-calorie, and distance permissions and native readers.
- [ ] Add typed imported daily-activity persistence retaining contributing records and origins.
- [ ] Implement source priority and overlap rules that never sum competing origins automatically.
- [ ] Add external exercise-session permission, reader, persistence, and presentation.
- [ ] Keep external exercise sessions separate from detailed Weight Control workout sessions, strength volume, and Coach assessments.
- [ ] Add weight permission, reader, partial body-measurement model, history presentation, and manual/import conflict behavior.
- [ ] Do not require or invent fat and muscle values for imported weight.
- [ ] Add blood-pressure permission, reader, typed persistence, source presentation, and conflict behavior.
- [ ] Add the historical-read permission only behind an explicit older-range import action.
- [ ] Add feature-level permission explanations and update the Play declaration inventory after each record family.
- [ ] Add fixture, backend, UI, and real-device tests for every record family before enabling its permission in production.
- [ ] Verify at least one non-Oura source before advertising multi-device compatibility.

Definition of done: each enabled record type has a complete permission, mapping, provenance, conflict, deletion, presentation, and test path; the application never requests a data type with no shipped user-facing feature.

Validation:

```bash
scripts/check.sh backend test
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
scripts/check.sh android test
scripts/check.sh android connectedCheck
scripts/check.sh android lint
```

## 8. Integrate imported data across Weight Control and Coach

Dependencies: groups 5–7 for each delivered domain.

- [ ] Define combined query services for manual and preferred imported BODY, VITALS, RECOVERY, and activity data.
- [ ] Update dashboard and history summaries without changing existing manual-entry mutation contracts.
- [ ] Recalculate personal records correctly after imported updates, deletions, and preferred-source changes.
- [ ] Add imported activity semantics without counting external exercise sessions as Weight Control strength sessions.
- [ ] Extend Coach catalog coverage only for data visible in the corresponding product history.
- [ ] Include concise source/aggregation semantics in Coach context without package names or external identifiers.
- [ ] Preserve reflection eligibility, dates, ratings, writes, and existing response JSON.
- [ ] Treat absent imported values as unknown and recorded zeroes as valid.
- [ ] Update `docs/coach/plan.md`, `docs/coach/todo.md`, `docs/coach/coach-action.openapi.yaml`, and `docs/coach/coach-gpt.md` for only the delivered contracts.
- [ ] Add regression tests for dashboard, weekly summaries, records, Coach context, privacy exclusions, and reflections.
- [ ] Keep private GPT publication as a separately authorized step after repository delivery.

Definition of done: preferred imported data contributes consistently to user-visible analysis and Coach context without corrupting manual records, workout meaning, privacy, or reflection contracts.

Validation:

```bash
scripts/check.sh backend test
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh frontend test:e2e
```

## 9. Reach native product parity

Dependencies: groups 3 and 6.

- [ ] Add installation-scoped native push-token registration while retaining current browser push subscriptions.
- [ ] Extend backend notification delivery to select web push or native push per registered installation.
- [ ] Route notification taps and explicit actions through verified app links into the existing Vue route/query workflows.
- [ ] Preserve reminder dismissal, snooze, login continuation, and in-app bell behavior.
- [ ] Verify app links, external links, camera/files, photo previews, clipboard, local persisted drafts, timers, offline transitions, and update behavior.
- [ ] Ensure native app updates do not interact with the browser PWA service-worker update flow.
- [ ] Run authenticated and unauthenticated route parity tests across the product inventory.
- [ ] Add accessibility checks for system back, keyboard/focus, screen readers, text scaling, reduced motion, and permission explanations.
- [ ] Test process death, device reboot, token refresh, interrupted uploads, and pending-batch recovery.
- [ ] Decide from beta evidence whether background Health Connect synchronization is necessary.
- [ ] If justified, add background-read permission, WorkManager scheduling, battery-aware behavior, disclosure, Play justification, and foreground fallback tests.

Definition of done: the installed Android app supports the core Weight Control workflows and reminders as one coherent product; any documented difference from the PWA is intentional and not required for safe daily use.

Validation:

```bash
scripts/check.sh frontend test:e2e
scripts/check.sh frontend test:pwa
scripts/check.sh backend test
scripts/check.sh android test
scripts/check.sh android connectedCheck
scripts/check.sh android lint
scripts/check.sh android assembleDebug
```

## 10. Complete privacy, policy, and account lifecycle

Dependencies: groups 3, 6, 7, and 9.

- [ ] Write the in-app prominent disclosure and obtain legal/privacy review before external testing.
- [ ] Publish an accessible, non-PDF privacy policy covering health data, sources, purposes, storage, sharing, retention, deletion, and security.
- [ ] Add user-facing export, imported-data deletion, account deletion, installation revocation, and support flows.
- [ ] Verify deletion removes imported health records, provenance, batches, connection state, refresh credentials, and derived data.
- [ ] Complete a data-flow inventory for the Android app, backend, hosting, push provider, logging, backups, and Coach.
- [ ] Ensure health data and tokens are excluded from analytics, crash reporting, logs, screenshots, notifications, and backups unless explicitly reviewed.
- [ ] Threat-model token theft, replay, forged source/deletion data, cross-user access, exported Android components, debug builds, app backups, and rooted-device limits.
- [ ] Complete Google Play Data safety answers from the verified data-flow inventory.
- [ ] Complete the Health apps declaration and justify every requested Health Connect type with an implemented user-facing feature.
- [ ] Prepare reviewer instructions and a dedicated test account without exposing production credentials.
- [ ] Confirm marketing and store copy make wellness claims only and list compatibility by data type rather than promising every metric for every device.

Definition of done: actual behavior, policy declarations, privacy documentation, deletion flows, and store claims agree and have evidence suitable for Play review.

Validation:

```bash
git diff --check
scripts/check.sh backend test
scripts/check.sh frontend test:e2e
scripts/check.sh android connectedCheck
```

## 11. Validate and prepare the commercial release

Dependencies: groups 1–10.

- [ ] Test Android 9/13 with the Health Connect app, Android 14 with system Health Connect, and the current supported Android release.
- [ ] Test Oura plus at least one non-Oura source across grant, partial grant, denial, revocation, sync, update, deletion, conflict, and disconnect flows.
- [ ] Test offline synchronization, duplicate batch retry, daylight-saving/time-zone changes, logout, account switching, reinstall, process death, and app upgrade.
- [ ] Run the full browser/PWA/backend regression suite and confirm existing customers are unaffected.
- [ ] Add Android App Bundle creation, signing without checked-in secrets, checksum recording, and artifact retention to the release-artifact gate.
- [ ] Verify release shrinking/obfuscation rules preserve Capacitor and Health Connect behavior.
- [ ] Produce store screenshots and descriptions from a release-equivalent build with synthetic data.
- [ ] Complete internal Play testing, then a controlled closed test with monitoring and support procedures.
- [ ] Record stage timings and distinguish implementation, testing, artifact creation, store review, rollout, and production verification.
- [ ] Obtain explicit authorization before Play submission, production deployment, or staged rollout.
- [ ] Publish through a staged rollout with crash, authentication, sync-failure, and API monitoring plus a tested rollback path.

Definition of done: the signed candidate passes the complete locked release gate and real-device acceptance, policy review material matches the product, and an explicitly authorized staged rollout can be monitored and reversed.

Validation:

```bash
# Use the repository's extended complete release-artifact gate after it owns the Android artifact.
```

## 12. Add future providers only when justified

Dependencies: a stable commercial Health Connect release.

- [ ] Measure missing or delayed data by device and record type before selecting another provider.
- [ ] Add Apple Health through an iOS Weight Control app using the same typed synchronization contracts and provenance rules.
- [ ] Add direct Oura OAuth/webhooks only for valuable Oura data absent from Health Connect; do not reintroduce tokens on `users`.
- [ ] Evaluate Fitbit/Google Health and other vendor cloud APIs independently for coverage, terms, review, rate limits, deletion, and commercial availability.
- [ ] Give every provider a focused adapter, connection lifecycle, consent UI, revocation flow, webhook verification, and contract test suite.
- [ ] Keep cross-provider source priority explicit and never silently merge overlapping measurements.
- [ ] Update privacy disclosures, processor inventory, support documentation, Coach semantics, and store declarations for each enabled provider.

Definition of done: each additional provider solves a measured coverage problem while reusing the user-owned, typed, idempotent ingestion model rather than adding provider logic to product domains.
