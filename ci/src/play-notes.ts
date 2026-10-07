import { readFile } from "node:fs/promises";
import { join } from "node:path";
import * as v from "valibot";
import semver from "semver";
import { capture, readCommits, root } from "./process.ts";
import { baseline, versionFromTag } from "./model.ts";
import type { BuildInfo, ReleaseState } from "./model.ts";
import { analyzeReleaseType } from "./analyzer.ts";
import { PlayNotesSchema } from "./play.ts";
import type { PlayNotes } from "./play.ts";

export async function readPlayNotes(info: BuildInfo, cwd = root): Promise<PlayNotes> {
  const path = join(cwd, "ci", "play-notes", `${info.versionName}.json`);
  let source: string;
  try {
    source = await readFile(path, "utf8");
  } catch (error) {
    if (!(error instanceof Error) || !("code" in error) || error.code !== "ENOENT") throw error;
    const rawTags = await capture("git", ["tag", "--list", "v*"], cwd);
    const tags = rawTags.split("\n");
    const stableVersions = tags.map(versionFromTag);
    const versions = stableVersions.filter((version): version is string => version !== null && semver.lt(version, info.versionName));
    versions.sort(semver.rcompare);
    const previousTag = versions[0] ? `v${versions[0]}` : baseline.tag;
    const range = `${previousTag}..${info.commitSha}`;
    const changed = await capture("git", ["diff", "--name-only", range, "--", "app", "gradle", "gradle.properties", "build.gradle.kts", "settings.gradle.kts"], cwd);
    if (changed) throw new Error(`Add reviewed et, en-US, and ru-RU notes in ci/play-notes/${info.versionName}.json for this Android update.`);
    return {
      et: "Uuendatud on rakenduse koostamise ja väljalaskmise protsessi.",
      "en-US": "Updated the app build and release process.",
      "ru-RU": "Обновлён процесс сборки и выпуска приложения.",
    };
  }
  const parsed = JSON.parse(source);
  return v.parse(PlayNotesSchema, parsed);
}

// PRs forecast with the same commit policy; production verifies the actual version.
export async function checkNextPlayNotes(previous: ReleaseState | undefined, commitSha: string, cwd = root): Promise<BuildInfo> {
  const previousTag = previous?.tag ?? baseline.tag;
  const previousVersion = previous?.buildInfo?.versionName ?? baseline.version;
  const previousCode = previous?.buildInfo?.versionCode ?? baseline.versionCode;
  const commits = await readCommits(previousTag, commitSha, cwd);
  const context = { cwd, logger: console, commits };
  const releaseType = await analyzeReleaseType(context);
  const versionName = releaseType ? semver.inc(previousVersion, releaseType) : previousVersion;
  if (!versionName) throw new Error("Cannot calculate the next Google Play version.");
  const versionCode = releaseType ? previousCode + 1 : previousCode;
  const info = { versionName, versionCode, commitSha };
  await readPlayNotes(info, cwd);
  return info;
}
