import type { SnackKeyword } from '../../models/snack.model';
import { highlightKeywords } from './keyword-highlighter';
import { RichBlock, RichInline } from './rich-text.model';

/**
 * Pure, synchronous, dependency-free parser for the snack rich-text whitelist (feature 012).
 *
 * Grammar (contract: specs/012-snack-rich-formatting/contracts/snack-text-format.md):
 * - blank line            → paragraph boundary
 * - `/^\s*[-*•]\s+/` line → bullet item (consecutive items form one list block)
 * - `**bold**`            → bold span (non-space boundary rule)
 * - single `\n`           → soft line break inside a paragraph/list item
 * - anything else         → inert text node
 *
 * SECURITY BOUNDARY: this function never produces HTML. It returns typed nodes; the component
 * renders them through Angular interpolation, which escapes every character. There is no
 * `[innerHTML]` and no `DomSanitizer` anywhere in this feature (FR-015, FR-017, FR-018).
 */

/** A bullet line: optional leading whitespace, then `-`, `*` or `•`, then whitespace. */
const BULLET_LINE = /^\s*[-*•]\s+/;

const NAMED_ENTITIES: Readonly<Record<string, string>> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
};

/** Minimal HTML-entity decode (named + numeric references), applied to text nodes only. */
const ENTITY_PATTERN = /&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);/g;

/**
 * Parse a raw snack text field into ordered block nodes.
 *
 * Empty or whitespace-only input yields zero blocks so no empty `<p>`/`<ul>` artifact is produced
 * (FR-014).
 */
export function parseRichText(text: string, keywords: SnackKeyword[] = []): RichBlock[] {
  const baseBlocks = parseBase(text);
  if (keywords.length === 0) {
    return baseBlocks;
  }
  return highlightKeywords(baseBlocks, keywords);
}

/** The feature-012 base grammar, unchanged; keyword highlighting is layered on top. */
function parseBase(text: string): RichBlock[] {
  if (!text) {
    return [];
  }

  const normalized = text.replace(/\r\n?/g, '\n');
  if (normalized.trim() === '') {
    return [];
  }

  const blocks: RichBlock[] = [];
  let paragraphLines: string[] = [];
  let listItems: string[] = [];

  const flushParagraph = (): void => {
    if (paragraphLines.length > 0) {
      blocks.push({ kind: 'paragraph', nodes: parseInline(paragraphLines.join('\n')) });
      paragraphLines = [];
    }
  };
  const flushList = (): void => {
    if (listItems.length > 0) {
      blocks.push({ kind: 'list', items: listItems.map((item) => parseInline(item)) });
      listItems = [];
    }
  };
  const flushAll = (): void => {
    flushParagraph();
    flushList();
  };

  for (const line of normalized.split('\n')) {
    if (line.trim() === '') {
      // Blank line → paragraph/list boundary; consecutive blanks collapse to one boundary.
      flushAll();
      continue;
    }

    const bullet = BULLET_LINE.exec(line);
    if (bullet) {
      // Bullet line → one list item; the marker itself is discarded.
      flushParagraph();
      listItems.push(line.slice(bullet[0].length));
      continue;
    }

    // Ordinary line → part of the current paragraph (soft breaks preserved via `\n`).
    flushList();
    paragraphLines.push(line);
  }

  flushAll();
  return blocks;
}

/** Tokenize one paragraph line / list item into inline nodes (text, bold, break). */
export function parseInline(text: string): RichInline[] {
  const nodes: RichInline[] = [];
  let buffer = '';

  const flushText = (): void => {
    if (buffer.length > 0) {
      nodes.push({ kind: 'text', text: decodeEntities(buffer) });
      buffer = '';
    }
  };

  let i = 0;
  while (i < text.length) {
    const ch = text[i];

    if (ch === '\n') {
      flushText();
      nodes.push({ kind: 'break' });
      i += 1;
      continue;
    }

    if (ch === '*' && text[i + 1] === '*') {
      const bold = matchBold(text, i);
      if (bold) {
        flushText();
        // Bold is recursive: its content is a single text child that the highlight pass may split
        // into text/keyword nodes (FR-014, US2 AS4). With no keywords it renders exactly as before.
        nodes.push({
          kind: 'bold',
          nodes: [{ kind: 'text', text: decodeEntities(bold.content) }],
        });
        i = bold.end;
        continue;
      }
      // Unclosed / invalid `**` → literal text (never swallows the rest of the field).
      buffer += '**';
      i += 2;
      continue;
    }

    buffer += ch;
    i += 1;
  }

  flushText();
  return nodes;
}

/**
 * Match a `**bold**` span starting at `start`.
 *
 * Recognized only when the opening `**` is immediately followed by a non-space and the closing
 * `**` is immediately preceded by a non-space (Markdown-like rule), so `5 * 3 = 15` and `**  `
 * are never mangled. Returns `null` for an unclosed marker.
 */
function matchBold(text: string, start: number): { content: string; end: number } | null {
  const openEnd = start + 2;
  const first = text[openEnd];
  if (first === undefined || /\s/.test(first)) {
    return null;
  }

  let search = openEnd;
  while (search < text.length) {
    const idx = text.indexOf('**', search);
    if (idx < 0) {
      return null;
    }
    const before = text[idx - 1];
    // Require non-space before the closer and non-empty content (`idx > openEnd`).
    if (idx > openEnd && before !== undefined && !/\s/.test(before)) {
      return { content: text.slice(openEnd, idx), end: idx + 2 };
    }
    search = idx + 2;
  }
  return null;
}

function decodeEntities(value: string): string {
  return value.replace(ENTITY_PATTERN, (match, ref: string) => {
    if (ref.startsWith('#x') || ref.startsWith('#X')) {
      const code = parseInt(ref.slice(2), 16);
      return isValidCodePoint(code) ? String.fromCodePoint(code) : match;
    }
    if (ref.startsWith('#')) {
      const code = parseInt(ref.slice(1), 10);
      return isValidCodePoint(code) ? String.fromCodePoint(code) : match;
    }
    return NAMED_ENTITIES[ref] ?? match;
  });
}

function isValidCodePoint(code: number): boolean {
  return Number.isInteger(code) && code >= 0 && code <= 0x10ffff;
}
