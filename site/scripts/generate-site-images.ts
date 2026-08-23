import { createHash } from 'node:crypto'
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

type Locale = 'et' | 'en' | 'ru'
type ScreenshotId = 'camera' | 'review' | 'report'
type ResponsiveWidth = 420 | 600

interface SocialText {
  readonly footer: string
  readonly lines: readonly string[]
}

interface ImageFileRecord {
  readonly format: string | undefined
  readonly hasExif: boolean
  readonly height: number | undefined
  readonly path: string
  readonly sha256: string
  readonly size: number
  readonly width: number | undefined
}

interface ImageManifest {
  readonly generatorHash: string
  readonly outputs: readonly ImageFileRecord[]
  readonly schemaVersion: 1
  readonly sources: readonly ImageFileRecord[]
}

const scriptPath = fileURLToPath(import.meta.url)
const siteRoot = path.resolve(path.dirname(scriptPath), '..')
const manifestPath = path.join(siteRoot, 'scripts/site-images-manifest.json')
const locales = ['et', 'en', 'ru'] as const satisfies readonly Locale[]
const screenshotIds = ['camera', 'review', 'report'] as const satisfies readonly ScreenshotId[]
const responsiveWidths = [420, 600] as const satisfies readonly ResponsiveWidth[]
const socialText = {
  et: {
    lines: ['Teade valesti pargitud', 'sõidukist'],
    footer: 'Android · Eesti'
  },
  en: {
    lines: ['Report a vehicle', 'blocking a path'],
    footer: 'Android · Estonia'
  },
  ru: {
    lines: ['Обращение о машине', 'на велодорожке', 'или тротуаре'],
    footer: 'Android · Эстония'
  }
} satisfies Record<Locale, SocialText>

function getSourcePath(locale: Locale, screenshotId: ScreenshotId): string {
  return path.join(siteRoot, `public/screenshots/${locale}/${screenshotId}.webp`)
}

function getResponsivePath(
  locale: Locale,
  screenshotId: ScreenshotId,
  width: ResponsiveWidth
): string {
  return path.join(siteRoot, `public/screenshots/${locale}/${screenshotId}-${width}.webp`)
}

function getSocialPath(locale: Locale): string {
  return path.join(siteRoot, `public/social/pole-parkla-${locale}.jpg`)
}

function toRelativePath(filePath: string): string {
  return path.relative(siteRoot, filePath).split(path.sep).join('/')
}

function hashBuffer(buffer: Buffer): string {
  return createHash('sha256').update(buffer).digest('hex')
}

async function hashFile(filePath: string): Promise<string> {
  const contents = await readFile(filePath)

  return hashBuffer(contents)
}

function escapeXml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;')
}

function createSocialTextSvg(locale: Locale): Buffer {
  const text = socialText[locale]
  const lineElements = text.lines
    .map((line, index) => {
      const y = 276 + index * 58

      return `<text x="72" y="${y}" class="tagline">${escapeXml(line)}</text>`
    })
    .join('')

  return Buffer.from(`
    <svg width="820" height="630" xmlns="http://www.w3.org/2000/svg">
      <style>
        text { font-family: Inter, Arial, sans-serif; }
        .brand { fill: #f3f5ef; font-size: 50px; font-weight: 700; letter-spacing: -1px; }
        .tagline { fill: #f3f5ef; font-size: 46px; font-weight: 700; letter-spacing: -1.2px; }
        .footer { fill: #d8ff63; font-size: 24px; font-weight: 650; letter-spacing: 0.4px; }
      </style>
      <text x="192" y="126" class="brand">Pole parkla</text>
      <rect x="72" y="178" width="112" height="6" rx="3" fill="#d8ff63" />
      ${lineElements}
      <text x="72" y="548" class="footer">${escapeXml(text.footer)}</text>
    </svg>
  `)
}

function createRoundedMask(width: number, height: number, radius: number): Buffer {
  return Buffer.from(`
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <rect width="${width}" height="${height}" rx="${radius}" fill="#fff" />
    </svg>
  `)
}

