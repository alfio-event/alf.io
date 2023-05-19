import { Component, Input, OnInit } from '@angular/core';
import { EventInfo } from '../../model/event';

@Component({
  selector: 'app-event-item',
  templateUrl: './event-item.component.html',
  styleUrls: ['./event-item.component.scss'],
})
export class EventItemComponent implements OnInit {
  @Input()
  public event: EventInfo | undefined;

  @Input()
  showImage: boolean = true;

  constructor() {}

  ngOnInit(): void {}
}
