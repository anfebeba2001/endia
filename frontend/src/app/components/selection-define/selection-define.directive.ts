import {
  Directive,
  ElementRef,
  HostListener,
  OnDestroy,
  inject,
  output,
} from '@angular/core';

/**
 * Feature 016 (FR-001..FR-003, FR-016): turns a held text selection inside the host element
 * (the snack reading container) into a transient "Define" affordance trigger.
 *
 * The directive is pure selection/timer logic — it renders nothing and performs no HTTP. It listens
 * to `document:selectionchange` (plus host `pointerdown` and document `keyup` as belt-and-braces)
 * and starts a single ~2s debounce timer only for a non-collapsed selection whose range is
 * contained in the host. Any change or clear cancels the timer and emits `selectionCleared`; when
 * the hold completes it emits the normalized plain-text term and the selection rect.
 *
 * SECURITY: the selected text is read via `Selection.toString()` (plain text) only — this directive
 * never reads `innerHTML` and never renders anything, preserving the feature-012 boundary.
 *
 * The single debounce timer is the key invariant: a stale timer can never show an affordance for a
 * selection that already changed, because every event clears the previous timer before re-evaluating.
 */
@Directive({
  selector: '[appSelectionDefine]',
  standalone: true,
})
export class SelectionDefineDirective implements OnDestroy {
  /** Hold duration before the affordance appears (FR-001). */
  private static readonly HOLD_MS = 2000;

  /** Emitted when a selection has been held long enough to define. */
  readonly termSelected = output<{ term: string; rect: DOMRect }>();

  /** Emitted on any selection change/clear so the host can dismiss the affordance. */
  readonly selectionCleared = output<void>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private timer: ReturnType<typeof setTimeout> | null = null;

  @HostListener('document:selectionchange')
  onSelectionChange(): void {
    this.evaluate();
  }

  /**
   * Host-scoped: a pointerdown inside the reading content starts a new selection gesture. This does
   * not fire for the page-level affordance (which lives outside the host), so activating "Define"
   * never cancels its own trigger.
   */
  @HostListener('pointerdown')
  onPointerDown(): void {
    this.evaluate();
  }

  /** Keyboard/programmatic selection also fires `selectionchange`; this is a redundant safety net. */
  @HostListener('document:keyup')
  onKeyUp(): void {
    this.evaluate();
  }

  /** Clear any pending hold; a valid in-content selection starts a fresh timer (FR-002). */
  private evaluate(): void {
    this.clearTimer();
    this.selectionCleared.emit();

    const selection = typeof document !== 'undefined' ? document.getSelection() : null;
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
      return;
    }
    const range = selection.getRangeAt(0);
    if (!this.host.nativeElement.contains(range.commonAncestorContainer)) {
      return;
    }
    this.timer = setTimeout(() => this.fire(range), SelectionDefineDirective.HOLD_MS);
  }

  /** Read the term/rect at fire time (never at event time) and emit the held selection. */
  private fire(range: Range): void {
    this.timer = null;
    const term = (range.toString() ?? '').trim().replace(/\s+/g, ' ');
    this.termSelected.emit({ term, rect: range.getBoundingClientRect() });
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }
}
