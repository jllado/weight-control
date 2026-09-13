# Weight Control Android and Health Connect Plan

## Purpose

Ship one commercial-ready Weight Control Android app that contains the existing Vue experience and reads approved health data from Health Connect without requiring a separate companion app.

Keep the browser/PWA product operational, preserve the Spring and MariaDB backend as the system of record, and establish provider-neutral contracts that can support additional Android devices, Apple Health, and selected cloud providers later.

## Product decisions

- Publish one Android application named **Weight Control**; do not publish or expose a separate synchronization companion.
- Package the existing Vue application with Capacitor and add a small native Kotlin layer for Health Connect, Google authentication, secure credential storage, deep links, and notifications.
- Keep the PWA available. The Android package is another client of the same backend, not a replacement frontend or a second product.
- Read from Health Connect only in the first release. Do not write Weight Control records back until source-loop prevention and a concrete user benefit are designed.
- Import all approved origins selected by the user rather than filtering for Oura, and preserve the actual originating Android package on every imported record.
- Keep provider-specific credentials and APIs outside the first Health Connect release. Add direct providers later only for valuable data they do not publish to Health Connect.
- Use explicit, typed backend contracts and relational persistence for supported health concepts; do not introduce an unrestricted JSON health-event endpoint.
- Keep manual records distinguishable from imported records and never silently overwrite a manual entry.
- Start with foreground synchronization and a user-visible **Sync now** action. Add background access only after the foreground flow is reliable and its benefit justifies the extra permission.
- Treat Play Store publication, policy declarations, and production rollout as separate authorized release work; planning or implementation does not authorize publishing or deployment.

## Why this architecture

Health Connect is an Android, on-device data store. A browser or PWA cannot read it directly, and the current Weight Control backend cannot pull it from Google. A native layer must read records on the phone and upload the normalized result for the authenticated Weight Control user.

Capacitor keeps the current Vue 3, Vite, PrimeVue, routing, and service structure while providing the native bridge. The user sees and installs only Weight Control:

```text
Oura / Fitbit / compatible device apps
                    |
                    v
          Health Connect on Android
                    |
                    v
  Weight Control Android app (Vue + Kotlin)
                    |
          authenticated sync batches
                    |
                    v
       Spring API -> MariaDB -> app/Coach
```

The native package must contain the compiled frontend rather than loading production pages remotely. This gives releases a reviewable, versioned client and avoids turning the app into a thin remote website wrapper.

## Current repository constraints

- The frontend is a Vite-built Vue 3 PWA and calls same-origin `/api` endpoints with an HTTP-only session cookie.
- The Android package will use a different origin and cannot rely on the browser cookie contract.
- Google login currently uses the web Google Identity Services button, and the backend permits only one hard-coded email address. Neither contract is suitable for a commercial Android release.
- Existing PWA push notifications and service-worker action routes need native equivalents before the Android app can claim product parity.
- `sleeps` permits one record per user and date. Imported sessions from multiple devices therefore need an explicit source-selection and conflict policy.
- Existing weight rows require body-composition values that a Health Connect weight record may not contain.
- Existing workout sessions contain Weight Control exercise lines and assessments. An imported exercise session is not automatically equivalent to a recorded Weight Control workout.
- Migrations V3 and V4 show that an Oura-specific token and sleep integration was added and then removed. Do not restore provider credentials or `oura_*` fields as part of this work.

## Target repository shape

```text
android/                         Capacitor-generated Android project and Kotlin bridge
capacitor.config.*               Native package and bundled web configuration
src/native/                      JavaScript adapters for native-only capabilities
src/services/HealthSyncService.js
backend/.../api/DeviceSyncController.java
backend/.../api/dto/DeviceSyncDtos.java
backend/.../service/DeviceSyncService.java
backend/.../domain/              Connections, sources, and imported health records
backend/.../repository/          User-scoped synchronization repositories
docs/health-connect/             Plan, checklist, contracts, and later evidence
```

