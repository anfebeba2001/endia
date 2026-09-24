import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnChanges,
  SimpleChanges,
  ViewChild,
  input,
  output,
  signal,
} from '@angular/core';
import type { SnackKeyword } from '../../models/snack.model';

/**
 * Standalone, dismissible definition card for a highlighted keyword (feature 013, FR-018..FR-023).
 *
 * The page hosts exactly one instance, so opening a different term simply replaces the input and
 * the previous card is gone (FR-021). Dismissal is via outside pointerdown, the visible close
 * control, or Escape, with no navigation (FR-020). Focus moves into the card on open and returns
 * to the term control on close (FR-023). Positioning is viewport-clamped, with a bottom-sheet
 * layout on narrow screens, so the card never overflows horizontally (FR-022).
 *
 * SECURITY: the term and definition are rendered with escaped interpolation only — there is no
 * `[innerHTML]` and no `DomSanitizer` (FR-026..FR-028).
 */
@Component({
  selector: 'app-keyword-definition-card',
  standalone: true,
  templateUrl: './keyword-definition-card.component.html',
  styleUrl: './keyword-definition-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KeywordDefinitionCardComponent implements OnChanges {
  /** The selected keyword, or null when no card is open. */
  readonly keyword = input<SnackKeyword | null>(null);

  /** The term control that opened the card; focus returns here on close. */
  readonly anchor = input<HTMLElement | null>(null);

  /**
   * Feature 014: whether the "Generate snack" action is available. Hosts that cannot resolve a
   * theme + source subtopic pass `false` (FR-001, plan.md Open Decisions #1).
   */
  readonly generateEnabled = input<boolean>(true);

  /** Feature 014: the generation action state — `idle`, `loading`, or `done` (FR-003/FR-005/FR-006). */
  readonly generateState = input<'idle' | 'loading' | 'done'>('idle');

  /** Feature 014: a user-safe error to show inline, or null (FR-004). */
  readonly generateError = input<string | null>(null);

  /**
   * Feature 017: true when the card was opened by the custom-Define flow. Selects the action label
   * (`Create snack`/`Creating…`) for the reused feature-014 action slot; default `false` so every
   * feature-013/014 surface (results/history/regular keyword) is unchanged (FR-001/FR-003).
   */
  readonly customDefinition = input<boolean>(false);

  /** Feature 016: whether a custom definition is currently being generated (loading state). */
  readonly definitionLoading = input<boolean>(false);

  /** Feature 016: a user-safe error for the custom-definition flow, or null. */
  readonly definitionError = input<string | null>(null);

  /**
   * Feature 016: the selection rect to position the card from when there is no `anchor` element
   * (the custom-Define flow has no term control). Ignored when `anchor` is present.
   */
  readonly anchorRect = input<DOMRect | null>(null);

  /** Feature 014: emitted when the user activates "Generate snack" (the host owns the request). */
  readonly generateRequested = output<SnackKeyword>();

  /** Emitted on any dismissal so the page can clear its active keyword. */
  readonly closed = output<void>();

  @ViewChild('card') private cardRef?: ElementRef<HTMLElement>;

  protected readonly position = signal<{ top: string; left: string } | null>(null);
  protected readonly sheet = signal(false);

  private lastAnchor: HTMLElement | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['keyword'] && !changes['anchor'] && !changes['anchorRect']) {
      return;
    }
    if (this.keyword()) {
      this.lastAnchor = this.anchor();
      // Wait for the card to render before measuring and focusing it.
      setTimeout(() => this.openCard());
    }
  }

  @HostListener('document:pointerdown', ['$event'])
  onDocumentPointerDown(event: PointerEvent): void {
    if (!this.keyword()) {
      return;
    }
    const card = this.cardRef?.nativeElement;
    if (card && !card.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.keyword()) {
      this.close();
    }
  }

  close(): void {
    const anchor = this.lastAnchor;
    this.closed.emit();
    // Return focus to the term control that opened the card (FR-023).
    if (anchor && typeof anchor.focus === 'function') {
      anchor.focus();
    }
  }

  /**
   * Feature 014: emit the generation request for the current keyword. Guarded so a rapid second
   * activation while loading (or after done) starts no duplicate generation (FR-005/FR-006).
   */
  requestGenerate(): void {
    const keyword = this.keyword();
    if (!keyword || !this.generateEnabled() || this.generateState() !== 'idle') {
      return;
    }
    this.generateRequested.emit(keyword);
  }

  private openCard(): void {
    this.updatePosition();
    const closeButton =
      this.cardRef?.nativeElement.querySelector<HTMLElement>('.keyword-card__close');
    closeButton?.focus();
  }

  /** Clamp the card to the viewport; use a bottom sheet on narrow screens (FR-022). */
  private updatePosition(): void {
    const anchor = this.anchor();
    const card = this.cardRef?.nativeElement;
    if (!card || typeof window === 'undefined') {
      return;
    }
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    if (viewportWidth < 520) {
      this.sheet.set(true);
      this.position.set(null);
      return;
    }

    // Feature 016: prefer the anchor element; fall back to the selection rect (custom-Define flow).
    const rect = anchor ? anchor.getBoundingClientRect() : this.anchorRect();
    if (!rect) {
      return;
    }

    this.sheet.set(false);
    const cardWidth = Math.min(280, Math.max(200, card.offsetWidth || 240));
    const cardHeight = card.offsetHeight || 0;
    const left = Math.max(8, Math.min(rect.left, viewportWidth - cardWidth - 8));
    const below = rect.bottom + 8;
    const above = rect.top - cardHeight - 8;
    // Prefer opening below the term; flip above when that would cross the viewport bottom, then
    // clamp so the card always stays fully inside the viewport (FR-022). If the card is taller
    // than the viewport, it pins to the top and the page remains the only overflow.
    const preferred = below + cardHeight > viewportHeight - 8 && above >= 8 ? above : below;
    const top = Math.max(8, Math.min(preferred, viewportHeight - cardHeight - 8));
    this.position.set({ top: `${top}px`, left: `${left}px` });
  }
}
