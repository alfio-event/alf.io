import { Component, OnInit } from '@angular/core';
import { ActivationEnd, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter, map, Observable, tap } from 'rxjs';
import { Organization } from './model/organization';
import { OrganizationService } from './shared/organization.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

  public organizations$?: Observable<Organization[]>;
  public organizationId$?: Observable<string | null>;

  constructor(
    private readonly translateService: TranslateService,
    private readonly organizationService: OrganizationService,
    private readonly router: Router,
  ) {
  }

  ngOnInit(): void {
    this.translateService.setDefaultLang('en');
    this.translateService.use('en');
    this.organizations$ = this.organizationService.getOrganizations();
    this.organizationId$ = this.router.events.pipe(filter(a => a instanceof ActivationEnd), map(a => {
      const ae = a as ActivationEnd;
      return ae.snapshot.paramMap.get('organizationId');
    }));
  }
}
