# Android CI and releases

The existing [CI workflow](../.github/workflows/ci.yml) builds Android APKs and runs
Worker, website, and release-tool checks. The website still uses its own Cloudflare
build integration. Google Play AAB uploads remain manual.

## Build channels

| Trigger | Build | Download |
|---|---|---|
| PR to `master`, including forks | Debug APK and optimized Preview APK | Two Actions artifacts, 90 days |
| Push to `master` | Signed production APK and AAB | Stable GitHub Release |
| Manual CI on `master` | Signed production APK and AAB, no publication | Actions artifact, 90 days |
| Manual CI on another branch | Debug APK and Preview APK, no publication | Two Actions artifacts, 90 days |

PR jobs have read-only repository access and use the [public test key](../ci/signing/README.md).
They never use the production environment. New runs cancel an older Android job
for the same PR. Production jobs wait in the `android-production` queue with
`queue: max`; a started release is not cancelled by a newer run.

| Channel | Application ID | Launcher label |
|---|---|---|
| Production | `com.perdolique.poleparkla` | `Pole parkla!` |
| CI debug | `com.perdolique.poleparkla.debug` | `Pole parkla! Debug` |
| CI release | `com.perdolique.poleparkla.preview` | `Pole parkla! Preview` |

These three apps can be installed together. Debug and Preview use the same fixed
test certificate across runs. Install updates with `adb install -r` to keep their
local drafts. Each app has its own private data. Play Store links always open the
production listing. Local builds keep their existing IDs and signing behavior.

Both PR variants run lint and unit tests; CI also compiles the debug instrumentation
APK. Production runs `lintRelease`, `testReleaseUnitTest`, `assembleRelease`, and
`bundleRelease`. Unit tests for both variants are enabled explicitly for AGP 9.
Instrumentation tests need a disposable test device; mail checks must stop at the
populated draft, without sending a message.

## Versions and files

The private `ci/` package runs pinned semantic-release and plugins with Node
`26.7.0` from the root `.node-version`. All repository-owned scripts and settings
are TypeScript executed directly by Node. There is no npm publication or version
commit. The release branch is `master`; tags use `v${version}`.

- Breaking commits (`!`, `BREAKING CHANGE:`, or `BREAKING-CHANGE:`) select major.
- `feat` selects minor.
- All other new commits select patch, including messages without a conventional type.
- A range uses the largest increase. Release notes are generated in English.

The baseline is `v1.0.0` at `2ba03d59d23ceac77fca4836eaa7835537f27cf5`, with no
GitHub Release. Its Android `versionCode` is 2. The first automatic release gets
code 3. Every later published release adds one, even for a major version change.
The adapter reads the previous release's `build-info.json`, not a Git commit counter.

PR names are `<stable-version>-pr.<PR-number>.<run-number>`. Other test builds use
`<stable-version>-ci.<run-number>`. Both use `github.run_number` as `versionCode`.
A manual production build uses the next calculated version and code; when there
are no new commits, it uses the latest published version and code.

Each production release has exactly these assets:

- `pole-parkla-<version>-arm64-v8a.apk`;
- `pole-parkla-<version>.aab`;
- `mapping.txt`, the nonempty R8 mapping for that build;
- `build-info.json` with `versionName`, `versionCode`, and full `commitSha`;
- `SHA256SUMS`, covering the four other files.

The adapter checks IDs, versions, ARM64-only native libraries, signing certificates,
and the APK `debuggable` flag. It verifies the AAB signature and its bundled R8
mapping using checksum-pinned bundletool `1.18.3`. Before publication, it downloads
every GitHub asset, checks the complete set and SHA-256 values, and resolves the
remote tag to the build SHA. A draft becomes stable Latest only after these checks.

GitHub Release assets have no Actions retention timer. They stay while the
repository, release, and files exist; this is not an independent backup.
Actions artifacts expire after 90 days. See the official
[retention rules](https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization)
and [release limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## Production signing

The `android-production` GitHub environment allows only the `master` branch and
has no manual approval gate. Its secrets are:

- `ANDROID_KEYSTORE_BASE64`;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`.

The chosen keystore is `~/Library/Application Support/Pole Parkla/pole-parkla-upload.jks`,
alias `pole-parkla-upload`. Its password is stored in macOS Keychain under service
`Pole Parkla upload keystore`, account `pole-parkla`. Never print the password or
commit this file. Store and key passwords are passed to Gradle only through the
environment. The adapter restores a mode-0600 key in a mode-0700 directory under
`$RUNNER_TEMP/pole-parkla-signing`, then deletes it in `finally`. CI also removes
that directory in an `always()` step. Signed CI builds disable Gradle configuration
cache. Signing material is outside all artifact paths.

Production certificate SHA-256:
`34:DA:E8:A3:B1:C8:94:4C:25:9F:98:56:D2:6C:3B:5D:D1:0A:2A:78:AB:A6:71:50:3E:8B:05:4A:AC:F5:15:48`.
GitHub APK updates use this upload key. A Play installation may use a different
app-signing certificate, so installing a GitHub APK over a Play copy is not guaranteed.
Preserve the upload key for later GitHub updates and manual Play AAB uploads.

Gradle accepts `ppBuildChannel=local|pr|production`, `ppVersionName`, and
`ppVersionCode`. The default `local` channel still uses ignored `keystore.properties`
when present and otherwise produces an unsigned release. Both CI channels require
explicit versions and all four signing environment values, including
`ANDROID_KEYSTORE_PATH`. Production fails early if signing is incomplete.

## Dry-run and recovery

Install tools with `pnpm --dir ci install --frozen-lockfile`. Check them with
`pnpm --dir ci run check`. The release command needs a full Git checkout with
tags and `GH_TOKEN` (or `GITHUB_TOKEN`) available to semantic-release and `gh`.

```sh
pnpm --dir ci run release --dry-run
```

This reports the future version without building, creating tags, uploading, or
changing releases. Run it from `master`. For a manual production build, use the
CI workflow's **Run workflow** action on `master`; it calculates the version with
dry-run and calls the Android builder separately.

If a push job fails, rerun **the failed production job at its original SHA**.
Keep its tag and draft in place:

1. If the SHA already has a complete published release, the job verifies it and succeeds.
2. If the SHA has only a tag or a partial draft, it keeps that version and Android
   number, rebuilds, replaces all draft assets with one consistent set, verifies
   the downloads, then publishes.
3. An unfinished release at another SHA blocks newer publication. Recover that
   original run first. Do not delete its tag just to let a newer run pass.
4. An older queued SHA is skipped once a newer descendant has been published.
   Unrelated history is rejected.

Published assets are never overwritten. Missing or corrupt published assets fail
verification and need manual inspection. Do not reuse an Android code from a
published release for a different production build. Do not publish a draft by hand
before all five files, checksums, and tag SHA are verified. GitHub plugin comments,
failure issues, labels, and discussions are disabled; only the production job has
`contents: write`.

Local verification covers semantic rules, native TypeScript plugin loading,
dry-run, idempotence, orphan tags, partial drafts, SHA conflicts, upload corruption,
and publication guards. Actual installs, draft preservation after APK updates,
OCR, and real mail-app handoff still need an attached Android device. Check these
on both test apps and the optimized release; never press Send during mail checks.
