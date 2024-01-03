import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {Poll} from '../model/poll';
import {PollWithOptions} from '../model/poll-with-options';
import {PollVotePayload} from '../model/poll-vote-form';
import {ValidatedResponse} from '../../model/validated-response';
import {HttpClient} from '@angular/common/http';

@Injectable()
export class PollService {

  constructor(private http: HttpClient) { }


  getAllPolls(eventName: string, pin: string): Observable<ValidatedResponse<Poll[]>> {
    return this.http.get<ValidatedResponse<Poll[]>>(`/api/v2/public/event/${eventName}/poll`, {params: {pin: pin}});
  }

  getPoll(eventName: string, pollId: number, pin: string): Observable<ValidatedResponse<PollWithOptions>> {
    return this.http.get<ValidatedResponse<PollWithOptions>>(`/api/v2/public/event/${eventName}/poll/${pollId}`, {params: {pin: pin}});
  }

  registerAnswer(eventName: string, pollId: number, pollForm: PollVotePayload): Observable<ValidatedResponse<boolean>> {
    return this.http.post<ValidatedResponse<boolean>>(`/api/v2/public/event/${eventName}/poll/${pollId}/answer`, pollForm);
  }
}
