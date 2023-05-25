import { Component, OnInit } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-export-date-selector',
  templateUrl: './export-date-selector.component.html',
  styleUrls: ['./export-date-selector.component.scss'],
})
export class ExportDateSelectorComponent implements OnInit {
  constructor(
    private readonly modalService: NgbModal,
    public activeModal: NgbActiveModal
  ) {}

  ngOnInit(): void {}
}
