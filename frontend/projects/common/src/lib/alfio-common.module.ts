import {NgModule} from '@angular/core';
import {AuthTokenInterceptor, DOMGidExtractor, DOMXsrfTokenExtractor} from "./xsrf";
import {HTTP_INTERCEPTORS, HttpXsrfTokenExtractor} from "@angular/common/http";

@NgModule({
  declarations: [
  ],
  imports: [
  ],
  providers: [
    {provide: HTTP_INTERCEPTORS, useClass: AuthTokenInterceptor, multi: true},
    {provide: HttpXsrfTokenExtractor, useClass: DOMXsrfTokenExtractor},
    DOMGidExtractor,
    DOMXsrfTokenExtractor
  ],
  exports: [
  ]
})
export class AlfioCommonModule { }
