import {HttpClient} from "@angular/common/http";
import {Injectable} from "@angular/core";
import {Observable} from "rxjs";
import {Organization} from "../model/organization";

@Injectable()
export class OrganizationService {
  constructor(private httpClient: HttpClient) {
  }

  getOrganizations(): Observable<Organization[]> {
    return this.httpClient.get<Organization[]>('/admin/api/organizations');
  }
}
