import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceSetting } from '../model/instance-settings';

@Injectable({
  providedIn: 'root',
})
export class ConfigurationService {
  constructor(private httpClient: HttpClient) {}

  loadInstanceSetting(): Observable<InstanceSetting> {
    return this.httpClient.get<InstanceSetting>(
      '/admin/api/configuration/instance-settings'
    );
  }
}
