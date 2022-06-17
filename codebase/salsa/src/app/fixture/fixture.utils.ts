import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';

import { IFixtureServiceRoute, IFixtureUser } from './fixture.interface';
import { FixtureService } from './fixture.service';

export function doRealFirst(fixtureRoutes: IFixtureServiceRoute[]) {
  return fixtureRoutes.map(oldRoute => {
    return {
      ...oldRoute,
      handler: function(
        this: FixtureService,
        params: any,
        user: IFixtureUser,
        next: () => Observable<HttpResponse<any>>,
      ) {
        if (user._isRealUser) {
          return next().catch((error, caught) => {
            if (error instanceof HttpErrorResponse) {
              if (error.status >= 500 || [401, 403].includes(error.status)) {
                try {
                  const fixtureResponse = oldRoute.handler.call(this, params, user, () => caught);
                  return fixtureResponse instanceof Observable ? fixtureResponse : Observable.of(fixtureResponse);
                } catch (e) {
                  return Observable.throw(e);
                }
              }

              return caught;
            }

            return caught;
          });
        }

        return oldRoute.handler.call(this, params, user, next);
      },
    };
  });
}