Keep native capability detection behind focused adapters so ordinary Vue components do not import Capacitor packages directly.

## Native application foundation

Add Capacitor using versions pinned in `package.json` and the generated Android configuration. Use the application ID chosen for production from the start because Health Connect declarations, Google OAuth credentials, app links, and Play approval are package-specific.

The Android build must:

- Require Android 9 or later because that is the Health Connect compatibility floor.
- Use Java 21-compatible Android tooling where supported by the pinned Android Gradle Plugin and document the actual Android build JDK separately from the backend runtime when they differ.
- Serve bundled Vite assets and use a configured production API base URL.
- Disable PWA service-worker registration inside the native container; Play-delivered application updates own the bundled native assets.
- Retain normal PWA registration and same-origin API behavior in browsers.
- Open external links in the system browser and verified Weight Control links inside the app.
- Preserve file and camera workflows used by progress photos.

Implement the Health Connect bridge as a small repository-owned Kotlin Capacitor plugin over the official AndroidX Health Connect client. Avoid depending on an unmaintained community wrapper for sensitive health contracts.

## Authentication and API transport

Preserve the current web cookie flow. Add a native flow instead of weakening the cookie:

1. Use Android Credential Manager with the production package name and signing-certificate fingerprints to obtain a Google ID token for the backend audience.
2. Exchange that credential at a native authentication endpoint.
3. Return a short-lived access token and a rotating, revocable refresh credential associated with the user and Android installation.
4. Store the refresh credential using Android Keystore-backed secure storage; keep the access token in memory where practical.
5. Send the access token as `Authorization: Bearer` to ordinary `/api` requests from the native client.
6. Rotate credentials through a dedicated refresh endpoint and revoke the installation credential on logout or account/device removal.

Extend session authentication to accept user bearer tokens on normal application routes without changing the dedicated Coach and push-release authentication boundaries. Keep web session renewal in its HTTP-only cookie. Add an API transport adapter so existing services continue using relative URLs on the web and use the configured base URL plus bearer authentication in the native app.

Commercial release requires replacing the hard-coded email allow-list with an intentional onboarding and account policy. This is a prerequisite for external testers or customers, but it can follow the private native feasibility slice.

## Health Connect permissions and onboarding

The first permission screen must explain the user-facing benefit before Android displays its system permission controls. Request only the record types enabled in Weight Control settings.

Use staged permissions:

- Initial private slice: sleep sessions and stages, heart rate, and HRV.
- Activity slice: steps, active calories, distance, and exercise sessions.
- Measurement slice: weight and blood pressure.
- Historical access: request only when the user selects an import range older than the normal Health Connect window.
- Background access: defer until foreground sync is complete and tested.

The settings UI must show availability, granted data types, last successful synchronization, selected import range, data sources found, and the latest actionable error. Users must be able to open Health Connect permission management, run a sync, disconnect the installation, and delete imported data held by Weight Control.

Permission denial is a normal state. The rest of Weight Control must remain usable, and the app must not repeatedly prompt after a denial.

## Synchronization contract

### Native reader

The Kotlin layer owns Health Connect access and returns typed records to JavaScript. It must preserve:

- Health Connect record ID and record version or last-modified metadata when supplied.
- Record type, start/end instants, zone offsets, and source data origin.
- The minimum fields required for the supported Weight Control feature.
- Change tokens per supported record-type group after the initial import.

Use paged reads. Store change tokens and unsent batches in installation-scoped native storage so interrupted synchronization resumes without duplicating accepted records.

### Backend batch API

Add an authenticated endpoint such as `POST /api/device-sync/batches` with:

- A client-generated installation ID and idempotency key.
- A schema version.
- Typed upsert and deletion entries.
- Health Connect origin, external record ID, source timestamps, and normalized values.
- A bounded batch size.

The response returns accepted and rejected entry positions plus the next synchronization state. Retrying the same batch must have no additional effect. Validate the complete batch before applying it transactionally; reject unsupported schema versions and invalid records clearly.

