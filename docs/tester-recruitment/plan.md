# Tester Recruitment and Beta Plan

## Purpose

Recruit a small, representative group of adults in Spain to test whether the application is understandable, trustworthy, and useful for regularly tracking personal health and fitness information.

The project has two connected deliverables:

1. A public recruitment funnel that explains the beta and collects applications.
2. A safe, invitation-only beta application that can accept external users without exposing the owner's data.

The landing page may be published as a waitlist before the application is ready, but access credentials must not be issued until every beta-readiness requirement is complete.

## Success criteria

- Receive 20–30 applications from targeted channels.
- Identify at least 8 suitable applicants.
- Invite 8–10 applicants across two cohorts.
- Activate at least 5 testers.
- Complete at least 5 seven-day tests and final interviews.
- Produce a prioritized list of the ten most important product or usability findings.
- Have no cross-user data exposure, privacy incident, or unplanned transmission of health data.

## Product and audience decisions

### Tester profile

The first tester should:

- Be at least 18 years old and currently live in Spain.
- Already track at least two of food, workouts, sleep, weight, habits, or routines.
- Use Android or iPhone and be comfortable testing an installable web application.
- Have a Google account.
- Be comfortable using an English-language beta.
- Commit to seven days of use, approximately five minutes per day, and a 30-minute final interview.

Testers may use fictional data. They must not be asked to provide exact weight, diagnoses, illnesses, medication, blood pressure, progress photographs, or other unnecessary sensitive information in the recruitment form.

### Test offer

- Seven-day invitation-only beta.
- 15–20 minute onboarding session or guide.
- Approximately five minutes of daily use.
- One 30-minute final interview.
- `{{INCENTIVE}}` after completing the agreed tasks and interview, regardless of whether the feedback is positive.

### First-cohort feature scope

Test the smallest useful path:

- Google sign-in and onboarding.
- Daily overview.
- Weight entries without progress photos.
- Meals and calorie totals.
- Workouts.
- Sleep.
- Habits and routines.
- Editing and deleting records.
- PWA installation when supported.
- Account export and deletion in the final acceptance pass.

Do not include progress photos, sicknesses, back-pain episodes, medication information, blood-pressure tracking, ChatGPT reflections, owner-only imports, or backup administration in the first cohort.

## Name, domain, and public rebrand

