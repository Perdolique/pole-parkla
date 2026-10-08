import type { Commit } from "semantic-release";
import { capture, readCommits, root } from "./process.ts";

const buildFiles = new Set([
  "app/build.gradle.kts",
  "app/proguard-rules.pro",
  "build.gradle.kts",
  "settings.gradle.kts",
  "gradle.properties",
  "gradlew",
  "gradlew.bat",
  "ci/src/android.ts",
]);

// Version policy and release notes share the same production Android inputs.
export async function readAndroidCommits(from: string, to: string, cwd = root): Promise<Pick<Commit, "hash" | "message">[]> {
  const commits = await readCommits(from, to, cwd);
  const androidCommits = [];
  for (const commit of commits) {
    // First-parent merge diffs include conflict resolutions. Disabling rename
    // detection keeps removals visible when a build input moves outside Android.
    const files = await capture("git", ["diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "--diff-merges=first-parent", "--no-renames", "-z", commit.hash], cwd);
    const paths = files.split("\0");
    const affectsAndroid = paths.some((path) => {
      const buildFile = buildFiles.has(path);
      const sourceFile = path.startsWith("app/src/main/");
      const gradleFile = path.startsWith("gradle/");
      const androidInput = buildFile || sourceFile || gradleFile;
      return androidInput;
    });
    if (affectsAndroid) androidCommits.push(commit);
  }
  return androidCommits;
}
