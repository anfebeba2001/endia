import { Component, Input, inject, signal } from '@angular/core';

export type DragDropVariant = 'SEQUENCING' | 'CLASSIFICATION' | 'MATCHING';

/**
 * Reusable drag-and-drop question renderer (FR-005/007).
 * Renders sequencing / classification / matching via the native HTML5 drag-and-drop
 * API plus a keyboard-accessible reorder/assign fallback (Tab + arrow / select controls).
 */
@Component({
  selector: 'app-drag-drop-question',
  standalone: true,
  templateUrl: './drag-drop-question.component.html',
  styleUrl: './drag-drop-question.component.scss',
})
export class DragDropQuestionComponent {
  @Input({ required: true }) variant!: DragDropVariant;
  @Input({ required: true }) items: string[] = [];
  @Input() categories: string[] = [];
  @Input() rightItems: string[] = [];

  readonly order = signal<string[]>([]);
  readonly categoryAssignments = signal<Record<string, string>>({});
  readonly matches = signal<Record<string, string>>({});
  readonly dragging = signal<string | null>(null);

  ngOnInit(): void {
    this.order.set([...this.items]);
  }

  /** Build the submitted arrangement in the backend contract format. */
  getArrangement(): string[] {
    switch (this.variant) {
      case 'SEQUENCING':
        return this.order();
      case 'CLASSIFICATION':
        return this.items.map((i) => `${i}:${this.categoryAssignments()[i] ?? ''}`);
      case 'MATCHING':
        return this.items.map((i) => `${i}:${this.matches()[i] ?? ''}`);
      default:
        return [];
    }
  }

  isComplete(): boolean {
    if (this.variant === 'SEQUENCING') {
      return this.order().length === this.items.length;
    }
    if (this.variant === 'CLASSIFICATION') {
      return this.items.every((i) => !!this.categoryAssignments()[i]);
    }
    return this.items.every((i) => !!this.matches()[i]);
  }

  // Sequencing reorder

  moveUp(index: number): void {
    const arr = [...this.order()];
    if (index > 0) {
      [arr[index - 1], arr[index]] = [arr[index], arr[index - 1]];
      this.order.set(arr);
    }
  }

  moveDown(index: number): void {
    const arr = [...this.order()];
    if (index < arr.length - 1) {
      [arr[index], arr[index + 1]] = [arr[index + 1], arr[index]];
      this.order.set(arr);
    }
  }

  onDragStart(item: string): void {
    this.dragging.set(item);
  }

  onDrop(target: string): void {
    const moved = this.dragging();
    if (!moved || moved === target) {
      return;
    }
    const arr = this.order().filter((i) => i !== moved);
    const targetIndex = arr.indexOf(target);
    arr.splice(targetIndex, 0, moved);
    this.order.set(arr);
    this.dragging.set(null);
  }

  assignCategory(item: string, category: string): void {
    this.categoryAssignments.update((m) => ({ ...m, [item]: category }));
  }

  onDropToCategory(category: string): void {
    const moved = this.dragging();
    if (moved) {
      this.assignCategory(moved, category);
      this.dragging.set(null);
    }
  }

  assignMatch(item: string, right: string): void {
    this.matches.update((m) => ({ ...m, [item]: right }));
  }
}
