# Tester Recruitment TODO

Current milestone: **2. Complete legal review and beta protocol**

Last updated: **2026-08-30**

This checklist implements the [tester recruitment and beta plan](plan.md). Complete and validate one milestone before starting the next. Update `Current milestone` and `Last updated` whenever work moves to another milestone.

## 0. Planning pack

- [x] Document the end-to-end recruitment and beta plan.
- [x] Create the gated implementation checklist.
- [x] Draft the Spanish landing, form, outreach, and participant copy.
- [x] Draft the Spanish privacy, consent, legal, and beta terms for professional review.

Definition of done: the documentation pack is internally linked, uses shared placeholders, and has one progress checklist.

## 1. Select the product name and domain

Dependencies: milestone 0.

- [x] Write a one-paragraph naming brief centered on personal tracking, patterns, and progress without medical claims.
- [x] Produce at least five distinctive name candidates.
- [x] Exclude `FitControl` and `HealthControl` unless formal clearance resolves their existing conflicts.
- [x] Search Google, app stores, social networks, GitHub, EUIPO, and the Spanish Patent and Trademark Office.
- [x] Check `.es` and `.com` availability and common social usernames.
- [x] Select `Harmonovo` and `harmonovo.com`.
- [x] Purchase `harmonovo.com` and enable automatic renewal.
- [x] Create `hello@harmonovo.com` and configure SPF, DKIM, and DMARC.
- [x] Replace the shared placeholders in the planning, copy, and legal documents.

Definition of done: the selected name has documented screening results, the domain is controlled, the contact mailbox works, and no unresolved placeholder blocks implementation.

Validation:

```bash
rg -n '\{\{(PRODUCT_NAME|PRODUCT_DOMAIN|CONTACT_EMAIL)\}\}' docs/tester-recruitment
```

Expected result: no unresolved name, domain, or contact placeholders outside examples explaining the placeholder system.

## 2. Complete legal review and beta protocol

Dependencies: milestone 1.

Working documents: [legal review pack](legal-review-pack.es.md) and [beta protocol](beta-protocol.es.md). They prepare this milestone but do not replace the external assessment or professional review.

- [ ] Fill in the controller identity, address, processors, hosting location, transfer details, and exact retention dates.
- [ ] Run the AEPD Facilita Emprende assessment and retain its outputs internally.
- [ ] Complete the processing record, processor list, risk assessment, and breach procedure.
- [ ] Review the Tally data-processing agreement and subprocessor list.
- [ ] Send `legal.es.md` and the final form fields to a Spanish privacy professional.
- [ ] Apply the reviewed wording to the recruitment notice, explicit consent, legal notice, privacy policy, beta terms, and recording consent.
- [ ] Fix the cohort dates, completion conditions, support channel, and `{{INCENTIVE}}`.
- [ ] Decide who can access applicant data and record that access policy.
- [ ] Define the manual deletion calendar for applications, participant records, recordings, and beta accounts.

Definition of done: reviewed legal text, consent records, retention, support, incident handling, and participant conditions are ready before any public data collection.

## 3. Make the application beta-ready

Dependencies: milestone 2.

- [ ] Implement the public-facing product rename without renaming technical packages, databases, Docker resources, scripts, or server directories.
- [ ] Move the authenticated dashboard to `/app` and add public route metadata and authentication guards.
- [ ] Replace the hard-coded owner email with `APP_AUTH_ALLOWED_EMAILS`.
- [ ] Add the owner and selected test accounts only; keep public registration disabled.
- [ ] Replace owner-specific new-user defaults with explicit first-login onboarding.
- [ ] Limit the first cohort to the features listed in the plan.
- [ ] Hide owner-only, progress-photo, health-event, blood-pressure, backup, and ChatGPT features from beta users.
- [ ] Add machine-readable account export.
- [ ] Add complete account and file deletion with session invalidation.
- [ ] Add two-user authorization tests for every enabled beta domain.
- [ ] Enable CSRF protection for cookie-authenticated writes.
- [ ] Add compatible CSP, HSTS, `Referrer-Policy`, and `Permissions-Policy` headers.
- [ ] Audit application and proxy logs for secrets, identifiers, health payloads, and file URLs.
- [ ] Add tests for onboarding, allowlist denial, export, deletion, and preserved owner data.
- [ ] Run the backend, frontend, and end-to-end checks.

Definition of done: invited users can onboard and complete the beta tasks without owner defaults or disabled features, while cross-user access, export, deletion, and security tests pass.

Validation:

```bash
cd backend && ./gradlew test
yarn lint
yarn build
yarn test:e2e
```

## 4. Build the landing page and Tally form

Dependencies: milestones 1–3 for final claims; the page may be drafted earlier as an unpublished waitlist.

