import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {EventService} from '../shared/event.service';
import {TranslateService} from '@ngx-translate/core';
import {Event} from '../model/event';
import {I18nService} from '../shared/i18n.service';

@Component({
  selector: 'app-live-poll',
  templateUrl: './poll.component.html',
  styleUrls: ['./poll.component.scss']
})
export class PollComponent implements OnInit {

  event: Event

  constructor(
    private route: ActivatedRoute,
    private eventService: EventService,
    public translate: TranslateService,
    public i18nService: I18nService) { }

  ngOnInit(): void {

    this.route.params.subscribe(params => {
      const eventShortName = params['eventShortName'];
      this.eventService.getEvent(eventShortName).subscribe(ev => {
        this.i18nService.setPageTitle('poll.page.title', ev);
        this.event = ev;
      });
    });
  }
}
