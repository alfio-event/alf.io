import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MailService } from 'projects/admin/src/event/mail.service';
import { EventService } from '../../shared/event.service';
import { Observable, map, mergeMap, of } from 'rxjs';
import { Mail } from '../../model/mail';

@Component({
  selector: 'app-email-detail',
  templateUrl: './email-detail.component.html',
  styleUrls: ['./email-detail.component.scss'],
})
export class EmailDetailComponent implements OnInit {
  public mail$: Observable<Mail> = of();

  constructor(
    private readonly mailService: MailService,
    private readonly route: ActivatedRoute,
    private readonly eventService: EventService
  ) {}

  ngOnInit(): void {
    this.loadMails();
  }
  loadMails() {
    this.mail$ = this.route.paramMap.pipe(
      map((pm) => pm.get('eventId')),
      mergeMap((eventId) =>
        eventId != null ? this.eventService.getEvent(eventId) : of()
      ),
      mergeMap((event) => this.mailService.getMailInfo(event.shortName, 0, ''))
    );
  }
}
