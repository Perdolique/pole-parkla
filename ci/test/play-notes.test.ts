import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import semanticRelease from "semantic-release";
import { releaseOptions } from "../src/config.ts";
import type { ReleaseState } from "../src/model.ts";
import { checkNextPlayNotes, readPlayNotes } from "../src/play-notes.ts";
import { capture } from "../src/process.ts";

const notes = { et: "Lisati uue teate otsetee.", "en-US": "Added a new report shortcut.", "ru-RU": "Добавлен ярлык нового репорта." };

interface NotesRepository {
  directory: string;
  checkout: string;
  remote: string;
  baselineSha: string;
}

async function createRepository(): Promise<NotesRepository> {
  const directory = await mkdtemp("/tmp/pole-parkla-play-notes-");
  const remote = join(directory, "remote");
  const checkout = join(directory, "checkout");
  await capture("git", ["init", "--bare", "--initial-branch=master", remote], directory);
  await capture("git", ["clone", remote, checkout], directory);
  await capture("git", ["config", "user.email", "ci@example.invalid"], checkout);
  await capture("git", ["config", "user.name", "CI Test"], checkout);
  const app = join(checkout, "app/src/main");
  await mkdir(app, { recursive: true });
  const appPath = join(app, "Main.kt");
  await writeFile(appPath, "// Previous app version\n");
  await capture("git", ["add", "app"], checkout);
  await capture("git", ["commit", "-m", "chore: baseline"], checkout);
  await capture("git", ["tag", "v1.0.0"], checkout);
  await capture("git", ["tag", "v1.1.0"], checkout);
  await capture("git", ["push", "origin", "master", "--tags"], checkout);
  const baselineSha = await capture("git", ["rev-parse", "HEAD"], checkout);
  return { directory, checkout, remote, baselineSha };
}

test("PR notes check forecasts the exact version and rejects missing or invalid translations", async () => {
  const repo = await createRepository();
  try {
    const previous: ReleaseState = {
      tag: "v1.1.0",
      commitSha: repo.baselineSha,
      published: true,
      buildInfo: { versionName: "1.1.0", versionCode: 3, commitSha: repo.baselineSha },
    };
    const notesDirectory = join(repo.checkout, "ci/play-notes");
    await mkdir(notesDirectory, { recursive: true });
    const currentNotesPath = join(notesDirectory, "1.1.0.json");
    await writeFile(currentNotesPath, "{");
    await capture("git", ["commit", "--allow-empty", "-m", "ci: update release tools [skip release]"], repo.checkout);
    const buildSha = await capture("git", ["rev-parse", "HEAD"], repo.checkout);
    const buildInfo = await checkNextPlayNotes(previous, buildSha, repo.checkout);
    assert.deepEqual(buildInfo, { versionName: "1.1.0", versionCode: 3, commitSha: buildSha });
    await rm(currentNotesPath);
    const fallback = await readPlayNotes(buildInfo, repo.checkout);
    assert.deepEqual(Object.keys(fallback), ["et", "en-US", "ru-RU"]);
    assert.equal(fallback["en-US"], "Updated the app build and release process.");

    const appPath = join(repo.checkout, "app/src/main/Main.kt");
    await writeFile(appPath, "// Added the launcher shortcut\n");
    await capture("git", ["add", "app"], repo.checkout);
    await capture("git", ["commit", "-m", "feat(app): add new report shortcut [release skip]"], repo.checkout);
    const appSha = await capture("git", ["rev-parse", "HEAD"], repo.checkout);
    const content = JSON.stringify(notes);
    const otherVersionPath = join(notesDirectory, "1.1.1.json");
    await writeFile(otherVersionPath, content);
    const missing = checkNextPlayNotes(previous, appSha, repo.checkout);
    await assert.rejects(missing, /ci\/play-notes\/1\.2\.0\.json/);

    const notesPath = join(notesDirectory, "1.2.0.json");
    const untranslated = JSON.stringify({ et: notes.et, "en-US": notes["en-US"] });
    await writeFile(notesPath, untranslated);
    const incomplete = checkNextPlayNotes(previous, appSha, repo.checkout);
    await assert.rejects(incomplete, /ru-RU/);
    await writeFile(notesPath, "{");
    const invalid = checkNextPlayNotes(previous, appSha, repo.checkout);
    await assert.rejects(invalid, SyntaxError);
    await writeFile(notesPath, content);
    const appInfo = await checkNextPlayNotes(previous, appSha, repo.checkout);
    assert.deepEqual(appInfo, { versionName: "1.2.0", versionCode: 4, commitSha: appSha });
    const reviewed = await readPlayNotes(appInfo, repo.checkout);
    assert.deepEqual(reviewed, notes);
    const tags = await capture("git", ["tag", "--list"], repo.checkout);
    assert.equal(tags, "v1.0.0\nv1.1.0", "The PR check must not create a release tag");
  } finally {
    await rm(repo.directory, { recursive: true, force: true });
  }
});

test("production verifies Play notes in dry-run and before prepare or tag creation", async () => {
  const repo = await createRepository();
  try {
    const appPath = join(repo.checkout, "app/src/main/Main.kt");
    await writeFile(appPath, "// Added the launcher shortcut\n");
    await capture("git", ["add", "app"], repo.checkout);
    await capture("git", ["commit", "-m", "feat(app): add new report shortcut"], repo.checkout);
    await capture("git", ["push", "origin", "master"], repo.checkout);
    const sha = await capture("git", ["rev-parse", "HEAD"], repo.checkout);
    const events = join(repo.directory, "events");
    await writeFile(events, "");
    const options = releaseOptions(4, sha, true);
    const remoteUrl = pathToFileURL(repo.remote);
    options.repositoryUrl = remoteUrl.href;
    options.ci = false;
    // Keep the production analyzer, notes generator, and Android adapter. Replace only GitHub writes.
    const productionPlugins = options.plugins!.slice(0, -1);
    const fixtureUrl = new URL("./fixture-plugin.ts", import.meta.url);
    const fixture = fileURLToPath(fixtureUrl);
    options.plugins = [...productionPlugins, fixture];
    const env = { PATH: process.env.PATH, PP_TEST_EVENTS: events };
    for (const dryRun of [true, false]) {
      options.dryRun = dryRun;
      const rejected = semanticRelease(options, { cwd: repo.checkout, env });
      await assert.rejects(rejected, /ci\/play-notes\/1\.2\.0\.json/);
      const recorded = await readFile(events, "utf8");
      assert.equal(recorded, "", "Missing notes must stop before prepare and publish");
      const tags = await capture("git", ["tag", "--list"], repo.checkout);
      assert.equal(tags, "v1.0.0\nv1.1.0");
      const remoteTags = await capture("git", ["--git-dir", repo.remote, "tag", "--list"], repo.directory);
      assert.equal(remoteTags, "v1.0.0\nv1.1.0", "Missing notes must not publish a tag");
    }
    const notesDirectory = join(repo.checkout, "ci/play-notes");
    await mkdir(notesDirectory, { recursive: true });
    const content = JSON.stringify(notes);
    const notesPath = join(notesDirectory, "1.2.0.json");
    await writeFile(notesPath, content);
    options.dryRun = true;
    const result = await semanticRelease(options, { cwd: repo.checkout, env });
    assert.ok(result);
    assert.equal(result.nextRelease.version, "1.2.0");
    assert.equal(result.nextRelease.gitHead, sha);
    const recorded = await readFile(events, "utf8");
    assert.equal(recorded, "");
  } finally {
    await rm(repo.directory, { recursive: true, force: true });
  }
});
