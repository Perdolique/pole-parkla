import type { Commit } from 'semantic-release'
import { format } from 'worsier'
import { capture, readCommits, root } from './process.ts'

const buildScript = 'ci/src/android.ts'

const buildFiles = new Set([
  'app/build.gradle.kts',
  'app/proguard-rules.pro',
  'build.gradle.kts',
  'settings.gradle.kts',
  'gradle.properties',
  'gradlew',
  'gradlew.bat'
])

async function buildScriptChanged(hash: string, cwd: string): Promise<boolean> {
  const parents = await capture('git', ['show', '-s', '--format=%P', hash], cwd)
  const parent = parents.split(' ')[0]

  if (!parent) return true

  const beforeEntry = await capture('git', ['ls-tree', parent, '--', buildScript], cwd)
  const afterEntry = await capture('git', ['ls-tree', hash, '--', buildScript], cwd)

  if (!beforeEntry || !afterEntry) return true

  const beforeMode = beforeEntry.split(' ')[0]
  const afterMode = afterEntry.split(' ')[0]

  if (beforeMode !== afterMode || !beforeMode.startsWith('100')) return true

  const beforeRef = `${parent}:${buildScript}`
  const afterRef = `${hash}:${buildScript}`
  const before = await capture('git', ['show', beforeRef], cwd)
  const after = await capture('git', ['show', afterRef], cwd)
  const normalizedBefore = await format(buildScript, before)
  const normalizedAfter = await format(buildScript, after)

  return normalizedBefore !== normalizedAfter
}

// Version policy and release notes share the same production Android inputs.
export async function readAndroidCommits(from: string, to: string, cwd = root): Promise<Pick<Commit, 'hash' | 'message'>[]> {
  const commits = await readCommits(from, to, cwd)
  const androidCommits = []

  for (const commit of commits) {
    // First-parent merge diffs include conflict resolutions. Disabling rename
    // detection keeps removals visible when a build input moves outside Android.
    const files = await capture('git', ['diff-tree', '--root', '--no-commit-id', '--name-only', '-r', '--diff-merges=first-parent', '--no-renames', '-z', commit.hash], cwd)
    const paths = files.split('\0')

    let affectsAndroid = paths.some((path) => {
      const buildFile = buildFiles.has(path)
      const sourceFile = path.startsWith('app/src/main/')
      const gradleFile = path.startsWith('gradle/')
      const androidInput = buildFile || sourceFile || gradleFile

      return androidInput
    })

    if (!affectsAndroid && paths.includes(buildScript)) {
      affectsAndroid = await buildScriptChanged(commit.hash, cwd)
    }

    if (affectsAndroid) androidCommits.push(commit)
  }

  return androidCommits
}
