# Real Health Data Beta Readiness TODO

Current milestone: **1. Inventory and scope; execution has not started.**

Last updated: **2026-10-11**

This checklist implements the [readiness plan](plan.md). All readiness tasks start unchecked. Documentation delivery does not complete legal review, security verification, or the recruitment Go decision.

For each completed task, record the responsible role, date, candidate/environment, sanitised evidence, and remaining findings in the readiness evidence register. Keep confidential evidence outside Git; do not paste credentials, health records, personal identifiers, or legal advice into this checklist.

## 1. Inventory and cohort scope

Dependencies: planning may start now; application changes and external onboarding depend on the Go decision in the [recruitment TODO](../tester-recruitment/todo.md).

- [ ] Assign the project owner, privacy reviewer, technical implementer, verification reviewer, and incident primary/backup contacts.
- [ ] Record the recruitment decision and approved initial cohort scope before application changes.
- [ ] Inventory data categories, purposes, endpoints, database tables, files, derived summaries, caches, browser storage, backups, logs, email, push, and support copies.
- [ ] Map providers, subprocessors, locations, transfers, credentials, operational access, and retention responsibilities.
- [ ] Establish which account export/deletion and security controls exist, and record evidence gaps without treating uninspected areas as passed or absent.
- [ ] Record how excluded features and the owner's private Coach will remain inaccessible to participants.
- [ ] Define a synthetic verification environment isolated from owner data, files, credentials, and notification destinations.

Definition of done: an owned inventory and endpoint/store coverage matrix exist, and the intended cohort boundaries and unresolved findings are explicit.

## 2. Focused privacy review

Dependencies: milestone 1 inventory and scope.

- [ ] Review the actual data flows with a Spanish privacy professional using the existing [legal review pack](../tester-recruitment/legal-review-pack.es.md).
- [ ] Confirm controller disclosures, monitored contact channel, processor agreements, subprocessors, and transfer safeguards.
- [ ] Record the Article 6 basis and applicable Article 9 condition for each purpose, including any future AI disclosure.
- [ ] Review consent evidence, withdrawal, beta terms, data minimisation, and the separation of recruitment from application processing.
- [ ] Document whether a DPIA and DPO are required, with reasons, and complete or assign any resulting obligations.
- [ ] Approve concrete retention triggers/deadlines for all stores and narrowly justified exceptions.
- [ ] Define access, export, rectification, deletion, and other applicable rights procedures, proportionate identity verification, and response deadlines.
- [ ] Record review findings with severity, owners, required fixes, and acceptance criteria; keep public wording as draft until it matches the implemented service.

Definition of done: reviewed requirements and a remediation register are available; completed review does not imply technical fixes have passed.

## 3. Authentication and isolation remediation

Dependencies: recorded recruitment Go decision, approved scope, and milestone 2 findings.

- [ ] Implement missing invitation enforcement and server-side restrictions for excluded features.
- [ ] Verify Google token validation, session cookies, CSRF protection, and CORS/origin settings against the authentication design.
- [ ] Implement or correct documented session expiry, renewal, logout, revocation, and account-removal behaviour.
- [ ] Audit and correct ownership enforcement for direct/nested identifiers, CRUD, lists, aggregates, summaries, exports, files, and scheduled work.
- [ ] Separate beta infrastructure access, storage, secrets, backups, email recipients, and notification destinations from the owner environment.
- [ ] Verify named operational access, administrative account security, and credential rotation procedures.
- [ ] Remove unnecessary health payloads, credentials, and signed URLs from logs, alerts, and support evidence.
- [ ] Keep the private single-user Coach credential and Actions unavailable to testers.

Definition of done: scoped remediation is implemented and ready for verification; remaining findings are recorded, and no external tester has received access through this checklist.

## 4. Export, deletion, and retention implementation

Dependencies: milestones 2 and 3; reviewed retention and access requirements.

- [ ] Implement missing authenticated exports and account lifecycle operations using the inventory as the completeness checklist.
- [ ] Define and implement the distinction between individual-record deletion, consent withdrawal, account closure, and scheduled expiry.
- [ ] Revoke access and stop applicable background processing when an account closes.
- [ ] Apply deletion to database records, uploaded files, derived data, sessions, external grants, push subscriptions, and applicable provider/support copies.
- [ ] Implement recovery from partial deletion failures and expose pending work to the responsible operator.
- [ ] Document restricted legal retention, provider limitations, backup expiry, and handling of copies already shared with external AI services.
- [ ] Implement deletion reconciliation before restored data can become accessible.
- [ ] Assign the deletion schedule and evidence review to an operator; verify that public wording matches actual behaviour.

Definition of done: export, deletion, retention, and recovery procedures cover every inventoried store and are ready to demonstrate with synthetic accounts.

## 5. Incident preparation

Dependencies: milestone 1 ownership and inventory; milestone 2 legal requirements; implemented containment controls.

