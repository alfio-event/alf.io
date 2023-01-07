import {Component} from '@angular/core';
import {FeedbackService} from './feedback.service';
import {FeedbackType} from '../../model/feedback';
import {IconProp} from '@fortawesome/fontawesome-svg-core';


@Component({
  selector: 'app-feedback',
  templateUrl: './feedback.component.html',
  styleUrls: ['./feedback.component.scss']
})
export class FeedbackComponent {
  text: string;
  active: boolean;
  private type: FeedbackType;

  constructor(private feedbackService: FeedbackService) {
    this.feedbackService.displayNotification().subscribe(details => {
      this.active = details.active;
      this.text = details.message;
      this.type = details.type || 'INFO';
    });
  }

  public hide(): void {
    this.active = false;
  }

  get boxClass(): string {
    switch (this.type) {
      case 'SUCCESS':
        return 'border-success text-success';
      case 'ERROR':
        return 'border-danger text-danger';
      case 'INFO':
        return 'border-primary text-primary';
    }
  }

  get headerClass(): string {
    switch (this.type) {
      case 'SUCCESS':
        return 'bg-success text-white';
      case 'ERROR':
        return 'bg-danger text-white';
      case 'INFO':
        return 'bg-white text-primary';
    }
  }

  get boxIcon(): IconProp {
    switch (this.type) {
      case 'SUCCESS':
        return ['far', 'check-circle'];
      case 'ERROR':
        return ['fas', 'exclamation-circle'];
      case 'INFO':
        return ['fas', 'info-circle'];
    }
  }

}
