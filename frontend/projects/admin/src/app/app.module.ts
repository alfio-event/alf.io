import {APP_INITIALIZER, NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {provideSvgIconsConfig, SvgIconComponent} from '@ngneat/svg-icon';
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {AuthenticationModule} from "./authentication/authentication.module";
import {HTTP_INTERCEPTORS, HttpClient, HttpClientModule, HttpClientXsrfModule} from "@angular/common/http";
import {UserService} from "./shared/user.service";
import {Router} from "@angular/router";
import {firstValueFrom} from "rxjs";
import {OrganizationService} from './shared/organization.service';
import {MissingOrgComponent} from './missing-org/missing-org.component';
import {ICON_CONFIG} from './shared/icons';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {CustomLoader} from './shared/i18n.service';
import {OrgSelectorComponent} from './org-selector/org-selector.component';
import {NgbDropdownModule} from "@ng-bootstrap/ng-bootstrap";
import {SectionDashboardComponent} from "./shared/section-dashboard/section-dashboard.component";
import {SharedModule} from "./shared/shared.module";
import {HttpLoginInterceptor, redirectToLogin} from "./shared/http-login.interceptor";
import {AlfioCommonModule} from "common";
import { OrganizationsComponent } from './organizations/organizations.component';
import { OrganizationEditComponent } from './organization-edit/organization-edit.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';import { AccessControlComponent } from './access-control/access-control.component';
import { UserSystemComponent } from './user-system/user-system.component';
import { UserSystemEditComponent } from './user-system-edit/user-system-edit.component';
import { ApiKeySystemComponent } from './api-key-system/api-key-system.component';
import { ApiKeySystemEditComponent } from './api-key-system-edit/api-key-system-edit.component';
import { ApiKeySystemBulkComponent } from './api-key-system-bulk/api-key-system-bulk.component';

export function RedirectToLoginIfNeeded(userService: UserService, router: Router): () => Promise<boolean> {
  return async () => {
    const loggedIn = await firstValueFrom(userService.checkUserLoggedIn())
    if (!loggedIn) {
      return redirectToLogin(router);
    }
    return true;
  };
}

// AoT requires an exported function for factories
export function HttpLoaderFactory(http: HttpClient) {
  return new CustomLoader(http);
}

@NgModule({
  declarations: [
    AppComponent,
    MissingOrgComponent,
    OrgSelectorComponent,
    OrganizationsComponent,
    OrganizationEditComponent,
    AccessControlComponent,
    UserSystemComponent,
    UserSystemEditComponent,
    ApiKeySystemComponent,
    ApiKeySystemEditComponent,
    ApiKeySystemBulkComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    AuthenticationModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule,
    HttpClientXsrfModule.withOptions({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-CSRF-TOKEN',
    }),
    SvgIconComponent,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    NgbDropdownModule,
    SharedModule,
    AlfioCommonModule
  ],
  providers: [
    {provide: APP_INITIALIZER, useFactory: RedirectToLoginIfNeeded, deps: [UserService, Router], multi: true},
    {provide: HTTP_INTERCEPTORS, useClass: HttpLoginInterceptor, multi: true},
    provideSvgIconsConfig(ICON_CONFIG),
    UserService,
    OrganizationService,
  ],
  exports: [
    SectionDashboardComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
