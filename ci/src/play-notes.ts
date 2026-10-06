import { readFile } from "node:fs/promises";
import { join } from "node:path";
import * as v from "valibot";
import semver from "semver";
import { capture, root } from "./process.ts";
import { baseline, versionFromTag } from "./model.ts";
import type { BuildInfo } from "./model.ts";
import { PlayNotesSchema } from "./play.ts";
import type { PlayNotes } from "./play.ts";

export async function readPlayNotes(info: BuildInfo): Promise<PlayNotes> {
  const path = join(root, "ci", "play-notes", `${info.versionName}.json`);
  let source: string;
  try {
    source = await readFile(path, "utf8");
  } catch (error) {
    if (!(error instanceof Error) || !("code" in error) || error.code !== "ENOENT") throw error;
    const rawTags = await capture("git", ["tag", "--list", "v*"]);
    const tags = rawTags.split("\n");
    const stableVersions = tags.map(versionFromTag);
    const versions = stableVersions.filter((version): version is string => version !== null && semver.lt(version, info.versionName));
    versions.sort(semver.rcompare);
    const previousTag = versions[0] ? `v${versions[0]}` : baseline.tag;
    const range = `${previousTag}..${info.commitSha}`;
    const changed = await capture("git", ["diff", "--name-only", range, "--", "app", "gradle", "gradle.properties", "build.gradle.kts", "settings.gradle.kts"]);
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
