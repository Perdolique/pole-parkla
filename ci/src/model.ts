import * as v from 'valibot'
import semver from 'semver'

export const baseline = {
  tag: 'v1.0.0',
  version: '1.0.0',
  versionCode: 2,
  commitSha: '2ba03d59d23ceac77fca4836eaa7835537f27cf5'
}

export const BuildInfoSchema = v.object({
  versionName: v.pipe(v.string(), v.regex(/^\d+\.\d+\.\d+(?:-(?:pr\.\d+\.\d+|ci\.\d+))?$/)),
  versionCode: v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(2100000000)),
  commitSha: v.pipe(v.string(), v.regex(/^[a-f0-9]{40}$/))
})
export type BuildInfo = v.InferOutput<typeof BuildInfoSchema>

export interface ReleaseState {
  tag: string;
  commitSha: string;
  published: boolean;
  buildInfo?: BuildInfo;
}

export interface ReleaseDecision {
  action: 'new' | 'recover' | 'done' | 'stale';
  version: string;
  versionCode: number;
  tag: string;
}

export function versionFromTag(tag: string): string | null {
  const match = /^v(\d+\.\d+\.\d+)$/.exec(tag)

  if (!match) return null

  return semver.valid(match[1])
}

export function assetNames(version: string): string[] {
  return [
    `pole-parkla-${version}-arm64-v8a.apk`,
    `pole-parkla-${version}.aab`,
    'mapping.txt',
    'build-info.json',
    'SHA256SUMS'
  ]
}

export function latestPublished(states: ReleaseState[]): ReleaseState | undefined {
  const published = states.filter((state) => state.published)

  published.sort((a, b) => {
    const aVersion = a.tag.slice(1)
    const bVersion = b.tag.slice(1)

    return semver.rcompare(aVersion, bVersion)
  })

  return published[0]
}

// Select recovery before semantic-release can mistake an unfinished tag for a release.
export async function decideRelease(
  states: ReleaseState[],
  commitSha: string,
  ancestor: (older: string, newer: string) => Promise<boolean>
): Promise<ReleaseDecision> {
  const completed = states.filter((state) => state.published)

  completed.sort((a, b) => {
    const aVersion = a.tag.slice(1)
    const bVersion = b.tag.slice(1)

    return semver.compare(aVersion, bVersion)
  })

  let expectedCode = baseline.versionCode + 1

  for (const state of completed) {
    const info = v.parse(BuildInfoSchema, state.buildInfo)
    const version = state.tag.slice(1)
    const metadataMatches = info.versionName === version && info.commitSha === state.commitSha && info.versionCode === expectedCode

    if (!metadataMatches) {
      throw new Error(`Published ${state.tag} has inconsistent Android release metadata.`)
    }

    expectedCode += 1
  }

  const latest = latestPublished(states)
  const previousCode = latest?.buildInfo?.versionCode ?? baseline.versionCode
  const version = latest?.buildInfo?.versionName ?? baseline.version
  const pending = states.filter((state) => !state.published)
  const conflicting = pending.find((state) => state.commitSha !== commitSha)

  if (conflicting) {
    throw new Error(`Unfinished ${conflicting.tag} at ${conflicting.commitSha}. Rerun that SHA before publishing ${commitSha}.`)
  }

  if (pending.length > 1) throw new Error('Multiple unfinished releases need manual inspection.')

  if (latest && latest.commitSha !== commitSha) {
    const ahead = await ancestor(latest.commitSha, commitSha)

    if (!ahead) {
      const behind = await ancestor(commitSha, latest.commitSha)

      if (behind && pending.length === 0) {
        return {
          action: 'stale',
          version,
          versionCode: previousCode,
          tag: latest.tag
        }
      }

      throw new Error('The requested SHA does not follow the latest published release.')
    }
  }

  const same = states.find((state) => state.published && state.commitSha === commitSha)

  if (same) {
    if (pending.length) throw new Error('This SHA has both published and unfinished releases.')

    const info = v.parse(BuildInfoSchema, same.buildInfo)

    return {
      action: 'done',
      version: info.versionName,
      versionCode: info.versionCode,
      tag: same.tag
    }
  }

  const unfinished = pending[0]

  if (unfinished) {
    const recoveredVersion = unfinished.tag.slice(1)

    if (semver.lte(recoveredVersion, version)) throw new Error('Unfinished version is older than the latest release.')

    const nextCode = previousCode + 1

    if (unfinished.buildInfo && unfinished.buildInfo.versionCode !== nextCode) {
      throw new Error('Draft versionCode does not follow the latest published release.')
    }

    return {
      action: 'recover',
      version: recoveredVersion,
      versionCode: nextCode,
      tag: unfinished.tag
    }
  }

  return {
    action: 'new',
    version,
    versionCode: previousCode + 1,
    tag: latest?.tag ?? baseline.tag
  }
}

export function testBuildInfo(version: string, commitSha: string, runNumber: number, prNumber?: number): BuildInfo {
  const suffix = prNumber ? `pr.${prNumber}.${runNumber}` : `ci.${runNumber}`
  const versionName = `${version}-${suffix}`

  return v.parse(BuildInfoSchema, {
    versionName,
    versionCode: runNumber,
    commitSha
  })
}
