import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { pipeline } from "node:stream/promises";
import * as v from "valibot";
import { assetNames, baseline, BuildInfoSchema, versionFromTag } from "./model.ts";
import type { BuildInfo, ReleaseState } from "./model.ts";
import { capture, root } from "./process.ts";
import { verifyArtifactSet } from "./artifacts.ts";

export const repository = "Perdolique/pole-parkla";
const apiRoot = `repos/${repository}`;
const AssetSchema = v.object({
  id: v.number(), name: v.string(), size: v.number(), state: v.string(),
});
const ReleaseSchema = v.object({
  id: v.number(), tag_name: v.string(), draft: v.boolean(), prerelease: v.boolean(),
  body: v.nullable(v.string()), assets: v.array(AssetSchema),
});
export type GithubRelease = v.InferOutput<typeof ReleaseSchema>;

export async function listReleases(): Promise<GithubRelease[]> {
  const json = await capture("gh", ["api", "--paginate", "--slurp", `${apiRoot}/releases?per_page=100`]);
  const parsed = JSON.parse(json);
  const pages = v.parse(v.array(v.array(ReleaseSchema)), parsed);
  const releases = pages.flat();
  return releases.filter((release) => !release.prerelease && versionFromTag(release.tag_name));
}

// Keep a verified published build for the Play uploader; never rebuild a release.
export async function downloadPublishedRelease(tag: string, directory: string): Promise<BuildInfo> {
  const version = versionFromTag(tag);
  if (!version) throw new Error("Google Play requires a stable release tag.");
  const releases = await listReleases();
  const release = releases.find((item) => item.tag_name === tag);
  if (!release || release.draft) throw new Error(`Published GitHub release ${tag} does not exist.`);
  const expectedNames = assetNames(version);
  for (const asset of release.assets) {
    if (asset.state !== "uploaded" || asset.size === 0 || !expectedNames.includes(asset.name)) {
      throw new Error(`Unexpected or unfinished release asset: ${asset.name}`);
    }
    const path = join(directory, asset.name);
    await downloadAsset(asset.id, path);
  }
  const metadataPath = join(directory, "build-info.json");
  const rawInfo = await readFile(metadataPath, "utf8");
  const parsedInfo = JSON.parse(rawInfo);
  const info = v.parse(BuildInfoSchema, parsedInfo);
  const sha = await remoteTagSha(tag);
  if (info.versionName !== version || info.commitSha !== sha) {
    throw new Error("Published build does not match the requested version and remote tag.");
  }
  const names = release.assets.map((asset) => asset.name);
  await verifyArtifactSet(directory, info, names);
  return info;
}

async function remoteTagSha(tag: string): Promise<string> {
  const sha = await capture("gh", ["api", `${apiRoot}/commits/${tag}`, "--jq", ".sha"]);
  return v.parse(v.pipe(v.string(), v.regex(/^[a-f0-9]{40}$/)), sha);
}

// An automatic Play upload belongs to the CI commit, not the latest release.
export async function publishedReleaseTagForCommit(commitSha: string): Promise<string | null> {
  const expectedSha = v.parse(BuildInfoSchema.entries.commitSha, commitSha);
  const releases = await listReleases();
  for (const release of releases) {
    if (release.draft) continue;
    const sha = await remoteTagSha(release.tag_name);
    if (sha === expectedSha) return release.tag_name;
  }
  return null;
}

export async function loadState(publishedOnly = false): Promise<ReleaseState[]> {
  const baselineSha = await capture("git", ["rev-parse", `${baseline.tag}^{commit}`]);
  if (baselineSha !== baseline.commitSha) throw new Error("Baseline v1.0.0 does not point to the approved commit.");
  const tagOutput = await capture("git", ["tag", "--list", "v*"]);
  const tagNames = tagOutput.split("\n");
  const stableTags = tagNames.filter((tag) => versionFromTag(tag) && tag !== baseline.tag);
  const allReleases = await listReleases();
  const releases = publishedOnly ? allReleases.filter((release) => !release.draft) : allReleases;
  const tags = publishedOnly ? stableTags.filter((tag) => releases.some((release) => release.tag_name === tag)) : stableTags;
  const states: ReleaseState[] = [];
  for (const release of releases) {
    if (!tags.includes(release.tag_name)) throw new Error(`Release ${release.tag_name} has no fetched stable tag.`);
  }
  for (const tag of tags) {
    const commitSha = await capture("git", ["rev-parse", `${tag}^{commit}`]);
    const release = releases.find((item) => item.tag_name === tag);
    let buildInfo: BuildInfo | undefined;
    const metadata = release?.assets.find((asset) => asset.name === "build-info.json");
    // A draft can contain a failed or truncated metadata upload. Its Android number
    // is rebuilt from the previous published release; only published metadata is trusted.
    if (metadata && release && !release.draft) {
      const json = await capture("gh", ["api", `${apiRoot}/releases/assets/${metadata.id}`, "-H", "Accept: application/octet-stream"]);
      const parsed = JSON.parse(json);
      buildInfo = v.parse(BuildInfoSchema, parsed);
      const version = tag.slice(1);
      if (buildInfo.versionName !== version || buildInfo.commitSha !== commitSha) {
        throw new Error(`${tag} build-info.json does not match its version and commit.`);
      }
    }
    if (release && !release.draft && !buildInfo) throw new Error(`Published ${tag} is missing build-info.json.`);
    const published = Boolean(release && !release.draft);
    states.push({ tag, commitSha, published, buildInfo });
  }
  return states;
}

