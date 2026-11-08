# Walking illustration prompts and review

Generated on November 8, 2026 with the built-in image_gen tool in generate mode, one call per asset; no CLI/API fallback. Existing [walking](../../backend/src/main/resources/exercise-images/walking.jpg) and [exercise-bike](../../backend/src/main/resources/exercise-images/exercise-bike.jpg) illustrations were inspected as style references, not edit targets.

Both generated PNGs are 1254 × 1254 pixels. Finals preserve the original composition and dimensions, encoded as RGB JPEG at quality 88; original outputs remain outside the repository.

## Indoor walking

Final: [indoor-walking.jpg](../../backend/src/main/resources/exercise-images/indoor-walking.jpg).

Generated source: `/home/jllado/.codex/generated_images/01a1bac8-7cb7-7c71-bfb4-32f51bdf6e6e/exec-3bb1172a-ba70-4789-afdb-3839eef36073.png`.

Exact prompt:

```text
Use case: scientific-educational
Asset type: square exercise-catalog illustration, 1024 x 1024 composition.
Primary request: create a new illustration of indoor walking on a treadmill.
Input images: the previously viewed walking.jpg and exercise-bike.jpg are style references only, not edit targets.
Scene/backdrop: plain white background with only a subtle ground shadow, no room or scenery.
Subject: one adult man wearing a teal short-sleeved athletic shirt, navy shorts and gray trainers, walking at a normal comfortable pace on a level treadmill. Upright relaxed posture, natural alternating arm swing, ordinary short walking stride; front heel contacting the belt and rear forefoot still touching it, so there is no running flight phase.
Style/medium: realistic hand-painted instructional rendering matching the reference exercise illustrations, natural anatomy, restrained soft shading, crisp readable details.
Composition/framing: square canvas, full body in a three-quarter side view, full treadmill including belt, deck, front console, handrails and supports visible with generous white margin. Keep legs and feet clearly visible on the belt; arms swing freely beside the body rather than holding rails. Equipment must be correctly aligned and must not intersect the person's body.
Color palette: teal shirt, navy shorts, gray trainers, dark gray and silver treadmill, white background.
Constraints: exactly one person, two anatomically natural arms and two legs, all limbs and shoes intact and unclipped; normal walking rather than running; no text, labels, numbers, logos, watermarks, arrows or decorative symbols.
```

Review: the saved illustration shows an upright person walking on a level treadmill, with a forward heel and rear forefoot contacting the belt and relaxed opposing arm swing. The complete belt, deck, console, rails and supports are visible without intersecting the person. Both arms, hands, legs and shoes have natural proportions and remain unclipped. Teal shirt, navy shorts, gray trainers, white background and realistic painted shading match the catalog; there are no text, logos or arrows.

## Outdoor walking

Final: [outdoor-walking.jpg](../../backend/src/main/resources/exercise-images/outdoor-walking.jpg).

Generated source: `/home/jllado/.codex/generated_images/01a1bac8-7cb7-7c71-bfb4-32f51bdf6e6e/exec-b80de9be-5f7e-4be1-aba2-fd95410fdd86.png`.

Exact prompt:

```text
Use case: scientific-educational
Asset type: square exercise-catalog illustration, 1024 x 1024 composition.
Primary request: create a new illustration of outdoor walking along a small path.
Input images: the previously viewed walking.jpg and exercise-bike.jpg are style references only, not edit targets.
Scene/backdrop: a small pale earth walking path directly beneath the person, with a few subtle green grass tufts along its edges; path and grass fade into otherwise plain white surrounding background. No horizon, buildings, trees or expansive landscape.
Subject: one adult man wearing a teal short-sleeved athletic shirt, navy shorts and gray trainers, walking at a normal comfortable pace. Upright relaxed posture, natural alternating arm swing, ordinary walking stride; front heel contacting the path and rear forefoot still touching it, so there is no running flight phase.
Style/medium: realistic hand-painted instructional rendering matching the reference exercise illustrations, natural anatomy, restrained soft shading, crisp readable details.
Composition/framing: square canvas, full body in a three-quarter side view, all limbs and shoes clearly visible, centered with white margin above the head and around the path. A simple clear walking pose suitable for a thumbnail.
Color palette: teal shirt, navy shorts, gray trainers, muted beige path, subtle green grass, white background.
Constraints: exactly one person, two anatomically natural arms and two legs, all limbs and shoes intact and unclipped; normal walking rather than running. No treadmill, indoor equipment, text, labels, numbers, logos, watermarks, arrows or decorative symbols.
```

Review: the saved illustration shows normal outdoor walking, with the front heel and rear forefoot on a small earth path, upright posture and natural opposing arm swing. Subtle grass identifies the outdoor setting while the surrounding background stays white. Both arms, hands, legs and shoes have natural proportions and remain unclipped; no treadmill or other equipment appears. Clothing and realistic painted shading match the catalog; there are no text, logos or arrows.

## Verification boundary

Both generated sources and final JPEGs were visually reviewed for movement identity, anatomy, equipment/path placement and clipping, and both saved files decoded successfully. Authenticated endpoint and catalog verification belongs to the application delivery checks.

