import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('nav shows the four links with the right hrefs', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const links: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('nav a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Players', 'Chat', 'Compare players', 'Compare teams']);
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/', '/chat', '/compare', '/compare-teams']);
  });
});
