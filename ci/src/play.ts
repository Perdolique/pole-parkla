import { openAsBlob } from 'node:fs'
import * as v from 'valibot'
import type { BuildInfo } from './model.ts'
import { sha256 } from './artifacts.ts'

export const playPackage = 'com.perdolique.poleparkla'
export const playLanguages = ['et', 'en-US', 'ru-RU'] as const

const apiRoot = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${playPackage}`
const uploadRoot = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${playPackage}`

const TextSchema = v.pipe(v.string(), v.trim(), v.minLength(1), v.check((text) => {
  const characters = Array.from(text)

  return characters.length <= 500
}, 'Release notes exceed 500 Unicode characters.'))

export const PlayNotesSchema = v.strictObject({
  et: TextSchema,
  'en-US': TextSchema,
  'ru-RU': TextSchema
})
export type PlayNotes = v.InferOutput<typeof PlayNotesSchema>

const LocalizedTextSchema = v.object({
  language: v.string(),
  text: v.string()
})

const ReleaseSchema = v.looseObject({
  name: v.optional(v.string()),
  status: v.picklist(['draft', 'inProgress', 'halted', 'completed']),
  versionCodes: v.optional(v.array(v.string()), []),
  releaseNotes: v.optional(v.array(LocalizedTextSchema))
})

const TrackSchema = v.object({
  track: v.literal('production'),
  releases: v.optional(v.array(ReleaseSchema), [])
})

const BundleSchema = v.object({
  versionCode: v.number(),
  sha256: v.string()
})

const BundlesSchema = v.object({ bundles: v.optional(v.array(BundleSchema), []) })
const EditSchema = v.object({ id: v.pipe(v.string(), v.regex(/^[a-zA-Z0-9_-]+$/)) })

async function request(token: string, url: string, method = 'GET', body?: BodyInit, contentType = 'application/json'): Promise<unknown> {
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': contentType
  }

  const signal = AbortSignal.timeout(180_000)

  const response = await fetch(url, {
    method,
    headers,
    body,
    signal
  })

  const raw = await response.text()

  if (!response.ok) throw new Error(`Google Play ${method} ${url}: HTTP ${response.status}: ${raw}`)

  return raw ? JSON.parse(raw) : null
}

// This boundary can only save a production draft. Review and rollout stay in Play Console.
export async function uploadPlayDraft(token: string, bundlePath: string, info: BuildInfo, inputNotes: PlayNotes): Promise<'draft' | 'already-released'> {
  if (!token) throw new Error('Google Play access token is missing.')

  const notes = v.parse(PlayNotesSchema, inputNotes)
  const digest = await sha256(bundlePath)
  const versionCode = String(info.versionCode)
  const editJson = await request(token, `${apiRoot}/edits`, 'POST', '{}')
  const edit = v.parse(EditSchema, editJson)
  const editRoot = `${apiRoot}/edits/${edit.id}`
  let committed = false

  try {
    const results = await Promise.all([
      request(token, `${editRoot}/tracks/production`),
      request(token, `${editRoot}/bundles`)
    ])

    const track = v.parse(TrackSchema, results[0])
    const bundles = v.parse(BundlesSchema, results[1])
    const bundle = bundles.bundles.find((item) => item.versionCode === info.versionCode)

    if (bundle && bundle.sha256 !== digest) throw new Error('Google Play already has different bytes for this versionCode.')

    const existing = track.releases.find((release) => release.versionCodes.includes(versionCode))

    if (existing && existing.status !== 'draft') {
      if (!bundle) throw new Error('Released Google Play version has no matching bundle.')

      return 'already-released'
    }

    const newer = track.releases.some((release) => release.versionCodes.some((code) => Number(code) > info.versionCode))

    if (newer) throw new Error('Google Play already has a newer production version.')

    const pending = track.releases.filter((release) => release.status === 'draft')

    if (pending.length > 1 || pending.some((release) => release.versionCodes.length !== 1 || release.versionCodes[0] !== versionCode)) {
      throw new Error('Another Google Play draft is pending. Finish or discard it in Play Console first.')
    }

    if (!bundle) {
      const bytes = await openAsBlob(bundlePath)
      const uploadUrl = `${uploadRoot}/edits/${edit.id}/bundles?uploadType=media`
      const uploadedJson = await request(token, uploadUrl, 'POST', bytes, 'application/octet-stream')
      const uploaded = v.parse(BundleSchema, uploadedJson)

      if (uploaded.versionCode !== info.versionCode || uploaded.sha256 !== digest) {
        throw new Error('Uploaded Google Play bundle does not match the verified GitHub build.')
      }
    }

    const releaseNotes = playLanguages.map((language) => ({
      language,
      text: notes[language]
    }))

    const draft = {
      name: info.versionName,
      versionCodes: [versionCode],
      status: 'draft',
      releaseNotes
    }

    const retained = track.releases.filter((release) => release.status !== 'draft')
    const releases = [...retained, draft]

    const payload = JSON.stringify({
      track: 'production',
      releases
    })

    await request(token, `${editRoot}/tracks/production`, 'PUT', payload)

    const commitUrl = `${editRoot}:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW`

    await request(token, commitUrl, 'POST')

    committed = true

    const verifyJson = await request(token, `${apiRoot}/edits`, 'POST', '{}')
    const verifyEdit = v.parse(EditSchema, verifyJson)
    const verifyRoot = `${apiRoot}/edits/${verifyEdit.id}`

    try {
      const savedJson = await request(token, `${verifyRoot}/tracks/production`)
      const saved = v.parse(TrackSchema, savedJson)
      const release = saved.releases.find((item) => item.versionCodes.includes(versionCode))
      const savedNotes = release?.releaseNotes?.toSorted((a, b) => a.language.localeCompare(b.language))
      const expectedNotes = releaseNotes.toSorted((a, b) => a.language.localeCompare(b.language))
      const savedVersion = release?.name === info.versionName && release.versionCodes.length === 1 && release.versionCodes[0] === versionCode

      if (!savedVersion || release.status !== 'draft' || JSON.stringify(savedNotes) !== JSON.stringify(expectedNotes)) {
        throw new Error('Google Play did not save the expected draft and all three translations.')
      }
    } finally {
      await request(token, verifyRoot, 'DELETE').catch((error: unknown) => console.error('Could not discard the verification edit.', error))
    }

    return 'draft'
  } finally {
    if (!committed) await request(token, editRoot, 'DELETE').catch((error: unknown) => console.error('Could not discard the uncommitted Play edit.', error))
  }
}
