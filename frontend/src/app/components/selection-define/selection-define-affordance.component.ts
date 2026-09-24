import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  input,
  output,
} from '@angular/core';

/**
 * Feature 016 (FR-001, FR-003): the transient, selection-anchored "Define" affordance.
 *
 * Presentational only: it renders a single static `Define` button positioned from the selection
 * rect and clamped to the viewport (never overflows horizontally; flips above the selection when it
 * would cross the viewport bottom). It renders no user/model content — the label is static text.
 *
 * SECURITY: no `[innerHTML]`, no `DomSanitizer`.
 */
@Component({
  selector: 'app-selection-define-affordance',
  standalone: true,
  templateUrl: './selection-define-affordance.component.html',
  styleUrl: './selection-define-affordance.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectionDefineAffordanceComponent {
  /** Selection bounding box used to anchor the affordance, or null. */
  readonly rect = input<DOMRect | null>(null);

  /** Whether the affordance should be shown. */
  readonly visible = input<boolean>(false);

  /** Whether a definition is currently being generated (disables the control). */
  readonly loading = input<boolean>(false);

  /** Emitted when the user activates "Define". */
  readonly defineRequested = output<void>();

  /** Emitted when the user dismisses the affordance (Escape). */
  readonly dismissed = output<void>();

  private static readonly WIDTH = 104;
  private static readonly HEIGHT = 40;

  /** Viewport-clamped fixed position derived from the selection rect. */
  protected readonly position = computed<{ top: string; left: string }>(() => {
    const r = this.rect();
    if (!r || typeof window === 'undefined') {
      return { top: '0px', left: '0px' };
    }
    const width = SelectionDefineAffordanceComponent.WIDTH;
    const height = SelectionDefineAffordanceComponent.HEIGHT;
    const left = Math.max(8, Math.min(r.left, window.innerWidth - width - 8));
    let top = r.bottom + 8;
    if (top + height > window.innerHeight - 8) {
      top = Math.max(8, r.top - height - 8);
    }
    return { top: `${top}px`, left: `${left}px` };
  });

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.visible()) {
      this.dismissed.emit();
    }
  }

  onDefine(): void {
    if (!this.loading()) {
      this.defineRequested.emit();
    }
  }
}
