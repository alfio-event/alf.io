import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { Observable, map, of, switchMap } from 'rxjs';
import { Organization } from '../model/organization';

@Component({
  selector: 'app-organization-edit',
  templateUrl: './organization-edit.component.html',
  styleUrls: ['./organization-edit.component.scss'],
})
export class OrganizationEditComponent implements OnInit {
  public organizationId: string | null = null;
  public organization$: Observable<Organization | null> = of();
  public editMode: boolean | undefined;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly oganizationService: OrganizationService
  ) {}

  ngOnInit(): void {
    this.organizationId = this.route.snapshot.paramMap.get('organizationId');
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
