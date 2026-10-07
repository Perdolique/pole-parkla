import type { PrepareContext, VerifyReleaseContext } from "semantic-release";
import * as v from "valibot";
import { buildAndroid } from "./android.ts";
import { BuildInfoSchema } from "./model.ts";
import { readPlayNotes } from "./play-notes.ts";

export interface AdapterOptions {
  androidVersionCode: number;
  androidCommitSha: string;
}

export async function verifyRelease(config: AdapterOptions, context: VerifyReleaseContext): Promise<void> {
  const info = v.parse(BuildInfoSchema, {
    versionName: context.nextRelease.version,
    versionCode: config.androidVersionCode,
    commitSha: config.androidCommitSha,
  });
  if (context.nextRelease.gitHead !== info.commitSha) throw new Error("semantic-release SHA does not match the Android build.");
  await readPlayNotes(info, context.cwd);
}

export async function prepare(config: AdapterOptions, context: PrepareContext): Promise<void> {
  const info = v.parse(BuildInfoSchema, {
    versionName: context.nextRelease.version,
    versionCode: config.androidVersionCode,
    commitSha: config.androidCommitSha,
  });
  if (context.nextRelease.gitHead !== info.commitSha) throw new Error("semantic-release SHA does not match the Android build.");
  await buildAndroid("production", info);
}
