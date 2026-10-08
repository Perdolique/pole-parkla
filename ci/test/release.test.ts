import assert from "node:assert/strict";
import { test } from "node:test";
import { baseline, decideRelease, testBuildInfo } from "../src/model.ts";
import type { BuildInfo, ReleaseState } from "../src/model.ts";
import { releaseAndroid } from "../src/release.ts";
import type { ReleaseServices } from "../src/release.ts";

const firstSha = "1".repeat(40);
const nextSha = "2".repeat(40);
const firstInfo: BuildInfo = { versionName: "1.0.1", versionCode: 3, commitSha: firstSha };
const published: ReleaseState = { tag: "v1.0.1", commitSha: firstSha, published: true, buildInfo: firstInfo };

function services(states: ReleaseState[], events: string[], version: string | null = "1.0.2"): ReleaseServices {
  return {
    readState: async () => states,
    readPlayNotes: async (info) => {
      events.push(`notes ${info.versionName}`);
      return { et: "Uuendus", "en-US": "Update", "ru-RU": "Обновление" };
    },
    isAncestor: async (older, newer) => older === firstSha && newer === nextSha,
    verifyPublished: async (tag) => { events.push(`verify ${tag}`); },
    recover: async (tag, info, previous) => { events.push(`recover ${tag} ${info.versionCode} after ${previous}`); },
    semantic: async (code, dryRun) => { events.push(`semantic ${code} dry=${dryRun}`); return version; },
    finalize: async (tag) => { events.push(`finalize ${tag}`); },
    build: async (info) => { events.push(`build ${info.versionName} ${info.versionCode}`); },
  };
}

test("baseline starts at Android 3; each published release adds one", async () => {
  const initial = await decideRelease([], firstSha, async () => false);
  assert.equal(initial.version, "1.0.0");
  assert.equal(initial.versionCode, 3);
  assert.equal(initial.tag, baseline.tag);
  const next = await decideRelease([published], nextSha, async () => true);
  assert.equal(next.versionCode, 4);
  assert.equal(next.action, "new");
});

test("PR and manual test versions use run_number and distinct suffixes", () => {
  assert.deepEqual(testBuildInfo("1.2.3", firstSha, 42, 9), { versionName: "1.2.3-pr.9.42", versionCode: 42, commitSha: firstSha });
  assert.equal(testBuildInfo("1.2.3", firstSha, 43).versionName, "1.2.3-ci.43");
  assert.throws(() => testBuildInfo("1.2.3", firstSha, 0));
});

test("published SHA is verified and never rebuilt, retagged, or overwritten", async () => {
  const events: string[] = [];
  const result = await releaseAndroid(services([published], events), firstSha, "publish");
  assert.deepEqual(result, firstInfo);
  assert.deepEqual(events, ["verify v1.0.1"]);
});

test("an unfinished tag reuses the version and finishes in order", async () => {
  const pending: ReleaseState = { tag: "v1.0.2", commitSha: nextSha, published: false };
  const events: string[] = [];
  const result = await releaseAndroid(services([published, pending], events), nextSha, "publish");
  assert.equal(result?.versionName, "1.0.2");
  assert.equal(result?.versionCode, 4);
  assert.deepEqual(events, ["notes 1.0.2", "recover v1.0.2 4 after v1.0.1", "finalize v1.0.2"]);
});

test("recovery cannot rebuild or publish without valid Play notes", async () => {
  const pending: ReleaseState = { tag: "v1.0.2", commitSha: nextSha, published: false };
  for (const mode of ["publish", "dry-run", "build"] as const) {
    const events: string[] = [];
    const api = services([published, pending], events);
    api.readPlayNotes = async () => { throw new Error("missing reviewed Play notes"); };
    const rejected = releaseAndroid(api, nextSha, mode);
    await assert.rejects(rejected, /missing reviewed Play notes/);
    assert.deepEqual(events, []);
  }
});

test("unfinished other SHA, unrelated history, and inconsistent metadata block all writes", async () => {
  const pending: ReleaseState = { tag: "v1.0.2", commitSha: nextSha, published: false };
  for (const [states, sha] of [
    [[published, pending], firstSha],
    [[published], "3".repeat(40)],
    [[{ ...published, buildInfo: { ...firstInfo, versionCode: 99 } }], nextSha],
  ] as [ReleaseState[], string][]) {
    const events: string[] = [];
    await assert.rejects(releaseAndroid(services(states, events), sha, "publish"));
    assert.deepEqual(events, []);
  }
  const conflictEvents: string[] = [];
  const conflict = services([published, pending], conflictEvents);
  conflict.isAncestor = async () => true;
  await assert.rejects(releaseAndroid(conflict, "3".repeat(40), "publish"), /Unfinished v1.0.2/);
  assert.deepEqual(conflictEvents, []);
});

test("late SHA cannot publish after a newer release", async () => {
  const newer: ReleaseState = { tag: "v1.0.2", commitSha: nextSha, published: true, buildInfo: { versionName: "1.0.2", versionCode: 4, commitSha: nextSha } };
  const events: string[] = [];
  const result = await releaseAndroid(services([published, newer], events), firstSha, "publish");
  assert.equal(result, null);
  assert.deepEqual(events, []);
});

test("dry-run skips recovery/build/finalize; manual build uses next or last version", async () => {
  const pending: ReleaseState = { tag: "v1.0.2", commitSha: nextSha, published: false };
  const dryEvents: string[] = [];
  const dryInfo = await releaseAndroid(services([published, pending], dryEvents), nextSha, "dry-run");
  assert.equal(dryInfo?.versionName, "1.0.2");
  assert.deepEqual(dryEvents, ["notes 1.0.2"]);
  for (const [version, name, code] of [["1.0.2", "1.0.2", 4], [null, "1.0.1", 3]] as const) {
    const events: string[] = [];
    await releaseAndroid(services([published], events, version), nextSha, "build");
    assert.deepEqual(events, ["semantic 4 dry=true", `build ${name} ${code}`]);
  }
});

test("no Android changes skip publication and dry-run without building, notes, or finalization", async () => {
  for (const mode of ["publish", "dry-run"] as const) {
    const events: string[] = [];
    const result = await releaseAndroid(services([published], events, null), nextSha, mode);
    assert.equal(result, null);
    assert.deepEqual(events, [`semantic 4 dry=${mode === "dry-run"}`]);
  }
});

test("a failed draft verification cannot report publication success", async () => {
  const events: string[] = [];
  const api = services([published], events);
  api.finalize = async () => { throw new Error("incomplete assets"); };
  await assert.rejects(releaseAndroid(api, nextSha, "publish"), /incomplete assets/);
  assert.deepEqual(events, ["semantic 4 dry=false"]);
});
