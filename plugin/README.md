# Weight Control Coach in ChatGPT

Open the [Weight Control Coach plugin](https://chatgpt.com/plugins/plugin_asdk_app_sites_bab89a399cac8191b02a471adf43231e) in the owner's personal ChatGPT account. Install it and choose **Try in chat**. In an ordinary chat, select **@Weight Control Coach** and say **Start my coaching session**, ask a progress question or describe a meal to record. The Coach shows the meal and asks for confirmation before saving it.

## Connection and privacy

The owner-only ChatGPT Site runs a Worker that exposes the 30 operations from the [Coach OpenAPI contract](../docs/coach/coach-action.openapi.yaml). It submits requests over HTTPS to the existing Weight Control Coach API. Weight Control authenticates the Action credential and stores the records. The selected context and tool results pass through the Site to ChatGPT. For a progress-photo comparison, the Worker fetches the requested views using temporary signed URLs and returns labeled images to ChatGPT.

Keep Site access limited to the owner. Set the server-side secrets `CHATGPT_ACTION_TOKEN` to the existing Action credential and `COACH_OWNER_EMAIL` to the owner's verified Sites identity. Use the hosting secret controls and redeploy after an update. Keep both values out of source, the plugin archive, prompts and build output.

## Updating the integration

- Update the shared OpenAPI contract when an operation changes. The Worker derives its tools from that contract. Keep operation IDs aligned with the skill and preserve the backend's validation, confirmation and readback semantics.
- The directly installable [Chat skill](skills/weight-control-coach/SKILL.md) includes Chat-specific instructions followed by the canonical rules from the [GPT configuration](../docs/coach/coach-gpt.md#instructions). When a coaching rule changes, update the canonical rules in both places and check their agreement.
- The [Sites project](site/.openai/hosting.json) identifies the private deployment. The plugin's `.app.json` maps its installed skill to that Site's registered app. Keep the package name, app ID and manifest intact when updating the existing plugin.

From the repository root, install the locked Worker dependencies and run the focused checks and build sequentially.

```sh
npm ci --prefix plugin/site --ignore-scripts
scripts/check.sh frontend check:coach
scripts/check.sh frontend test:coach-plugin
scripts/check.sh frontend --cwd plugin/site build
```

For deployment, copy `worker.mjs`, `build.mjs`, `package.json` and `package-lock.json` from `site/` into the existing Site source checkout. Copy `docs/coach/coach-action.openapi.yaml` there as `openapi.yaml` and `plugin/skills/weight-control-coach/SKILL.md` as `skill.md`. In that checkout, run `npm ci --ignore-scripts`, then `node build.mjs --schema openapi.yaml --skill skill.md`. Publish the resulting `dist/server/index.js` with owner-only access and verify tool discovery.

To update the packaged skill, increment the version in `.codex-plugin/plugin.json`, then run the following from `plugin/`. Choose **More actions → Upload new version** on the existing private plugin and upload the archive.

```sh
zip /tmp/weight-control-coach-plugin.zip .app.json .codex-plugin/plugin.json skills/weight-control-coach/SKILL.md
```

## Acceptance before switching the app

1. Verify owner-only access, tool discovery, and rejection of anonymous and other-user requests. Compare the 30 tool names and parameter shapes with the Coach contract. Include backend validation errors, stale edits, cancellation and supported idempotent retries.
2. In ordinary Chat, use **Start my coaching session** and check that the Coach asks what to work on without loading records. Ask a dated progress question and check that it requests only the relevant dates and domains.
3. Exercise a synthetic multi-food meal through calculation, proposal, confirmation, write and readback. Compare the meal and nutrient values with the approved proposal. Cancel a second proposal and verify that no record appears. Keep synthetic writes in an isolated fixture.
4. Exercise workout edits, scale and sleep screenshots, selected-photo transfer and both reflection workflows. Verify whole-plan replacement and the required renewed consent for a materially changed proposal. The [Coach acceptance scenarios](../docs/coach/coach-gpt.md#cutover-and-acceptance) describe the expected behavior.
5. Verify the installed plugin and an owner-authorized production read in a fresh chat. Record live outcomes separately from synthetic tests. After these checks pass, record the previous `VITE_CHATGPT_COACH_URL` and set it to the verified plugin landing page before building the frontend. Test **Open Coach → Try in chat** at phone and desktop widths. To roll back the launcher, restore the recorded private GPT URL and rebuild and deploy the frontend.
