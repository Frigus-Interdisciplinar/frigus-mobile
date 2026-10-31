# PR #29: integration inventory and validation

- Base: `origin/dev` at `ae7813e96f4c5cf1a3bff8c956b9e93f30592731`.
- Original PR head: `a116b6afe760079bc714b0bb542a2d572ee8e541`; all four author commits remain ancestors of this integration branch.
- Isolated branch: `fix/mobile-pr29-integration`. The original uncommitted G11 and G12 worktrees were not changed.
- The final tree restores partial PR screens, inactive local data flows, and launcher resources to their `dev` versions. The original author work remains recoverable from the PR commits.

| Flow | State in `dev` | PR #29 proposal | Integration decision and evidence |
| --- | --- | --- | --- |
| Login and saved session | BFF login/profile and local logout | 403 logout and Google token handling | Keep the validated BFF session flow. 403 entitlement responses do not clear login. Registration, login, profile and saved-session restoration were exercised on an Android 12 phone. Expired sessions require manual login because refresh is not available. |
| Stock, lot edit and movements | M04/M05 and lot details, editing and M10/M11 | New edit visuals, local observations and quantity tracking | Keep the existing API-backed implementation. The PR editor did not preserve mutation confirmation and uncertain-result recovery. A group and stock were created through BFF on the phone; Home and the empty stock list loaded. A real lot was unavailable for edit/movement validation. |
| QR receipts | Published BFF receipt flow | Shared Manifest/resources | Keep the `dev` scanner. It opened and offered the newly created stock as a destination. Scanning and import await a real NFC-e for practical validation. |
| Recipes and notifications | Existing BFF screens | Shared navigation/resources | Keep the `dev` screens. Empty notifications opened. Recipe navigation opened; the external recipe service timed out, so recipe contents were not verified. |
| G11 subscription and family | Placeholder | Local/demo profile screens | Use the separate BFF integration: real Free plan, limits, empty transactions, group list, group creation, stock creation, members and invitations. Plan and limits, the new group and stock, and persistence after navigation were verified on the phone. Paid checkout is not offered without payment tokenization. Invitations and billing mutations were not sent during this verification. |
| G12 MoneySaving | Placeholder | Demo metrics | Use the separate BFF summary/events integration. The phone's domestic Free account received HTTP 403 with an honest access message. Positive metrics and filters require a commercial account with the entitlement enabled. No figures are fabricated. |
| Shopping, profile preferences, support and cosmetic product screens | Existing unavailable/read-only states | Firestore/local/demo screens and visual changes | Leave these partial flows out of the resulting tree. They do not have complete BFF-backed behavior in the PR. |

## Checks and runtime

`assembleDebug`, `lintDebug`, and `assembleDebugAndroidTest` passed in that order on the reduced tree on 2026-10-31. No local unit tests were added or run. The AndroidTest APK was packaged but instrumentation was not run on the personal phone because the existing session tests clear app credentials. Build the validation APK with `FRIGUS_BFF_BASE_URL=https://frigus-bff.onrender.com/`; credentials are never stored in this repository.

The phone verification created one domestic account, one group named `Casa`, and one stock named `Estoque`, each through a confirmed request. A first stock confirmation produced no visible result; a read confirmed absence before the successful second submission. A later inventory read confirmed the stock was empty. The final state displayed an empty stock summary without placeholder products. Profile date formatting was corrected for the server's `dd/MM/yyyy` value.

The BFF contract document describes `accountType` on registration, but the current Core `AuthService.register` forces `DOMESTIC` and its registration DTO has no account-type field. The app therefore does not offer commercial selection during registration. A commercial account and MoneySaving entitlement must be provisioned by the backend for a positive G12 test.

## Remaining merge evidence

The checks above establish compilation, UI navigation, and the authenticated empty-state/group/stock path. They do not establish product editing, M10/M11 movements, QR import, recipe contents, invitations, paid checkout, or positive G12 metrics. Review the changed tree against `dev`, wait for PR CI, and merge only when the essential affected flows have sufficient practical evidence. A build pass alone is not evidence that those untested flows work.
