## Summary
<!-- Concise summary of changes, motivation, and context. -->

## Scope & Affected Ownership
<!-- Check all modified modules per Master Prompt Section 7.1 ownership rules: -->
- [ ] `:core` (Integration Captain / Owner Agent)
- [ ] `:vision` / `:calib` (Person 1)
- [ ] `:gesture` / `:input` / `:engine` (Person 2)
- [ ] `:app` / `:ui` (Person 3)
- [ ] `:data` / `supabase/` / `.github/` / `docs/` (Person 4)
- [ ] Root build tooling / `gradle/` (Integration Captain only)

## Verification Performed
<!-- Describe local verification, test runs, and diagnostic checks performed. -->

## Build & Test Evidence
<!-- Provide relevant test output, terminal transcripts, or CI build links. -->

## Contract & Security Impact
<!-- Detail any impact on frozen contracts (DECISIONS.md), API boundaries, or RLS policies. -->
- Contract changes involved (requires CCR)? Yes / No
- Security / permissions impact? Yes / No

## Pre-Merge Checklist
- [ ] No API keys, credentials, or secrets committed.
- [ ] No generated build files (`build/`, `.gradle/`, `*.apk`, `*.aar`) committed.
- [ ] No local configuration (`local.properties`, `.env`) committed.
- [ ] No unrelated files or unintended formatting churn committed.
- [ ] Adheres strictly to module ownership boundaries (no unapproved cross-module edits).
- [ ] Branch rebased on latest `dev`.

