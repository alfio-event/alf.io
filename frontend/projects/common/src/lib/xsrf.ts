import {Inject, Injectable, Injector, INJECTOR, PLATFORM_ID} from '@angular/core';
import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponse,
  HttpXsrfTokenExtractor
} from '@angular/common/http';
import {DOCUMENT} from '@angular/common';
import {Observable} from 'rxjs';
import {tap} from 'rxjs/operators';

const GID_HEADER_NAME = 'x-auth-token';
const XSRF_TOKEN_HEADER = 'xsrf-token';

@Injectable()
export class DOMXsrfTokenExtractor implements HttpXsrfTokenExtractor {

  lastToken: string | null = null;

  constructor(@Inject(DOCUMENT) private doc: Document,
              @Inject(PLATFORM_ID) private platform: string) {}

  getToken(): string | null {
    if (this.platform === 'server') {
      return null;
    }
    return this.doc.head.querySelector<HTMLMetaElement>('meta[name="XSRF_TOKEN"]')?.content || null;
  }

  updateToken(newVal: string | null): void {
    if (newVal == null || newVal === this.lastToken) {
      return;
    }
    const element = retrieveHeadElement('XSRF_TOKEN', this.doc);
    element.content = newVal;
    this.lastToken = newVal;
  }
}

@Injectable()
export class DOMGidExtractor {

  lastToken: string | null = null;

  constructor(@Inject(DOCUMENT) private doc: Document,
              @Inject(PLATFORM_ID) private platform: string) {}

  getToken(): string | null {
    if (this.platform === 'server') {
      return null;
    }
    return this.doc.head.querySelector<HTMLMetaElement>('meta[name="GID"]')?.content || null;
  }

  updateToken(newVal: string | null): void {
    if (newVal == null || newVal === this.lastToken) {
      return;
    }
    const element = retrieveHeadElement('GID', this.doc);
    element.content = newVal;
    this.lastToken = newVal;
  }
}

function retrieveHeadElement(name: string, doc: Document): HTMLMetaElement {
  let element = doc.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
  if (element == null) {
    // it should only happen in dev mode
    element = doc.createElement('meta');
    element.name = name;
    doc.head.append(element);
  }
  return element;
}

@Injectable()
export class AuthTokenInterceptor implements HttpInterceptor {

  private domGidExtractor: DOMGidExtractor | null = null;
  private domXsrfTokenExtractor: DOMXsrfTokenExtractor | null = null;

  constructor(@Inject(INJECTOR) private injector: Injector) {
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    this.init();
    return next.handle(req.clone({
      headers: req.headers.set(GID_HEADER_NAME, this.domGidExtractor?.getToken() || '')
    })).pipe(tap(response => {
      if (response instanceof HttpResponse) {
        if (response.headers.has(GID_HEADER_NAME)) {
          this.domGidExtractor?.updateToken(response.headers.get(GID_HEADER_NAME));
        }
        if (req.method === 'GET' && response.headers.has(XSRF_TOKEN_HEADER)) {
          this.domXsrfTokenExtractor?.updateToken(response.headers.get(XSRF_TOKEN_HEADER));
        }
      }
    }));
  }

  private init(): void {
    if (this.domGidExtractor == null) {
      this.domGidExtractor = this.injector.get(DOMGidExtractor);
      this.domXsrfTokenExtractor = this.injector.get(DOMXsrfTokenExtractor);
    }
  }

}
