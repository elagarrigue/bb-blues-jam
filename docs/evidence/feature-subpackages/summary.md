# feature-subpackages evidence

- Base commit: fa94dcca2415ab8e55e9f4d8fa8f1fe5a7d4b944 (HEAD at start; nothing committed by the implementer).
- Baseline `CI=true ./init.sh` (baseline-gate.txt): exit 0, konsist/detekt/ktlint wired, 1133 testcases (sum of `tests=` over */build/test-results/test/*.xml), 19 `@Test` in :konsist-test.
- Final `CI=true ./init.sh` (final-gate.txt): exit 0, konsist/detekt/ktlint wired, 1133 testcases, 19 `@Test` in :konsist-test. No change under konsist-test/, detekt or ktlint config.
- `git diff HEAD -M --name-status`: 162 renames (R070..R099), 30 modified (26 root-package .kt files with import-only edits, AppNavHost.kt, architecture SKILL.md, presenter-pattern.md, feature_list.json), 0 pure adds/deletes among .kt files.
- package-path-check.txt: zero mismatches between `package` and directory in the three modules (loop over every .kt under src/).
- non-import-changes.txt is empty: `git diff HEAD -M -U0 -- '*.kt'` filtered to exclude `package`/`import`/blank lines yields zero lines.
- Per-module checkpoints: `:feature:past-jams:check :app:compileDebugKotlin`, `:feature:assistant:check :app:compileDebugKotlin`, `:feature:next-jam:check :app:assembleDebug` all green.
- RowTextAction: the class does not exist anywhere (only a stale KDoc in core/ui IconRowAction.kt:26); comment left as is.
- Cycles observed: package-level cycles accepted per spec (root<->admin, root<->overlay/refresh/addsong in next-jam; root<->admin, admin<->edit/status in past-jams; root<->apply/chat/jam, apply<->run in assistant). No rule added.
