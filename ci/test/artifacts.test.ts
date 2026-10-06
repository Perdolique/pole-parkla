import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { assetNames } from "../src/model.ts";
import { verifyArtifactSet, writeChecksums } from "../src/artifacts.ts";
import { testCertificate, verifyApkCertificate, verifyApkManifest } from "../src/android.ts";

test("publication rejects missing, corrupt, extra, or mismatched attachments", async () => {
  const directory = await mkdtemp(join("/tmp", "pole-parkla-artifacts-"));
  const info = { versionName: "1.0.1", versionCode: 3, commitSha: "1".repeat(40) };
  const names = assetNames(info.versionName);
  try {
    assert.deepEqual(names, [
      "pole-parkla-1.0.1-arm64-v8a.apk",
      "pole-parkla-1.0.1.aab",
      "mapping.txt",
      "build-info.json",
      "SHA256SUMS",
    ]);
    for (const name of names) await writeFile(join(directory, name), name);
    await writeFile(join(directory, "build-info.json"), JSON.stringify(info));
    await writeChecksums(directory, info.versionName);
    await verifyArtifactSet(directory, info, names);
    await assert.rejects(verifyArtifactSet(directory, info, names.slice(1)), /incomplete/);
    await assert.rejects(verifyArtifactSet(directory, info, [...names, "unexpected.apk"]), /unexpected/);
    await assert.rejects(verifyArtifactSet(directory, { ...info, commitSha: "2".repeat(40) }, names), /does not match/);
    await writeFile(join(directory, "mapping.txt"), "changed after upload");
    await assert.rejects(verifyArtifactSet(directory, info, names), /SHA-256 mismatch/);
    await writeChecksums(directory, info.versionName);
    await writeFile(join(directory, "SHA256SUMS"), "invalid manifest");
    await assert.rejects(verifyArtifactSet(directory, info, names), /Invalid SHA256SUMS/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("APK verification rejects wrong identity, version, ABI, and debuggable release", () => {
  const info = { versionName: "1.0.1", versionCode: 3, commitSha: "1".repeat(40) };
  const id = "com.perdolique.poleparkla";
  const badging = `package: name='${id}' versionCode='3' versionName='1.0.1'\nnative-code: 'arm64-v8a'\n`;
  verifyApkManifest(badging, info, id, false);
  assert.throws(() => verifyApkManifest(badging, info, `${id}.preview`, false), /package or version/);
  assert.throws(() => verifyApkManifest(badging, { ...info, versionCode: 4 }, id, false), /package or version/);
  assert.throws(() => verifyApkManifest(badging, info, id, true), /debuggable/);
  assert.throws(() => verifyApkManifest(`${badging}application-debuggable\n`, info, id, false), /debuggable/);
  assert.throws(() => verifyApkManifest(badging.replace("'arm64-v8a'", "'arm64-v8a' 'x86_64'"), info, id, false), /arm64-v8a/);
});

test("certificate checks accept Android SDK 36 and 37 output and reject a different or missing signer", () => {
  for (const prefix of ["Signer #1", "V2 Signer:"]) {
    const output = `Number of signers: 1\n${prefix} certificate SHA-256 digest: ${testCertificate}`;
    verifyApkCertificate(output, testCertificate);
    assert.throws(() => verifyApkCertificate(output, "0".repeat(64)), /certificate/);
    assert.throws(() => verifyApkCertificate(output.replace("signers: 1", "signers: 2"), testCertificate), /certificate/);
  }
  assert.throws(() => verifyApkCertificate("Number of signers: 1", testCertificate), /certificate/);
});
