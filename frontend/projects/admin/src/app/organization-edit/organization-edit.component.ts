import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { OrganizationService } from '../shared/organization.service';
import { Observable, map, of, switchMap } from 'rxjs';
import { Organization } from '../model/organization';

@Component({
  selector: 'app-organization-edit',
  templateUrl: './organization-edit.component.html',
  styleUrls: ['./organization-edit.component.scss'],
})
export class OrganizationEditComponent implements OnInit {
  public organizationId$: Observable<string | null> = of();
  public organization$: Observable<Organization | null> = of();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly oganizationService: OrganizationService
  ) {}

  ngOnInit(): void {
    this.organizationId$ = this.route.paramMap.pipe(
      map((pm) => pm.get('organizationId'))
    );
    this.organization$ = this.organizationId$.pipe(
      switchMap((v) => (v != null ? this.oganizationService.getOrganization(v) : of()))
    );
  }
}
