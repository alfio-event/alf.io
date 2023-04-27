import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { Observable, map, of, switchMap } from 'rxjs';
import { Organization } from '../model/organization';
import { ConfigurationService } from '../shared/configuration.service';
import { InstanceSetting } from '../model/instance-settings';

@Component({
  selector: 'app-organization-edit',
  templateUrl: './organization-edit.component.html',
  styleUrls: ['./organization-edit.component.scss'],
})
export class OrganizationEditComponent implements OnInit {
  public organizationId: string | null = null;
  public organization$: Observable<Organization | null> = of();
  public editMode: boolean | undefined;
  public instanceSetting$: Observable<InstanceSetting> = of();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly oganizationService: OrganizationService,
    private readonly configurationService : ConfigurationService,

  ) {}

  ngOnInit(): void {
    this.organizationId = this.route.snapshot.paramMap.get('organizationId');
    this.instanceSetting$ = this.configurationService.loadInstanceSetting();

    if (this.organizationId !== null) {
      this.editMode = true;
      this.organization$ = this.oganizationService.getOrganization(
        this.organizationId
      );
    } else{
      this.editMode = false;
    }
  }
}