Do not accept a user ID in the payload. Ownership always comes from the authenticated mobile credential.

### Provenance and identity

Create provider-neutral source and connection records:

- One mobile installation belongs to one user and has a revocable identity.
- A data source identifies its platform, origin package, and user-facing label.
- An imported record has a stable `(user, platform, origin, record type, external ID)` identity.
- Store source-created and source-modified timestamps, first/last import timestamps, and deletion state.

Never use timestamps alone as record identity. Health Connect deletions must remove or mark the corresponding imported record and recalculate affected summaries and personal records.

### Domain persistence

Use typed imported-domain tables or typed extensions to existing tables after a migration design review. The model must support partial values and multiple sources without forcing Health Connect data into incompatible manual contracts.

Recommended concepts:

- Imported sleep session: interval, sleep date, stage durations, total duration, awake duration, optional average sleeping heart rate, and optional average RMSSD HRV.
- Imported daily activity: local date, steps, active calories, and distance, retaining contributing source records.
- Imported exercise session: interval, activity type, title, duration, distance, calories, and source; do not create Weight Control exercise lines or Coach assessments from it.
- Imported body measurement: timestamp, weight, and optional body-composition values.
- Imported blood pressure: timestamp, systolic, diastolic, and optional contextual fields.

Keep high-volume raw heart-rate samples on the device for the first release. Upload only aggregates required by a supported product feature. Revisit time-series storage through a separate privacy and performance design.

## Conflict and aggregation rules

Multiple connected apps may publish overlapping observations. Apply these rules visibly and deterministically:

- Exact external records are updated idempotently and are never duplicated.
- Manual records remain editable and are not overwritten by imports.
- Imported records retain their source and are not presented as manual measurements.
- When one Weight Control daily concept permits one selected value, prefer the user's configured source priority; otherwise show a conflict and let the user select the preferred source.
- Do not sum overlapping step, calorie, or distance records from different origins by default.
- Do not merge overlapping sleep sessions from different origins automatically.
- Source-priority changes recalculate derived daily values without deleting source records.
- A source deletion or disconnection does not silently delete server data; the user chooses whether to retain or delete previously imported records.

The UI and Coach context must distinguish recorded, imported, aggregated, and unknown values. Missing fields remain unknown rather than zero.

## Initial data delivery

Deliver useful slices rather than requesting every Health Connect permission at once.

### Slice 1: private feasibility

- Build and install Weight Control as one Android package.
- Complete native Google authentication and authenticated API access.
- Read a small fixture and real-device Health Connect sleep range in the foreground.
- Upload idempotent sleep batches and display source-labelled imported sleep.
- Prove Oura-originated data reaches the application without an Oura credential.
- Keep the package private and make no Play Store claim from this slice.

### Slice 2: Android beta foundation

- Add settings, permissions, manual synchronization, change-token updates, deletions, source priority, and disconnect/delete controls.
- Add steps, active calories, distance, and external exercise sessions.
- Add weight and blood pressure only after their partial-data presentation is complete.
- Include imported data in dashboard, histories, summaries, records, and Coach context with explicit provenance semantics.

### Slice 3: product parity

- Add native push registration and notification action routing while preserving browser web push.
- Verify photos, file selection, camera capture, external links, login recovery, offline shell behavior, app links, and every core route.
- Add background Health Connect access only if beta evidence shows that manual/foreground sync is insufficient.

### Slice 4: commercial release

- Remove single-user onboarding restrictions and complete account lifecycle, deletion, and support flows.
- Complete the Google Play Data safety and Health apps declarations for only the requested data types.
- Publish an accessible privacy policy describing collection, use, retention, deletion, security, and sharing of health data.
- Prepare store assets, signing/key custody, internal testing, closed testing, staged rollout, monitoring, and rollback.
- Do not publish or deploy without explicit authorization and the repository release gate.

## Frontend experience

