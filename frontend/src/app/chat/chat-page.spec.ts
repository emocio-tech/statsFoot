import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ChatPage } from './chat-page';

describe('ChatPage', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(ChatPage);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), el: fixture.nativeElement as HTMLElement };
  }

  function submit(el: HTMLElement, prompt: string) {
    el.querySelector<HTMLInputElement>('[data-testid="prompt-input"]')!.value = prompt;
    el.querySelector<HTMLFormElement>('[data-testid="chat-form"]')!.dispatchEvent(new Event('submit'));
  }

  it('shows the response returned by the server', () => {
    const { fixture, http, el } = setup();
    submit(el, 'hello');
    const req = http.expectOne('/api/chat');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBe('hello');
    req.flush('hi there');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="response"]')!.textContent).toBe('hi there');
  });

  it('shows the HTTP status on error', () => {
    const { fixture, http, el } = setup();
    submit(el, 'hello');
    http.expectOne('/api/chat').flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="response"]')!.textContent).toBe('Error: 500');
  });
});
