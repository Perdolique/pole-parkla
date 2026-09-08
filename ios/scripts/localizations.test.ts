import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { spawnSync } from "node:child_process"
import test from "node:test"

type Catalog = {
  sourceLanguage?: unknown
  strings?: Record<string, {
    localizations?: Record<string, {
      stringUnit?: { state?: unknown; value?: unknown }
    }>
  }>
}

const catalogPaths = [
  "ios/PoleParkla/Resources/Localizable.xcstrings",
  "ios/PoleParkla/Resources/InfoPlist.xcstrings",
]
const requiredLanguages = ["en", "et", "ru"]

for (const path of catalogPaths) {
  test(`${path} has complete English, Estonian and Russian translations`, () => {
    const catalog = JSON.parse(readFileSync(path, "utf8")) as Catalog
    assert.equal(catalog.sourceLanguage, "en")
    for (const [key, entry] of Object.entries(catalog.strings ?? {})) {
      assert.deepEqual(Object.keys(entry.localizations ?? {}).sort(), requiredLanguages, key)
      for (const language of requiredLanguages) {
        const unit = entry.localizations?.[language]?.stringUnit
        assert.equal(unit?.state, "translated", `${key} ${language}`)
        assert.ok(typeof unit?.value === "string", `${key} ${language}`)
        assert.notEqual(unit.value.trim(), "", `${key} ${language}`)
      }
    }
  })
}

test("generated localization catalog is current", () => {
  const result = spawnSync(
    process.execPath,
    ["ios/scripts/generate-localizations.ts", "--check"],
    { encoding: "utf8" },
  )
  assert.equal(result.status, 0, result.stderr || result.stdout)
})
