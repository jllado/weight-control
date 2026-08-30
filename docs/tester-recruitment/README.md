# Tester Recruitment

Current milestone: **2. Complete legal review and beta protocol**

Last updated: **2026-08-30**

This directory contains the complete plan for recruiting and running the first external beta of the application.

## How to use these documents

1. Read the [end-to-end plan](plan.md) before starting implementation.
2. Track all progress in the [incremental TODO](todo.md).
3. Use the [Spanish public copy](copy.es.md) when building the landing page, form, and communications.
4. Send the [Spanish legal drafts](legal.es.md) for professional review before publishing them or accepting real health data.
5. Do not open the application to testers until the beta-readiness gate in the TODO is complete.
6. Consult the [name screening note](name-screening.md) for the selected brand and initial clearance scope.

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
| `{{BETA_START_DATE}}` | Planned first-cohort start date |
| `{{BETA_END_DATE}}` | Planned first-cohort end date |
| `{{INCENTIVE}}` | Completion incentive; initial assumption: `15 €` |

## Locked decisions

- Select and screen a new name before implementing public branding.
- Use `www.harmonovo.com` for the landing page and `app.harmonovo.com` for the isolated beta.
- Redirect the root domain to `www.harmonovo.com`.
- Rebrand only public-facing names, metadata, assets, domains, OAuth settings, email, and documentation.
- Keep Java packages, database names, Docker identifiers, deployment paths, and repository names unchanged.
- Build the landing page in this Vue repository and link to Tally instead of creating a public form API.
- Keep the first beta invitation-only and isolated from the owner's production database and files.
- Keep the application interface in English for the first cohort and disclose this before application.
- Do not install analytics, advertising pixels, or session-recording tools for the first campaign.
