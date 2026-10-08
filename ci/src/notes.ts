import { generateNotes as conventionalNotes } from '@semantic-release/release-notes-generator'
import type { NotesContext } from '@semantic-release/release-notes-generator'
import { readAndroidCommits } from './android-changes.ts'

// Normal publication and recovery both describe only production Android changes.
export async function generateNotes(config: unknown, context: NotesContext): Promise<string> {
  const commits = await readAndroidCommits(context.lastRelease.gitHead, context.nextRelease.gitHead, context.cwd)

  const androidContext: NotesContext = {
    ...context,
    commits
  }

  const notes = await conventionalNotes(config, androidContext)

  return notes
}
