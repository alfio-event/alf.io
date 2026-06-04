import {
    type HttpEvent,
    type HttpHandler,
    type HttpInterceptor,
    type HttpRequest,
    HttpResponse,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { UserService } from './shared/user.service';

@Injectable()
export class HeaderInformationRetriever implements HttpInterceptor {
    constructor(private userService: UserService) {}

    intercept(
        req: HttpRequest<any>,
        next: HttpHandler,
    ): Observable<HttpEvent<any>> {
        return next.handle(req).pipe(
            tap({
                next: (res) => {
                    if (req.method === 'GET' && res instanceof HttpResponse) {
                        this.userService.updateAuthenticationStatus(
                            'true' === res.headers.get('Alfio-OpenId-Enabled'),
                        );
                    }
                },
            }),
        );
    }
}
