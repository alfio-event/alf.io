import {Params} from '@angular/router';
import {HttpParams} from '@angular/common/http';

export class SearchParams {
    public constructor(private subscription: string,
                       private organizer: string,
                       public organizerSlug: string,
                       private tags: Array<string>) {
    }

    public static fromQueryAndPathParams(params: Params, pathParams: Params): SearchParams {
      return new SearchParams(params.subscription, params.organizer, pathParams.organizerSlug, params.tags);
    }

    public static transformParams(params: Params, pathParams: Params): Params {
      return SearchParams.fromQueryAndPathParams(params, pathParams).toParams();
    }

    public toHttpParams(): HttpParams {
      return new HttpParams({
          fromObject: this.extractParams()
      });
    }

    public toParams(): Params {
      return this.extractParams();
    }

    private extractParams(): { [param: string]: string | ReadonlyArray<string> } {
      const obj: { [param: string]: string | ReadonlyArray<string> } = {};
      if (this.subscription != null) {
        obj.subscription = this.subscription;
      }
      if (this.organizer != null) {
        obj.organizer = this.organizer;
      }
      if (this.tags != null) {
        obj.tags = this.tags;
      }
      if (this.organizerSlug != null) {
        obj.organizerSlug = this.organizerSlug;
      }
      return obj;
    }

}
