import assert from 'node:assert/strict'
import { test } from 'node:test'
import { analyzeReleaseType } from '../src/analyzer.ts'

test('release policy selects the largest increase, including fallback commits', async () => {
  const cases: [string[], string | null][] = [
    [['fix: repair drafts'], 'patch'],
    [['feat: add preview builds'], 'minor'],
    [['feat!: replace report storage'], 'major'],
    [['fix: change format\n\nBREAKING CHANGE: old drafts need migration'], 'major'],
    [['chore: change format\n\nBREAKING-CHANGE: old format removed'], 'major'],
    [['docs: explain releases', 'refactor: simplify signing', 'test: check reports', 'ci: build APK', 'perf: reduce work', 'revert: undo change', 'build: update tooling', 'style: format code'], 'patch'],
    [['Merge pull request #42', 'unstructured message'], 'patch'],
    [['fix: repair drafts', 'feat: add export', 'docs: explain export'], 'minor'],
    [['feat: add export', 'chore!: remove old export'], 'major'],
    [[], null]
  ]

  for (const [messages, expected] of cases) {
    const commits = messages.map((message, index) => ({
      message,
      hash: String(index)
    }))

    const result = await analyzeReleaseType({
      cwd: process.cwd(),
      logger: console,
      commits
    })

    assert.equal(result, expected, messages.join('; '))
  }

  const hash = '1'.repeat(40)

  for (const [message, expected] of [['feat: add export', 'minor'], ['feat!: replace storage', 'major']]) {
    const revertMessage = `Revert "${message}"\n\nThis reverts commit ${hash}.`

    // Git log returns the newer revert before the original commit.
    const commits = [{
      hash: '2'.repeat(40),
      message: revertMessage
    }, {
      hash,
      message
    }]

    const result = await analyzeReleaseType({
      cwd: process.cwd(),
      logger: console,
      commits
    })

    assert.equal(result, expected, revertMessage)
  }
})
