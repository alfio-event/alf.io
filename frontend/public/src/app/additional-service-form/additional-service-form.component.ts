import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {AdditionalServiceWithData} from '../model/reservation-info';
import {MoveAdditionalServiceRequest, Ticket, TicketIdentifier} from '../model/ticket';
import {FormArray, FormGroup} from '@angular/forms';

@Component({
  selector: 'app-additional-service-form',
  templateUrl: './additional-service-form.component.html'
})
export class AdditionalServiceFormComponent implements OnChanges {

  @Input()
  additionalServicesForm: FormArray | null;

  @Input()
  additionalServices: AdditionalServiceWithData[] = [];

  hasSupplements = false;

  @Input()
  otherTickets: TicketIdentifier[] | null = null;

  @Input()
  ticket: Ticket;

  @Input()
  cardStyle = true;

  @Output()
  moveAdditionalServiceRequest: EventEmitter<MoveAdditionalServiceRequest> = new EventEmitter<MoveAdditionalServiceRequest>();

  getAdditionalServiceForm(idx: number): FormGroup | undefined {
    return this.additionalServicesForm.at(idx)?.get('additional') as FormGroup | undefined;
  }

  moveAdditionalServiceItem(index: number, asw: AdditionalServiceWithData, tkt: TicketIdentifier): void {
    this.moveAdditionalServiceRequest.emit({
      index,
      itemId: asw.itemId,
      currentTicketUuid: asw.ticketUUID,
      newTicketUuid: tkt.uuid
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.additionalServices) {
      const toCheck: AdditionalServiceWithData[] = changes.additionalServices.currentValue;
      this.hasSupplements = toCheck.some((as) => as.type === 'SUPPLEMENT');
    }
  }
}
