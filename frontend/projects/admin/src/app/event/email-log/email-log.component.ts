import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MailService } from 'projects/admin/src/event/mail.service';
import { Observable, map, mergeMap, of, retry, tap } from 'rxjs';
import { EventService } from '../../shared/event.service';
import { MailLog } from '../../model/mail';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-email-log',
  templateUrl: './email-log.component.html',
  styleUrls: ['./email-log.component.scss'],
})
export class EmailLogComponent implements OnInit {
  public mailLog$: Observable<MailLog> = of();

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
    if (!this.route.parent) {
      return;
    }
    this.mailLog$ = this.route.parent.paramMap.pipe(
      map((pm) => pm.get('eventId')),
      mergeMap((eventId) =>
        eventId != null ? this.eventService.getEvent(eventId) : of()
      ),
      mergeMap((event) => this.mailService.getAllMails(event.shortName, 0, ''))
    );
  }

  get dateTimeFormat(): string {
    return this.translateService.instant('admin.common.date-time');
  }
}
