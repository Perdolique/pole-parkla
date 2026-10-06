import { chmod, copyFile, mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { spawn } from "node:child_process";
import { join } from "node:path";
import semver from "semver";
import { assetNames, BuildInfoSchema } from "./model.ts";
import type { BuildInfo } from "./model.ts";
import * as v from "valibot";
import { capture, root, run } from "./process.ts";
import { sha256, verifyArtifactSet, writeChecksums } from "./artifacts.ts";

export const productionCertificate = "34dae8a3b1c8944c259f9856d26c3b5dd10a2a78aba671503e8b054aacf51548";
export const testCertificate = "e118c6b7422142e17a14dfdfc97424fe165567d68b8645164bd9f80e8f2307b7";
export const artifactDirectory = join(root, "ci/artifacts");
const productionId = "com.perdolique.poleparkla";
const bundletoolDigest = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29";

interface AndroidTools {
  aapt2: string;
  apksigner: string;
  java: string;
  keytool: string;
  jarsigner: string;
}

function requireEnvironment(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

async function androidTools(): Promise<AndroidTools> {
  const sdk = requireEnvironment("ANDROID_HOME");
  const jdk = requireEnvironment("JAVA_HOME");
  const buildToolsDirectory = join(sdk, "build-tools");
  const versions = await readdir(buildToolsDirectory);
  const stableVersions = versions.filter((version) => semver.valid(version));
  stableVersions.sort(semver.rcompare);
  if (!stableVersions[0]) throw new Error("Android build-tools are not installed.");
  const tools = join(buildToolsDirectory, stableVersions[0]);
  return {
    aapt2: join(tools, "aapt2"),
    apksigner: join(tools, "apksigner"),
    java: join(jdk, "bin/java"),
    keytool: join(jdk, "bin/keytool"),
    jarsigner: join(jdk, "bin/jarsigner"),
  };
}

export function verifyApkManifest(badging: string, info: BuildInfo, id: string, debug: boolean): void {
  const packageLine = /^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/m.exec(badging);
  if (!packageLine || packageLine[1] !== id || packageLine[2] !== String(info.versionCode) || packageLine[3] !== info.versionName) {
    throw new Error("APK package or version does not match the requested build.");
  }
  const debuggable = /^application-debuggable\s*$/m.test(badging);
  if (debuggable !== debug) throw new Error("APK debuggable flag is incorrect.");
  const abi = /^native-code: (.+)$/m.exec(badging);
  if (abi?.[1].trim() !== "'arm64-v8a'") throw new Error("APK must contain only arm64-v8a native code.");
}

async function verifyApk(path: string, info: BuildInfo, id: string, debug: boolean, certificate: string): Promise<void> {
  const tools = await androidTools();
  const badging = await capture(tools.aapt2, ["dump", "badging", path]);
  verifyApkManifest(badging, info, id, debug);
  const signature = await capture(tools.apksigner, ["verify", "--verbose", "--print-certs", path]);
  verifyApkCertificate(signature, certificate);
}

export function verifyApkCertificate(signature: string, certificate: string): void {
  const matches = signature.matchAll(/^.*certificate SHA-256 digest: ([a-f0-9]{64})$/gm);
  const digests = [...matches];
  const signerCount = /^Number of signers: (\d+)$/m.exec(signature)?.[1];
  if (signerCount !== "1" || digests.length === 0 || digests.some((match) => match[1] !== certificate)) {
    throw new Error("APK signing certificate does not match.");
  }
}

async function bundletool(): Promise<string> {
  const directory = process.env.RUNNER_TEMP ?? "/tmp";
  const path = join(directory, "pole-parkla-bundletool-1.18.3.jar");
  try {
    const existingDigest = await sha256(path);
    if (existingDigest === bundletoolDigest) return path;
  } catch (error) {
    if (!(error instanceof Error && "code" in error && error.code === "ENOENT")) throw error;
  }
  const response = await fetch("https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar");
  if (!response.ok) throw new Error(`bundletool download failed: ${response.status}`);
  const buffer = await response.arrayBuffer();
  const bytes = Buffer.from(buffer);
  await writeFile(path, bytes);
  const downloadedDigest = await sha256(path);
  if (downloadedDigest !== bundletoolDigest) throw new Error("bundletool checksum does not match the pinned release.");
  return path;
}

async function zipEntrySha256(path: string, entry: string): Promise<string> {
  const child = spawn("unzip", ["-p", path, entry], { stdio: ["ignore", "pipe", "inherit"] });
  const exited = new Promise<void>((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(`Cannot read AAB mapping: exit ${code}`)));
  });
  const hash = createHash("sha256");
  const read = async () => {
    for await (const chunk of child.stdout) hash.update(chunk);
    return hash.digest("hex");
  };
  const [digest] = await Promise.all([read(), exited]);
  return digest;
}

