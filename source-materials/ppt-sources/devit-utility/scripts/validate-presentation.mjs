import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { inflateRawSync } from 'node:zlib'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const deckPath = path.resolve(process.argv[2] || path.join(projectRoot, '..', 'Smart_Utility_Demand_Forecasting_Hackathon_Deck.pptx'))

function readZipEntries(buffer) {
  const eocdSignature = 0x06054b50
  const centralSignature = 0x02014b50
  const localSignature = 0x04034b50
  const minimumEocdOffset = Math.max(0, buffer.length - 65_557)
  let eocdOffset = -1
  for (let offset = buffer.length - 22; offset >= minimumEocdOffset; offset -= 1) {
    if (buffer.readUInt32LE(offset) === eocdSignature) {
      eocdOffset = offset
      break
    }
  }
  assert.ok(eocdOffset >= 0, 'PPTX ZIP end record is missing')

  const entryCount = buffer.readUInt16LE(eocdOffset + 10)
  const centralOffset = buffer.readUInt32LE(eocdOffset + 16)
  const entries = []
  let cursor = centralOffset

  for (let index = 0; index < entryCount; index += 1) {
    assert.equal(buffer.readUInt32LE(cursor), centralSignature, `PPTX ZIP central entry ${index} is invalid`)
    const compressionMethod = buffer.readUInt16LE(cursor + 10)
    const compressedSize = buffer.readUInt32LE(cursor + 20)
    const nameLength = buffer.readUInt16LE(cursor + 28)
    const extraLength = buffer.readUInt16LE(cursor + 30)
    const commentLength = buffer.readUInt16LE(cursor + 32)
    const localOffset = buffer.readUInt32LE(cursor + 42)
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8')
    assert.equal(buffer.readUInt32LE(localOffset), localSignature, `PPTX ZIP local entry for ${name} is invalid`)
    const localNameLength = buffer.readUInt16LE(localOffset + 26)
    const localExtraLength = buffer.readUInt16LE(localOffset + 28)
    const contentOffset = localOffset + 30 + localNameLength + localExtraLength
    const compressed = buffer.subarray(contentOffset, contentOffset + compressedSize)
    const contents = compressionMethod === 0
      ? compressed
      : compressionMethod === 8
        ? inflateRawSync(compressed)
        : null
    assert.ok(contents, `Unsupported PPTX ZIP compression method ${compressionMethod} for ${name}`)
    entries.push({ name, contents })
    cursor += 46 + nameLength + extraLength + commentLength
  }

  return entries
}

const deckBytes = await readFile(deckPath)
const runbookText = await readFile(path.join(projectRoot, 'docs', 'PRESENTATION_RUNBOOK.md'), 'utf8')
const entries = readZipEntries(deckBytes)
const entryByName = new Map(entries.map((entry) => [entry.name, entry.contents]))
const slideEntries = entries.filter((entry) => /^ppt\/slides\/slide\d+\.xml$/.test(entry.name))
const noteEntries = entries.filter((entry) => /^ppt\/notesSlides\/notesSlide\d+\.xml$/.test(entry.name))
const mediaEntries = entries.filter((entry) => entry.name.startsWith('ppt/media/'))
const slideText = slideEntries.map((entry) => entry.contents.toString('utf8')).join('\n')
const noteText = noteEntries.map((entry) => entry.contents.toString('utf8')).join('\n')
const coverText = entryByName.get('ppt/slides/slide1.xml')?.toString('utf8') || ''

assert.equal(slideEntries.length, 12, 'Presentation must contain 12 slides')
assert.equal(noteEntries.length, slideEntries.length, 'Every slide must have a notes part')
assert.equal(mediaEntries.length, 16, 'Presentation media count changed; inspect the visual package')
assert.equal(noteEntries.filter((entry) => entry.contents.toString('utf8').includes('[Sources]')).length, 12, 'Every slide note must contain a [Sources] block')
assert.equal(noteEntries.filter((entry) => entry.contents.toString('utf8').includes('rtgs.ap.gov.in')).length, 1, 'RTGS reference must remain confined to one source note')
assert.match(coverText, /03 SEPTEMBER 2026[\s·]+TEAM DATE/, 'Cover must qualify the team date')
assert.match(coverText, /WEDEVIT PRIVATE LIMITED/, 'Cover must retain the legal footer')
assert.match(entryByName.get('ppt/slides/slide12.xml')?.toString('utf8') || '', /WEDEVIT PRIVATE LIMITED/, 'Closing slide must retain the legal footer')
assert.equal((slideText.match(/03 SEPTEMBER 2026/g) || []).length, 1, 'Visible presentation date must occur once')
for (const phrase of ['revolutionary', 'seamless', 'game-changing', 'world-class', 'transformative', 'cutting-edge', 'empower']) {
  assert.doesNotMatch(slideText, new RegExp(phrase, 'i'), `Presentation must not use generic pitch language: ${phrase}`)
}
assert.match(runbookText, /https:\/\/cdma\.ap\.gov\.in\//, 'Presentation runbook must retain the official AP CDMA reference')
assert.match(runbookText, /https:\/\/mangalagiri\.cdma\.ap\.gov\.in\/services/, 'Presentation runbook must retain the official MTMC services reference')

console.log(`Presentation validation passed: ${slideEntries.length} slides, ${noteEntries.length} source-noted notes, ${mediaEntries.length} media entries, qualified team date, scoped RTGS reference, legal opening/closing footers, and SHA-256 ${createHash('sha256').update(deckBytes).digest('hex').toUpperCase()}.`)
