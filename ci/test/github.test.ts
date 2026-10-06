import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { assetNames } from "../src/model.ts";
import { writeChecksums } from "../src/artifacts.ts";
import { finalizeRelease, loadState, replaceDraft } from "../src/github.ts";
import type { FixtureState } from "./fixture-gh.ts";

test("GitHub finalizer verifies downloads before publication and recovery replaces only drafts", async () => {
  const directory = await mkdtemp(join("/tmp", "pole-parkla-github-"));
  const info = { versionName: "1.0.1", versionCode: 3, commitSha: "1".repeat(40) };
  const tag = "v1.0.1";
  const names = assetNames(info.versionName);
  const fixture = fileURLToPath(new URL("./fixture-gh.ts", import.meta.url));
  const statePath = join(directory, "state.json");
  const originalPath = process.env.PATH;
  try {
    for (const name of names) await writeFile(join(directory, name), name);
    await writeFile(join(directory, "build-info.json"), JSON.stringify(info));
    await writeChecksums(directory, info.versionName);
    const assets = [];
    for (const [id, name] of names.entries()) {
      const content = await readFile(join(directory, name), "utf8");
      assets.push({ id, name, content, size: content.length, state: "uploaded" });
    }
    const release = { id: 10, tag_name: tag, draft: true, prerelease: false, body: "notes", assets };
    const state: FixtureState = { sha: info.commitSha, releases: [release], events: [] };
    await writeFile(statePath, JSON.stringify(state));
    // A temporary CLI shim exercises the same REST paths and byte downloads as CI.
    const nodePath = process.execPath.replaceAll("'", "'\\''");
    const fixturePath = fixture.replaceAll("'", "'\\''");
    await writeFile(join(directory, "gh"), `#!/bin/sh\nexec '${nodePath}' '${fixturePath}' "$@"\n`, { mode: 0o700 });
    await writeFile(join(directory, "git"), `#!/bin/sh\nexec '${nodePath}' '${fixturePath}' git "$@"\n`, { mode: 0o700 });
    process.env.PATH = `${directory}:${originalPath}`;
    process.env.PP_TEST_GITHUB_STATE = statePath;
    await finalizeRelease(tag, info, directory);
    let savedJson = await readFile(statePath, "utf8");
    let saved: FixtureState = JSON.parse(savedJson);
    assert.equal(saved.releases[0].draft, false);
    assert.equal(saved.releases[0].prerelease, false);
    assert.deepEqual(saved.publication, { draft: "false", prerelease: "false", make_latest: "true" });
    assert.deepEqual(saved.events, ["publish 10"]);
    await finalizeRelease(tag, info);
    savedJson = await readFile(statePath, "utf8");
    saved = JSON.parse(savedJson);
    assert.deepEqual(saved.events, ["publish 10"]);
    await assert.rejects(replaceDraft(tag, info, directory, "new notes"), /never be replaced/);

    for (const failure of ["sha", "missing", "corrupt"]) {
      const altered = structuredClone(state);
      if (failure === "sha") altered.sha = "2".repeat(40);
      if (failure === "missing") altered.releases[0].assets.pop();
      if (failure === "corrupt") altered.releases[0].assets[0].content = "corrupt upload";
      await writeFile(statePath, JSON.stringify(altered));
      await assert.rejects(finalizeRelease(tag, info));
      savedJson = await readFile(statePath, "utf8");
      saved = JSON.parse(savedJson);
      assert.equal(saved.releases[0].draft, true);
      assert.deepEqual(saved.events, []);
    }

    const partial = structuredClone(state);
    partial.releases[0].assets = [{ id: 9, name: "build-info.json", content: "{truncated upload", size: 17, state: "starter" }];
    await writeFile(statePath, JSON.stringify(partial));
    const pending = await loadState();
    assert.equal(pending[0].published, false);
    assert.equal(pending[0].buildInfo, undefined);
    const publishedOnly = await loadState(true);
    assert.deepEqual(publishedOnly, []);
    await replaceDraft(tag, info, directory, "notes");
    await finalizeRelease(tag, info);
    savedJson = await readFile(statePath, "utf8");
    saved = JSON.parse(savedJson);
    assert.deepEqual(saved.events, ["delete 9", "upload 5", "publish 10"]);
    const recoveredNames = saved.releases[0].assets.map((asset) => asset.name);
    recoveredNames.sort();
    const expectedNames = names.toSorted();
    assert.deepEqual(recoveredNames, expectedNames);

    const orphan: FixtureState = { sha: info.commitSha, releases: [], events: [] };
    await writeFile(statePath, JSON.stringify(orphan));
    await replaceDraft(tag, info, directory, "recovered notes");
    await finalizeRelease(tag, info);
    savedJson = await readFile(statePath, "utf8");
    saved = JSON.parse(savedJson);
    assert.deepEqual(saved.events, [`create ${tag}`, "upload 5", "publish 10"]);
    assert.equal(saved.releases[0].body, "recovered notes");
  } finally {
    process.env.PATH = originalPath;
    delete process.env.PP_TEST_GITHUB_STATE;
    await rm(directory, { recursive: true, force: true });
  }
});