async function createSocialCard(locale: Locale, brandMark: Buffer): Promise<void> {
  const reviewPath = getSourcePath(locale, 'review')
  const screenshotWidth = 316
  const screenshotHeight = 612
  const screenshot = await sharp(reviewPath)
    .resize({ width: screenshotWidth })
    .extract({ left: 0, top: 0, width: screenshotWidth, height: screenshotHeight })
    .composite([
      {
        input: createRoundedMask(screenshotWidth, screenshotHeight, 28),
        blend: 'dest-in'
      }
    ])
    .png()
    .toBuffer()
  const phoneBackground = Buffer.from(`
    <svg width="352" height="630" xmlns="http://www.w3.org/2000/svg">
      <rect width="352" height="670" x="0" y="0" rx="44" fill="#f3f5ef" />
    </svg>
  `)
  const resizedMark = await sharp(brandMark).resize({ width: 96, height: 96 }).png().toBuffer()

  await sharp({
    create: {
      width: 1200,
      height: 630,
      channels: 4,
      background: '#174b38'
    }
  })
    .composite([
      { input: phoneBackground, left: 824, top: 0 },
      { input: screenshot, left: 842, top: 18 },
      { input: resizedMark, left: 72, top: 58 },
      { input: createSocialTextSvg(locale), left: 0, top: 0 }
    ])
    .flatten({ background: '#174b38' })
    .jpeg({ quality: 86, chromaSubsampling: '4:4:4' })
    .toFile(getSocialPath(locale))
}

async function generateImages(): Promise<void> {
  const socialDirectory = path.join(siteRoot, 'public/social')
  const brandMarkPath = path.join(siteRoot, 'src/assets/brand-mark.png')
  const brandMark = await readFile(brandMarkPath)

  await mkdir(socialDirectory, { recursive: true })

  for (const locale of locales) {
    for (const screenshotId of screenshotIds) {
      const sourcePath = getSourcePath(locale, screenshotId)

      for (const width of responsiveWidths) {
        const outputPath = getResponsivePath(locale, screenshotId, width)

        await sharp(sourcePath)
          .resize({ width, withoutEnlargement: true })
          .webp({ quality: 82, effort: 6 })
          .toFile(outputPath)
      }
    }

    await createSocialCard(locale, brandMark)
  }

  const resizedMark = await sharp(brandMark).resize({ width: 116, height: 116 }).png().toBuffer()

  await sharp({
    create: {
      width: 180,
      height: 180,
      channels: 4,
      background: '#174b38'
    }
  })
    .composite([{ input: resizedMark, left: 32, top: 32 }])
    .png({ compressionLevel: 9 })
    .toFile(path.join(siteRoot, 'public/apple-touch-icon.png'))
}

function getSourcePaths(): string[] {
  const screenshotPaths = locales.flatMap((locale) =>
    screenshotIds.map((screenshotId) => getSourcePath(locale, screenshotId))
  )

  return [path.join(siteRoot, 'src/assets/brand-mark.png'), ...screenshotPaths]
}

function getOutputPaths(): string[] {
  const responsivePaths = locales.flatMap((locale) =>
    screenshotIds.flatMap((screenshotId) =>
      responsiveWidths.map((width) => getResponsivePath(locale, screenshotId, width))
    )
  )
  const socialPaths = locales.map(getSocialPath)

  return [...responsivePaths, ...socialPaths, path.join(siteRoot, 'public/apple-touch-icon.png')]
}

async function getFileRecord(filePath: string): Promise<ImageFileRecord> {
  const [fileHash, fileStat, metadata] = await Promise.all([
    hashFile(filePath),
    stat(filePath),
    sharp(filePath).metadata()
  ])

  return {
    path: toRelativePath(filePath),
    sha256: fileHash,
    size: fileStat.size,
    format: metadata.format,
    width: metadata.width,
    height: metadata.height,
    hasExif: Boolean(metadata.exif)
  }
}

