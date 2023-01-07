import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {from, Observable, of} from 'rxjs';
import {TicketInfo} from '../model/ticket-info';
import {UntypedFormBuilder, UntypedFormGroup} from '@angular/forms';
import {AdditionalField, Ticket} from '../model/ticket';
import {ValidatedResponse} from '../model/validated-response';
import {TicketsByTicketCategory} from '../model/reservation-info';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ReleaseTicketComponent} from '../reservation/release-ticket/release-ticket.component';
import {mergeMap} from 'rxjs/operators';
import {User, UserAdditionalData} from '../model/user';
import {DateValidity} from '../model/date-validity';

@Injectable({
    providedIn: 'root'
})
export class TicketService {

    private static getUserDataLabelValue(name: string, index: number, userLanguage?: string, additionalData?: UserAdditionalData): {l: string, v: string} | null {
      if (additionalData != null && additionalData[name] && additionalData[name].values.length > index) {
        const val = additionalData[name];
        return { l: val.label[userLanguage] || val.label[0], v: val.values[index] };
      }
      return { l: null, v: null };
    }

    constructor(
        private http: HttpClient,
        private formBuilder: UntypedFormBuilder,
        private modalService: NgbModal) { }

    getTicketInfo(eventName: string, ticketIdentifier: string): Observable<TicketInfo> {
        return this.http.get<TicketInfo>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}`);
    }

    getTicket(eventName: string, ticketIdentifier: string): Observable<TicketsByTicketCategory> {
      return this.http.get<TicketsByTicketCategory>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}/full`);
    }

    getOnlineCheckInInfo(eventName: string, ticketIdentifier: string, checkInCode: string): Observable<DateValidity> {
      return this.http.get<DateValidity>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}/code/${checkInCode}/check-in-info`, {
        params: {
          tz: this.retrieveTimezone()
        }
      });
    }

    sendTicketByEmail(eventName: string, ticketIdentifier: string): Observable<boolean> {
        return this.http.post<boolean>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}/send-ticket-by-email`, {});
    }

    buildFormGroupForTicket(ticket: Ticket, user?: User): UntypedFormGroup {
        return this.formBuilder.group(this.buildTicket(ticket, user));
    }


    updateTicket(eventName: string, ticketIdentifier: string, ticket: any): Observable<ValidatedResponse<boolean>> {
      return this.http.put<ValidatedResponse<boolean>>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}`, ticket);
    }

    openReleaseTicket(ticket: Ticket, eventName: string): Observable<boolean> {
      return from(this.modalService.open(ReleaseTicketComponent, {centered: true}).result)
        .pipe(mergeMap(res => {
          if (res === 'yes') {
            return this.releaseTicket(eventName, ticket.uuid);
          } else {
            return of(false);
          }
        }));
    }

    releaseTicket(eventName: string, ticketIdentifier: string): Observable<boolean> {
      return this.http.delete<boolean>(`/api/v2/public/event/${eventName}/ticket/${ticketIdentifier}`, {});
    }

    private buildTicket(ticket: Ticket, user?: User): {firstName: string, lastName: string, email: string, userLanguage, additional: UntypedFormGroup} {
      return {
          firstName: ticket.firstName || user?.firstName,
          lastName: ticket.lastName || user?.lastName,
          email: ticket.email || user?.emailAddress,
          userLanguage: ticket.userLanguage,
          additional: this.buildAdditionalFields(ticket.ticketFieldConfigurationBeforeStandard,
            ticket.ticketFieldConfigurationAfterStandard, ticket.userLanguage, user?.profile?.additionalData)
      };
    }

    private buildAdditionalFields(before: AdditionalField[], after: AdditionalField[], userLanguage: string, userData?: UserAdditionalData): UntypedFormGroup {
      const additional = {};
      if (before) {
        this.buildSingleAdditionalField(before, additional, userLanguage, userData);
      }
      if (after) {
        this.buildSingleAdditionalField(after, additional, userLanguage, userData);
      }
      return this.formBuilder.group(additional);
    }

    private buildSingleAdditionalField(a: AdditionalField[], additional: {}, userLanguage: string, userData?: UserAdditionalData): void {
      a.forEach(f => {
        const arr = [];

        if (f.type === 'checkbox') { // pre-fill with empty values for the checkbox cases, as we can have multiple values!
          for (let i = 0; i < f.restrictedValues.length; i++) {
            arr.push(this.formBuilder.control(null));
          }
        }

        f.fields.forEach(field => {
          arr[field.fieldIndex] = this.formBuilder.control(field.fieldValue || TicketService.getUserDataLabelValue(f.name, field.fieldIndex, userLanguage, userData).v);
        });
        additional[f.name] = this.formBuilder.array(arr);
      });
    }

    private retrieveTimezone(): string | null {
      try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
      } catch (e) {
        return null;
      }
    }
}
