import { readFile, writeFile } from 'node:fs/promises'
import { basename } from 'node:path'

interface FixtureAsset {
  id: number;
  name: string;
  size: number;
  state: string;
  content: string;
}
interface FixtureRelease {
  id: number;
  tag_name: string;
  draft: boolean;
  prerelease: boolean;
  body: string | null;
  assets: FixtureAsset[];
}
interface PublicationFields {
  draft?: string;
  prerelease?: string;
  make_latest?: string;
}
export interface FixtureState {
  sha: string;
  tagShas?: Record<string, string>;
  releases: FixtureRelease[];
  events: string[];
  publication?: PublicationFields;
}

const path = process.env.PP_TEST_GITHUB_STATE!
const raw = await readFile(path, 'utf8')
const state: FixtureState = JSON.parse(raw)
const args = process.argv.slice(2)

if (args[0] === 'git') {
  if (args[1] === 'tag') process.stdout.write('v1.0.0\nv1.0.1\n')
  else if (args[2] === 'v1.0.0^{commit}') process.stdout.write('2ba03d59d23ceac77fca4836eaa7835537f27cf5')
  else process.stdout.write(state.sha)
} else if (args[0] === 'api') {
  const endpoint = args.find((arg) => arg.startsWith('repos/'))!
  const methodIndex = args.indexOf('--method')
  const method = methodIndex < 0 ? 'GET' : args[methodIndex + 1]

  if (method === 'DELETE') {
    const parts = endpoint.split('/')
    const lastPart = parts.at(-1)
    const id = Number(lastPart)

    state.events.push(`delete ${id}`)

    for (const release of state.releases) release.assets = release.assets.filter((asset) => asset.id !== id)
  } else if (method === 'PATCH') {
    const parts = endpoint.split('/')
    const lastPart = parts.at(-1)
    const id = Number(lastPart)
    const fields = new Map<string, string>()

    for (let i = 0; i + 1 < args.length; i += 1) {
      if (args[i] !== '-F' && args[i] !== '-f') continue

      const field = args[i + 1]
      const separator = field.indexOf('=')
      const name = field.slice(0, separator)
      const value = field.slice(separator + 1)

      fields.set(name, value)
    }

    const publication: PublicationFields = {
      draft: fields.get('draft'),
      prerelease: fields.get('prerelease'),
      make_latest: fields.get('make_latest')
    }

    state.publication = publication

    state.events.push(`publish ${id}`)

    const release = state.releases.find((item) => item.id === id)!

    if (publication.draft !== undefined) release.draft = publication.draft === 'true'

    if (publication.prerelease !== undefined) release.prerelease = publication.prerelease === 'true'
  } else if (endpoint.includes('/commits/')) {
    const tag = endpoint.split('/').at(-1)!

    process.stdout.write(state.tagShas?.[tag] ?? state.sha)
  } else if (endpoint.includes('/releases/assets/')) {
    const parts = endpoint.split('/')
    const lastPart = parts.at(-1)
    const id = Number(lastPart)
    const assets = state.releases.flatMap((release) => release.assets)
    const asset = assets.find((item) => item.id === id)!

    process.stdout.write(asset.content)
  } else {
    const json = JSON.stringify([state.releases])

    process.stdout.write(json)
  }
} else if (args[0] === 'release' && args[1] === 'upload') {
  const release = state.releases.find((item) => item.tag_name === args[2])!
  const paths = args.slice(3, args.indexOf('--repo'))

  state.events.push(`upload ${paths.length}`)

  for (const assetPath of paths) {
    const content = await readFile(assetPath, 'utf8')
    const name = basename(assetPath)

    release.assets.push({
      id: 100 + release.assets.length,
      name,
      size: content.length,
      state: 'uploaded',
      content
    })
  }
} else if (args[0] === 'release' && args[1] === 'create') {
  const content = await readFile(args[args.indexOf('--notes-file') + 1], 'utf8')

  state.events.push(`create ${args[2]}`)

  state.releases.push({
    id: 10,
    tag_name: args[2],
    draft: true,
    prerelease: false,
    body: content,
    assets: []
  })
} else {
  throw new Error(`Unexpected gh call: ${args.join(' ')}`)
}

const updatedJson = JSON.stringify(state)

await writeFile(path, updatedJson)
