# Workout Diary Performance TODO

This checklist implements the [Workout Diary Performance Plan](plan.md). Complete groups in order and keep existing record contracts operational after every group.

## 1. Persistent record-event projection

- [x] Add a Flyway migration for `personal_record_events` with user ownership, stable event key, record values, source coordinates, context fields, and query indexes.
- [x] Add the event domain model and repository.
- [x] Rebuild event rows atomically with current-record snapshots from the existing calculator output.
- [x] Preserve routine current-record behavior and persist routine milestone history.
- [x] Use the existing startup rebuild to backfill every user after deployment.
- [ ] Add migration, ownership, rebuild, tie, improvement, correction, deletion, and settings tests.

## 2. Projection-backed reads

- [x] Serve personal-record history and exact event-key lookups from persisted events.
- [x] Serve workout and dashboard badge events from persisted events.
- [x] Serve Coach record progression from persisted events without changing its response contract.
- [x] Rebuild after every record-affecting mutation, including routine check-ins, undo, update, and deletion.
- [x] Preserve celebration and notification rules.
- [x] Add tests proving read paths do not invoke the record calculator.

## 3. Paged Workout Diary API

- [x] Add `GET /api/workouts/diary?page=&size=` with descending workouts, complete line detail, matching badge events, and page metadata.
- [x] Keep `GET /api/workouts` unchanged.
- [x] Add a preload endpoint returning at most ten complete workouts before a selected date.
- [x] Use bounded page and preload queries with existing ownership rules.
- [ ] Add service and controller tests for ordering, page boundaries, event scoping, preload date, and preload limit.

## 4. Frontend loading flow

- [x] Convert the Diary table to lazy server pagination with a default of ten rows.
- [x] Fetch badge events from the Diary response and remove the follow-up history request.
- [x] Refresh the active page after editing or deleting, and page one after creating.
- [x] Fetch preload templates only while the new-workout dialog is open and refresh them when its date changes.
- [ ] Verify mobile and desktop table behavior, long exercise lists, and loading states.

## 5. Acceptance and release

- [x] Add Playwright coverage that initial Diary loading does not call `/api/personal-records/history`.
- [ ] Verify badges, Records history, dashboard badges, Coach records, notifications, and celebrations remain correct.
- [ ] Measure the owner’s production-sized dataset before and after the change.
- [x] Run `cd backend && ./gradlew test`, `yarn lint`, `yarn build`, and focused Playwright tests.
