import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MailService } from 'projects/admin/src/event/mail.service';
import { Observable, map, mergeMap, of, switchMap } from 'rxjs';
import { EventService } from '../../shared/event.service';

@Component({
  selector: 'app-email-log',
  templateUrl: './email-log.component.html',
  styleUrls: ['./email-log.component.scss'],
})
export class EmailLogComponent implements OnInit {
  public mails$: Observable<any[]> = of();

  constructor(
    private readonly mailService: MailService,
    private readonly route: ActivatedRoute,
    private readonly eventService: EventService
  ) {}

  ngOnInit(): void {
    this.loadMails();
  }

  loadMails() {
    this.mails$ = this.route.paramMap.pipe(
      map((pm) => pm.get('eventId')),
      mergeMap((eventId) =>
        eventId != null ? this.eventService.getEvent(eventId) : of()
      ),
      mergeMap((event) => this.mailService.getMails(event.shortName, 0, ''))
    );
  }
}
