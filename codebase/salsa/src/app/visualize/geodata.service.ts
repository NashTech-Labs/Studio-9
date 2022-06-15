import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import * as _ from 'lodash';
import { NumericDictionary } from 'lodash';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { MemoizeObservable } from '../core/core.memoizeObservable';

const US_MAP_URL = require('file-loader?name=assets/us-atlas.[hash].json!us-atlas/us/10m.json');

export interface IUsStateCounty {
  id: number;
  code: string;
  name: string;
}

export interface IUSLocationRow {
  zip_code: number;
  latitude: number;
  longitude: number;
  city: string;
  state: string;
  county: string;
  stateName: string;
}

@Injectable()
export class GeoDataService {
  constructor(
    private http: HttpClient,
  ) {}

  @MemoizeObservable()
  getUSMap(): Observable<any> {
    return this.http.get(US_MAP_URL, {responseType: 'json'});
  }

  @MemoizeObservable()
  getCountyNames(): Observable<NumericDictionary<IUsStateCounty>> {
    return this.http.get('/assets/topojson/us-county-names.json', {responseType: 'json'})
      .map((counties: IUsStateCounty[]) => {
        return _.keyBy(counties, county => {
          return county.id;
        });
      });
  }

  @MemoizeObservable()
  getStateNames(): Observable<NumericDictionary<IUsStateCounty>> {
    return this.http.get('/assets/topojson/us-state-names.json', {responseType: 'json'})
      .map((states: IUsStateCounty[]) => {
        return _.keyBy(states, state => {
          return state.id;
        });
      });
  }

  @MemoizeObservable()
  getZIPCoords(): Observable<NumericDictionary<IUSLocationRow>> {
    return this.http.get('/assets/topojson/us_common_data.json', {responseType: 'json'})
      .map((objects: IUSLocationRow[]) => {
        return _.keyBy(objects, object => {
          return object.zip_code;
        });
      });
  }
}
