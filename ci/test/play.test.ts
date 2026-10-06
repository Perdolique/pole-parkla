import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { mock, test } from "node:test";
import { parse } from "yaml";
import * as v from "valibot";
import { PlayNotesSchema, uploadPlayDraft } from "../src/play.ts";
import { readPlayNotes } from "../src/play-notes.ts";

interface FixtureBundle {
  versionCode: number;
  sha256: string;
}

interface FixtureEdit {
  releases: Record<string, unknown>[];
  bundles: FixtureBundle[];
}

test("Play upload keeps active releases, commits only a translated draft, and handles retries and collisions", async () => {
  const directory = await mkdtemp("/tmp/pole-parkla-play-test-");
  const path = join(directory, "app.aab");
  const info = { versionName: "1.1.0", versionCode: 3, commitSha: "1".repeat(40) };
  const notes = { et: "Lisatud GitHubi lingid.", "en-US": "Added GitHub links.", "ru-RU": "Добавлены ссылки на GitHub." };
  const active = { name: "1.0.0", status: "completed", versionCodes: ["2"], inAppUpdatePriority: 3 };
  const expectedNotes = [
    { language: "et", text: notes.et },
    { language: "en-US", text: notes["en-US"] },
    { language: "ru-RU", text: notes["ru-RU"] },
  ];
  let releases: Record<string, unknown>[] = [active];
  let bundles: FixtureBundle[] = [];
  let uploadedDigest = "";
  const edits = new Map<string, FixtureEdit>();
  const events: string[] = [];
  let nextEdit = 0;
  let rejectCommit = false;
  let corruptUpload = false;
  const fetchMock = mock.method(globalThis, "fetch", async (input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(String(input));
    assert.equal(url.hostname, "androidpublisher.googleapis.com");
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("Authorization"), "Bearer test-token");
    const method = init?.method ?? "GET";
    const endpoint = url.pathname.split("/edits")[1];
    if (endpoint === "" && method === "POST") {
      const id = String(++nextEdit);
      edits.set(id, structuredClone({ releases, bundles }));
      return Response.json({ id });
    }
    const match = /^\/([^/:]+)(.*)$/.exec(endpoint);
    assert.ok(match, `Unexpected Play request: ${method} ${url}`);
    const [, id, resource] = match;
    const edit = edits.get(id);
    assert.ok(edit, "Every operation must use an open edit");
    if (resource === "" && method === "DELETE") {
      edits.delete(id);
      return new Response(null, { status: 204 });
    }
    if (resource === "/tracks/production" && method === "GET") return Response.json({ track: "production", releases: edit.releases });
    if (resource === "/bundles" && method === "GET") return Response.json({ bundles: edit.bundles });
    if (resource === "/bundles" && method === "POST") {
      assert.equal(url.searchParams.get("uploadType"), "media");
      assert.equal(headers.get("Content-Type"), "application/octet-stream");
      assert.ok(init?.body instanceof Blob);
      const bytes = await init.body.arrayBuffer();
      const digest = createHash("sha256").update(new Uint8Array(bytes)).digest("hex");
      uploadedDigest = digest;
      const bundle = { versionCode: 3, sha256: corruptUpload ? "f".repeat(64) : digest };
      edit.bundles.push(bundle);
      events.push("upload");
      return Response.json(bundle);
    }
    if (resource === "/tracks/production" && method === "PUT") {
      assert.equal(typeof init?.body, "string");
      const track = JSON.parse(String(init?.body));
      assert.equal(track.track, "production");
      assert.deepEqual(track.releases[0], active, "Keep every field on the existing production release");
      assert.deepEqual(track.releases[1], { name: "1.1.0", status: "draft", versionCodes: ["3"], releaseNotes: expectedNotes });
      edit.releases = track.releases;
      events.push("draft");
      return Response.json(track);
    }
    if (resource === ":commit" && method === "POST") {
      assert.equal(url.searchParams.get("changesInReviewBehavior"), "ERROR_IF_IN_REVIEW");
      assert.equal(url.searchParams.has("changesNotSentForReview"), false);
      if (rejectCommit) return Response.json({ error: { message: "CHANGES_ALREADY_IN_REVIEW" } }, { status: 400 });
      releases = edit.releases;
      bundles = edit.bundles;
      edits.delete(id);
      events.push("commit");
      return Response.json({ id });
    }
    assert.fail(`Unexpected Play request: ${method} ${url}`);
  });
  try {
    await writeFile(path, "verified-aab-bytes");
    assert.equal(await uploadPlayDraft("test-token", path, info, notes), "draft");
    assert.deepEqual(events, ["upload", "draft", "commit"]);
    assert.equal(edits.size, 0, "Readback edits must be discarded");
    assert.equal(await uploadPlayDraft("test-token", path, info, notes), "draft");
    assert.deepEqual(events, ["upload", "draft", "commit", "draft", "commit"], "Retry must reuse the uploaded bundle");

    releases[1].status = "completed";
    const beforeReleased = events.length;
    assert.equal(await uploadPlayDraft("test-token", path, info, notes), "already-released");
    assert.equal(events.length, beforeReleased, "An approved or rolled-out version must not be changed");
    assert.equal(edits.size, 0);

    bundles = [{ versionCode: 3, sha256: "a".repeat(64) }];
    await assert.rejects(uploadPlayDraft("test-token", path, info, notes), /different bytes/);
    assert.equal(events.length, beforeReleased);

    bundles = [{ versionCode: 3, sha256: uploadedDigest }];
    releases = [active, { status: "draft", versionCodes: ["1"] }];
    await assert.rejects(uploadPlayDraft("test-token", path, info, notes), /Another Google Play draft/);
    assert.equal(events.length, beforeReleased, "Do not overwrite a different pending draft");

    releases = [{ ...active, versionCodes: ["4"] }];
    await assert.rejects(uploadPlayDraft("test-token", path, info, notes), /newer production version/);
    assert.equal(events.length, beforeReleased);

    releases = [active];
    bundles = [];
    corruptUpload = true;
    await assert.rejects(uploadPlayDraft("test-token", path, info, notes), /does not match the verified/);
    assert.equal(events.at(-1), "upload");
    assert.equal(edits.size, 0);
    corruptUpload = false;
    rejectCommit = true;
    await assert.rejects(uploadPlayDraft("test-token", path, info, notes), /HTTP 400.*CHANGES_ALREADY_IN_REVIEW/);
    assert.equal(releases.length, 1, "A failed edit must not alter production");
    assert.equal(edits.size, 0);
    await assert.rejects(uploadPlayDraft("", path, info, notes), /access token is missing/);
  } finally {
    fetchMock.mock.restore();
    await rm(directory, { recursive: true, force: true });
  }
});

