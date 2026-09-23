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

  /** Emitted on any dismissal so the page can clear its active keyword. */
  readonly closed = output<void>();

  @ViewChild('card') private cardRef?: ElementRef<HTMLElement>;

  protected readonly position = signal<{ top: string; left: string } | null>(null);
  protected readonly sheet = signal(false);

  private lastAnchor: HTMLElement | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['keyword'] && !changes['anchor']) {
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
    if (!anchor || !card || typeof window === 'undefined') {
      return;
    }
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    if (viewportWidth < 520) {
      this.sheet.set(true);
      this.position.set(null);
      return;
    }

    this.sheet.set(false);
    const rect = anchor.getBoundingClientRect();
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