async function verifyBundle(path: string, info: BuildInfo, mappingPath: string): Promise<void> {
  const tools = await androidTools();
  const jar = await bundletool();
  const manifest = await capture(tools.java, ["-jar", jar, "dump", "manifest", `--bundle=${path}`, "--module=base"]);
  if (!manifest.includes(`package="${productionId}"`) || !manifest.includes(`android:versionCode="${info.versionCode}"`) || !manifest.includes(`android:versionName="${info.versionName}"`)) {
    throw new Error("AAB package or version does not match the requested build.");
  }
  if (/android:debuggable="(?:true|1)"/.test(manifest)) throw new Error("Production AAB must not be debuggable.");
  const entries = await capture("unzip", ["-Z1", path]);
  const libraryMatches = entries.matchAll(/^base\/lib\/([^/]+)\/.+\.so$/gm);
  const nativeLibraries = [...libraryMatches];
  const abiNames = nativeLibraries.map((match) => match[1]);
  const abis = new Set(abiNames);
  if (abis.size !== 1 || !abis.has("arm64-v8a")) throw new Error("AAB must contain only arm64-v8a native code.");
  const verified = await capture(tools.jarsigner, ["-J-Duser.language=en", "-verify", path]);
  if (!verified.includes("jar verified.") || verified.includes("unsigned entries")) throw new Error("AAB signature verification failed.");
  const certificate = await capture(tools.keytool, ["-J-Duser.language=en", "-printcert", "-jarfile", path]);
  const fingerprint = /SHA256: ([A-F0-9:]+)/.exec(certificate)?.[1];
  const withoutColons = fingerprint?.replaceAll(":", "");
  const digest = withoutColons?.toLowerCase();
  if (digest !== productionCertificate) throw new Error("AAB signing certificate does not match the upload key.");
  const bundledMapping = await zipEntrySha256(path, "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map");
  const mapping = await sha256(mappingPath);
  if (bundledMapping !== mapping) throw new Error("AAB R8 mapping does not match mapping.txt.");
}

