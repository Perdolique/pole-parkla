import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";
import { assetNames } from "../src/model.ts";
import { writeChecksums } from "../src/artifacts.ts";
import type { FixtureState } from "./fixture-gh.ts";

const execFileAsync = promisify(execFile);

test("Play prepare selects the CI release, skips unrelated commits, and preserves manual tag selection", async () => {
  const directory = await mkdtemp("/tmp/pole-parkla-play-cli-");
  const info = { versionName: "1.2.0", versionCode: 5, commitSha: "1".repeat(40) };
  const tag = "v1.2.0";
  const names = assetNames(info.versionName);
  const statePath = join(directory, "state.json");
  const outputPath = join(directory, "output");
  const summaryPath = join(directory, "summary");
  const fixtureUrl = new URL("./fixture-gh.ts", import.meta.url);
  const cliUrl = new URL("../src/play-cli.ts", import.meta.url);
  const fixture = fileURLToPath(fixtureUrl);
  const cli = fileURLToPath(cliUrl);
  try {
    for (const name of names) {
      const path = join(directory, name);
      await writeFile(path, name);
    }
    const metadata = JSON.stringify(info);
    const metadataPath = join(directory, "build-info.json");
    await writeFile(metadataPath, metadata);
    await writeChecksums(directory, info.versionName);
    const assets = [];
    for (const [id, name] of names.entries()) {
      const path = join(directory, name);
      const content = await readFile(path, "utf8");
      assets.push({ id, name, content, size: content.length, state: "uploaded" });
    }
    const release = { id: 10, tag_name: tag, draft: false, prerelease: false, body: "shortcut", assets };
    const state: FixtureState = {
      sha: info.commitSha,
      tagShas: { "v1.3.0": info.commitSha, "v1.2.1": "2".repeat(40), "v1.2.0": info.commitSha },
      releases: [
        { id: 12, tag_name: "v1.3.0", draft: true, prerelease: false, body: "unfinished", assets: [] },
        { id: 11, tag_name: "v1.2.1", draft: false, prerelease: false, body: "newer", assets: [] },
        release,
      ],
      events: [],
    };
    const nodePath = process.execPath.replaceAll("'", "'\\''");
    const fixturePath = fixture.replaceAll("'", "'\\''");
    const ghPath = join(directory, "gh");
    const shim = `#!/bin/sh\nexec '${nodePath}' '${fixturePath}' "$@"\n`;
    await writeFile(ghPath, shim, { mode: 0o700 });
    const env = {
      ...process.env,
      PATH: `${directory}:${process.env.PATH}`,
      RUNNER_TEMP: directory,
      GITHUB_OUTPUT: outputPath,
      GITHUB_STEP_SUMMARY: summaryPath,
      PP_TEST_GITHUB_STATE: statePath,
      PP_PLAY_COMMIT_SHA: info.commitSha,
      PP_PLAY_TAG: "",
    };
    let stateJson = JSON.stringify(state);
    await writeFile(statePath, stateJson);
    await execFileAsync(process.execPath, [cli, "prepare"], { env });
    let output = await readFile(outputPath, "utf8");
    assert.match(output, /^should_upload=true\n/);
    assert.match(output, /\ntag=v1\.2\.0\n/);
    let staged = /^directory=(.+)$/m.exec(output)![1];
    const stagedMetadataPath = join(staged, "build-info.json");
    const stagedMetadata = await readFile(stagedMetadataPath, "utf8");
    const stagedInfo = JSON.parse(stagedMetadata);
    assert.deepEqual(stagedInfo, info);
    const stagedNotesPath = join(staged, "play-notes.json");
    const stagedNotes = await readFile(stagedNotesPath, "utf8");
    assert.match(stagedNotes, /New report/);
    await rm(staged, { recursive: true, force: true });

    env.PP_PLAY_COMMIT_SHA = "3".repeat(40);
    await writeFile(outputPath, "");
    const skipped = await execFileAsync(process.execPath, [cli, "prepare"], { env });
    assert.match(skipped.stdout, /Skip Google Play/);
    output = await readFile(outputPath, "utf8");
    assert.equal(output, "should_upload=false\n");
    const remaining = await readdir(directory);
    assert.ok(!remaining.some((name) => name.startsWith("pole-parkla-play-")));
    const summary = await readFile(summaryPath, "utf8");
    assert.match(summary, /Google Play upload skipped/);

    env.PP_PLAY_COMMIT_SHA = "";
    env.PP_PLAY_TAG = tag;
    await writeFile(outputPath, "");
    await execFileAsync(process.execPath, [cli, "prepare"], { env });
    output = await readFile(outputPath, "utf8");
    assert.match(output, /\ntag=v1\.2\.0\n/);
    staged = /^directory=(.+)$/m.exec(output)![1];
    await rm(staged, { recursive: true, force: true });

    state.releases = [release];
    stateJson = JSON.stringify(state);
    await writeFile(statePath, stateJson);
    env.PP_PLAY_TAG = "";
    await writeFile(outputPath, "");
    await execFileAsync(process.execPath, [cli, "prepare"], { env });
    output = await readFile(outputPath, "utf8");
    assert.match(output, /\ntag=v1\.2\.0\n/);
    staged = /^directory=(.+)$/m.exec(output)![1];
    await rm(staged, { recursive: true, force: true });

    const corrupt = structuredClone(state);
    corrupt.tagShas![tag] = "4".repeat(40);
    const corruptJson = JSON.stringify(corrupt);
    await writeFile(statePath, corruptJson);
    env.PP_PLAY_COMMIT_SHA = "4".repeat(40);
    await writeFile(outputPath, "");
    const mismatch = execFileAsync(process.execPath, [cli, "prepare"], { env });
    await assert.rejects(mismatch, /does not match/);
    output = await readFile(outputPath, "utf8");
    assert.equal(output, "");

    await writeFile(statePath, "{");
    const apiFailure = execFileAsync(process.execPath, [cli, "prepare"], { env });
    await assert.rejects(apiFailure);
    output = await readFile(outputPath, "utf8");
    assert.equal(output, "");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
