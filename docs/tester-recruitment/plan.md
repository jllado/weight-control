# Tester Recruitment Plan

## Purpose

Validate whether enough suitable people want to test Harmonovo before investing in beta-specific application changes.

The near-term deliverable is a Spanish public landing page and a separate Tally application form. The existing owner application remains unchanged and no tester receives application access during recruitment.

## Recruitment goal

- Publish a clear, trustworthy invitation for adults in Spain.
- Receive 20–30 applications as a useful funnel-health signal.
- Confirm at least eight qualified applicants with a useful mix of Android and iPhone devices, tracking experience, and tracked categories.
- Make a written go/no-go decision once eight qualified applicants are confirmed.

Eight qualified applicants—not a finished beta application—is the decision gate for future product work.

## Audience and offer

Applicants must be at least 18, live in Spain, use Android or iPhone, have a Google account, already track at least two relevant areas, and be comfortable with an English-language beta.

The future offer is a seven-day invitation-only test, about five minutes per day, and a 30-minute final interview. Participants may use fictional data and receive `{{INCENTIVE}}` after the agreed tasks and interview, regardless of feedback sentiment.

Do not ask for exact weight, diagnoses, illnesses, medication, blood pressure, progress photos, or other unnecessary sensitive information in recruitment.

## Landing and application form

Build a responsive Spanish landing page with:

1. One clear promise, the seven-day commitment, incentive, and application action.
2. Three clearly labelled fictional product screenshots: daily overview, data-entry flow, and weekly progress.
3. Who the beta is for, how selection works, and what selected people will do.
4. A statement that the application is in English, installable from the browser when supported, may be tested with fictional data, and is not a medical service.
5. FAQ, repeated application action, and links to reviewed recruitment privacy and legal information.

Link to Tally rather than embedding it. Pass a channel-specific `source` query value into one hidden field. Collect only preferred name, email, eligibility, device, general tracking categories and tools, tracking difficulty, availability, and the required/optional consents described in [copy.es.md](copy.es.md).

Do not add analytics, advertising pixels, session replay, testimonials, user counts, clinical claims, guaranteed outcomes, or unverified security claims.

## Minimum legal preparation for recruitment

Before collecting applications, complete only the legal work needed for the landing and Tally form:

- Confirm the controller identity, contact address, form processors, access policy, retention, and deletion process.
- Review the Tally DPA and active subprocessor list, enable two-factor authentication, avoid response exports, and disable answer-rich email notifications.
- Obtain review of the recruitment notice, required consent, optional future-waitlist consent, privacy information, and legal notice.
- Publish only the reviewed recruitment wording.

Beta-account health-data processing, beta terms, account export/deletion, and the future beta environment remain deferred until the go decision.

## Recruitment channels and measurement

Use a personal LinkedIn post, direct messages to 10–15 Spanish trainers, small creators, or local gyms, and relevant Spanish communities after moderator approval.

Track only the source parameter, aggregate short-retention access logs, Tally applications, qualification, and invitations. Use this funnel:

```text
Targeted visit -> Application -> Qualified -> Go/no-go decision
```

If visits are low, improve outreach volume or channels. If visits do not become applications, revise the proposition, commitment, incentive, or trust information before increasing scope.

## Decision after recruitment

When eight qualified applicants are confirmed, record one of these decisions:

- **Go:** create a separate, invitation-only beta environment and implement only the application work required for the selected cohort.
- **Revise recruitment:** improve the landing, form, screenshots, or outreach and continue recruitment.
- **Stop or defer:** close recruitment and apply the retention schedule.

Do not issue credentials, add applicants to an allowlist, copy owner data, or change the application for external users before a documented **Go** decision.

## Deferred beta scope

If the decision is **Go**, plan the smallest safe cohort path separately: invitation-only authentication, onboarding, isolation from owner data, feature limits, export/deletion, authorization tests, and security/deployment hardening.

The future cohort should test Google sign-in, daily overview, weight without photos, meals, workouts, sleep, habits, routines, editing/deleting records, and PWA installation. Progress photos, health events, medication, blood pressure, ChatGPT, imports, and backup administration remain excluded unless a later decision changes scope.
