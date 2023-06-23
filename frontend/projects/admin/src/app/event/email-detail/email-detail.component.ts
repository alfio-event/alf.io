import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MailService } from 'projects/admin/src/event/mail.service';
import { EventService } from '../../shared/event.service';
import { Observable, map, mergeMap, of } from 'rxjs';
import { Mail } from '../../model/mail';
import { EventInfo } from '../../model/event';

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
    this.loadMail();
  }
  loadMail() {
    this.mail$ = this.route.paramMap.pipe(
      mergeMap((pm) => {
        const eventId = pm.get('eventId');
        const mailId = pm.get('emailId');
        if (eventId != null && mailId != null) {
          return this.eventService
            .getEvent(eventId)
            .pipe(
              map((event) => new ShortNameAndMailId(event.shortName, mailId))
            );
        }
        return of();
      }),
      mergeMap((shortNameAndMailId) =>
        this.mailService.getMail(
          shortNameAndMailId.shortName,
          shortNameAndMailId.mailId
        )
      )
    );
  }
}
class ShortNameAndMailId {
  constructor(public shortName: string, public mailId: string) {}
}
