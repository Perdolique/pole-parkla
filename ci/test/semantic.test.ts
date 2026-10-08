import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import semanticRelease from 'semantic-release'
import { analyzerPath, notesPath, releaseOptions } from '../src/config.ts'
import { capture } from '../src/process.ts'

test('configured semantic-release API loads native TS, skips prepare in dry-run, and creates the exact tag', async () => {
  const directory = await mkdtemp(join('/tmp', 'pole-parkla-semantic-'))
  const repository = join(directory, 'repo')
  const events = join(directory, 'events')

  try {
    await capture('git', ['init', '--bare', '--initial-branch=master', repository], directory)

    const checkout = join(directory, 'checkout')

    await capture('git', ['clone', repository, checkout], directory)
    await capture('git', ['config', 'user.email', 'ci@example.invalid'], checkout)
    await capture('git', ['config', 'user.name', 'CI Test'], checkout)
    await capture('git', ['commit', '--allow-empty', '-m', 'chore: baseline'], checkout)
    await capture('git', ['tag', 'v1.0.0'], checkout)
    await capture('git', ['push', 'origin', 'master', '--tags'], checkout)

    const appDirectory = join(checkout, 'app/src/main')

    await mkdir(appDirectory, { recursive: true })

    const appPath = join(appDirectory, 'Main.kt')

    await writeFile(appPath, '// Add preview builds\n')
    await capture('git', ['add', 'app'], checkout)
    await capture('git', ['commit', '-m', 'feat: build preview APKs [skip release]'], checkout)
    await capture('git', ['push', 'origin', 'master'], checkout)

    const sha = await capture('git', ['rev-parse', 'HEAD'], checkout)

    await writeFile(events, '')

    const fixture = fileURLToPath(new URL('./fixture-plugin.ts', import.meta.url))
    const options = releaseOptions(3, sha, true)

    options.repositoryUrl = pathToFileURL(repository).href
    options.ci = false
    options.plugins = [analyzerPath, [notesPath, { preset: 'conventionalcommits' }], fixture]

    // CI branch variables refer to the outer PR, not this temporary master branch.
    const env = {
      PATH: process.env.PATH,
      PP_TEST_EVENTS: events
    }

    const dry = await semanticRelease(options, {
      cwd: checkout,
      env
    })

    assert.ok(dry)
    assert.equal(dry.nextRelease.version, '1.1.0')
    assert.match(dry.nextRelease.notes ?? '', /Features/)

    let recordedEvents = await readFile(events, 'utf8')

    assert.equal(recordedEvents, '')

    const dryTags = await capture('git', ['tag', '--list'], checkout)

    assert.equal(dryTags, 'v1.0.0')

    options.dryRun = false

    const released = await semanticRelease(options, {
      cwd: checkout,
      env
    })

    assert.ok(released)
    assert.equal(released.nextRelease.gitHead, sha)

    const releasedSha = await capture('git', ['rev-parse', 'v1.1.0'], checkout)

    assert.equal(releasedSha, sha)

    recordedEvents = await readFile(events, 'utf8')

    assert.equal(recordedEvents, 'prepare 1.1.0\npublish 1.1.0\n')

    const again = await semanticRelease(options, {
      cwd: checkout,
      env
    })

    assert.equal(again, false)

    const website = join(checkout, 'site')

    await mkdir(website)

    const websitePath = join(website, 'index.html')

    await writeFile(websitePath, 'New website\n')
    await capture('git', ['add', 'site'], checkout)
    await capture('git', ['commit', '-m', 'feat(site)!: replace layout'], checkout)
    await capture('git', ['push', 'origin', 'master'], checkout)

    const websiteRelease = await semanticRelease(options, {
      cwd: checkout,
      env
    })

    assert.equal(websiteRelease, false)

    const websiteEvents = await readFile(events, 'utf8')

    assert.equal(websiteEvents, 'prepare 1.1.0\npublish 1.1.0\n')

    const websiteTags = await capture('git', ['tag', '--list'], checkout)

    assert.equal(websiteTags, 'v1.0.0\nv1.1.0')

    const markedCommits = [
      ['docs: explain install [skip release]', '1.1.1'],
      ['docs: explain upgrade [release skip]', '1.1.2'],
      ['feat: add export [release skip]', '1.2.0'],
      ['feat!: replace storage [skip release]', '2.0.0'],
      ['feat!: replace format [release skip]', '3.0.0']
    ]

    for (const [message, version] of markedCommits) {
      const source = `${message}\n`

      await writeFile(appPath, source)
      await capture('git', ['add', 'app'], checkout)
      await capture('git', ['commit', '-m', message], checkout)
      await capture('git', ['push', 'origin', 'master'], checkout)

      const result = await semanticRelease(options, {
        cwd: checkout,
        env
      })

      assert.ok(result, message)
      assert.equal(result.nextRelease.version, version, message)
      assert.doesNotMatch(result.nextRelease.notes ?? '', /replace layout/)
    }
  } finally {
    await rm(directory, {
      recursive: true,
      force: true
    })
  }
})
