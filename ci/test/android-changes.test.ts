import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { readAndroidCommits } from "../src/android-changes.ts";
import { analyzeReleaseType } from "../src/analyzer.ts";
import { capture } from "../src/process.ts";

async function createRepository(): Promise<string> {
  const cwd = await mkdtemp("/tmp/pole-parkla-android-changes-");
  await capture("git", ["init", "--initial-branch=master"], cwd);
  await capture("git", ["config", "user.email", "ci@example.invalid"], cwd);
  await capture("git", ["config", "user.name", "CI Test"], cwd);
  await capture("git", ["commit", "--allow-empty", "-m", "chore: baseline"], cwd);
  return cwd;
}

async function commitFile(cwd: string, path: string, message: string): Promise<string> {
  const file = join(cwd, path);
  const directory = dirname(file);
  await mkdir(directory, { recursive: true });
  await writeFile(file, `${message}\n`);
  await capture("git", ["add", path], cwd);
  await capture("git", ["commit", "-m", message], cwd);
  const sha = await capture("git", ["rev-parse", "HEAD"], cwd);
  return sha;
}

test("real Git file changes select only production Android inputs for version policy", async () => {
  const cwd = await createRepository();
  try {
    const ignored = ["site/src/data/updates/en/1.2.0.md", "worker/src/index.ts", "ios/App.swift", "docs/android-releases.md", "app/src/test/MainTest.kt", "app/src/androidTest/MainTest.kt", "ci/play-notes/1.2.0.json", "ci/src/release.ts", "ci/src/play-cli.ts", ".github/workflows/google-play.yml"];
    const inputs = ["app/src/main/java/Main.kt", "app/src/main/res/values/strings.xml", "app/build.gradle.kts", "app/proguard-rules.pro", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties", "gradlew", "gradlew.bat", "ci/src/android.ts"];
    const groups = [[ignored, null], [inputs, "minor"]] as const;
    for (const [paths, expected] of groups) {
      for (const path of paths) {
        const previous = await capture("git", ["rev-parse", "HEAD"], cwd);
        const message = `feat: change ${path}`;
        const sha = await commitFile(cwd, path, message);
        const commits = await readAndroidCommits(previous, sha, cwd);
        const releaseType = await analyzeReleaseType({ cwd, logger: console, commits });
        assert.equal(releaseType, expected, path);
        const expectedCommits = expected ? [{ hash: sha, message }] : [];
        assert.deepEqual(commits, expectedCommits, path);
      }
    }
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("mixed ranges ignore website features and breaking changes when selecting Android versions", async () => {
  const cwd = await createRepository();
  try {
    const baseline = await capture("git", ["rev-parse", "HEAD"], cwd);
    await commitFile(cwd, "site/index.html", "feat(site)!: replace the website");
    const fix = await commitFile(cwd, "app/src/main/Main.kt", "fix(app): repair drafts");
    const fixes = await readAndroidCommits(baseline, fix, cwd);
    const patch = await analyzeReleaseType({ cwd, logger: console, commits: fixes });
    assert.equal(patch, "patch");
    const feature = await commitFile(cwd, "app/src/main/Main.kt", "feat(app): add new report shortcut [release skip]");
    const features = await readAndroidCommits(baseline, feature, cwd);
    const minor = await analyzeReleaseType({ cwd, logger: console, commits: features });
    assert.equal(minor, "minor");
    await capture("git", ["revert", "--no-edit", feature], cwd);
    const reverted = await readAndroidCommits(baseline, "HEAD", cwd);
    const revertedType = await analyzeReleaseType({ cwd, logger: console, commits: reverted });
    assert.equal(revertedType, "minor");
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("first-parent merge diffs include Android changes without counting inherited changes in site merges", async () => {
  const cwd = await createRepository();
  try {
    const baseline = await commitFile(cwd, "app/src/main/Main.kt", "chore: baseline app");
    await capture("git", ["checkout", "-b", "shortcut"], cwd);
    const feature = await commitFile(cwd, "app/src/main/Main.kt", "feat(app): add shortcut");
    await capture("git", ["checkout", "master"], cwd);
    await commitFile(cwd, "docs/readme.md", "docs: explain reports");
    await capture("git", ["merge", "--no-ff", "shortcut", "-m", "Merge shortcut"], cwd);
    const merge = await capture("git", ["rev-parse", "HEAD"], cwd);
    const commits = await readAndroidCommits(baseline, merge, cwd);
    const hashes = commits.map((commit) => commit.hash);
    assert.deepEqual(hashes, [merge, feature]);
    const releaseType = await analyzeReleaseType({ cwd, logger: console, commits });
    assert.equal(releaseType, "minor");

    await capture("git", ["checkout", "-b", "website", baseline], cwd);
    await commitFile(cwd, "site/index.html", "feat(site)!: replace layout");
    await capture("git", ["checkout", "master"], cwd);
    await capture("git", ["merge", "--no-ff", "website", "-m", "Merge website"], cwd);
    const siteCommits = await readAndroidCommits(merge, "HEAD", cwd);
    assert.deepEqual(siteCommits, []);
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("moving inputs outside Android and deleting resources still select a release", async () => {
  const cwd = await createRepository();
  try {
    const baseline = await commitFile(cwd, "app/src/main/Main.kt", "chore: baseline app");
    const docs = join(cwd, "docs");
    await mkdir(docs);
    await capture("git", ["mv", "app/src/main/Main.kt", "docs/Main.kt"], cwd);
    await capture("git", ["commit", "-m", "refactor: remove obsolete app code"], cwd);
    const moved = await readAndroidCommits(baseline, "HEAD", cwd);
    assert.equal(moved.length, 1);
    const movedType = await analyzeReleaseType({ cwd, logger: console, commits: moved });
    assert.equal(movedType, "patch");

    const resource = await commitFile(cwd, "app/src/main/res/xml/shortcuts.xml", "chore: baseline resource");
    await capture("git", ["rm", "app/src/main/res/xml/shortcuts.xml"], cwd);
    await capture("git", ["commit", "-m", "fix: remove obsolete shortcut"], cwd);
    const deleted = await readAndroidCommits(resource, "HEAD", cwd);
    assert.equal(deleted.length, 1);
    const deletedType = await analyzeReleaseType({ cwd, logger: console, commits: deleted });
    assert.equal(deletedType, "patch");
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});
