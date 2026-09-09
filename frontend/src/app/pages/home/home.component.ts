import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ThemeService } from '../../services/theme.service';
import { AppStateService } from '../../services/app-state.service';
import { ErrorService } from '../../services/error.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  readonly themeControl = new FormControl('', [
    Validators.required,
    Validators.minLength(2),
    Validators.maxLength(500),
  ]);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private themeService: ThemeService,
    private router: Router,
    private appState: AppStateService,
    private errorService: ErrorService
  ) {}

  onSubmit(): void {
    this.errorMessage.set(null);

    if (this.themeControl.invalid) {
      if (this.themeControl.errors?.['minlength']) {
        this.errorMessage.set('Theme must be at least 2 characters long.');
      } else if (this.themeControl.errors?.['maxlength']) {
        this.errorMessage.set('Theme must be 500 characters or fewer.');
      } else {
        this.errorMessage.set('Please enter a theme to study.');
      }
      return;
    }

    const prompt = (this.themeControl.value ?? '').trim();
    if (prompt.length < 2) {
      this.errorMessage.set('Theme must be at least 2 characters long.');
      return;
    }

    this.loading.set(true);
    this.themeService.suggest(prompt).subscribe({
      next: (response) => {
        this.appState.setTheme(response);
        this.loading.set(false);
        this.router.navigate(['/subtopics']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.errorService.getMessage(err));
      },
    });
  }
}
