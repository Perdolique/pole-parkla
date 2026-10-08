# Android CI and releases

The existing [CI workflow](../.github/workflows/ci.yml) builds Android APKs and runs
Worker, website, and release-tool checks. The website still uses its own Cloudflare
build integration. The [Google Play draft workflow](../.github/workflows/google-play.yml)
uploads a verified published AAB automatically. Review submission and publication
remain manual in Play Console.

## Build channels

| Trigger | Build | Download |
|---|---|---|
| PR to `master`, including forks | Debug APK and optimized Preview APK | Two Actions artifacts, 7 days |
| Push to `master` with production Android changes | Signed production APK and AAB | Stable GitHub Release |
| Push to `master` without production Android changes | Node checks, no production build | No new Android release |
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

All Node and Android jobs use `Perdolique/automations/.github/actions/setup-pnpm@v4`
with pnpm `12.9.1` pinned in each package. The shared action performs a shallow
checkout. Android jobs then fetch full history and tags with a second checkout
that removes saved Git credentials, before installing dependencies. Release
version checks and recovery need that history. The shared Cloudflare deploy
workflow is not used here; existing deploy integrations keep their own setup.
Worker and site settings allow the `esbuild` and `workerd` install scripts needed
by their build tools. The release-tool package keeps pnpm's one-day minimum
release age, with one exact exception for the already tested `handlebars@4.7.10`.

## Versions and files

The private `ci/` package runs pinned semantic-release and plugins with Node
`26.7.0` from the root `.node-version`. All repository-owned scripts and settings
are TypeScript executed directly by Node. There is no npm publication or version
commit. The release branch is `master`; tags use `v${version}`.

- Breaking Android commits (`!`, `BREAKING CHANGE:`, or `BREAKING-CHANGE:`) select major.
- Android `feat` commits select minor.
- All other Android commits select patch, including messages without a conventional type.
- A range uses the largest Android increase. Release notes are generated in English.

The analyzer reads the full Git range from the previous release to `HEAD`, then
keeps only commits that change production Android inputs:

- `app/src/main/**`, including bundled models and resources;
- `app/build.gradle.kts` and `app/proguard-rules.pro`;
- root Gradle settings, build properties, both wrapper scripts, and `gradle/**`;
- `ci/src/android.ts`, which controls the Android build.

Merge commits use their first-parent diff. Deleted files and files moved outside
Android still count. Website, Worker, iOS, documentation, tests, Play notes, and
publication-tool changes alone do not release Android. For example,
`feat(site)` followed by `fix(app)` selects patch. Changes to shared Gradle files
still count even when they only update a test dependency.

The PR version forecast, release analyzer, and normal and recovery release notes
use this same input policy. With no Android commits, automatic publication exits
without a production build, tag, release, or version increase. PR APK builds and
the Worker, site, and release-tool checks still run.

Reverted Android feature and breaking commits still count. `[skip release]` and
`[release skip]` do not suppress an Android release under this policy. Each
eligible commit is analyzed separately so upstream revert filtering cannot change
the largest bump. Existing releases and their version codes stay unchanged.

The baseline is `v1.0.0` at `2ba03d59d23ceac77fca4836eaa7835537f27cf5`, with no
GitHub Release. Its Android `versionCode` is 2. The first automatic release gets
code 3. Every later published release adds one, even for a major version change.
The adapter reads the previous release's `build-info.json`, not a Git commit counter.