Add a **Health data** section to Settings using existing panel, form, status, and action patterns. On the web, explain that connection requires the Android version and link to the official distribution channel once one exists. In the Android app, show the permission and sync controls.

Use concise states such as:

- `Health Connect unavailable`
- `Not connected`
- `Permission required`
- `Ready to sync`
- `Syncing…`
- `Last synced 10 minutes ago`
- `Some records need attention`

Display the source next to imported values where it affects interpretation. Provide accessible error details without exposing package identifiers unless the user opens technical details.

Follow `docs/design-guidelines.md`, including mobile widths, long labels, focus behavior, pending states, action hierarchy, and the shared `ActionButton`/`SaveFields` patterns.

## Backend and database boundaries

- Follow controller -> DTO/service -> repository/domain.
- Put the sync HTTP contract in a focused DTO file and synchronization decisions in focused services.
- Use append-only Flyway migrations.
- Keep every query user-scoped and add unique constraints for external identity and batch idempotency.
- Recalculate dependent personal records, weekly summaries, dashboard snapshots, and Coach availability only after the batch commits.
- Do not expose Android package names, external IDs, installation IDs, permission state, or sync tokens through Coach context.
- Add retention and user-initiated deletion services before external testing.

## Coach and reflection impact

Imported health information is relevant to the existing BODY, VITALS, RECOVERY, and TRAINING domains. Extend those contexts only when each product-facing history accepts the same imported values.

- Preserve current reflection date eligibility and response contracts.
- Include source and aggregation semantics without internal provenance identifiers.
- Treat absent imported fields as unknown.
- Do not infer clinical meaning from device measurements.
- Keep external exercise sessions separate from Weight Control strength-workout volume and assessments.
- Update `docs/coach/plan.md`, `docs/coach/todo.md`, the Coach schema, and GPT instructions only for delivered domains.
- Private GPT publication remains a separately authorized delivery step.

## Privacy and security

Health Connect data is sensitive user data. The implementation must include:

- Purpose-limited permission requests and a prominent disclosure before access.
- Encryption in transit and existing protected database/storage controls.
- Keystore-backed native refresh credentials and revocation per installation.
- No health data, tokens, record payloads, or external identifiers in routine logs, analytics, crash reports, push payloads, or screenshots.
- User-visible source, retention, disconnect, export, and deletion behavior.
- Account deletion that also removes imported data, connections, tokens, and sync state.
- A documented processor/subprocessor review before adding analytics, crash reporting, or third-party mobile SDKs.
- Threat-model review for token theft, replayed batches, cross-user writes, source spoofing, forged deletions, exported Android components, backups, and debug builds.

Health features remain wellness features unless a separately reviewed medical-device scope is intentionally introduced.

## Validation strategy

### Automated tests

- Kotlin unit tests for mapping, units, time zones, pagination, changes, deleted records, and permission states.
- Android instrumentation tests using Health Connect test APIs or controlled fixtures for permission, initial-read, incremental-sync, retry, and reinstall behavior.
- Backend service/controller tests for ownership, schema validation, idempotency, updates, deletions, conflicts, priorities, and transaction rollback.
- Migration tests with MariaDB for unique constraints and existing manual data.
- Vue tests and Playwright scenarios for web/native settings states, provenance, partial data, conflicts, pending actions, and responsive layouts.
- Contract fixtures shared by Kotlin, JavaScript, and Java to detect unit or schema drift.
- Regression tests proving manual records, dashboards, personal records, reflections, and Coach contexts remain correct.

### Device matrix

At minimum, verify:

- Android 9/13 with the Health Connect application.
- Android 14 and the current supported Android release with system Health Connect.
- One Oura-backed device and one non-Oura source before claiming multi-device compatibility.
- Permission grant, partial grant, denial, revocation, historical access, clock/time-zone changes, offline sync, duplicate retry, source update, source deletion, logout, and account switching.

### Repository commands

