import type { SnackKeyword } from '../../models/snack.model';
import { RichBlock, RichInline } from './rich-text.model';

/**
 * Pure keyword highlight pass (feature 013, FR-014..FR-017).
 *
 * Given the feature-012 block AST and a snack's keywords, it splits matching text runs into
 * `keyword` inline nodes. Matching rules (contract §2.1):
 * - case-insensitive;
 * - literal (terms are compared by index scan, never compiled as regular expressions, so `C++`,
 *   `(x)` and `a.b` are safe);
 * - whole-word (the characters immediately before/after a match must not be word characters, with
 *   an exemption at an edge where the term itself starts/ends with a non-word character);
 * - every occurrence is emitted;
 * - longest-term-wins and non-overlapping (terms are tried longest-first at each position and the
 *   cursor advances past a match);
 * - confined to a single contiguous inline text run (never crosses a paragraph, list-item or bold
 *   boundary), recursing into bold children so a term inside `**bold**` is both emphasized and
 *   highlighted.
 *
 * SECURITY: this function only produces typed nodes; it never builds HTML. `related` is never
 * passed to it, so it is never highlighted (FR-014).
 */

interface Term {
  term: string;
  lower: string;
  definition: string;
}

const WORD_CHAR = /[A-Za-z0-9_]/;

/** Highlight all keywords in every block, returning a new AST (inputs are not mutated). */
export function highlightKeywords(blocks: RichBlock[], keywords: SnackKeyword[]): RichBlock[] {
  const terms = normalizeTerms(keywords);
  if (terms.length === 0) {
    return blocks;
  }
  return blocks.map((block) => {
    if (block.kind === 'list') {
      return { kind: 'list', items: block.items.map((item) => highlightInline(item, terms)) };
    }
    return { kind: 'paragraph', nodes: highlightInline(block.nodes, terms) };
  });
}

/** Trim, drop blanks, de-duplicate case-insensitively, and sort longest-first. */
function normalizeTerms(keywords: SnackKeyword[] | null | undefined): Term[] {
  const seen = new Set<string>();
  const terms: Term[] = [];
  for (const keyword of keywords ?? []) {
    if (!keyword) {
      continue;
    }
    const term = (keyword.term ?? '').trim();
    const definition = (keyword.definition ?? '').trim();
    if (term.length === 0 || definition.length === 0) {
      continue;
    }
    const lower = term.toLowerCase();
    if (seen.has(lower)) {
      continue;
    }
    seen.add(lower);
    terms.push({ term, lower, definition });
  }
  terms.sort((a, b) => b.term.length - a.term.length);
  return terms;
}

function highlightInline(nodes: RichInline[], terms: Term[]): RichInline[] {
  const out: RichInline[] = [];
  for (const node of nodes) {
    switch (node.kind) {
      case 'text':
        out.push(...highlightText(node.text, terms));
        break;
      case 'bold':
        out.push({ kind: 'bold', nodes: highlightInline(node.nodes, terms) });
        break;
      default:
        out.push(node);
    }
  }
  return out;
}

/** Split one contiguous text run into text/keyword nodes with a non-overlapping cursor. */
function highlightText(text: string, terms: Term[]): RichInline[] {
  const out: RichInline[] = [];
  const lowerText = text.toLowerCase();
  let buffer = '';

  const flush = (): void => {
    if (buffer.length > 0) {
      out.push({ kind: 'text', text: buffer });
      buffer = '';
    }
  };

  let i = 0;
  while (i < text.length) {
    let matched: Term | null = null;
    for (const term of terms) {
      if (lowerText.startsWith(term.lower, i) && isWholeWord(text, i, term.term.length)) {
        matched = term;
        break;
      }
    }
    if (matched) {
      flush();
      out.push({
        kind: 'keyword',
        text: text.slice(i, i + matched.term.length),
        term: matched.term,
        definition: matched.definition,
      });
      i += matched.term.length;
    } else {
      buffer += text[i];
      i += 1;
    }
  }

  flush();
  return out;
}

/**
 * Whole-word check with an edge exemption: a term that itself begins/ends with a non-word
 * character is not required to have a non-word neighbour at that edge (so `C++` and `(x)` match).
 */
function isWholeWord(text: string, start: number, length: number): boolean {
  const end = start + length;
  const before = start > 0 ? text[start - 1] : '';
  const after = end < text.length ? text[end] : '';
  const termFirst = text[start];
  const termLast = text[end - 1];

  const beforeOk = start === 0 || !WORD_CHAR.test(before) || !WORD_CHAR.test(termFirst);
  const afterOk = end === text.length || !WORD_CHAR.test(after) || !WORD_CHAR.test(termLast);
  return beforeOk && afterOk;
}