Name selection is a blocking milestone. `FitControl` already has a directly competing Spanish fitness product at [fitcontrol.co](https://fitcontrol.co/), and `HealthControl` is used by existing health and MedTech businesses such as [healthcontrol.rs](https://healthcontrol.rs/). Neither candidate should be used without formal trademark, domain, app-store, and social-name screening.

The naming process must:

1. Define a short brief emphasizing personal tracking, patterns, and progress without implying diagnosis or treatment.
2. Produce at least five distinctive candidates.
3. Search Google, app stores, social networks, GitHub, EUIPO, and the Spanish Patent and Trademark Office.
4. Check `.es` and `.com` availability and common social usernames.
5. Select the name, buy the root domain, and create `{{CONTACT_EMAIL}}`.
6. Configure SPF, DKIM, DMARC, registrar locking, automatic renewal, and two-factor authentication.

Use this public layout:

- Root domain redirects permanently to `https://www.{{PRODUCT_DOMAIN}}/`.
- `www.{{PRODUCT_DOMAIN}}` hosts the canonical public landing, privacy, legal, and beta-information pages.
- `app.{{PRODUCT_DOMAIN}}` hosts the isolated beta application and redirects its bare root to `/login`.

The public-facing rename includes visible UI text, page titles, PWA metadata, icons and alt text, Open Graph content, OAuth branding, public email, documentation, and deployed domains. Internal Java packages, Gradle identifiers, database names, Docker project names, server directories, scripts, and repository names remain `weight-control` to avoid a risky mechanical rewrite.

## Recruitment funnel

### Landing page

Build a responsive, Spanish-language public page in the current Vue frontend with these sections:

1. Hero with one promise, the seven-day commitment, incentive, and primary application button.
2. Three real screenshots created from a fictional demo account: daily overview, one data-entry flow, and weekly progress.
3. A clear explanation of who the beta is for and what selected testers will do.
4. Privacy and trust statements that are already implemented and verifiable.
5. FAQ, repeated application button, and footer links to privacy and legal documents.

The page must say that the application is currently in English, is an installable web application rather than a native store application, permits fictional data, and is not a medical service.

Do not add testimonials, user counts, clinical claims, guaranteed outcomes, or security claims that have not been independently established. Promotional statements affect a software product's regulatory intended purpose, so the page must describe lifestyle and well-being tracking without diagnosis or treatment claims. See the [EU Medical Device Regulation](https://eur-lex.europa.eu/legal-content/EN/TXT/?qid=1496918076971&uri=CELEX%3A32017R0745).

### Tally application form

Link to a separate Tally form instead of embedding it, preventing third-party form scripts from loading on the landing page.

Collect only:

- Preferred name and email address.
- Confirmation of being at least 18 and living in Spain.
- Android, iPhone, or both.
- Categories currently tracked and tools currently used.
- The applicant's biggest tracking difficulty.
- Availability for seven days and a 30-minute interview.
- Planned availability dates.
- Required explicit consent for recruitment processing.
- Separate optional consent if an unsuccessful applicant may remain on a future waitlist.

Tally is an EU-based processor and publishes a data-processing agreement, but the controller remains responsible for the form fields and retention. Review [Tally's GDPR documentation](https://tally.so/help/gdpr), enable two-factor authentication, avoid response exports, and configure email notifications that do not reproduce full answers.

### Channels

Start with three targeted channels:

- A personal LinkedIn post explaining why the application exists.
- Individual messages to 10–15 Spanish trainers, small fitness creators, or local gyms.
- Relevant Spanish Facebook or beta-testing communities after moderator approval.

Use a different `source` query value for each channel, such as `linkedin`, `trainer`, `gym`, `instagram`, or `reddit`. Pass it into a hidden Tally field. Do not use health-condition targeting, retargeting, or behavioral advertising.

## Application beta readiness

### Public and protected frontend routes

- Add public landing, privacy, legal, beta-terms, and login routes.
- Move the authenticated dashboard from `/` to `/app` and update menu, push, PWA shortcut, and internal navigation destinations.
- Use route metadata and a router guard instead of redirecting every anonymous visitor from `App.vue`.
- Load Google Identity only on the login surface.
- Keep the canonical public pages on `www` and redirect the bare `app` host to `/login` at the proxy layer.

### Invitation-only authentication

- Replace the hard-coded owner-email check with an `APP_AUTH_ALLOWED_EMAILS` configuration property containing the owner and selected tester Google emails.
- Reject otherwise valid Google accounts with a neutral access-not-enabled message.
- Create user records only after the email passes the allowlist.
- Use a distinct production OAuth client for `app.{{PRODUCT_DOMAIN}}`; do not reuse local, owner-production, or beta credentials.
- Configure the selected name, logo, homepage, privacy URL, authorized domain, and JavaScript origin in Google Cloud.

Google requires an external production application to have a public homepage and privacy policy; the homepage cannot be only a login page. See [Google's homepage requirements](https://support.google.com/cloud/answer/13807376?hl=en).

### Onboarding and data minimization

- Remove the owner's hard-coded calorie targets from new-account creation.
- Introduce a first-login onboarding flow and keep new accounts out of the dashboard until it is complete.
- Ask only for fields required by the enabled beta features.
- Explain why each health-related field is required before it is collected.
- Preserve the owner's existing profile and data during migrations.

### User rights and lifecycle

- Add an authenticated export containing the tester's records in a machine-readable archive.
- Add account deletion that removes owned database rows, push subscriptions, uploaded files, and active session access.
- Provide an email process for access, correction, withdrawal, and deletion requests.
- Delete rejected recruitment applications after 30 days.
- Delete selected-participant administration records 90 days after their cohort ends.
- Delete beta application health data 30 days after the cohort unless the tester explicitly elects to continue.
- Delete interview recordings within 30 days and remove the link between identity and research notes within 90 days.

### Isolation and security

- Deploy the beta with a separate Compose project, MariaDB volume, backend file volume, secrets, OAuth client, allowed-email list, and backups.
- Do not copy the owner's production database or photos into the beta environment.
- Add automated two-user tests proving user A cannot list, read, update, delete, or download user B's records or files.
- Enable CSRF protection appropriate for cookie-authenticated writes.
- Keep session cookies secure, HTTP-only, and appropriately scoped.
- Add CSP, HSTS, `Referrer-Policy`, and `Permissions-Policy` headers compatible with Google Identity.
- Ensure application and proxy logs exclude health payloads, authorization headers, session cookies, signed URLs, and form responses.
- Encrypt beta backups, restrict access, test restoration, and document the deletion effect on backups.
- Create a breach-response contact and incident log before inviting testers.

GDPR treats health information as a special category and requires security appropriate to its risk. Use the [AEPD health-data guidance](https://www.aepd.es/areas-de-actuacion/salud/tus-derechos-en-relacion-con-tus-datos-de-salud), [AEPD Facilita Emprende](https://www.aepd.es/guias-y-herramientas/herramientas/facilita-emprende), and [GDPR Article 32](https://eur-lex.europa.eu/eli/reg/2016/679/art_32/oj/eng) as starting points, followed by professional legal review.

## Privacy and analytics

- Publish reviewed privacy, legal, and beta-terms pages before opening the form.
- Keep recruitment consent, health-data consent, optional waitlist consent, and interview-recording consent separate.
- Maintain a record of processing activities, processor list, retention schedule, risk assessment, and breach procedure.
- Do not install Google Analytics, Meta Pixel, session replay, advertising cookies, or similar trackers in the first campaign.
- Use only source query parameters, aggregate short-retention access logs, Tally submissions, and application activation counts.

If non-essential cookies are added later, block them until consent and provide equally visible accept and reject choices. See the [AEPD cookie guidance](https://www.aepd.es/guias/guia-cookies.pdf).

## Running the beta

### Selection

Select 8–10 applicants with a useful mix of Android and iPhone devices, tracking experience, and tracked categories. Avoid selecting only developers or only highly technical users.

Run two cohorts of 4–5 testers. Do not start the second cohort until critical security, data-loss, authentication, and task-blocking issues from the first cohort are fixed.

### Standard tasks

1. Sign in and complete onboarding.
2. Record fictional data for yesterday's sleep.
3. Record a meal and review the daily total.
4. Record a workout.
5. Add and complete one habit or routine.
6. Find the weekly summary.
7. Edit and delete an entry.
8. Install the PWA when supported.
9. Explain confusing wording or behavior.
10. In the final acceptance pass, export and delete a disposable account.

Use email as the support channel and ask testers not to send health details through ordinary email. Record usability sessions only after separate consent.

### Interview questions

- What did you expect before opening the application?
- Where did you hesitate or become confused?
- Which information was hardest to enter?
- Which screen was most useful?
- What increased or reduced your trust?
- What would make you use it again next week?
- What would prevent regular use?
- What should be removed before anything new is added?

## Measurement and decisions

Track this funnel without individual behavioral analytics:

```text
Targeted visit -> Application -> Qualified -> Invited -> Activated -> Day-7 completion -> Interview
```

Interpret results by stage:

- Few targeted visits means the channel or outreach volume is the problem.
- Visits without applications means the proposition, trust, commitment, or incentive needs revision.
- Applications without activation means invitation, authentication, or onboarding needs revision.
- Activation without continued use means core usability or product value needs revision.

After both cohorts, classify every finding as security/privacy, blocker, major usability issue, minor issue, or product request. Fix security/privacy issues and blockers first, then select the smallest set of major usability improvements for the next round.

## Schedule and budget assumptions

Suggested sequence:

- Week 1: name, domain, email, legal drafts, and beta protocol.
- Week 2: authentication, onboarding, lifecycle, security, and isolated beta configuration.
- Week 3: landing page, Tally form, screenshots, and launch validation.
- Week 4: recruitment and selection.
- Week 5: first cohort and critical fixes.
- Week 6: second cohort, interviews, and evaluation.

Expected direct pilot costs:

- Domain and email: confirm current registrar pricing before purchase; Dinahosting currently includes one mailbox with a domain.
- Tally: free plan for the first application round.
- Incentives: 8 testers x 15 € = 120 €.
- Existing hosting: no assumed additional server fee, subject to capacity.
- Privacy/legal review: separate budget and mandatory review gate.

## Out of scope for the first beta

- Public registration.
- Native app-store releases.
- Full Spanish application localization.
- Progress-photo analysis or ChatGPT Coach access for testers.
- Paid advertising, analytics platforms, retargeting, or marketing automation.
- A full technical rename of packages, databases, Docker resources, scripts, or deployment directories.
- Medical diagnosis, treatment, prevention, or clinical outcome claims.