- [ ] Add public landing, privacy, legal, beta-terms, and login routes.
- [ ] Implement the Spanish content from `copy.es.md` with one primary application action.
- [ ] Create a fictional demo account and capture three responsive screenshots without personal data.
- [ ] Add page title, description, canonical URL, Open Graph metadata, favicon, and share image.
- [ ] Add visible privacy, legal, and beta-terms links.
- [ ] Build the Tally form using only the documented questions.
- [ ] Add required recruitment consent and separate optional waitlist consent.
- [ ] Add a hidden `source` field and preserve approved query values.
- [ ] Link to Tally instead of embedding the form.
- [ ] Configure Tally notifications without copying full responses into email.
- [ ] Do not add analytics, pixels, session recording, or non-essential cookies.
- [ ] Test keyboard navigation, mobile layouts, CTA links, legal links, and form completion.

Definition of done: the page accurately describes the implemented beta, the form stores the minimum reviewed data, and the complete funnel works on mobile and desktop.

Validation:

```bash
yarn lint
yarn build
yarn test:e2e
```

## 5. Prepare the isolated beta deployment

Dependencies: milestones 2–4.

- [ ] Create separate beta Compose project, database volume, backend file volume, secrets, and backup destination.
- [ ] Do not copy the owner's production database, imports, or files.
- [ ] Configure the root redirect, `www` landing host, and `app` beta host.
- [ ] Create and configure a distinct Google production OAuth client for the beta domain.
- [ ] Add only selected tester emails to the production allowlist.
- [ ] Configure secure cookies, allowed origins, TLS, security headers, and email contact values.
- [ ] Create encrypted backup and restoration procedures for the beta environment.
- [ ] Document account deletion behavior for live data and backups.
- [ ] Perform a clean deployment rehearsal without using real tester data.

Definition of done: the isolated environment is reproducible, contains no owner data, uses distinct credentials and storage, and passes restoration and deletion rehearsals.

## 6. Complete launch validation

Dependencies: milestones 1–5.

- [ ] Verify the public name, domains, email, OAuth screen, and legal links are consistent.
- [ ] Verify no prohibited or unsupported medical, privacy, security, or availability claim is published.
- [ ] Test the full anonymous landing-to-form path on Android, iPhone, and desktop widths.
- [ ] Test invited and non-invited Google accounts.
- [ ] Test every standard beta task with fictional data.
- [ ] Test cross-user access with two independent invited accounts.
- [ ] Test export and deletion on a disposable account.
- [ ] Test backup restoration without restoring a deleted account into the live environment.
- [ ] Verify support, incident, withdrawal, and deletion-request procedures.
- [ ] Obtain explicit final approval for public form activation and tester invitations.

Definition of done: every launch scenario passes, the legal review is incorporated, and no beta access is issued before approval.

## 7. Recruit and select participants

Dependencies: milestone 6.

- [ ] Publish source-tagged LinkedIn outreach.
- [ ] Contact 10–15 suitable trainers, creators, or gyms individually.
- [ ] Request moderator permission before posting in relevant groups.
- [ ] Review applications only through the approved Tally account.
- [ ] Select 8–10 adults with a useful mix of devices and tracking experience.
- [ ] Divide selected testers into two cohorts of 4–5.
- [ ] Send invitation or rejection messages from `copy.es.md`.
- [ ] Add only accepted testers to `APP_AUTH_ALLOWED_EMAILS`.
- [ ] Schedule onboarding and final interviews.
- [ ] Start the deletion deadline for rejected applications.

Definition of done: both cohorts are selected, consent and schedules are recorded, invitations are allowlisted, and unsuccessful applications have deletion dates.

## 8. Run the two cohorts

Dependencies: milestone 7.

- [ ] Run cohort-one onboarding using fictional data by default.
- [ ] Track activation, task completion, support needs, incidents, and interview completion.
- [ ] Pay the promised incentive for completed participation regardless of sentiment.
- [ ] Stop the cohort immediately for cross-user exposure, data loss, or a serious privacy incident.
- [ ] Fix security, privacy, data-loss, and task-blocking defects before cohort two.
- [ ] Run cohort two with the same core tasks and revised build.
- [ ] Conduct and summarize final interviews.
- [ ] Delete recordings and identifying research links on schedule.
- [ ] Delete or continue beta accounts according to each tester's documented choice.

Definition of done: at least five testers complete the seven-day test and interview, incentives are fulfilled, and retention actions are scheduled or completed.

## 9. Evaluate and choose the next iteration

Dependencies: milestone 8.

- [ ] Calculate visits, applications, qualified applicants, invitations, activations, day-seven completions, and interviews by source.
- [ ] Classify findings as security/privacy, blocker, major usability issue, minor issue, or product request.
- [ ] Prioritize the ten most important findings with supporting evidence.
- [ ] Decide whether to continue, narrow, reposition, or stop the beta.
- [ ] Select the smallest set of fixes for the next cohort.
- [ ] Complete due deletion and archival actions.
- [ ] Record the decision and update this plan before opening another recruitment round.

Definition of done: the pilot has a written outcome, prioritized evidence, a retention-compliant closeout, and a clear next decision.

## 10. Account hardening follow-up

Dependencies: none; complete before any public launch.

- [ ] Enable Cloudflare Registrar Lock for `harmonovo.com`.
- [ ] Enable two-factor authentication for the Cloudflare and Proton accounts.

Definition of done: the domain and mail-provider accounts have their additional transfer and sign-in protections enabled.
