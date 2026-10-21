import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatApiService } from './chat-api.service';

@Component({
  selector: 'app-chat-page',
  templateUrl: './chat-page.html',
  styleUrl: './chat-page.css',
})
export class ChatPage {
  private readonly api = inject(ChatApiService);
  private request?: Subscription;

  readonly response = signal('');

  onSubmit(event: Event, prompt: string): void {
    event.preventDefault();
    this.request?.unsubscribe();
    this.response.set('Loading...');
    this.request = this.api.send(prompt).subscribe({
      next: (text) => this.response.set(text),
      error: (error) => this.response.set(this.errorMessage(error)),
    });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status > 0) {
      return `Error: ${error.status}`;
    }
    return 'Error: unable to reach the server.';
  }
}
