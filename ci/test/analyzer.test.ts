import assert from "node:assert/strict";
import { test } from "node:test";
import { analyzeCommits } from "../src/analyzer.ts";

test("release policy selects the largest increase, including fallback commits", async () => {
  const cases: [string[], string | null][] = [
    [["fix: repair drafts"], "patch"],
    [["feat: add preview builds"], "minor"],
    [["feat!: replace report storage"], "major"],
    [["fix: change format\n\nBREAKING CHANGE: old drafts need migration"], "major"],
    [["chore: change format\n\nBREAKING-CHANGE: old format removed"], "major"],
    [["docs: explain releases", "refactor: simplify signing", "test: check reports", "ci: build APK", "perf: reduce work", "revert: undo change", "build: update tooling", "style: format code"], "patch"],
    [["Merge pull request #42", "unstructured message"], "patch"],
    [["fix: repair drafts", "feat: add export", "docs: explain export"], "minor"],
    [["feat: add export", "chore!: remove old export"], "major"],
    [[], null],
  ];
  for (const [messages, expected] of cases) {
    const commits = messages.map((message, index) => ({ message, hash: String(index) }));
    const result = await analyzeCommits({}, { cwd: process.cwd(), logger: console, commits });
    assert.equal(result, expected, messages.join("; "));
  }
});
