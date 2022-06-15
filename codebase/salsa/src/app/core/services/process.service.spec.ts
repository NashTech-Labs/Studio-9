import { HttpRequest } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import config from '../../config';
import { IAsset } from '../interfaces/common.interface';
import { IProcess } from '../interfaces/process.interface';

import { EventService } from './event.service';
import { AppHttp } from './http.service';
import { NotificationService } from './notification.service';
import { ProcessService } from './process.service';
import { StorageService } from './storage.service';

describe('ProcessService', () => {
  const baseUrl = config.api.base;
  let service: ProcessService;
  let httpMock: HttpTestingController;
  let processes: IProcess[] = [
    {
      id: '101',
      ownerId: '1',
      targetId: '101',
      target: IAsset.Type.TABLE,
      status: IProcess.Status.RUNNING,
      progress: 0.3,
      estimate: 6000,
      created: '2016-09-09T09:42:19.245Z',
      started: '2016-09-09T09:42:19.245Z',
      completed: '2016-09-23T13:05:52.359Z',
      jobType: IProcess.JobType.TABULAR_UPLOAD,
    },
    {
      id: '102',
      ownerId: '1',
      targetId: '102',
      target: IAsset.Type.TABLE,
      status: IProcess.Status.RUNNING,
      progress: 0.1,
      estimate: 6000,
      created: '2016-09-09T09:42:19.245Z',
      started: '2016-09-09T09:42:19.245Z',
      completed: '2016-09-23T13:05:52.359Z',
      jobType: IProcess.JobType.TABULAR_UPLOAD,
    },
    {
      id: '103',
      ownerId: '1',
      targetId: '103',
      target: IAsset.Type.TABLE,
      status: IProcess.Status.RUNNING,
      progress: 0.1,
      estimate: 6000,
      created: '2016-09-09T09:42:19.245Z',
      started: '2016-09-09T09:42:19.245Z',
      completed: '2016-09-23T13:05:52.359Z',
      jobType: IProcess.JobType.TABULAR_UPLOAD,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        AppHttp,
        StorageService,
        ProcessService,
        NotificationService,
        EventService,
      ],
    });
    httpMock = TestBed.get(HttpTestingController);
    service = TestBed.get(ProcessService);
  });

  it('Should get process by id', (done) => {
    let process = processes[0];

    service.get(process.id).subscribe(() => {
      expect(service.data.view).toEqual(process);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}processes/${process.id}` && request.method === 'GET');
    });
    request.flush(process, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('Should getByTarget table', (done) => {
    const process = processes[0];

    service.getByTarget(process.id, process.target).subscribe(() => {
      expect(service.data.view).toEqual(process);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}${config.asset.aliasesPlural[process.target]}/${process.id}/process` && request.method === 'GET');
    });
    request.flush(process, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('Should getByTarget SOME-OTHER-TYPE', (done) => {
    let process = processes[0];
    service.getByTarget(process.id, <IAsset.Type> 'otherType').subscribe(() => {
      expect(service.data.view).toEqual(process);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${process.id}/process` && request.method === 'GET');
    });
    request.flush(process, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('if no interval', () => {
    config.process.interval = null;
    expect(service.INTERVAL).toBeDefined();
  });

  it('should find []', () => {
    let process = processes[0];
    expect(service.find(process.id)).toBeUndefined();
  });
});