async function createManifest(): Promise<ImageManifest> {
  const generatorHash = await hashFile(scriptPath)
  const sourceRecords = await Promise.all(getSourcePaths().map(getFileRecord))
  const outputRecords = await Promise.all(getOutputPaths().map(getFileRecord))

  return {
    schemaVersion: 1,
    generatorHash,
    sources: sourceRecords,
    outputs: outputRecords
  }
}

function assertEqual<T>(actual: T, expected: T, message: string): void {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, received ${actual}`)
  }
}

function isImageFileRecord(value: unknown): value is ImageFileRecord {
  if (value === null || typeof value !== 'object') {
    return false
  }

  const record = value as Partial<Record<keyof ImageFileRecord, unknown>>

  return (
    typeof record.path === 'string' &&
    typeof record.sha256 === 'string' &&
    typeof record.size === 'number' &&
    typeof record.format === 'string' &&
    typeof record.width === 'number' &&
    typeof record.height === 'number' &&
    typeof record.hasExif === 'boolean'
  )
}

function parseManifest(manifestSource: string): ImageManifest {
  const value: unknown = JSON.parse(manifestSource)

  if (value === null || typeof value !== 'object') {
    throw new Error('Image manifest must be an object')
  }

  const manifest = value as Partial<Record<keyof ImageManifest, unknown>>

  if (
    manifest.schemaVersion !== 1 ||
    typeof manifest.generatorHash !== 'string' ||
    !Array.isArray(manifest.sources) ||
    !manifest.sources.every(isImageFileRecord) ||
    !Array.isArray(manifest.outputs) ||
    !manifest.outputs.every(isImageFileRecord)
  ) {
    throw new Error('Image manifest has an invalid shape')
  }

  return {
    schemaVersion: manifest.schemaVersion,
    generatorHash: manifest.generatorHash,
    sources: manifest.sources,
    outputs: manifest.outputs
  }
}

async function checkImages(): Promise<void> {
  const savedManifest = parseManifest(await readFile(manifestPath, 'utf8'))
  const currentGeneratorHash = await hashFile(scriptPath)

  assertEqual(savedManifest.schemaVersion, 1, 'Unsupported image manifest schema')
  assertEqual(currentGeneratorHash, savedManifest.generatorHash, 'Image generator changed; run pnpm images:generate')

  for (const expectedSource of savedManifest.sources) {
    const sourcePath = path.join(siteRoot, expectedSource.path)
    const actualSource = await getFileRecord(sourcePath)

    assertEqual(actualSource.sha256, expectedSource.sha256, `Source image changed: ${expectedSource.path}`)
  }

  for (const expectedOutput of savedManifest.outputs) {
    const outputPath = path.join(siteRoot, expectedOutput.path)
    const actualOutput = await getFileRecord(outputPath)

    assertEqual(actualOutput.sha256, expectedOutput.sha256, `Generated image is stale: ${expectedOutput.path}`)
    assertEqual(actualOutput.width, expectedOutput.width, `Unexpected width: ${expectedOutput.path}`)
    assertEqual(actualOutput.height, expectedOutput.height, `Unexpected height: ${expectedOutput.path}`)
    assertEqual(actualOutput.format, expectedOutput.format, `Unexpected format: ${expectedOutput.path}`)
    assertEqual(actualOutput.hasExif, false, `Generated image contains EXIF: ${expectedOutput.path}`)

    if (expectedOutput.path.startsWith('public/social/')) {
      const maximumSocialCardSize = 300 * 1024

      if (actualOutput.size > maximumSocialCardSize) {
        throw new Error(`Social card exceeds 300 KiB: ${expectedOutput.path}`)
      }
    }
  }

  console.log(`Verified ${savedManifest.outputs.length} generated site images.`)
}

if (process.argv.includes('--check')) {
  await checkImages()
} else {
  await generateImages()
  const manifest = await createManifest()

  await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`)
  console.log(`Generated ${manifest.outputs.length} site images and updated the manifest.`)
}
