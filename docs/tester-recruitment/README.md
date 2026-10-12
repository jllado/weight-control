# Tester Recruitment

Current milestone: **Recruitment live; complete operational follow-up**

Last updated: **2026-10-12**

The [current plan](plan.md) and [TODO](todo.md) cover recruitment only. The older copy, legal review pack, and beta protocol contain historical assumptions; use the landing repository's current wording and operations procedure for this release. Application health-data access has a separate readiness gate.

## How to use these documents

1. Read the [end-to-end plan](plan.md) before starting implementation.
2. Track all progress in the [incremental TODO](todo.md).
3. Use the landing repository's implemented Spanish copy; [copy.es.md](copy.es.md) is a historical draft and must not be published unchanged.
4. Use the recruitment plan and implemented landing wording as the current decisions; the [older legal drafts](legal.es.md) contain superseded beta assumptions and must not be published unchanged. Seek targeted professional advice for unresolved questions.
5. Use the landing operations procedure for recruitment; the [legal review pack](legal-review-pack.es.md) and [beta protocol](beta-protocol.es.md) are historical material for a separately updated beta-readiness plan.
6. Do not open the health-data application until its separate readiness plan is complete.
7. Consult the [name screening note](name-screening.md) for the selected brand and initial clearance scope.

## Shared placeholders

Use the same values in every document and replace them only after the naming milestone is complete.

| Placeholder | Meaning |
| --- | --- |
| `{{PRODUCT_NAME}}` | Selected public product name |
| `{{PRODUCT_DOMAIN}}` | Registrable root domain without protocol or subdomain |
| `{{CONTACT_EMAIL}}` | Public contact and privacy email |
| `{{CONTROLLER_NAME}}` | Full name or legal entity acting as data controller |
| `{{CONTROLLER_ADDRESS}}` | Address required in the reviewed legal notice |
| `{{FORM_URL}}` | Published Tally application form URL |
| `{{INCENTIVE}}` | Completion incentive; initial assumption: `15 €` |

## Locked decisions

- Select and screen a new name before implementing public branding.
- Use `testing.harmonovo.com` for the recruitment landing and beta-testing page.
- Reserve `harmonovo.com` and `www.harmonovo.com` for the app.
- Rebrand only public-facing names, metadata, assets, domains, OAuth settings, email, and documentation.
- Keep Java packages, database names, Docker identifiers, deployment paths, and repository names unchanged.
- Maintain the recovered landing in [jllado/harmonovo-landing](https://github.com/jllado/harmonovo-landing), deploy `master` to Cloudflare Pages at `testing.harmonovo.com`, and link to Tally for applications; see that repository's production release instructions.
- Keep the first beta invitation-only and isolated from the owner's production database and files.
- Keep the application interface in English for the first cohort and disclose this before application.
- Do not install analytics, advertising pixels, or session-recording tools for the first campaign.
