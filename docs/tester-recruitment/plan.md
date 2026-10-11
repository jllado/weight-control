# Recruitment Landing Release Plan

Last updated: **2026-10-11**

## Release scope

Release the corrected Spanish recruitment page at **https://testing.harmonovo.com/** and its minimal Tally application form for approximately 20 adults living in Spain.

Recruitment can open before the health-data application and marketplace integration are ready. This plan does not authorize tester accounts, health-data collection, or publication of the Coach integration.

The recovered private `jllado/harmonovo-landing` repository already deploys through Cloudflare Pages. Hosting setup is not a new milestone; verify the current deployment before release. Preserve the application domains and email DNS.

Use this plan and [TODO](todo.md) as the current recruitment decisions. Supporting Spanish copy, legal drafts, protocols, and README contain older requirements and must be reconciled before their text is published.

Locked decisions:

- Spanish landing and application form; disclose that the application interface is English.
- No fixed test duration, interviews, unconfirmed incentive, or promise of immediate access.
- Product-focused copy and clearly labelled synthetic screenshots; no personal founder biography.
- Describe ChatGPT as external; describe marketplace availability as planned until verified.
- No health questions, advertising, analytics, session replay, or campaign-source tracking.
- No paid legal package or subscription is an automatic prerequisite; document actual obligations and resolve specific uncertainties.

## Minimum launch requirements

Opening applications requires controller identification, an accurate privacy notice, a lawful basis, restricted access, and an operational deletion schedule. A signature or privacy checkbox does not waive the controller's responsibilities.

A showcase without an application form needs less preparation, but a commercial website can still require legal identification and disclosures about hosting/contact data. Do not claim that a page collects no personal data merely because it has no form.

The full application's health-data consent, DPIA assessment, account isolation, export/deletion, security work, and marketplace approval are separate beta-readiness work. Their completion is not a prerequisite for collecting minimal recruitment applications.

## Controller and contact

Identify **Juan Luis Lladó Rodríguez** where legally required and use **Harmonovo** for marketing. Do not publish the home address.

Obtain Nexus's written offer and confirm permitted address use, applicability to this activity, VAT, setup fees, renewal, cancellation, scanning, and official correspondence handling. Activate a suitable contract before publishing its address; advertised mail handling alone does not establish legal suitability.

Collect any required identification details privately. Keep identification documents, contracts, and private contact details outside Git. Publish only the information required for the actual operator.

Verify inbound delivery and replies through **hello@harmonovo.com** and document the actual delivery/forwarding providers. Record evidence without committing private messages.

Completion: accurate legal identity and suitable address are available, and the contact channel works.

## Recruitment privacy

Prepare the recruitment wording ourselves using official guidance and verified facts. Obtain targeted advice for unresolved questions where necessary; do not label drafts certified or automatically require a comprehensive paid review.

Document:

- Controller, recruitment purpose, specific consent as the chosen lawful basis, recipients, rights, retention, and withdrawal contact.
- Cloudflare hosting, Tally, and the actual email providers, including forwarding and copies.
- Provider roles, applicable agreements, locations, subprocessors, and international-transfer safeguards.
- Administrative access, MFA, minimal notifications, retention tasks, and rights-request handling.
- A dated review record distinguishing confirmed facts from unresolved issues.

Prepare `/privacidad`, `/aviso-legal`, and `/participacion`. Keep participation information limited to recruitment and future invitation; do not introduce blanket liability waivers or claim the future beta is already approved.

Provide a concise notice before submission and a link to the full policy. Keep recruitment consent separate from merely acknowledging the notice. Resolve material uncertainties before collecting applications; external review is one way to resolve them, not a blanket purchase requirement.

## Application form and data handling

Close the current Tally form while correcting it, including its direct public URL. Inspect submission counts before changing anything; preserve any existing records pending a retention review.

Use only these fields:

| Field | Requirement |
| --- | --- |
| Email | Required |
| Confirmation of age 18+ | Required |
| Confirmation of residence in Spain | Required |
| Specific recruitment consent | Required, initially unchecked |

Remove name, device, tracking habits, free-text questions, optional marketing/waitlist consent, and hidden source tracking. Do not request health information.

Store the submission timestamp and the exact consent/notice version in force. Archive versioned wording and start a new form version when the purpose or consent changes so historical acceptance remains attributable.

Show a Spanish confirmation that the application was received, access is not guaranteed, and selected people will receive a later invitation. Do not enroll people in the application automatically.

Restrict administrative access, enable MFA, avoid exports and unnecessary response copies, and disable partial-response capture where available.

