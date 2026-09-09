# Coach Action connection diagnosis — September 9, 2026

Status: the published GPT connection remains unresolved; application deployment and schema publication are not evidence that Actions execute successfully.

## Observations

- The editor initially contained malformed YAML and no parsed Actions. Repository-equivalent compact JSON restored all 30 operations, and the GPT was published privately. A fresh editor confirmed Live / Only me.
- Catalog and sleep reads still reported `ClientResponseError: Encountered exception: <class 'aiohttp.client_exceptions.ClientResponseError'>` in fresh published conversations. ChatGPT did not expose an HTTP status.
- A temporary unpublished draft containing only the catalog operation and its referenced schemas failed with the same error. The complete 30-operation schema was restored afterward; the reduced schema was never published.
- Workout assessment context also failed in the GPT preview with the same error. Its direct authenticated production equivalent returned HTTP 200 with the workout, active plan and required version timestamps.
- Direct authenticated production reads returned HTTP 200 for catalog, sleep, training context, active plan, constraints, warnings and reflection overview. Credentials were read from the ignored environment file and were not printed or changed.
- A bounded read-only server packet observation emitted method/path/status metadata only. It observed a direct catalog request at 08:21:58 UTC with HTTP 200; no matching Action request was observed during the controlled GPT retries. This narrows the investigation to the connection before application execution, but does not identify an exact upstream cause or exclude a missed request.
- The live gateway routes the Coach domain directly to the application proxy. DNS resolved to the deployed host; gateway logs showed no corresponding error. No authentication weakening, certificate change, gateway change or token rotation was justified by the evidence.

## Implemented instruction correction

New sleep entries no longer require a preliminary lookup. After confirmation, `createSleep` performs the write and the existing service rejects a duplicate date. Replacement still requires retrieving the existing record and confirming the exact replacement. Unsupported screenshot observations are omitted; no new metrics, database fields or API contracts were added. Workout assessment continues to require retrieved context, current timestamps and confirmation.

## Remaining acceptance

- Establish successful catalog, sleep and workout-context calls in a fresh published-GPT conversation.
- Save and read back a real sleep entry with exact confirmed values; do not infer uncertain timestamps or create a synthetic production entry.
- Save and read back a confirmed workout assessment using current context timestamps.
- If the connection still fails before an observable application request, escalate the GPT identifier, UTC attempt times and displayed exception to the platform provider without credentials or health payloads. Do not report the connection fixed based on backend tests, deployment or a parsed schema alone.
