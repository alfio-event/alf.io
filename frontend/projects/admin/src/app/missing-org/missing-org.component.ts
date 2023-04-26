import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';
import {OrganizationService} from '../shared/organization.service';

@Component({
  selector: 'app-missing-org',
  templateUrl: './missing-org.component.html',
  styleUrls: ['./missing-org.component.scss']
})
export class MissingOrgComponent implements OnInit {

  constructor(
    private readonly organizationService: OrganizationService,
    private readonly router: Router
  ) { }

  ngOnInit(): void {
    firstValueFrom(this.organizationService.getOrganizations()).then(orgs => {
      if (orgs.length > 0) {
        this.router.navigate(['organization', orgs[0].id, 'event']);
      }
    });
  }
}
