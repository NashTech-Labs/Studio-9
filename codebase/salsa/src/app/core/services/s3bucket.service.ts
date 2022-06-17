import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { IS3BucketInfo } from '../core.interface';
import { IBackendList } from '../interfaces/common.interface';

import { AppHttp } from './http.service';

@Injectable()
export class S3BucketService {
  constructor(
    protected http: AppHttp,
  ) {
  }

  list(): Observable<IBackendList<IS3BucketInfo>> {
    return this.http.get('s3buckets', {}, {
      catchWith: () => Observable.of({data: [], count: 0}),
    });
  }

  listAWSRegions(): Observable<string[]> {
    return this.http.get('config/aws-regions', {}, {
      catchWith: () => Observable.of([]),
    });
  }
}
