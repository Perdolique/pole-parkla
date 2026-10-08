import { analyzeCommits as conventionalAnalyze } from "@semantic-release/commit-analyzer";
import type { AnalysisContext } from "@semantic-release/commit-analyzer";
import type { AnalyzeCommitsContext } from "semantic-release";
import { readAndroidCommits } from "./android-changes.ts";

export const analyzerOptions = {
  preset: "conventionalcommits",
  releaseRules: [
    { breaking: true, release: "major" },
    { type: "feat", release: "minor" },
    { type: "*", release: "patch" },
  ],
};

export async function analyzeReleaseType(context: AnalysisContext) {
  if (context.commits.length === 0) return null;
  let releaseType: "patch" | "minor" = "patch";
  for (const commit of context.commits) {
    // Analyze each commit before the upstream analyzer can remove a revert pair.
    const singleCommitContext: AnalysisContext = { cwd: context.cwd, logger: context.logger, commits: [commit] };
    const selected = await conventionalAnalyze(analyzerOptions, singleCommitContext);
    if (selected === "major") return "major";
    if (selected === "minor") releaseType = "minor";
  }
  return releaseType;
}

export async function analyzeCommits(_config: unknown, context: AnalyzeCommitsContext) {
  // semantic-release removes skip markers before this hook; our policy includes them.
  const commits = await readAndroidCommits(context.lastRelease.gitHead, "HEAD", context.cwd);
  const analysisContext: AnalysisContext = { cwd: context.cwd, logger: context.logger, commits };
  return analyzeReleaseType(analysisContext);
}
