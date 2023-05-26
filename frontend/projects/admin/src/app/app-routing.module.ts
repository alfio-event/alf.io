import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { MissingOrgComponent } from './missing-org/missing-org.component';
import { OrganizationsComponent } from './organizations/organizations.component';
import { OrganizationEditComponent } from './organization-edit/organization-edit.component';
import { AccessControlComponent } from './access-control/access-control.component';
import { UserSystemComponent } from './user-system/user-system.component';
import { UserSystemEditComponent } from './user-system-edit/user-system-edit.component';
import { ApiKeySystemEditComponent } from './api-key-system-edit/api-key-system-edit.component';
import { ApiKeySystemBulkComponent } from './api-key-system-bulk/api-key-system-bulk.component';
import { ApiKeySystemComponent } from './api-key-system/api-key-system.component';

const routes: Routes = [
  {
    path: 'organization/:organizationId/event/:eventId',
    loadChildren: () =>
      import('./event/event.module').then((m) => m.EventModule),
  },
  {
    path: 'organization/:organizationId',
    loadChildren: () =>
      import('./dashboard/dashboard.module').then((m) => m.DashboardModule),
  },
  {
    path: 'authentication',
    loadChildren: () =>
      import('./authentication/authentication.module').then(
        (m) => m.AuthenticationModule
      ),
  },
  {
    path: 'organizations',
    component: OrganizationsComponent,
  },
  {
    path: 'organizations/new',
    component: OrganizationEditComponent,
  },
  {
    path: 'organizations/:organizationId/edit',
    component: OrganizationEditComponent,
  },
  {
    path: 'access-control',
    component: AccessControlComponent,
  },
  {
    path: 'access-control/users',
    component: UserSystemComponent,
  },
  {
    path: 'access-control/users/new',
    component: UserSystemEditComponent,
  },
  {
    path: 'access-control/users/:userId/edit',
    component: UserSystemEditComponent,
  },
  { path: 'access-control/api-keys', component: ApiKeySystemComponent },
  {
    path: 'access-control/api-keys/new',
    component: ApiKeySystemEditComponent,
  },
  {
    path: 'access-control/api-keys/:userId/edit',
    component: ApiKeySystemEditComponent,
  },
  {
    path: 'access-control/api-keys/bulk',
    component: ApiKeySystemBulkComponent,
  },
  {
    path: '',
    component: MissingOrgComponent,
    pathMatch: 'full',
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}
