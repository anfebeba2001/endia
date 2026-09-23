import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { parseRichText } from './rich-text.parser';
import { RichBlock, RichInline } from './rich-text.model';
import type { SnackKeyword } from '../../models/snack.model';

/**
 * Shared, dependency-free rich-text renderer for snack text (feature 012, extended by 013).
 *
 * Parses the stored field into a typed block AST and renders it with Angular template bindings
 * only (`@for`/`@if`/`@switch` + interpolation). There is deliberately **no** `[innerHTML]` and
 * **no** `DomSanitizer` here: Angular escapes every interpolated text node, so non-whitelisted
 * markup is displayed as inert text and can never become an element (FR-026..FR-029).
 *
 * Reused by the snack card, the results recap, and the history view so all surfaces render
 * identically (FR-013, FR-025).
 */
@Component({
  selector: 'app-rich-text',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './rich-text.component.html',
  styleUrl: './rich-text.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RichTextComponent {
  /** Raw stored snack field value (may contain the whitelisted markup). */
  readonly text = input<string>('');

  /** This snack's keywords; empty/absent disables highlighting entirely (FR-024). */
  readonly keywords = input<SnackKeyword[]>([]);

  /** Emitted when a highlighted term is activated; the page hosts a single card (FR-018/FR-021). */
  readonly keywordSelected = output<{ keyword: SnackKeyword; anchor: HTMLElement }>();

  /** Parsed (and highlighted) block AST, computed once per input change. */
  protected readonly blocks = computed<RichBlock[]>(() =>
    parseRichText(this.text(), this.keywords()),
  );

  /**
   * Forward a keyword activation to the page-level card. The native `<button>` already handles
   * pointer and keyboard activation; the component itself holds no card state (FR-018).
   */
  onKeywordClick(event: Event, node: RichInline): void {
    if (node.kind !== 'keyword') {
      return;
    }
    const anchor = event.currentTarget as HTMLElement;
    this.keywordSelected.emit({
      keyword: { term: node.term, definition: node.definition },
      anchor,
    });
  }
}
