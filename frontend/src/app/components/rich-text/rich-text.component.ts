import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { parseRichText } from './rich-text.parser';
import { RichBlock } from './rich-text.model';

/**
 * Shared, dependency-free rich-text renderer for snack text (feature 012).
 *
 * Parses the stored field into a typed block AST and renders it with Angular template bindings
 * only (`@for`/`@if`/`@switch` + interpolation). There is deliberately **no** `[innerHTML]` and
 * **no** `DomSanitizer` here: Angular escapes every interpolated text node, so non-whitelisted
 * markup is displayed as inert text and can never become an element (FR-016, FR-018, FR-019).
 *
 * Reused by the snack card, the results recap, and the history view so all surfaces render
 * identically (FR-013, FR-023).
 */
@Component({
  selector: 'app-rich-text',
  standalone: true,
  templateUrl: './rich-text.component.html',
  styleUrl: './rich-text.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RichTextComponent {
  /** Raw stored snack field value (may contain the whitelisted markup). */
  readonly text = input<string>('');

  /** Parsed block AST, computed once per input change. */
  protected readonly blocks = computed<RichBlock[]>(() => parseRichText(this.text()));
}
