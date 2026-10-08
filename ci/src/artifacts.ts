import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readFile, stat, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import * as v from 'valibot'
import { assetNames, BuildInfoSchema } from './model.ts'
import type { BuildInfo } from './model.ts'

export async function sha256(path: string): Promise<string> {
  const hash = createHash('sha256')

  for await (const chunk of createReadStream(path)) hash.update(chunk)

  return hash.digest('hex')
}

export async function writeChecksums(directory: string, version: string): Promise<void> {
  const allNames = assetNames(version)
  const names = allNames.filter((name) => name !== 'SHA256SUMS')
  const lines: string[] = []

  for (const name of names) {
    const path = join(directory, name)
    const digest = await sha256(path)

    lines.push(`${digest}  ${name}`)
  }

  const path = join(directory, 'SHA256SUMS')
  const content = `${lines.join('\n')}\n`

  await writeFile(path, content)
}

// The same check runs on staged files and on files downloaded from GitHub.
export async function verifyArtifactSet(directory: string, expected: BuildInfo, names: string[]): Promise<void> {
  const wanted = assetNames(expected.versionName)

  wanted.sort()

  const actual = names.toSorted()
  const actualSet = JSON.stringify(actual)
  const wantedSet = JSON.stringify(wanted)

  if (actualSet !== wantedSet) throw new Error('Release attachment set is incomplete or unexpected.')

  for (const name of wanted) {
    const path = join(directory, name)
    const info = await stat(path)

    if (!info.isFile() || info.size === 0) throw new Error(`Empty or missing release file: ${name}`)
  }

  const metadataPath = join(directory, 'build-info.json')
  const rawInfo = await readFile(metadataPath, 'utf8')
  const parsedInfo = JSON.parse(rawInfo)
  const info = v.parse(BuildInfoSchema, parsedInfo)

  if (info.versionName !== expected.versionName || info.versionCode !== expected.versionCode || info.commitSha !== expected.commitSha) {
    throw new Error('build-info.json does not match the release tag, Android number, and commit.')
  }

  const checksumsPath = join(directory, 'SHA256SUMS')
  const checksums = await readFile(checksumsPath, 'utf8')
  const content = checksums.trim()
  const lines = content.split('\n')
  const expectedNames = wanted.filter((name) => name !== 'SHA256SUMS')
  const seen = new Set<string>()

  for (const line of lines) {
    const match = /^([a-f0-9]{64})  ([^/\\]+)$/.exec(line)

    if (!match || !expectedNames.includes(match[2]) || seen.has(match[2])) throw new Error('Invalid SHA256SUMS manifest.')

    const path = join(directory, match[2])
    const digest = await sha256(path)

    if (digest !== match[1]) throw new Error(`SHA-256 mismatch: ${match[2]}`)

    seen.add(match[2])
  }

  if (seen.size !== expectedNames.length) throw new Error('SHA256SUMS does not cover every release file.')
}
