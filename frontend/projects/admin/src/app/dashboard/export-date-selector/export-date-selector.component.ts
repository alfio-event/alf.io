import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { FormGroup } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-export-date-selector',
  templateUrl: './export-date-selector.component.html',
  styleUrls: ['./export-date-selector.component.scss'],
})
export class ExportDateSelectorComponent implements OnInit {
  public dateForm: FormGroup;
  constructor(
    private readonly modalService: NgbModal,
    public activeModal: NgbActiveModal,
    formBuilder: FormBuilder
  ) {
    this.dateForm = formBuilder.group({
      fromDate: [null, Validators.required],
      toDate: [null, Validators.required],
    });
  }

  get fromDate() {
    return this.dateForm.get('fromDate');
  }

  get toDate() {
    return this.dateForm.get('toDate');
  }

  ngOnInit(): void {}
}
