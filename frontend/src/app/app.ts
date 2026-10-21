import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly menuOpen = signal(false);

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Panel', icon: '◆' },
    { path: '/katas', label: 'Retos', icon: '⌘' },
    { path: '/quizzes', label: 'Conceptos', icon: '?' },
    { path: '/design', label: 'Diseno', icon: '▲' },
  ];

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }
}
