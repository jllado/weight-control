# Real Health Data Beta Readiness Plan

Status: **Planned; privacy review and technical acceptance are pending.**

Last updated: **2026-10-11**

## Purpose and boundaries

Before accepting real health records from external participants, obtain a focused privacy review and verify authentication, user isolation, deletion, and incident handling.

Track delivery in the [readiness TODO](todo.md). This plan prepares the future application beta; the [recruitment plan](../tester-recruitment/plan.md) and its go/no-go decision remain separate. Writing these documents does not authorise invitations, production changes, or health-data collection.

Use synthetic records in an isolated environment throughout readiness work. Preserve the owner's existing application, records, credentials, and private Coach. Do not copy owner data into a tester environment.

The initial scope proposed by the recruitment plan is Google sign-in, daily overview, weight without photos, meals, workouts, sleep, habits, routines, record editing/deletion, and PWA installation. Confirm the cohort scope before implementation. Progress photos, health events, medication, blood pressure, ChatGPT, imports, and backup administration remain excluded from that initial cohort unless a later decision explicitly expands it.

Review excluded features too wherever their routes, files, background jobs, or credentials could remain accessible. An omitted navigation link does not enforce an exclusion.

## Current implementation and evidence limits

| Area | Observed baseline | Readiness evidence still needed |
| --- | --- | --- |
| Storage | Vue services call the Spring Boot API; MariaDB and server files hold application records. | Complete inventory of stores, derived data, copies, access paths, and retention. |
| Browser authentication | Google authentication and a JWT session cookie exist; session parsing and renewal are implemented. | Verified invitation enforcement, token validation, expiry, logout, revocation, and ownership checks. |
| Coach authentication | The private Action bearer token resolves to one configured user. | Keep it inaccessible to testers; external access requires individual authorisation and separate acceptance. |
| Existing checks | Authentication filter/service tests and login browser coverage exist. | A dated coverage matrix and results for all approved beta data paths; existing tests do not prove complete isolation. |
| Account lifecycle | Individual record deletion exists in several domains. | Complete account export/deletion has not been established by this review; inventory it before designing missing work. |
| Legal preparation | Recruitment legal drafts and a review pack exist. | Professional findings, approved application disclosures, implemented controls, and a recorded launch decision. |

Use the [project guide](../project-guide.md) for current paths and commands, the [legal review pack](../tester-recruitment/legal-review-pack.es.md) for review inputs, and the [legal drafts](../tester-recruitment/legal.es.md) as unapproved working material. Reconcile differences with the actual implementation rather than publishing draft promises about disabled features or deletion.

## 1. Inventory and focused privacy review

The project owner coordinates a Spanish privacy professional and the technical implementer; record named owners privately before review starts.

- Map collection, use, disclosure, storage, export, and deletion for each enabled data category, including identifiers, measurements, photos where applicable, derived summaries, and consent evidence.
- Include database tables, file storage, logs, caches, browser storage, backups, email, push subscriptions, monitoring, support exports, and external providers.
- Record each purpose, Article 6 basis, applicable Article 9 condition, retention trigger and deadline, recipient, processing location, and international-transfer safeguard.
- Review controller identity/contact details, processor agreements and subprocessors, beta terms, consent language, withdrawal, access/export/deletion requests, and how users receive privacy information.
- Determine whether a DPIA or DPO is required; retain the reasoning and complete any required assessment before the affected processing begins.
- Confirm the minimum enabled feature set, invitation criteria, cohort size, and whether any processing must be excluded pending remediation.
- Assign a monitored rights channel and a procedure for proportionate identity verification, the normal one-month response deadline, and lawful extensions or exceptions.

Deliver a findings register with severity, owner, remediation, acceptance evidence, and reviewer disposition. Keep contracts, legal advice, credentials, personal identifiers, and participant records in restricted storage outside Git. Repository status notes may record dates, roles, and sanitised results.

## 2. Authentication and user isolation

- Inventory every public, session-authenticated, administrator, scheduled-job, signed-file, and Coach route; explicitly identify the routes available to participants.
- Verify Google token signature, issuer, audience and expiry checks, invitation enforcement, secure session-cookie settings, CSRF protection, and CORS/origin policy for the actual authentication design.
- Define and verify session expiry, renewal, logout, revocation, account removal, and compromised-credential handling; do not assume deleting a browser cookie invalidates a copied token.
- Derive the acting user from trusted authentication and enforce ownership in every relevant query and mutation, including nested identifiers, lists, aggregates, exports, and file downloads.
- Isolate beta databases, files, secrets, backup access, email recipients, and notification destinations from the owner's production environment.
- Restrict operational access to named authorised people, verify administrative account security, and prevent tokens or health payloads from appearing in logs and alerts.

Use at least two synthetic participant accounts plus an owner/operator account. Verify positive access as well as rejection; rejected requests must not reveal protected content or cause writes, notifications, or other side effects.

## 3. Export, deletion, and retention

Define the account lifecycle across every inventoried store before choosing implementation details or promising deadlines publicly.

- Provide an authenticated, usable export of the participant's applicable records and files, with no other person's data.
- Define record deletion, account closure, consent withdrawal, and retention expiry separately; identify any narrowly justified legal retention and restrict its use.
- On account closure, block further access and scheduled processing, revoke applicable sessions and external grants, and remove live records, files, derived data, and notification subscriptions according to the reviewed schedule.
- Include recruitment copies, support exports, email and provider-held copies where applicable; record provider responsibilities and deletion limitations.
- Document retry and recovery for real partial failures across database, file, and external operations; completion evidence must identify anything still pending.
- Set backup retention and access restrictions, and a deletion-reconciliation procedure that runs before restored data becomes available again.
- Explain that deleting Harmonovo records does not automatically delete copies already shared with ChatGPT or independently retained by a user; review provider-specific rights and retention separately.