test("missing reviewed notes stop Android changes before upload but allow build-only updates", async () => {
  const directory = await mkdtemp("/tmp/pole-parkla-play-notes-test-");
  const originalPath = process.env.PATH;
  const script = join(directory, "git");
  const info = { versionName: "999.0.1", versionCode: 4, commitSha: "2".repeat(40) };
  const tags = "#!/bin/sh\nif [ \"$1\" = tag ]; then printf 'v999.0.0\\n'; else ";
  try {
    process.env.PATH = `${directory}:${originalPath}`;
    await writeFile(script, `${tags}printf 'app/src/main/Settings.kt\\n'; fi\n`, { mode: 0o700 });
    await assert.rejects(readPlayNotes(info), /Add reviewed et, en-US, and ru-RU notes/);
    await writeFile(script, `${tags}exit 0; fi\n`, { mode: 0o700 });
    const notes = await readPlayNotes(info);
    assert.deepEqual(Object.keys(notes), ["et", "en-US", "ru-RU"]);
    assert.equal(notes["en-US"], "Updated the app build and release process.");
  } finally {
    process.env.PATH = originalPath;
    await rm(directory, { recursive: true, force: true });
  }
});

test("release notes include every app language and count Unicode characters", async () => {
  const source = await readFile(new URL("../play-notes/1.1.0.json", import.meta.url), "utf8");
  const raw = JSON.parse(source);
  const notes = v.parse(PlayNotesSchema, raw);
  assert.deepEqual(Object.keys(notes), ["et", "en-US", "ru-RU"]);
  assert.match(notes["en-US"], /source code.*GitHub Issues/);
  assert.match(notes.et, /lähtekoodile.*GitHub Issuesi/);
  assert.match(notes["ru-RU"], /исходный код.*GitHub Issues/);
  for (const invalid of [{ ...notes, et: "" }, { ...notes, "en-US": "x".repeat(501) }, { et: notes.et, "en-US": notes["en-US"] }]) {
    assert.throws(() => v.parse(PlayNotesSchema, invalid));
  }
  assert.equal(v.parse(PlayNotesSchema, { ...notes, et: "😎".repeat(500) }).et.length, 1000);
});

test("Play workflow uses trusted master code and short-lived credentials only after asset checks", async () => {
  const source = await readFile(new URL("../../.github/workflows/google-play.yml", import.meta.url), "utf8");
  const workflow = parse(source);
  assert.deepEqual(workflow.permissions, { contents: "read" });
  assert.equal(workflow.on.pull_request, undefined);
  assert.equal(workflow.on.pull_request_target, undefined);
  assert.deepEqual(workflow.on.workflow_run.workflows, ["CI"]);
  const job = workflow.jobs.draft;
  assert.match(job.if, /workflow_run\.event == 'push'/);
  assert.match(job.if, /workflow_run\.conclusion == 'success'/);
  assert.match(job.if, /refs\/heads\/master/);
  assert.equal(job.environment, "google-play");
  assert.deepEqual(job.permissions, { contents: "read", "id-token": "write" });
  assert.equal(job.concurrency["cancel-in-progress"], false);
  const checkout = job.steps.find((step: { uses?: string }) => step.uses?.startsWith("actions/checkout@"));
  assert.equal(checkout.with.ref, "master");
  assert.equal(checkout.with["persist-credentials"], false);
  const prepare = job.steps.findIndex((step: { id?: string }) => step.id === "prepare");
  const auth = job.steps.findIndex((step: { id?: string }) => step.id === "auth");
  assert.ok(prepare >= 0 && prepare < auth);
  assert.equal(job.steps[auth].uses, "google-github-actions/auth@v3");
  assert.equal(job.steps[auth].with.access_token_scopes, "https://www.googleapis.com/auth/androidpublisher");
  assert.equal(job.steps[auth].with.create_credentials_file, false);
  assert.equal(job.steps[auth].with.access_token_lifetime, "600s");
  assert.doesNotMatch(source, /secrets\.|credentials_json|head_sha/);
});
