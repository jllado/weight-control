# Exercise pictures

All 39 seeded training, warm-up, and stretching exercises have generated instructional illustrations; the catalogs, workout editor, and diary share thumbnails with an enlarged view and the existing description.

## Assets and review

The JPEGs in `backend/src/main/resources/exercise-images/` were generated with the imagegen skill on September 10, 2026, then encoded at quality 88 without changing their composition; all 39 together occupy about 5.4 MB. Original generation outputs remain outside the repository. The illustrations use a white background, teal clothing, full-body positions, and visible equipment; movements use multiple poses where helpful, and McGill Big Three includes all three movements.

Visual review checks movement identity, equipment, body positions, unclipped limbs, and agreement with the seeded descriptions. The wall hamstring illustration was corrected to remove a door intersecting the extended leg. Reference material was consulted for movement review, not copied as image assets:

- [ACE exercise library](https://www.acefitness.org/resources/everyone/exercise-library/).
- [Mayo Clinic basic stretches](https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/stretching/art-20546848).
- [MUSC Big Three demonstrations](https://www.musc.edu/content-hub/News/2022/12/20/The-Big-Three).

## Persistence and API

Flyway V63 maps existing seed names once to stable illustration keys; later renames preserve those keys. Custom exercises start without a picture. Images share the existing global exercise catalog and require app authentication.

Exercise responses add `imageUrl` and `hasCustomImage`; existing JSON write requests and workout responses are unchanged. `/api/workout-exercises/{id}/image` supports GET, multipart POST with a `file` part, and DELETE. POST replaces the uploaded picture; DELETE restores the built-in illustration or leaves custom exercises without a picture. Responses never expose storage paths. Image URLs have version parameters and responses use `Cache-Control: no-store`.

Uploads accept JPEG/PNG up to 10 MB and 40 megapixels; decoded pixels are oriented using EXIF, resized to a maximum 1600-pixel edge, flattened onto white, and encoded as JPEG without source metadata. The metadata-extractor dependency is used only to read orientation. Files live under the configured storage root at `exercise-images/{exerciseId}/{uuid}.jpg`, alongside but separate from progress photos. The existing persistent storage volume carries them across deployments.

Picture mutations and exercise edits use a shared row lock. Failed transactions remove new files; successful replacements, removals, and exercise deletions clean up prior uploads after commit. Cleanup failures are logged without misreporting the committed change as failed.

The editor stages file selection and removal until Save. Details are saved before the image; upload failure retains the saved exercise ID and selected file so retry updates the same exercise. Cancel discards pending picture changes.

## Coach and validation

Pictures are app-only; no Coach Actions, GPT instructions, reflection contracts, training metrics, personal records, or assessment demand changes are needed. No GPT publication is required.

Focused coverage includes image normalization/orientation, invalid uploads, rollback/cleanup, authenticated endpoints, replacement/restoration, migration/asset coverage, rename persistence, upload retry/cancel behavior, and responsive catalog/editor/history views at 390, 575, 640, 960, and 1280 pixels. Run checks through `scripts/check.sh`; the release artifact gate runs the complete suites and production builds.
