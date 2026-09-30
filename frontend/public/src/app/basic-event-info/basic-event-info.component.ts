import {Component, Input, OnInit} from '@angular/core';
import {BasicEventInfo} from '../model/basic-event-info';
import {TranslateService} from '@ngx-translate/core';
import {Params} from '@angular/router';
import {getLocalizedContent} from '../shared/subscription.service';

@Component({
  selector: 'app-basic-event-info',
  templateUrl: './basic-event-info.component.html',
  styleUrls: ['./basic-event-info.component.scss']
})
export class BasicEventInfoComponent implements OnInit {

  @Input()
  event: BasicEventInfo;

  @Input()
  params: Params;

  constructor(private translateService: TranslateService) { }

  ngOnInit(): void {
  }


  public get isEventOnline(): boolean {
    return this.event.format === 'ONLINE';
  }

  get title(): string {
    return getLocalizedContent(this.event.title, this.translateService.currentLang);
  }

}
