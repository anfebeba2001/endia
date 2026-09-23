/**
 * Client-side rendering types for the snack rich-text renderer (feature 012).
 *
 * These are pure, in-memory view models: they are produced by `parseRichText` and consumed only by
 * `RichTextComponent`. They never cross the API boundary and are not persisted.
 *
 * The output vocabulary is deliberately tiny — the renderer can only ever produce `<p>`, `<ul>`,
 * `<li>`, `<strong>` and `<br>`. Everything outside the whitelist stays an inert text node, so
 * untrusted model output can never become an element (FR-015, FR-018).
 */

/** A single inline node inside a paragraph or a list item. */
export type RichInline =
  | { kind: 'text'; text: string }
  | { kind: 'bold'; text: string }
  | { kind: 'break' };

/** A block-level node: a paragraph or a bullet list. */
export type RichBlock =
  | { kind: 'paragraph'; nodes: RichInline[] }
  | { kind: 'list'; items: RichInline[][] };