async function withSigning(channel: "pr" | "production", build: (env: NodeJS.ProcessEnv) => Promise<void>): Promise<void> {
  const parent = join(process.env.RUNNER_TEMP ?? "/tmp", "pole-parkla-signing");
  await mkdir(parent, { recursive: true, mode: 0o700 });
  const temporaryPrefix = join(parent, "key-");
  const directory = await mkdtemp(temporaryPrefix);
  await chmod(directory, 0o700);
  const path = join(directory, "keystore.p12");
  try {
    const fixturePath = join(root, "ci/signing/test-keystore.p12.b64");
    const base64 = channel === "pr"
      ? await readFile(fixturePath, "utf8")
      : requireEnvironment("ANDROID_KEYSTORE_BASE64");
    const keyBytes = Buffer.from(base64, "base64");
    await writeFile(path, keyBytes, { mode: 0o600 });
    const env: NodeJS.ProcessEnv = { ...process.env, ANDROID_KEYSTORE_PATH: path };
    if (channel === "pr") {
      env.ANDROID_KEYSTORE_PASSWORD = "android";
      env.ANDROID_KEY_ALIAS = "pole-parkla-test";
      env.ANDROID_KEY_PASSWORD = "android";
    } else {
      requireEnvironment("ANDROID_KEYSTORE_PASSWORD");
      requireEnvironment("ANDROID_KEY_ALIAS");
      requireEnvironment("ANDROID_KEY_PASSWORD");
    }
    await build(env);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

export async function buildAndroid(channel: "pr" | "production", rawInfo: BuildInfo): Promise<void> {
  const info = v.parse(BuildInfoSchema, rawInfo);
  const sha = await capture("git", ["rev-parse", "HEAD"]);
  if (info.commitSha !== sha) throw new Error("Build SHA does not match checkout HEAD.");
  await rm(artifactDirectory, { recursive: true, force: true });
  await mkdir(artifactDirectory, { recursive: true });
  const tasks = channel === "pr"
    ? ["lintDebug", "lintRelease", "testDebugUnitTest", "testReleaseUnitTest", "assembleDebug", "assembleRelease", "assembleDebugAndroidTest"]
    : ["lintRelease", "testReleaseUnitTest", "assembleRelease", "bundleRelease"];
  await withSigning(channel, async (env) => {
    const javaHome = requireEnvironment("JAVA_HOME");
    const androidHome = requireEnvironment("ANDROID_HOME");
    const javaEnvironment = `JAVA_HOME=${javaHome}`;
    const androidEnvironment = `ANDROID_HOME=${androidHome}`;
    const channelProperty = `-PppBuildChannel=${channel}`;
    const nameProperty = `-PppVersionName=${info.versionName}`;
    const codeProperty = `-PppVersionCode=${info.versionCode}`;
    await run("env", [
      javaEnvironment, androidEnvironment,
      "./gradlew", "--no-daemon", "--no-configuration-cache",
      channelProperty, nameProperty, codeProperty,
      ...tasks,
    ], env);
  });
  const releaseApk = join(root, "app/build/outputs/apk/release/app-release.apk");
  const certificate = channel === "pr" ? testCertificate : productionCertificate;
  const id = channel === "pr" ? `${productionId}.preview` : productionId;
  await verifyApk(releaseApk, info, id, false, certificate);
  const mapping = join(root, "app/build/outputs/mapping/release/mapping.txt");
  const mappingInfo = await stat(mapping);
  if (mappingInfo.size === 0) throw new Error("Release R8 mapping is empty.");
  if (channel === "pr") {
    const debugApk = join(root, "app/build/outputs/apk/debug/app-debug.apk");
    const debugId = `${productionId}.debug`;
    await verifyApk(debugApk, info, debugId, true, testCertificate);
    const debugOutput = join(artifactDirectory, "pole-parkla-debug.apk");
    const previewOutput = join(artifactDirectory, "pole-parkla-preview.apk");
    await copyFile(debugApk, debugOutput);
    await copyFile(releaseApk, previewOutput);
  } else {
    const bundle = join(root, "app/build/outputs/bundle/release/app-release.aab");
    await verifyBundle(bundle, info, mapping);
    const names = assetNames(info.versionName);
    const apkOutput = join(artifactDirectory, names[0]);
    const bundleOutput = join(artifactDirectory, names[1]);
    const mappingOutput = join(artifactDirectory, "mapping.txt");
    const metadataOutput = join(artifactDirectory, "build-info.json");
    await copyFile(releaseApk, apkOutput);
    await copyFile(bundle, bundleOutput);
    await copyFile(mapping, mappingOutput);
    const json = `${JSON.stringify(info, null, 2)}\n`;
    await writeFile(metadataOutput, json);
    await writeChecksums(artifactDirectory, info.versionName);
    await verifyArtifactSet(artifactDirectory, info, names);
  }
}
