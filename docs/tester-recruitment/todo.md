# Tester Recruitment TODO

Current milestone: **2. Launch recruitment landing and application form**

Last updated: **2026-08-30**

This checklist implements the [tester recruitment plan](plan.md). The current objective is recruitment only; do not start beta-application changes until the go/no-go decision.

## 0. Planning and name

- [x] Document the recruitment-first plan and decision gate.
- [x] Draft Spanish landing, form, outreach, and participant copy.
- [x] Select Harmonovo and harmonovo.com.
- [x] Purchase the domain, configure hello@harmonovo.com, and complete the initial name screening.

## 1. Recruitment legal preparation

Working documents: [legal review pack](legal-review-pack.es.md) and [beta protocol](beta-protocol.es.md).

- [x] Prepare the legal-review pack and beta protocol.
- [ ] Confirm controller identity, address, contact details, processors, data locations, access policy, and retention dates for recruitment data.
- [ ] Run Facilita Emprende and retain the output internally.
- [ ] Review the Tally DPA and active subprocessor list; enable 2FA and configure minimal notifications.
- [ ] Send `legal.es.md` and the final Tally fields to a Spanish privacy professional.
- [ ] Apply the approved recruitment notice, required consent, optional waitlist consent, privacy information, and legal notice.
- [ ] Define and calendar the deletion of rejected applications and optional-waitlist records.

Definition of done: the landing and Tally form may lawfully collect only the planned recruitment data.

## 2. Launch recruitment landing and application form

Dependencies: milestone 1.

- [ ] Create a separate `harmonovo-landing` project for the responsive Spanish public landing and recruitment-information pages.
- [ ] Add the `harmonovo.devjllado.com` DNS record and shared-gateway route in `hades-staging`; deploy the landing project as `harmonovo-landing-caddy` on the `shared_edge` Docker network.
- [ ] Use the approved copy with one Tally application action and no embedded third-party form scripts.
- [ ] Add three fictional screenshots: daily overview, entry flow, and weekly progress.
- [ ] Configure the Tally URL and hidden `source` field for each approved channel.
- [ ] Add page title, description, canonical URL, Open Graph metadata, favicon, and share image.
- [ ] Verify the landing at mobile and desktop widths, including links, long text, and keyboard navigation.
- [ ] Point the production Harmonovo domain at the landing only when its DNS and email records are confirmed unaffected.
- [ ] Publish only after the recruitment legal preparation is complete.

Definition of done: eligible people can understand the offer and submit a minimal, legally reviewed application without loading analytics or embedded third-party form scripts.

Validation:

```bash
git diff --check
cd ../harmonovo-landing && yarn lint && yarn build
```

## 3. Recruit and decide

Dependencies: milestone 2.

- [ ] Publish the LinkedIn post and begin direct outreach to 10–15 relevant Spanish contacts.
- [ ] Request moderator approval before posting in communities.
- [ ] Track applications and qualification by `source` without behavioural analytics.
- [ ] Confirm eight qualified applicants with a useful Android/iPhone and tracking-experience mix.
- [ ] Record a go, revise-recruitment, or stop/defer decision.

Definition of done: eight qualified applicants are confirmed, or there is a written decision to revise, stop, or defer recruitment.

## 4. Deferred: make the application beta-ready

Dependencies: a recorded **Go** decision in milestone 3.

- [ ] Define the smallest safe cohort scope from the selected applicants and their feedback.
- [ ] Create a separate beta-readiness plan before changing the application.
- [ ] Implement only the agreed invitation, onboarding, isolation, lifecycle, authorization, and security work.

Definition of done: not applicable until recruitment produces a documented **Go** decision.
