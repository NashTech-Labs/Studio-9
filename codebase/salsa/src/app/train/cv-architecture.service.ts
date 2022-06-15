import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { AppHttp } from '../core/services/http.service';

import {
  ICVArchitecture,
  ICVClassifier,
  ICVDecoder,
  ICVDetector,
} from './cv-architecture.interfaces';

@Injectable()
export class CVArchitectureService {
  constructor(
    protected http: AppHttp,
  ) {
  }

  listArchitectures(): Observable<ICVArchitecture[]> {
    return this.http.get('config/cv-architectures');
  }

  listClassifiers(): Observable<ICVClassifier[]> {
    return this.http.get('config/cv-classifiers');
  }

  listDetectors(): Observable<ICVDetector[]> {
    return this.http.get('config/cv-detectors');
  }

  listDecoders(): Observable<ICVDecoder[]> {
    return this.http.get('config/cv-decoders');
  }
}
