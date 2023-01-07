import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';
import {MissingOrgComponent} from './missing-org/missing-org.component';

const routes: Routes = [
  {
    path: 'organization/:organizationId/event/:eventId',
    loadChildren: () => import('./event/event.module').then(m => m.EventModule)
  },
  {
    path: 'organization/:organizationId',
    loadChildren: () => import('./dashboard/dashboard.module').then(m => m.DashboardModule)
  },
  {
    path: 'authentication',
    loadChildren: () => import('./authentication/authentication.module').then(m => m.AuthenticationModule)
  },
  {
    path: '',
    component: MissingOrgComponent,
    pathMatch: 'full'
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })],
  exports: [RouterModule]
})
export class AppRoutingModule { }
