import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import { parse } from "yaml";
import { releaseOptions } from "../src/config.ts";

test("workflow keeps fork PRs away from production secrets and serializes release publication", async () => {
  const source = await readFile(new URL("../../.github/workflows/ci.yml", import.meta.url), "utf8");
  const workflow = parse(source);
  assert.deepEqual(workflow.permissions, { contents: "read" });
  assert.ok(workflow.on.pull_request);
  assert.equal(workflow.on.pull_request_target, undefined);
  assert.equal(workflow.concurrency, undefined);
  const preview = workflow.jobs["android-test"];
  assert.equal(preview.environment, undefined);
  assert.equal(preview.permissions, undefined);
  assert.equal(preview.concurrency["cancel-in-progress"], true);
  assert.match(preview.if, /pull_request/);
  assert.doesNotMatch(JSON.stringify(preview), /secrets\./);
  const uploads = preview.steps.filter((step: { uses?: string }) => step.uses?.startsWith("actions/upload-artifact@"));
  assert.equal(uploads.length, 2);
  for (const upload of uploads) {
    assert.equal(upload.with["retention-days"], "${{ github.event_name == 'pull_request' && 7 || 90 }}");
    assert.equal(upload.with["if-no-files-found"], "error");
  }
  const production = workflow.jobs["android-production"];
  assert.equal(production.environment, "android-production");
  assert.deepEqual(production.permissions, { contents: "write" });
  assert.deepEqual(production.concurrency, { group: "android-production", queue: "max", "cancel-in-progress": false });
  assert.equal(production.needs, "node");
  for (const android of [preview, production]) {
    const checkout = android.steps.findIndex((step: { uses?: string }) => step.uses?.startsWith("actions/checkout@"));
    const install = android.steps.findIndex((step: { run?: string }) => step.run === "pnpm --dir ci install --frozen-lockfile");
    assert.ok(checkout >= 0 && checkout < install, "full checkout must precede dependency installation");
    assert.equal(android.steps[checkout].with["fetch-depth"], 0);
    assert.equal(android.steps[checkout].with["persist-credentials"], false);
  }
  assert.deepEqual(workflow.jobs.node.strategy.matrix.project, ["worker", "site", "ci"]);
  assert.match(production.if, /refs\/heads\/master/);
});

test("semantic-release publishes only a draft without npm, comments, issues, or labels", () => {
  const options = releaseOptions(3, "1".repeat(40), false);
  assert.deepEqual(options.branches, ["master"]);
  assert.equal(options.tagFormat, "v${version}");
  const plugins = options.plugins!;
  assert.equal(plugins.length, 4);
  assert.doesNotMatch(JSON.stringify(plugins), /@semantic-release\/npm/);
  const github = plugins.at(-1);
  assert.ok(Array.isArray(github));
  assert.equal(github[1].draftRelease, true);
  assert.equal(github[1].successCommentCondition, false);
  assert.equal(github[1].failCommentCondition, false);
  assert.equal(github[1].releasedLabels, false);
  assert.equal(github[1].labels, false);
});
