import { appendFile, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import * as v from 'valibot'
import semver from 'semver'
import { downloadPublishedRelease, listReleases, publishedReleaseTagForCommit } from './github.ts'
import { assetNames, BuildInfoSchema } from './model.ts'
import { PlayNotesSchema, uploadPlayDraft } from './play.ts'
import { readPlayNotes } from './play-notes.ts'
import { verifyArtifactSet } from './artifacts.ts'

async function main(): Promise<void> {
  const command = process.argv[2]

  if (command === 'prepare') {
    const commitSha = process.env.PP_PLAY_COMMIT_SHA
    let tag = process.env.PP_PLAY_TAG

    if (commitSha) {
      const releaseTag = await publishedReleaseTagForCommit(commitSha)

      tag = releaseTag ?? undefined

      if (!tag) {
        console.log(`Skip Google Play: no published Android release for CI commit ${commitSha}.`)

        if (process.env.GITHUB_OUTPUT) await appendFile(process.env.GITHUB_OUTPUT, 'should_upload=false\n')

        if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, 'Google Play upload skipped: this CI commit has no published Android release.\n')

        return
      }
    } else if (!tag) {
      const releases = await listReleases()
      const published = releases.filter((release) => !release.draft)

      published.sort((a, b) => semver.rcompare(a.tag_name.slice(1), b.tag_name.slice(1)))

      tag = published[0]?.tag_name
    }

    if (!tag) throw new Error('No published Android release is available.')

    const prefix = join(process.env.RUNNER_TEMP ?? '/tmp', 'pole-parkla-play-')
    const directory = await mkdtemp(prefix)

    try {
      const info = await downloadPublishedRelease(tag, directory)

      if (commitSha && info.commitSha !== commitSha) throw new Error('Published Android build does not match the triggering CI commit.')

      const notes = await readPlayNotes(info)
      const notesPath = join(directory, 'play-notes.json')
      const content = JSON.stringify(notes, null, 2)

      await writeFile(notesPath, content)

      if (process.env.GITHUB_OUTPUT) {
        const output = `should_upload=true\ndirectory=${directory}\ntag=${tag}\n`

        await appendFile(process.env.GITHUB_OUTPUT, output)
      }

      console.log(`Verified ${tag} (Android ${info.versionCode}); Play upload files: ${directory}`)
    } catch (error) {
      await rm(directory, {
        recursive: true,
        force: true
      })

      throw error
    }

    return
  }

  if (command !== 'upload') throw new Error('Usage: node ci/src/play-cli.ts prepare | upload <verified-directory>')

  const directory = process.argv[3]

  if (!directory) throw new Error('Verified release directory is missing.')

  const metadataPath = join(directory, 'build-info.json')
  const metadata = await readFile(metadataPath, 'utf8')
  const parsedInfo = JSON.parse(metadata)
  const info = v.parse(BuildInfoSchema, parsedInfo)
  const names = assetNames(info.versionName)

  await verifyArtifactSet(directory, info, names)

  const notesPath = join(directory, 'play-notes.json')
  const rawNotes = await readFile(notesPath, 'utf8')
  const parsedNotes = JSON.parse(rawNotes)
  const notes = v.parse(PlayNotesSchema, parsedNotes)
  const bundlePath = join(directory, `pole-parkla-${info.versionName}.aab`)
  const status = await uploadPlayDraft(process.env.PP_PLAY_ACCESS_TOKEN ?? '', bundlePath, info, notes)
  const message = status === 'draft' ? 'Production draft saved with et, en-US, and ru-RU notes. Review and publish it manually in Play Console.' : 'This version is already released in Google Play. No changes made.'

  console.log(message)

  if (process.env.GITHUB_STEP_SUMMARY) {
    const summary = `Google Play **${info.versionName}** (Android ${info.versionCode}): ${message}\n`

    await appendFile(process.env.GITHUB_STEP_SUMMARY, summary)
  }
}

try {
  await main()
} catch (error) {
  console.error(error)

  process.exitCode = 1
}
