import { Component, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ThemePreferenceService } from './services/theme-preference.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly theme = signal<'light' | 'dark'>('light');

  constructor(private themePref: ThemePreferenceService) {
    this.theme.set(this.themePref.theme);
  }

  toggleTheme(): void {
    this.theme.set(this.themePref.toggle());
  }
}