- [ ] Write the restricted incident runbook with contacts, escalation, awareness timestamps, evidence handling, and provider coordination.
- [ ] Document containment, credential/session revocation, restoration, and safe reopening procedures.
- [ ] Prepare a breach register and assessment of affected people, data, consequences, and risk.
- [ ] Document the authority-notification decision and applicable 72-hour requirement, including reasons for any delay.
- [ ] Document when affected people must be informed without undue delay and any applicable exceptions.
- [ ] Prepare communication templates and assign review responsibility without sending real notices during drills.
- [ ] Schedule a synthetic tabletop exercise and record corrective actions from it.

Definition of done: primary and backup contacts can execute the runbook, and notification decisions can be supported by recorded evidence.

## 6. Technical verification and operational exercises

Dependencies: milestones 3–5 implemented; synthetic fixtures and isolated environment ready.

- [ ] Select exact focused commands and coverage before running checks; use `scripts/check.sh frontend <yarn arguments>` and `scripts/check.sh backend <Gradle arguments>` sequentially.
- [ ] Verify valid invited access and rejection of uninvited, missing, invalid, expired, and revoked credentials.
- [ ] Exercise renewal, logout, copied-token replay, account removal, and the promised revocation boundaries.
- [ ] Use synthetic users A and B to test cross-user direct/nested IDs, CRUD, lists, aggregates, summaries, exports, and files; independently confirm no protected output or side effects.
- [ ] Verify restricted feature routes, owner/operator endpoints, signed file access where applicable, and background email/push recipients.
- [ ] Export and delete synthetic account A; check every applicable store, denied subsequent access, and preservation of B's data.
- [ ] Exercise partial deletion failure and recovery without falsely reporting completion.
- [ ] Obtain explicit authorisation before any backup/restore operation; verify deletion reconciliation using only synthetic data in an isolated restoration exercise.
- [ ] Complete the breach tabletop and demonstrate containment, risk assessment, notification decisions, evidence protection, and recovery.
- [ ] Check affected onboarding, consent, account lifecycle, and privacy interfaces on mobile and desktop, including keyboard navigation and long labels.
- [ ] Record commands, exit codes, stage/cleanup timings, candidate identity, results, and reviewer disposition; rerun failing checks first after fixes.

Definition of done: the scope coverage matrix has reproducible passing evidence, and blocking findings have been resolved and verified.

## 7. Real-data beta release decision

Dependencies: recorded recruitment Go decision and milestones 1–6 complete.

- [ ] Reconcile final privacy information and beta terms with the implemented scope, providers, consent, retention, deletion, and incident procedures.
- [ ] Obtain privacy-review closure and technical acceptance; document any residual non-blocking risks and their owners.
- [ ] Confirm readiness evidence, rights-channel operation, administrative access, and incident contacts are current.
- [ ] Record a dated owner Go/No-go decision for accepting real participant health data; an incomplete blocking requirement means No-go.
- [ ] Follow the [release skill](../../.agents/skills/release-plan/SKILL.md), including the complete artifact gate for authentication/authorisation or other qualifying changes.
- [ ] Verify the actual isolated beta deployment's identity, readiness, and synthetic acceptance before onboarding; do not substitute the owner's production verification.
- [ ] Invite only the approved cohort after the gate passes and record ongoing retention and incident responsibilities.

Definition of done: the reviewed, verified beta deployment is ready for the approved data and participants; public marketplace availability is not included.

## 8. Later: external Coach and marketplace readiness

Dependencies: separate approval of AI scope and delivery plan; applicable milestones above repeated for the expanded processing.

- [ ] Review [Coach domains, context, Actions and instructions](../coach/plan.md) and the [Coach TODO](../coach/todo.md); reconcile the future marketplace goal with the current private-only architecture.
- [ ] Confirm the supported public distribution route, eligibility, health-data restrictions, publisher information, and current authentication requirements.
- [ ] Design and implement individual authorisation, least privilege, revocation, and isolation for the integration; never share the owner's Action token.
- [ ] Review additional data flows, provider terms, consent/disclosures, and deletion limitations before real records reach AI services.
- [ ] Verify two-user read/write isolation, grant withdrawal, scoped context, signed-file access where applicable, and existing write-confirmation/reflection semantics.
- [ ] For existing GPT schema/instruction changes, run `scripts/check.sh frontend check:coach` and preserve its documented operation and text limits.
- [ ] Deploy supporting backend changes before separately publishing and checking the matching GPT/plugin configuration.
- [ ] Verify live synthetic conversations, confirmed writes, independent read-back, and persisted published configuration; repository checks alone do not establish live acceptance.
- [ ] Record a separate release decision and any required marketplace review outcome before announcing availability.

Definition of done: the authorised public integration has its own privacy and technical evidence, verified deployment/publication, and release decision.
