import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs';
import { Organization } from '../model/organization';

@Component({
  selector: 'app-org-selector',
  templateUrl: './org-selector.component.html',
  styleUrls: ['./org-selector.component.scss'],
})
export class OrgSelectorComponent implements OnInit {
  public organizations$?: Observable<Organization[]>;
  public organizationId$?: Observable<string | null>;

  constructor(public activeModal: NgbActiveModal) {}

  ngOnInit(): void {}

  public selectOrganization(org: Organization): void {
    this.activeModal.close(org);
  }
}