PR names are `<stable-version>-pr.<PR-number>.<run-number>`. Other test builds use
`<stable-version>-ci.<run-number>`. Both use `github.run_number` as `versionCode`.
A manual production build uses the next calculated version and code; when there
are no new Android commits, it uses the latest published version and code.

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
PR Actions artifacts expire after 7 days; manual build artifacts expire after 90 days. See the official
[retention rules](https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization)
and [release limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## Google Play drafts

After successful push CI on `master`, the Google Play workflow looks for a stable
published GitHub Release at the triggering run's `head_sha`. It passes that SHA
as `PP_PLAY_COMMIT_SHA`; the prepare step returns `should_upload`. If this commit
has no published Android release, it skips Google authentication and upload.
The triggering SHA is separate from this workflow's own `GITHUB_SHA`, which can
already point to a newer `master` commit.

For a matching release, it verifies all five assets, their SHA-256 values, the
Android version, and the remote tag and build metadata against the triggering
commit. API and verification errors fail the run. It uploads the existing signed
AAB, without another Android build. Manual dispatch can select a published tag,
such as `v1.2.0`, or select the latest published release by leaving the tag empty.
PR and manual build CI runs do not trigger a Play upload.

The uploader creates a `production` release with status `draft`. It preserves
existing active releases and rejects a different pending draft. A retry reuses
an uploaded bundle only when its SHA-256 matches. An already released version
is left unchanged. The workflow reads back the saved draft and its translations.
It does not submit a release for review or roll it out. A running review is not
cancelled: the commit uses `changesInReviewBehavior=ERROR_IF_IN_REVIEW`.

In Play Console, open the draft, check its files and notes, and submit it for
review when ready. Keep **Managed publishing** on in **Publishing overview**.
After Google approves the update, select **Publish changes** to release it.
Managed publishing is enabled for this app. See Google's
[draft release API](https://developers.google.com/android-publisher/tracks#draft_releases)
and [managed publishing guide](https://support.google.com/googleplay/android-developer/answer/9859654).

### Localized notes

Keep reviewed user-facing notes in `ci/play-notes/<version>.json`, with exactly
`et`, `en-US`, and `ru-RU`. Each value must be nonempty and at most 500 Unicode
characters. Notes for `1.1.0` describe the GitHub source and feedback links added
after the `1.0.0 (2)` build published on September 8, 2026. The later Git tag
`v1.0.0` already contains those links, so its diff is not the right basis for
that first Play update.

Before building PR test APKs, CI forecasts the next version with the same Android
commit policy used for releases. It checks Play notes only when a new Android
release is needed. This read-only
check needs a full checkout with tags and a GitHub token:

```sh
pnpm --dir ci run check:play-notes
```

Production checks the actual version during `verifyRelease`, including dry-run,
before the Android build, tag creation, or publication. Recovery checks notes
before rebuilding an unfinished release. The Play uploader checks them again
before Google authentication.

For a later release without reviewed notes, all these checks compare Android
source and build files with the previous stable tag. If any changed, they ask for
reviewed notes. If Android files did not change, they use a short build-process
note in all three languages. GitHub release notes remain separate and are
generated from commits.

### Google access

Use Google Cloud project `pole-parkla-play` (number `76053822492`) and a dedicated
service account `github-play@pole-parkla-play.iam.gserviceaccount.com`. Enable
`androidpublisher.googleapis.com`, `iamcredentials.googleapis.com`, and
`sts.googleapis.com`. The service account needs no project-level roles or JSON
key. Add it in Play Console **Users and permissions**, with access only to
`com.perdolique.poleparkla`, **View app information (read-only)** and **Release to
production, exclude devices, and use Play App Signing**. Play's production
permission can publish releases; the uploader's draft-only contract and Managed
publishing keep the requested release flow manual.

Authenticate through Workload Identity Federation. Create pool `github-play`
and OIDC provider `pole-parkla`, with issuer
`https://token.actions.githubusercontent.com` and mappings:

```text
google.subject=assertion.sub
attribute.repository_id=assertion.repository_id
attribute.repository_owner_id=assertion.repository_owner_id
```

Use this provider condition to accept only the dedicated trusted workflow:

```text
assertion.repository_owner_id == '161577745' && assertion.repository_id == '1342880600' && assertion.ref == 'refs/heads/master' && assertion.workflow_ref == 'Perdolique/pole-parkla/.github/workflows/google-play.yml@refs/heads/master'
```

Grant `roles/iam.workloadIdentityUser` on the service account only to:

```text
principalSet://iam.googleapis.com/projects/76053822492/locations/global/workloadIdentityPools/github-play/attribute.repository_id/1342880600
```

Create the GitHub environment `google-play`, limited to `master`, and set its
variables:

- `PLAY_SERVICE_ACCOUNT`: `github-play@pole-parkla-play.iam.gserviceaccount.com`;
- `PLAY_WORKLOAD_IDENTITY_PROVIDER`:
  `projects/76053822492/locations/global/workloadIdentityPools/github-play/providers/pole-parkla`.

The workflow obtains a ten-minute OAuth access token with only the
`androidpublisher` scope after downloading and checking the build. It writes no
Google credential file and stores no long-lived Google secret. See
[Google's GitHub authentication action](https://github.com/google-github-actions/auth)
and [its security guidance](https://github.com/google-github-actions/auth/blob/main/docs/SECURITY_CONSIDERATIONS.md).

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