The privacy professional reviews the schedule, while the technical owner demonstrates that each deadline and promise can be met. Do not claim immediate removal from every backup unless that behaviour is implemented and verified.

## 4. Incident handling

Maintain a restricted operational runbook with a primary owner, backup contact, provider contacts, and access to the tools required for containment.

1. Detect and record the incident, including the time of awareness and affected systems.
2. Contain access, revoke compromised credentials, and preserve relevant evidence without copying unnecessary health data into tickets or chat.
3. Assess affected people, data categories, confidentiality, integrity, availability, likely consequences, and remaining exposure.
4. Document every personal-data breach and the notification decision; notify the competent authority without undue delay and, where feasible, within 72 hours of awareness unless risk to people's rights and freedoms is unlikely.
5. Notify affected people without undue delay when high risk requires it, subject to applicable exceptions; record reasons and advice supporting the decision.
6. Recover safely, verify isolation and deletion reconciliation, communicate appropriately, and assign preventive follow-up work.

Run a tabletop exercise using a simulated credential leak and cross-user disclosure. Do not send real regulatory notifications, contact participants, disable production credentials, or restore production data as part of a drill. Provisioning, backup, restore, and destructive production operations require separate explicit authorisation.

## 5. Private Coach and future marketplace release

Review the [Coach architecture](../coach/plan.md), [Coach TODO](../coach/todo.md), and [publication guidance](../coach/coach-gpt.md). The current private integration remains private; the intended marketplace migration is a separate future workstream, not a completed capability.

- Review Coach domains, scoped context, read/write Actions, GPT instructions, progress-photo access, consent, and transfers before permitting participant data to reach AI services.
- Require individual authorisation, least-privilege access, revocation, and cross-user acceptance for any external integration; never distribute the owner's bearer credential.
- Review current marketplace eligibility, health-data restrictions, publisher disclosures, and supported authentication when that work starts.
- Preserve existing reflection semantics and write-confirmation rules; assess any proposed changes explicitly rather than assuming migration preserves them.
- Deploy supporting application changes before publishing matching GPT/plugin configuration; verify the published configuration and live synthetic read/write/read-back journeys separately from repository checks.
- For changes to the existing GPT, retain 30 Actions, descriptions of at most 300 characters, and instructions of at most 8,000 characters; reassess limits separately for a new platform contract.

## Acceptance and release gate

| Scenario | Required result |
| --- | --- |
| Missing, invalid, expired, or revoked credentials; uninvited account | Protected operations fail without protected data or mutation; valid invited access still works. |
| Session renewal, logout, copied token, account removal | Behaviour matches the documented expiry/revocation design; access cannot outlive the promised boundary. |
| User A supplies user B's direct or nested identifiers | Reads, edits, deletes, exports, files, lists, and aggregates remain scoped; B's data and side effects remain unchanged. |
| Disabled beta features and operator endpoints | Direct requests and background paths enforce the restriction independently of the UI. |
| Export and complete deletion of synthetic user A | Export contains only A; access is revoked and applicable live data disappears while B remains intact. |
| Interrupted deletion and isolated backup restoration | Pending work is recoverable; deleted data cannot reappear in a reopened environment. |
| Simulated breach | Owners can contain, assess, document, make notification decisions, and recover within the required process. |
| Future AI access | Individual grants, withdrawal, data minimisation, write confirmation, and cross-user isolation work in the published integration. |

Maintain a coverage matrix connecting each enabled endpoint/store to a check and evidence. Record candidate commit, environment, synthetic fixtures, exact commands, exit codes, stage timings including cleanup, reviewer, findings, and disposition; mark exclusions explicitly.

Implementation validation must use the locked commands in the project guide and run sequentially. Authentication/authorisation, shared contracts, infrastructure or deployment changes require the complete release-artifact gate under the [release skill](../../.agents/skills/release-plan/SKILL.md). Mobile/desktop and keyboard checks apply to affected UI; Coach configuration changes also require `scripts/check.sh frontend check:coach` before publication.

Accept real participant health records only after the recruitment Go decision, approved scope and privacy review, implemented operational procedures, passed technical evidence, resolved blocking findings, and a dated owner release decision. Verify the actual target beta deployment before onboarding. A passed build, published policy, rented address, or completed documentation task alone is not evidence that this gate has passed.

## References

- [GDPR: Articles 5, 6, 9, 12–22, 25, 28, 32–35 and 37](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32016R0679)
- [AEPD: information duties](https://www.aepd.es/derechos-y-deberes/conoce-tus-derechos/derecho-de-informacion)
- [AEPD: personal-data breach notification](https://www.aepd.es/derechos-y-deberes/cumple-tus-deberes/medidas-de-cumplimiento/brechas-de-datos-personales-notificacion)
- [EDPB: practical guidance for small businesses](https://www.edpb.europa.eu/sme_en)
- [OpenAI: GPT migration](https://learn.chatgpt.com/docs/migrate-custom-gpts) and [plugin guidelines](https://developers.openai.com/plugins/plugin-guidelines)

Recheck external requirements during professional review and before public AI distribution; these references do not certify compliance.
