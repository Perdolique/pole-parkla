import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import semanticRelease from "semantic-release";
import { analyzerPath, notesPath, releaseOptions } from "../src/config.ts";
import { capture } from "../src/process.ts";

test("configured semantic-release API loads native TS, skips prepare in dry-run, and creates the exact tag", async () => {
  const directory = await mkdtemp(join("/tmp", "pole-parkla-semantic-"));
  const repository = join(directory, "repo");
  const events = join(directory, "events");
  try {
    await capture("git", ["init", "--bare", "--initial-branch=master", repository], directory);
    const checkout = join(directory, "checkout");
    await capture("git", ["clone", repository, checkout], directory);
    await capture("git", ["config", "user.email", "ci@example.invalid"], checkout);
    await capture("git", ["config", "user.name", "CI Test"], checkout);
    await capture("git", ["commit", "--allow-empty", "-m", "chore: baseline"], checkout);
    await capture("git", ["tag", "v1.0.0"], checkout);
    await capture("git", ["push", "origin", "master", "--tags"], checkout);
    await capture("git", ["commit", "--allow-empty", "-m", "feat: build preview APKs"], checkout);
    await capture("git", ["push", "origin", "master"], checkout);
    const sha = await capture("git", ["rev-parse", "HEAD"], checkout);
    await writeFile(events, "");
    const fixture = fileURLToPath(new URL("./fixture-plugin.ts", import.meta.url));
    const options = releaseOptions(3, sha, true);
    options.repositoryUrl = pathToFileURL(repository).href;
    options.ci = false;
    options.plugins = [analyzerPath, [notesPath, { preset: "conventionalcommits" }], fixture];
    // CI branch variables refer to the outer PR, not this temporary master branch.
    const env = { PATH: process.env.PATH, PP_TEST_EVENTS: events };
    const dry = await semanticRelease(options, { cwd: checkout, env });
    assert.ok(dry);
    assert.equal(dry.nextRelease.version, "1.1.0");
    assert.match(dry.nextRelease.notes ?? "", /Features/);
    assert.equal(await readFile(events, "utf8"), "");
    assert.equal(await capture("git", ["tag", "--list"], checkout), "v1.0.0");
    options.dryRun = false;
    const released = await semanticRelease(options, { cwd: checkout, env });
    assert.ok(released);
    assert.equal(released.nextRelease.gitHead, sha);
    assert.equal(await capture("git", ["rev-parse", "v1.1.0"], checkout), sha);
    assert.equal(await readFile(events, "utf8"), "prepare 1.1.0\npublish 1.1.0\n");
    const again = await semanticRelease(options, { cwd: checkout, env });
    assert.equal(again, false);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
