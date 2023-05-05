import {Component, OnInit} from '@angular/core';
import {ActivationEnd, Router} from '@angular/router';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslateService} from '@ngx-translate/core';
import {combineLatest, filter, map, Observable, of} from 'rxjs';
import {Organization} from './model/organization';
import {UserInfo} from './model/user';
import {OrgSelectorComponent} from './org-selector/org-selector.component';
import {OrganizationService} from './shared/organization.service';
import {UserService} from './shared/user.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

  public organizations$?: Observable<Organization[]>;
  public organizationId$?: Observable<string | null>;
  public currentUser$?: Observable<UserInfo>;
  public currentOrganization$?: Observable<Organization | undefined>;

  constructor(
    private readonly translateService: TranslateService,
    private readonly organizationService: OrganizationService,
    private readonly userService: UserService,
    private readonly router: Router,
    private readonly modalService: NgbModal,
  ) {
  }

  ngOnInit(): void {
    this.translateService.setDefaultLang('en');
    this.translateService.use('en');
    this.organizations$ = this.organizationService.getOrganizations();
    this.organizationId$ = this.router.events.pipe(filter(a => a instanceof ActivationEnd), map(a => {
      const ae = a as ActivationEnd;
      return ae.snapshot.params['organizationId'];
    }));
    this.currentUser$ = this.userService.getCurrent();
    this.currentOrganization$ = combineLatest([this.organizationId$, this.organizations$])
      .pipe(map(([id, orgs]) => orgs.find(o => id !== null && o.id === Number.parseInt(id))));
  }

  public openOrgSelector(): void {
    const modalRef = this.modalService.open(OrgSelectorComponent, { size: 'lg' });
    const selector: OrgSelectorComponent = modalRef.componentInstance
		selector.organizations$ = this.organizations$;
    selector.organizationId$ = this.organizationId$;
    modalRef.result.then((res: Organization) => {
      console.log('selected', res);
      this.router.navigate(['/organization', res.id, 'event']).then(r => {if (r) {this.currentOrganization$ = of(res)}});

    }).catch(() => {
      // we do nothing on dismiss
    });
  }
}
