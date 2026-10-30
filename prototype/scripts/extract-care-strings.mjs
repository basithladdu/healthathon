#!/usr/bin/env node
// Extracts every English string passed to t(en, hi) / careText(lang, en, hi) in app/**/*.tsx
// (plus the [en, hi] pairs and { en, hi } label objects those calls read) into
// app/i18n/strings.en.json, with the inline Hindi alongside as context for translators.
//
// Template literals become keys with {0}, {1}… placeholders, e.g. `Read by ${n}` -> "Read by {0}".
// careText() matches those keys against the rendered English text at runtime.
//
//   node scripts/extract-care-strings.mjs           write app/i18n/strings.en.json
//   node scripts/extract-care-strings.mjs --check   exit 1 if the JSON is out of date
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const APP = path.join(ROOT, 'app');
const OUT = path.join(APP, 'i18n', 'strings.en.json');
const DEVANAGARI = /[ऀ-ॿ]/;
const EN_KEYS = new Set(['en', 'english', 'label', 'title', 'text', 'name', 'question', 'heading', 'summary', 'description']);
const HI_KEYS = new Set(['hi', 'hindi', 'hindiText', 'hindiLabel', 'hindiName', 'hindiQuestion', 'translated', 'hiLabel']);

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'api' || entry.name === 'i18n' || entry.name.startsWith('.')) continue;
      walk(full, files);
    } else if (/\.(tsx|ts)$/.test(entry.name) && !entry.name.endsWith('.d.ts')) files.push(full);
  }
  return files;
}

/** Returns { key, parts } for a string-ish node, or null when it is not a literal. */
function literal(node, sf) {
  if (!node) return null;
  if (ts.isParenthesizedExpression(node)) return literal(node.expression, sf);
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) return { key: node.text, exprs: [] };
  if (ts.isTemplateExpression(node)) {
    let key = node.head.text; const exprs = [];
    for (const span of node.templateSpans) {
      exprs.push(span.expression.getText(sf));
      key += `{${exprs.length - 1}}` + span.literal.text;
    }
    return { key, exprs };
  }
  return null;
}

/** Renumbers the Hindi template so {n} refers to the same expression as in the English key. */
function hindiText(hiNode, enExprs, sf) {
  if (ts.isTemplateExpression(hiNode)) {
    let out = hiNode.head.text;
    for (const span of hiNode.templateSpans) {
      const expr = span.expression.getText(sf);
      const index = enExprs.indexOf(expr);
      out += (index >= 0 ? `{${index}}` : `{?${expr}}`) + span.literal.text;
    }
    return out;
  }
  return literal(hiNode, sf)?.key ?? '';
}

export function extractCareStrings() {
  const strings = new Map();
  let dynamic = 0;
  const add = (en, hi, file) => {
    const key = en.key.trim() ? en.key : null;
    if (!key || DEVANAGARI.test(key) || !/[A-Za-z]/.test(key)) return;
    const entry = strings.get(key) ?? { hi: '', files: [] };
    if (!entry.hi && hi) entry.hi = hi;
    if (!entry.files.includes(file)) entry.files.push(file);
    strings.set(key, entry);
  };
  for (const full of walk(APP)) {
    const text = fs.readFileSync(full, 'utf8');
    if (!/careText|\bt\(/.test(text) || full.endsWith('care-i18n.ts')) continue;
    const file = path.relative(APP, full).replace(/\\/g, '/');
    const sf = ts.createSourceFile(full, text, ts.ScriptTarget.Latest, true, full.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    const usesCareText = text.includes('careText');
    const visit = (node) => {
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
        const name = node.expression.text;
        const args = name === 't' && node.arguments.length === 2 ? node.arguments
          : name === 'careText' && node.arguments.length >= 2 ? node.arguments.slice(1) : null;
        if (args) {
          const en = literal(args[0], sf);
          if (en) add(en, args[1] ? hindiText(args[1], en.exprs, sf) : '', file);
          else dynamic++;
        }
      }
      if (usesCareText && ts.isArrayLiteralExpression(node)) {
        const els = node.elements;
        for (let i = 0; i + 1 < els.length; i++) {
          const en = literal(els[i], sf); const hi = literal(els[i + 1], sf);
          if (en && hi && !DEVANAGARI.test(en.key) && DEVANAGARI.test(hi.key)) add(en, hindiText(els[i + 1], en.exprs, sf), file);
        }
      }
      if (usesCareText && ts.isObjectLiteralExpression(node)) {
        const props = node.properties.filter(ts.isPropertyAssignment);
        const nameOf = (p) => (ts.isIdentifier(p.name) || ts.isStringLiteral(p.name)) ? p.name.text : '';
        const enProp = props.find((p) => EN_KEYS.has(nameOf(p)) && literal(p.initializer, sf) && !DEVANAGARI.test(literal(p.initializer, sf).key));
        for (const p of props) {
          const hi = literal(p.initializer, sf);
          if (!hi || !DEVANAGARI.test(hi.key)) continue;
          if (HI_KEYS.has(nameOf(p)) && enProp) { const en = literal(enProp.initializer, sf); add(en, hindiText(p.initializer, en.exprs, sf), file); }
          else if (!HI_KEYS.has(nameOf(p)) && /^[A-Z]|\s/.test(nameOf(p)) && !/^[A-Z0-9_]+$/.test(nameOf(p))) add({ key: nameOf(p), exprs: [] }, hi.key, file); // { English: 'हिन्दी' } maps
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(sf);
  }
  const sorted = Object.fromEntries([...strings.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, { hi: v.hi, files: v.files.sort() }]));
  return { strings: sorted, dynamic };
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const { strings, dynamic } = extractCareStrings();
  const json = JSON.stringify(strings, null, 1) + '\n';
  if (process.argv.includes('--check')) {
    const current = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
    if (current !== json) { console.error('app/i18n/strings.en.json is out of date. Run: node scripts/extract-care-strings.mjs'); process.exit(1); }
  } else fs.writeFileSync(OUT, json);
  const total = Object.keys(strings).length;
  const templated = Object.keys(strings).filter((k) => /\{\d+\}/.test(k)).length;
  console.log(`${total} strings (${templated} with placeholders); ${dynamic} t()/careText() calls with non-literal English were skipped.`);
}
