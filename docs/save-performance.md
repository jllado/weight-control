# Save feedback and performance

## Behavior

Every save form shows `Saving…` and disables the submit button while its request is pending. `SaveFields` prevents edits during submission; save and cancel controls remain outside that boundary. Dialog cancellation and dismissal stay disabled until completion. Standalone mutations use `ActionButton`, which awaits the complete action and exposes a native disabled button and `aria-busy`.

Failed saves retain entered values. A weight whose photo upload fails retains its saved ID and completed uploads, so retrying updates that entry and uploads only remaining files. History and dashboard refresh failures are reported separately with retry controls. Blood-pressure saving no longer downloads the complete history to calculate values omitted from its write payload.

## Backend changes

- Batch historical meal-dish fetching in groups of 50 instead of issuing a query for every meal.
- Convert weight and blood-pressure timestamps to Europe/Madrid dates once per record calculation, reusing those dates across historical windows.
- Resolve small per-subject queries before loading large histories, reducing repeated Hibernate dirty-check scans.

Record calculations, transaction boundaries, record notifications, historical corrections, DTOs, and Flyway schemas remain unchanged. Coach Actions benefit from the shared persistence changes; their domains, context, confirmation, privacy, reflection contracts, and GPT instructions are unchanged. No private GPT publication is required.

## Local audit

The September 12, 2026 audit used a fresh production database dump restored into an isolated MariaDB 11.8 container with loopback access. Existing local volumes were preserved. Push, weekly email, and Telegram delivery were disabled before startup. Requests used a local session signing key; payloads, cookies, database dumps, raw traces, and personal screenshots remain in ignored local storage.

The audit covers each data-changing app API, including create/update/delete, reminders, check-ins, photos, settings, pauses, and notification dismissal. External push/email delivery is deliberately disabled and tested as a rejection; it is not timed as successful delivery. Google sign-in is an external authentication operation, outside the save audit.

The baseline was commit `db200b4`. Measurements use Java 21, a restored history containing 60 workouts and 1,784 meals, and sequential local HTTP requests. The final run starts from the same dump; each CRUD case creates, replaces, and deletes a synthetic entry three times. Photo cases use a synthetic 1200 × 1600 JPEG. Fixture preparation and intentionally rejected requests are excluded from success timings. These local measurements indicate direction, not a production latency guarantee.

Before changing code, workout updates took 3.3–3.6 seconds in initial profiling and about 2.1–2.3 seconds after further JVM warmup. Hibernate recorded roughly 2,000 statements per update, and Java Flight Recorder samples highlighted repeated date conversions in historical windows. The table below reports medians from the three-repeat audit, including its first iteration; JVM warmup and concurrent machine activity can affect these small samples.

All timings are milliseconds. CRUD cells show **create / update / delete** medians.

| Item | Before | After |
| --- | ---: | ---: |
| Weight | 2663 / 3159 / 2462 | 1761 / 1658 / 1528 |
| Blood pressure | 2414 / 2254 / 2499 | 1532 / 1769 / 1695 |
| Mood | 2707 / 1850 / 1800 | 1567 / 1548 / 1309 |
| Sleep | 2057 / 1832 / 1829 | 1376 / 1345 / 1377 |
| Lipid panel | 1831 / 1790 / 1752 | 1534 / 1704 / 1415 |
| Sickness | 4 / 3 / 4 | 10 / 10 / 10 |
| Back check-in | 6 / 5 / 4 | 11 / 12 / 8 |
| Meal | 1830 / 1842 / 1775 | 1490 / 1447 / 1298 |
| Fasting period | 5 / 4 / 4 | 11 / 12 / 9 |
| Food | 6 / 5 / 4 | 14 / 13 / 11 |
| Saved dish | 7 / 7 / 5 | 13 / 15 / 10 |
| Habit | 7 / 1730 / 1719 | 9 / 1196 / 1223 |
| Routine | 7 / 7 / 5 | 12 / 9 / 8 |
| Medication | 6 / 5 / 4 | 8 / 5 / 8 |
| Health constraint | 5 / 3 / 3 | 7 / 6 / 7 |
| Exercise | 5 / 5 / 4 | 8 / 8 / 8 |
| Workout | 1808 / 1765 / 1719 | 1484 / 1471 / 1364 |
| Stretching set | 9 / 9 / 7 | 5 / 6 / 4 |

| Other action | Before median | After median |
| --- | ---: | ---: |
| workouts realistic update | 2816 | 1668 |
| workouts refresh | 42 | 27 |
| habit complete | 1799 | 1329 |
| habit undo | 1761 | 1325 |
| routine reminder time | 8 | 9 |
| routine snooze | 6 | 11 |
| routine checkin | 36 | 68 |
| routine undo | 34 | 62 |
| medication reminder time | 5 | 9 |
| medication log dose | 5 | 10 |
| /profile save | 1770 | 1375 |
| /coaching-plan save | 6 | 8 |
| /push/reminder-settings save | 4 | 7 |
| dashboard retreat | 26 | 41 |
| dashboard advance | 23 | 37 |
| dashboard refresh | 1772 | 1544 |
| decision create | 16 | 17 |
| decision reason | 6 | 8 |
| notifications dismiss all | 5 | 9 |
| exercise image upload | 75 | 71 |
| exercise image remove | 6 | 4 |
| weight photo upload front | 7 | 7 |
| weight photo remove front | 5 | 4 |
| weight photo upload right | 10 | 6 |
| weight photo remove right | 5 | 4 |
| weight photo upload left | 6 | 6 |
| weight photo remove left | 6 | 4 |
| pause start | 9 | 7 |
| pause cancel | 8 | 6 |
| pause checkin | 8 | 7 |
| pause repeat | 7 | 5 |
| pause finish | 8 | 6 |
| dashboard complete | 1746 | 976 |
| dashboard reopen | 1815 | 967 |
| personal record settings | — | 1007 |
| medication snooze | — | 7 |
| medication take | — | 8 |
| notification dismiss | — | 6 |

A dash means that operation was checked only on the final build; no speedup is claimed for it. Push subscription changes, push test delivery, and weekly-summary sending each returned HTTP 400 with delivery disabled, as intended.

The final API audit recorded 276 successful requests across 92 named operations, plus 12 expected disabled-delivery rejections.

## Validation

Focused browser checks cover delayed saves, draft retention after failure, retry, duplicate submission, locked fields, disabled cancellation, and workout deletion failure. Health forms are inspected at 390 and 1280px; workout controls are checked at 390, 575, 640, 960, and 1280px. A staged photo failure verifies exactly one weight creation, one completed front upload, and a retry of the failed right upload.

Focused backend checks cover personal-record calculations, persistence, mutation services/controllers, and a MariaDB regression proving 51 meals and their dishes load in at most four queries. Required assertions and native caching are preserved. Final release validation runs the repository's complete artifact gate; validation, artifact building, and deployment results are recorded separately under `tmp/checks/`.
