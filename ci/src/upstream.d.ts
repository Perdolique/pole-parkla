declare module '@semantic-release/commit-analyzer' {
  import type { AnalyzeCommitsContext, Commit, ReleaseType } from 'semantic-release'

  export type CommitInput = Pick<Commit, 'message' | 'hash'>
  export type AnalysisContext = Pick<AnalyzeCommitsContext, 'cwd' | 'logger'> & { commits: readonly CommitInput[] }
  export function analyzeCommits(config: unknown, context: AnalysisContext): Promise<ReleaseType | null>
}
declare module '@semantic-release/release-notes-generator' {
  import type { Commit, GenerateNotesContext, LastRelease, NextRelease } from 'semantic-release'

  export interface NotesContext extends Pick<GenerateNotesContext, 'cwd' | 'options' | 'logger'> {
    commits: Pick<Commit, 'message' | 'hash'>[];
    lastRelease: Pick<LastRelease, 'gitTag' | 'gitHead'>;
    nextRelease: Pick<NextRelease, 'version' | 'gitTag' | 'gitHead'>;
  }
  export function generateNotes(config: unknown, context: NotesContext): Promise<string>
}
