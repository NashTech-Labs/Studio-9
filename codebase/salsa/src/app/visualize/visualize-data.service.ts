import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { AppHttp } from '../core/services/http.service';

import {
  GeospatialDataRequest,
  GeospatialDataResponse,
  TabularDataRequest,
  TabularDataResponse,
} from './visualize.interface';

@Injectable()
export class VisualizeDataService {

  constructor(protected http: AppHttp) {

  }

  fetchGeospatialData<T extends GeospatialDataRequest.Mode>(
    request: GeospatialDataRequest<T>,
  ): Observable<GeospatialDataResponse<T>> {
    const observable = this.http.post(`visualize/geo-data`, request);

    return AppHttp.execute(observable);
  }

  fetchTabularData(request: TabularDataRequest): Observable<TabularDataResponse> {
    const observable = this.http.post(`visualize/tabular-data`, request);

    return AppHttp.execute(observable);
  }
}
