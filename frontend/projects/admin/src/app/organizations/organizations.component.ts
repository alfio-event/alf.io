import { Component, OnInit } from '@angular/core';
import { OrganizationService } from '../shared/organization.service';
import { Observable } from 'rxjs';
import { Organization } from '../model/organization';

@Component({
  selector: 'app-organizations',
  templateUrl: './organizations.component.html',
  styleUrls: ['./organizations.component.scss'],
})
export class OrganizationsComponent implements OnInit {
  public organizations$?: Observable<Organization[]>;

  constructor(private readonly organizationService: OrganizationService) {}

  ngOnInit(): void {
    this.organizations$ = this.organizationService.getOrganizations();
  }
}
