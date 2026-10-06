import { analyzeCommits as conventionalAnalyze } from "@semantic-release/commit-analyzer";
import type { AnalysisContext } from "@semantic-release/commit-analyzer";

export const analyzerOptions = {
  preset: "conventionalcommits",
  releaseRules: [
    { breaking: true, release: "major" },
    { type: "feat", release: "minor" },
    { type: "*", release: "patch" },
  ],
};

export async function analyzeCommits(_config: unknown, context: AnalysisContext) {
  const releaseType = await conventionalAnalyze(analyzerOptions, context);
  if (releaseType) return releaseType;
  if (context.commits.length > 0) return "patch";
  return null;
}
