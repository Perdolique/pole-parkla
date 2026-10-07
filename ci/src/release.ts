import { baseline, decideRelease } from "./model.ts";
import type { BuildInfo, ReleaseState } from "./model.ts";
import type { PlayNotes } from "./play.ts";

export interface ReleaseServices {
  readState: () => Promise<ReleaseState[]>;
  readPlayNotes: (info: BuildInfo) => Promise<PlayNotes>;
  isAncestor: (older: string, newer: string) => Promise<boolean>;
  verifyPublished: (tag: string, info: BuildInfo) => Promise<void>;
  recover: (tag: string, info: BuildInfo, previousTag: string) => Promise<void>;
  semantic: (versionCode: number, dryRun: boolean) => Promise<string | null>;
  finalize: (tag: string, info: BuildInfo) => Promise<void>;
  build: (info: BuildInfo) => Promise<void>;
}

export async function releaseAndroid(
  services: ReleaseServices,
  commitSha: string,
  mode: "publish" | "dry-run" | "build",
): Promise<BuildInfo | null> {
  const states = await services.readState();
  const decision = await decideRelease(states, commitSha, services.isAncestor);
  if (decision.action === "stale") {
    console.log(`Skip old SHA ${commitSha}; ${decision.tag} is already newer.`);
    if (mode === "build") throw new Error("Manual production build SHA is older than the latest release.");
    return null;
  }
  let info: BuildInfo = { versionName: decision.version, versionCode: decision.versionCode, commitSha };
  if (decision.action === "done") {
    await services.verifyPublished(decision.tag, info);
    console.log(`${decision.tag} is already published and complete.`);
  } else if (decision.action === "recover") {
    await services.readPlayNotes(info);
    if (mode === "publish") {
      const published = states.filter((state) => state.published);
      const previousCode = decision.versionCode - 1;
      const previous = published.find((state) => state.buildInfo?.versionCode === previousCode);
      await services.recover(decision.tag, info, previous?.tag ?? baseline.tag);
      await services.finalize(decision.tag, info);
    }
    const status = mode === "publish" ? "Recovered" : "Recoverable";
    console.log(`${status} ${decision.tag}, Android ${decision.versionCode}.`);
  } else {
    const dryRun = mode !== "publish";
    const version = await services.semantic(decision.versionCode, dryRun);
    if (version) {
      info = { versionName: version, versionCode: decision.versionCode, commitSha };
      const tag = `v${version}`;
      if (mode === "publish") await services.finalize(tag, info);
    } else {
      info = { versionName: decision.version, versionCode: decision.versionCode - 1, commitSha };
    }
  }
  if (mode === "build") await services.build(info);
  console.log(`${mode}: ${info.versionName}, Android versionCode ${info.versionCode}, SHA ${commitSha}`);
  return info;
}
