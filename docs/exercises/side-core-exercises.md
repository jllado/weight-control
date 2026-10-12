# Side core exercises

Three production-only catalog additions use the existing exercise creation and custom-picture upload APIs; they do not change workout plans, recorded sessions, seeded exercises, or Coach contracts.

## Catalog data

All entries use category `TRAINING`, primary muscle group `CORE`, and no estimated-time override (`estimatedSecondsPerSet: null`). Each general entry covers both sides; separate left/right entries are not created.

| Name | Tracking mode | Secondary muscle group | Picture |
| --- | --- | --- | --- |
| Side plank | `SECONDS` | `SHOULDERS` | [Illustration](images/side-plank.png) |
| Side plank hip dips | `REPS` | `SHOULDERS` | [Illustration](images/side-plank-hip-dips.png) |
| Side jackknife crunches | `REPS` | None | [Illustration](images/side-jackknife-crunches.png) |

**Side plank:** Support yourself on one forearm with the elbow below the shoulder and legs straight. Lift your hips and hold your shoulders, hips, and feet in one line. Repeat on the other side.

**Side plank hip dips:** Start in a forearm side plank with legs straight. Lower your hips toward the floor, then raise them back into alignment with controlled movement. Repeat on the other side.

**Side jackknife crunches:** Lie on your side, slightly rolled backward, with your lower arm supporting you and your upper hand behind your head. Bend your knees toward your upper elbow, then extend both legs without resting them on the floor. Repeat on the other side.

## Generation and visual review

Generated on October 12, 2026 with the built-in imagegen tool after inspecting the bundled `plank.jpg`, `mcgill-big-three.jpg`, and `abdominal-crunch.jpg` as visual style references: realistic hand-painted rendering, white background, teal shirt, navy shorts, gray trainers, subtle floor shadow, full-body framing, and no text, labels, arrows, or logos. The selected PNGs remain unchanged; production normalizes uploads through the existing image endpoint.

Reviewed all three illustrations against those references and the extracted video movements: side plank has a raised pelvis, straight legs, stacked feet, and a supporting forearm; hip dips show lowered and raised hips with unchanged forearm and stacked-foot supports; side jackknife crunches show hovering straight legs and a bent-knee tuck with the lower hip and hand grounded. All figures have complete, unclipped limbs and match their catalog descriptions.

### Generation prompts

**side-plank:** Use case: scientific-educational. Asset type: square exercise catalog illustration. Match the established realistic hand-painted fitness illustration style: adult athletic man with short brown hair, teal short-sleeve T-shirt, navy shorts, gray trainers, clean white background, subtle floor shadow, full body entirely inside frame with generous margins. Detailed correct anatomy, two arms and two legs in every pose. No text, labels, numbers, arrows, logos, watermarks or colored panel backgrounds. Clear three-quarter side view, no equipment. Create one pose of a FOREARM SIDE PLANK: head toward left of image, feet toward right. His left elbow and forearm support him on the floor directly below the shoulder, forearm pointing forward. Left outer foot rests on the floor with right foot stacked on it. Both knees stay straight. Pelvis raised so shoulders, hips and ankles form a straight line; torso and pelvis remain side-on, chest faces the viewer. Right hand rests on right hip. The lower arm must be anatomically intact and clearly bent at the elbow, head and neck aligned with spine. This is an elevated side plank, not side lying.

**side-plank-hip-dips:** Use case: scientific-educational. Asset type: square exercise catalog illustration. Match the established realistic hand-painted fitness illustration style: adult athletic man with short brown hair, teal short-sleeve T-shirt, navy shorts, gray trainers, clean white background, subtle floor shadow, full body entirely inside frame with generous margins. Detailed correct anatomy, two arms and two legs in every pose. No text, labels, numbers, arrows, logos, watermarks or colored panel backgrounds. Clear three-quarter side view, no equipment. Create TWO fully separate full-body poses stacked vertically, showing a FOREARM SIDE PLANK HIP DIP. Same man, same camera, same left-side support in both poses, head left and feet right. Top pose: left elbow and left forearm on floor below shoulder, forearm pointing forward, legs straight and feet stacked, right hand on hip, hips lowered close to the floor without sitting or bending the knees. Bottom pose: identical elbow and stacked-foot support, hips raised back to a straight shoulder-hip-ankle line. Torso stays side-on and chest faces viewer in both poses. The action is lowering and raising the pelvis, not rotating the torso, not a knee-supported plank. Allow ample space between both full bodies, all limbs clearly visible.

**side-jackknife-crunches:** Use case: scientific-educational. Asset type: square exercise catalog illustration. Match the established realistic hand-painted fitness illustration style: adult athletic man with short brown hair, teal short-sleeve T-shirt, navy shorts, gray trainers, clean white background, subtle floor shadow, full body entirely inside frame with generous margins. Detailed correct anatomy, two arms and two legs in every pose. No text, labels, numbers, arrows, logos, watermarks or colored panel backgrounds. Clear three-quarter side view, no equipment. Create TWO fully separate full-body poses stacked vertically, showing a SIDE JACKKNIFE CRUNCH WITH BENT KNEES matching this exact movement. He lies on his left hip, rolled slightly backward, head left and feet right, chest angled toward viewer. His left arm extends sideways and diagonally forward along the floor with palm grounded to assist balance; it is not a side plank and left hip stays on floor. His right hand touches the back of his head lightly, right elbow points upward toward knees. Top pose extended position: both legs together and straight, hovering just above floor, trunk slightly lifted. Bottom pose contracted position: both knees bend together toward his torso while upper shoulder and right elbow curl toward knees, the lower arm stays extended with palm planted, both feet hover. Illustrate the bent-knee tuck, NOT straight-leg oblique V-ups, bicycle crunches, side planks or knees twisting across the body. The same lower hip supports both poses. Show all fingers, elbows, knees and feet clearly, without overlap between the two separate figures.


## Acceptance

Acceptance completed on October 12, 2026:

- Independent read-only review approved the catalog data, descriptions, illustration anatomy, support points, and style.
- All three catalog creates and immediate picture uploads returned HTTP 200; subsequent reads confirmed the exact descriptions, tracking modes, category, muscle assignments, custom pictures, and null estimated-time overrides.
- All three authenticated image endpoints returned HTTP 200 and decodable 1254 × 1254 JPEGs; the catalog loaded the matching thumbnails.
- Visual checks at 390px and 1280px confirmed readable labels and descriptions, aligned action controls, working pictures, and no horizontal page overflow.
- The complete workout plan and all 79 pre-existing catalog entries matched their pre-write snapshots; only the three intended exercises were added.

Source changes retain the original 1254 × 1254 PNGs and this review record only; no application build, deployment, or GPT publication is required.