async function downloadAsset(assetId: number, path: string): Promise<void> {
  const child = spawn("gh", ["api", `${apiRoot}/releases/assets/${assetId}`, "-H", "Accept: application/octet-stream"], { cwd: root, stdio: ["ignore", "pipe", "inherit"] });
  const exited = new Promise<void>((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(`Asset download failed: exit ${code}`)));
  });
  const output = createWriteStream(path, { flags: "wx", mode: 0o600 });
  await Promise.all([pipeline(child.stdout, output), exited]);
}

export async function verifyGithubRelease(tag: string, expected: BuildInfo, stagedDirectory?: string): Promise<GithubRelease> {
  const releases = await listReleases();
  const release = releases.find((item) => item.tag_name === tag);
  if (!release) throw new Error(`GitHub release ${tag} does not exist.`);
  const sha = await remoteTagSha(tag);
  if (sha !== expected.commitSha) throw new Error("Remote tag does not match the build SHA.");
  const names = release.assets.map((asset) => asset.name);
  names.sort();
  const wanted = assetNames(expected.versionName);
  wanted.sort();
  const actualSet = JSON.stringify(names);
  const expectedSet = JSON.stringify(wanted);
  if (actualSet !== expectedSet) throw new Error("GitHub release attachment set is incomplete or unexpected.");
  const temporaryPrefix = join(process.env.RUNNER_TEMP ?? "/tmp", "pole-parkla-verify-");
  const directory = await mkdtemp(temporaryPrefix);
  try {
    for (const asset of release.assets) {
      if (asset.state !== "uploaded" || asset.size === 0) throw new Error(`GitHub attachment is not ready: ${asset.name}`);
      const path = join(directory, asset.name);
      await downloadAsset(asset.id, path);
    }
    await verifyArtifactSet(directory, expected, names);
    if (stagedDirectory) {
      const uploadedPath = join(directory, "SHA256SUMS");
      const stagedPath = join(stagedDirectory, "SHA256SUMS");
      const uploadedSums = await readFile(uploadedPath, "utf8");
      const stagedSums = await readFile(stagedPath, "utf8");
      if (uploadedSums !== stagedSums) throw new Error("Uploaded checksums do not match the staged Android build.");
    }
    return release;
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

export async function finalizeRelease(tag: string, expected: BuildInfo, stagedDirectory?: string): Promise<void> {
  const release = await verifyGithubRelease(tag, expected, stagedDirectory);
  if (!release.draft) return;
  await capture("gh", ["api", "--method", "PATCH", `${apiRoot}/releases/${release.id}`, "-F", "draft=false", "-F", "prerelease=false", "-f", "make_latest=true"]);
  console.log(`Published https://github.com/${repository}/releases/tag/${tag}`);
}

export async function replaceDraft(tag: string, info: BuildInfo, directory: string, notes: string): Promise<void> {
  const releases = await listReleases();
  let release = releases.find((item) => item.tag_name === tag);
  if (release && !release.draft) throw new Error("Published release files must never be replaced.");
  const sha = await remoteTagSha(tag);
  if (sha !== info.commitSha) throw new Error("Recovery tag does not match checkout SHA.");
  const names = assetNames(info.versionName);
  await verifyArtifactSet(directory, info, names);
  if (!release) {
    const notesPath = join(directory, "release-notes.md");
    await writeFile(notesPath, notes);
    try {
      await capture("gh", ["release", "create", tag, "--repo", repository, "--verify-tag", "--draft", "--title", tag, "--notes-file", notesPath]);
    } finally {
      await rm(notesPath, { force: true });
    }
    const updated = await listReleases();
    release = updated.find((item) => item.tag_name === tag);
  }
  if (!release?.draft) throw new Error("Recovery draft was not created.");
  // Remove all old assets, including unexpected names, before uploading one coherent build.
  for (const asset of release.assets) {
    await capture("gh", ["api", "--method", "DELETE", `${apiRoot}/releases/assets/${asset.id}`]);
  }
  const paths = names.map((name) => join(directory, name));
  await capture("gh", ["release", "upload", tag, ...paths, "--repo", repository]);
}
