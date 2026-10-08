import { fileURLToPath } from 'node:url'
import type { Options } from 'semantic-release'
import { artifactDirectory } from './android.ts'
import { repository } from './github.ts'

const analyzerUrl = new URL('./analyzer.ts', import.meta.url)
const adapterUrl = new URL('./adapter.ts', import.meta.url)
const notesUrl = new URL('./notes.ts', import.meta.url)
const githubUrl = import.meta.resolve('@semantic-release/github')

export const analyzerPath = fileURLToPath(analyzerUrl)
export const notesPath = fileURLToPath(notesUrl)

const githubPath = fileURLToPath(githubUrl)
const adapterPath = fileURLToPath(adapterUrl)

export function releaseOptions(versionCode: number, commitSha: string, dryRun: boolean): Options {
  const assetPattern = `${artifactDirectory}/*`

  return {
    branches: ['master'],
    tagFormat: 'v${version}',
    repositoryUrl: `https://github.com/${repository}.git`,
    dryRun,

    plugins: [
      analyzerPath,
      [notesPath, { preset: 'conventionalcommits' }],
      [adapterPath, {
      androidVersionCode: versionCode,
      androidCommitSha: commitSha
    }],
      [githubPath, {
        assets: [assetPattern],
        draftRelease: true,
        successCommentCondition: false,
        failCommentCondition: false,
        releasedLabels: false,
        labels: false,
        discussionCategoryName: false
      }]
    ]
  }
}
