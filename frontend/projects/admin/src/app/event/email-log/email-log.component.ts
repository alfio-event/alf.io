import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MailService } from 'projects/admin/src/event/mail.service';
import { Observable, map, mergeMap, of } from 'rxjs';
import { EventService } from '../../shared/event.service';
import { Mail, MailInfo } from '../../model/mail';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-email-log',
  templateUrl: './email-log.component.html',
  styleUrls: ['./email-log.component.scss'],
})
export class EmailLogComponent implements OnInit {
  public mail$: Observable<Mail> = of();

  constructor(
    private readonly mailService: MailService,
    private readonly route: ActivatedRoute,
    private readonly eventService: EventService,
    private readonly translateService: TranslateService
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

  get dateTimeFormat(): string {
    return this.translateService.instant('admin.common.date-time');
  }
}
