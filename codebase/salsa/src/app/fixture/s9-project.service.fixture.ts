import 'rxjs/add/operator/concatMap';
import 'rxjs/add/operator/delay';
import { Observable } from 'rxjs/Observable';

import { TObjectId } from '../core/interfaces/common.interface';
import { IS9ProjectSession } from '../develop/s9-project.interfaces';
import { S9ProjectService } from '../develop/s9-project.service';

export class S9ProjectServiceFixture extends S9ProjectService {
  getSessionStatusStream(s9ProjectId: TObjectId): Observable<IS9ProjectSession.Status> {
    return Observable.from([
      IS9ProjectSession.Status.QUEUED,
      IS9ProjectSession.Status.SUBMITTED,
      IS9ProjectSession.Status.RUNNING,
      IS9ProjectSession.Status.COMPLETED,
    ])
      .concatMap(status => Observable.of(status).delay(2000))
      .do(status => {
        console.log(status);
      });
  }
}
