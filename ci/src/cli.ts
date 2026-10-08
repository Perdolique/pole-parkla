import { appendFile } from "node:fs/promises";
import semanticRelease from "semantic-release";
import { generateNotes } from "@semantic-release/release-notes-generator";
import type { NotesContext } from "@semantic-release/release-notes-generator";
import * as v from "valibot";
import { buildAndroid, artifactDirectory } from "./android.ts";
import { baseline, latestPublished, testBuildInfo } from "./model.ts";
import type { BuildInfo } from "./model.ts";
import { capture, isAncestor, root } from "./process.ts";
import { readAndroidCommits } from "./android-changes.ts";
import { finalizeRelease, loadState, replaceDraft, verifyGithubRelease } from "./github.ts";
import { releaseOptions } from "./config.ts";
import { releaseAndroid } from "./release.ts";
import { checkNextPlayNotes, readPlayNotes } from "./play-notes.ts";

async function recoveryNotes(tag: string, info: BuildInfo, previousTag: string): Promise<string> {
  const commits = await readAndroidCommits(previousTag, info.commitSha);
  const previousSha = await capture("git", ["rev-parse", `${previousTag}^{commit}`]);
  // The notes generator only consumes these fields of the semantic-release context.
  const context: NotesContext = {
    cwd: root,
    options: { repositoryUrl: "https://github.com/Perdolique/pole-parkla.git" },
    logger: console,
    commits,
    lastRelease: { gitTag: previousTag, gitHead: previousSha },
    nextRelease: { version: info.versionName, gitTag: tag, gitHead: info.commitSha },
  };
  return generateNotes({ preset: "conventionalcommits" }, context);
}

async function main(): Promise<void> {
  const command = process.argv[2];
  const dryRun = process.argv.includes("--dry-run");
  if (!["release", "test", "production", "check-play-notes"].includes(command)) {
    throw new Error("Usage: node ci/src/cli.ts release [--dry-run] | test | production | check-play-notes");
  }
  const commitSha = await capture("git", ["rev-parse", "HEAD"]);
  if (command === "check-play-notes") {
    if (dryRun) throw new Error("--dry-run applies only to release.");
    const states = await loadState(true);
    const previous = latestPublished(states);
    const info = await checkNextPlayNotes(previous, commitSha);
    console.log(`Google Play version checked: ${info.versionName} (Android ${info.versionCode}). Notes are required only for a new Android release.`);
    return;
  }
  if (command === "test") {
    if (dryRun) throw new Error("--dry-run applies only to release.");
    const states = await loadState(true);
    const published = latestPublished(states);
    const version = published?.buildInfo?.versionName ?? baseline.version;
    const rawRunNumber = Number(process.env.GITHUB_RUN_NUMBER);
    const runNumber = v.parse(v.pipe(v.number(), v.integer(), v.minValue(1)), rawRunNumber);
    const prNumber = process.env.PP_PR_NUMBER ? Number(process.env.PP_PR_NUMBER) : undefined;
    if (prNumber !== undefined) v.parse(v.pipe(v.number(), v.integer(), v.minValue(1)), prNumber);
    const info = testBuildInfo(version, commitSha, runNumber, prNumber);
    await buildAndroid("pr", info);
    if (process.env.GITHUB_STEP_SUMMARY) {
      const summary = `Built Debug and Preview **${info.versionName}** (Android ${info.versionCode}). Both use the public test key. Download the two APK artifacts from this run.\n`;
      await appendFile(process.env.GITHUB_STEP_SUMMARY, summary);
    }
    return;
  }
  if (process.env.GITHUB_ACTIONS === "true" && process.env.GITHUB_REF !== "refs/heads/master") {
    throw new Error("Production release and manual production builds require master.");
  }
  const mode = command === "production" ? "build" : dryRun ? "dry-run" : "publish";
  const info = await releaseAndroid({
    readState: loadState,
    readPlayNotes,
    isAncestor,
    verifyPublished: async (tag, expected) => { await verifyGithubRelease(tag, expected); },
    recover: async (tag, expected, previousTag) => {
      await buildAndroid("production", expected);
      const notes = await recoveryNotes(tag, expected, previousTag);
      await replaceDraft(tag, expected, artifactDirectory, notes);
    },
    semantic: async (versionCode, dry) => {
      const options = releaseOptions(versionCode, commitSha, dry);
      const result = await semanticRelease(options, { cwd: root });
      return result ? result.nextRelease.version : null;
    },
    finalize: async (tag, expected) => { await finalizeRelease(tag, expected, artifactDirectory); },
    build: async (expected) => { await buildAndroid("production", expected); },
  }, commitSha, mode);
  if (info && process.env.GITHUB_STEP_SUMMARY) {
    const status = command === "production" ? "Manual build: no publication." : "Release state verified.";
    const summary = `Android **${info.versionName}** (${info.versionCode}), commit \`${info.commitSha}\`. ${status}\n`;
    await appendFile(process.env.GITHUB_STEP_SUMMARY, summary);
  } else if (process.env.GITHUB_STEP_SUMMARY) {
    await appendFile(process.env.GITHUB_STEP_SUMMARY, "Android release skipped. No new version was published.\n");
  }
}

try {
  await main();
} catch (error) {
  console.error(error);
  process.exitCode = 1;
}
