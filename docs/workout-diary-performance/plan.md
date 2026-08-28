# Workout Diary Performance Plan

## Purpose

Make the Workout Diary responsive regardless of retained workout history without changing personal-record meaning, ownership, celebrations, or Coach contracts.

## Current bottleneck

The Diary downloads every detailed workout, then requests personal-record history. That request recalculates workout progression from all retained workouts before the first page can render.

## Architecture

Source records remain authoritative. Persist a user-owned, rebuildable personal-record event projection alongside the existing current-record snapshots. Rebuild both projections transactionally after a source mutation and at application startup. Read history and inline badges from the projection.

Expose a dedicated server-paginated Diary endpoint that returns only the selected detailed workouts and their persisted badge events. Keep the existing workout list endpoint unchanged for compatibility. Load the form's existing ten eligible preload templates only when creating a workout.

## Delivery sequence

1. Add the event projection and rebuild it from the existing calculator.
2. Move personal-record history, dashboard badge, and Coach progression reads to the projection.
3. Add the paginated Diary and preload endpoints.
4. Convert the Vue Diary table and new-workout form to the new APIs.
5. Validate correctness, performance behavior, and existing contracts.

## Invariants

- Event rows are derived data and are never manually edited.
- A backdated edit, deletion, setting change, or routine change replaces stale projection rows in the same transaction.
- Existing event keys and response shapes remain stable.
- Routine current records stay derived from routine check-ins; routine milestone history is persisted in the event projection.
- The Diary never waits for a dynamically recalculated personal-record history request.