Retention defaults for the final notice and operating procedure:

| Records | Deletion deadline |
| --- | --- |
| Rejected or withdrawn applications | Within 30 days of rejection or withdrawal |
| Unresolved applications | 90 days after submission |
| Accepted recruitment submissions | Within 30 days of onboarding |

Apply the unresolved deadline to selected applicants who have not onboarded; selection does not create indefinite retention. Any extension requires a documented purpose, lawful basis, and updated information before expiry.

Resolve and document the limited retention of necessary consent or rights-request evidence separately before publication. Do not keep full applications indefinitely as proof of consent.

Review deadlines weekly; include Tally trash and email copies in the deletion procedure. Never purge unrelated records. Record completion without duplicating applicant information.

## Landing implementation

Make the changes in the existing sibling `harmonovo-landing` repository, preserving its visual style and frontend stack.

- Correct the application-language claim: the form is Spanish and the application interface is English.
- Remove personal founder-history copy, fixed-duration/interview references, incentives not agreed, and unverified availability or security claims.
- Add the three legal/information pages and link them from the footer and relevant application text.
- Keep all application actions consistent and remove source-query forwarding.
- Add an explicit recruitment-open setting; closing recruitment must not fall back to the existing Tally URL.
- Preserve labelled synthetic screenshots, responsive layouts, keyboard access, and accurate metadata.
- Reconcile supporting copy, legal drafts, README, and protocol references; remove superseded eight-applicant gates and broad form fields.
- Review actual cookies/storage and hosted assets; implement only disclosures or consent controls required by the deployed behavior.

## Validation and deployment

Follow the repository-local release workflow and the landing repository's README. This is a landing release, not an application deployment.

Review mobile and desktop rendering, long text, keyboard navigation, accessible labels, FAQ behavior, all application actions, direct legal URLs, and reloads. Check that no old source tracking or personal story remains.

Use an isolated duplicate form with synthetic data to verify submission, consent, and confirmation. Do not submit test applications to the live form. Verify the production form's configuration and open/closed state separately.

Run checks sequentially from the Weight Control repository:

```bash
scripts/check.sh frontend --cwd ../harmonovo-landing lint
scripts/check.sh frontend --cwd ../harmonovo-landing build
```

Commit the completed landing changes, build the clean candidate, and push `master` to trigger Pages. Confirm the deployed commit and run the following from the landing repository:

```bash
python3 scripts/verify-deployment.py https://testing.harmonovo.com "$(git rev-parse 'HEAD^{tree}')"
```

Record exit codes, timings, and release identity. Verify HTTPS, legal links, form configuration, and contact details on production. Preserve apex, `www`, MX, TXT, and unrelated DNS records.

Keep the live form closed until the minimum requirements and release checks pass. Open the form, enable recruitment actions, and verify the resulting production state. If a privacy or form defect appears, close intake immediately; roll back the landing only to a suitable verified deployment. The form and static page have separate release states.

## Operation after release

- Review applications, access, and deletion deadlines weekly.
- Track only necessary application status and aggregate counts.
- Prepare outreach separately; sending messages or publishing posts requires explicit authorization.
- Keep the contact channel monitored and implement withdrawal and rights requests.
- Close intake when capacity is reached or recruitment is paused.
- Invite no one into the health-data application until the separate beta-readiness requirements are met.

Completion: the corrected production page collects only the documented recruitment data, every disclosure matches the deployed behavior, and the owner can operate access, deletion, and closure procedures.

## References

- [AEPD: information to provide when collecting data](https://www.aepd.es/derechos-y-deberes/conoce-tus-derechos/derecho-de-informacion).
- [AEPD: external compliance providers are voluntary](https://www.aepd.es/preguntas-frecuentes/2-tus-obligaciones-como-responsable-del-tratamiento/3-que-obligaciones-establece-el-rgpd-para-los-responsables/FAQ-0244-sobre-obligacion-de-contratar-consultora-externa-por-pyme-o-autonomo).
- [AEPD: consent and privacy information are distinct](https://www.aepd.es/preguntas-frecuentes/2-tus-obligaciones-como-responsable-del-tratamiento/6-el-deber-de-informacion/FAQ-0248-sobre-si-el-usuario-tiene-que-dar-consentimiento-a-clausula-de-privacidad).
- [LSSI: scope and website obligations](https://lssi.digital.gob.es/lssi/la-ley/preguntas-frecuentes).
- [LSSI Article 10: provider identification](https://www.boe.es/buscar/act.php?id=BOE-A-2002-13758#a10).
