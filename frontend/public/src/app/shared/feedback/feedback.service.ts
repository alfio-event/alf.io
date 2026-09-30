import {Injectable} from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {FeedbackContent} from '../../model/feedback';

@Injectable({
  providedIn: 'root'
})
export class FeedbackService {

  private toastSubject = new Subject<FeedbackContent>();

  public showSuccess(message: string) {
    this.toastSubject.next({
      active: true,
      message,
      type: 'SUCCESS'
    });
  }

  public showError(message: string) {
    this.toastSubject.next({
      active: true,
      message,
      type: 'ERROR'
    });
  }

  public showInfo(message: string) {
    this.toastSubject.next({
      active: true,
      message,
      type: 'INFO'
    });
  }

  public hide() {
    this.toastSubject.next({ active: false });
  }

  public displayNotification(): Observable<FeedbackContent> {
    return this.toastSubject;
  }

}
