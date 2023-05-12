import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-api-key-system-bulk',
  templateUrl: './api-key-system-bulk.component.html',
  styleUrls: ['./api-key-system-bulk.component.scss']
})
export class ApiKeySystemBulkComponent implements OnInit {

  constructor( private readonly route: ActivatedRoute,) { }


  // get organizationName() {
  //   return this.organizationForm.get('name');
  // }


  ngOnInit(): void {
  }

}

