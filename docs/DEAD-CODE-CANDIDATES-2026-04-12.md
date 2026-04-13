# Dead-Code Candidate Report (Mark-Only)

Date: 2026-04-12

This report is auto-generated from changed-file heuristics. It does not delete code.

## Varynx20

## Varynx_Android

### Untracked new-file candidates
| File | Review Note |
|---|---|
| app/src/main/res/drawable/bg_dialog_surface.xml | New untracked file; validate usage before keep/delete |
| app/src/main/res/drawable/bg_onboarding_badge.xml | New untracked file; validate usage before keep/delete |
| app/src/main/res/drawable/bg_onboarding_bottom_panel.xml | New untracked file; validate usage before keep/delete |
| app/src/main/res/drawable/bg_settings_status_chip.xml | New untracked file; validate usage before keep/delete |
| app/src/main/res/drawable/bg_varynx_guardian.xml | New untracked file; validate usage before keep/delete |

## Safety Rules
1. Do not delete files referenced by manifest, reflection, DI, Gradle, JNI, or serialization names only.
2. Approve deletions in small batches and run build/test after each batch.
3. Keep migration path changes separate from logic changes.