Extend `scripts/check.sh` with a locked Android target before Android source lands. Run checks sequentially and preserve existing commands:

```bash
scripts/check.sh frontend lint
scripts/check.sh frontend build
scripts/check.sh backend test
scripts/check.sh android test
scripts/check.sh android lint
scripts/check.sh android assembleDebug
```

The complete release-artifact gate must eventually build and checksum the Android App Bundle in addition to current web/backend artifacts. Never bypass the worktree lock with raw Gradle commands.

## Release gates

The Android application is not ready for external customers until all of these are true:

- Users install only Weight Control and complete connection inside it.
- Authentication has no hard-coded user restriction and supports logout, revocation, and account deletion.
- Requested Health Connect data types match the implemented features and Play declarations.
- Source conflicts, deletions, and partial values behave deterministically.
- Existing PWA users retain their current web authentication, push, and application behavior.
- Native notifications and deep links cover the existing reminder workflows required for parity.
- Privacy policy, prominent disclosure, Data safety, Health apps declaration, support, and deletion documentation are complete.
- Automated checks pass at exit zero, real-device acceptance passes, and the signed release artifact passes the authorized release gate.

## Future providers

The backend source envelope should later accept additional adapters without changing product-domain contracts:

- Apple Health through the iOS Weight Control app.
- Direct Oura OAuth and webhooks only for Oura metrics absent from Health Connect.
- Direct Fitbit/Google Health or other vendor APIs where cloud synchronization materially improves coverage.
- File import only through explicit, validated formats with the same provenance and idempotency rules.

Each provider remains responsible for mapping its source records into the supported typed contracts. Do not build a generic integration marketplace before a real provider requires it.

## Non-goals

- A separately branded or separately installed companion application.
- Google Fit API integration; it reaches end of support after 2026.
- Writing data to Health Connect in the first release.
- Silent background collection before foreground synchronization is proven.
- Automatic merging of overlapping device data.
- Treating external exercise sessions as detailed Weight Control workouts.
- Uploading raw continuous heart-rate streams without an approved feature and retention design.
- Diagnosing conditions or making medical claims from imported data.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A web wrapper feels incomplete | Require native auth, Health Connect, notifications, app links, photos, and core-route parity before commercial release. |
| Duplicate data from multiple apps | Preserve origin and external identity; use explicit source priorities and no automatic cross-source summing. |
| Missing fields do not fit current records | Model imported values as partial and update presentation deliberately instead of inventing zeroes or defaults. |
| Web and native authentication diverge | Share backend identity verification while retaining separate cookie and revocable mobile-token transports. |
| Health permissions delay Play approval | Request only shipped data types, document user benefit, and prepare policy evidence before submission. |
| Background sync becomes unreliable | Ship foreground sync first, persist change tokens/batches, and add background access only with measured need. |
| Native releases regress the PWA | Keep platform adapters isolated and run web, PWA, backend, and Android gates for release candidates. |
| Provider capabilities differ | Advertise compatibility per data type and source, not a blanket claim that every device supplies every metric. |

## Current authoritative references

Reviewed on 2026-09-13:

- [Health Connect overview](https://developer.android.com/health-and-fitness/health-connect)
- [Health Connect availability](https://developer.android.com/health-and-fitness/health-connect/availability)
- [Health Connect data types and additional permissions](https://developer.android.com/health-and-fitness/health-connect/data-types)
- [Health Connect synchronization guidance](https://developer.android.com/health-and-fitness/health-connect/sync-data)
- [Publishing a Health Connect app](https://developer.android.com/health-and-fitness/health-connect/publish)
- [Google Fit migration guide](https://developer.android.com/health-and-fitness/health-connect/migration/fit)
- [Oura Health Connect data exchange](https://support.ouraring.com/hc/en-us/articles/10786105824531-Health-Connect-by-Android-Integration)

Recheck current Android, Health Connect, Google Play, OAuth, and Capacitor requirements before selecting versions or submitting a release.
