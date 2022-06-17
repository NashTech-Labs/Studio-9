import { HttpRequest } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import * as _ from 'lodash';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IObjectId } from '../core/interfaces/common.interface';
import { EventService } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { StorageService } from '../core/services/storage.service';
import { flows as fixtureFlows } from '../fixture/fixtures/flows';
import { EventServiceMock } from '../mocks/event.service.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { StorageServiceMock } from '../mocks/storage.service.mock';

import { IBackendFlow, IFlowstep } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepService } from './flowstep.service';

describe('FlowstepService', () => {
  let service: FlowstepService;
  let httpMock: HttpTestingController;
  const baseUrl = config.api.base;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        AppHttp,
        {
          provide: StorageService,
          useClass: StorageServiceMock,
        },
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        {
          provide: EventService,
          useClass: EventServiceMock,
        },
        FlowstepService,
      ],
    });
    spyOn(TestBed.get(EventService), 'filter').and.callFake(() => {
      return Observable.of({
        data: {
          target: {},
        },
      });
    });
    service = TestBed.get(FlowstepService);
    httpMock = TestBed.get(HttpTestingController);
  });

  it('should get flowstep', (done) => {
    let flow = fixtureFlows.data[0];
    let step = (<any> flow).steps[0];
    const deserializedFlowstep = FlowstepService._deserializeFlowstep(step);
    service.get(flow.id, step.id).subscribe((flowstep: IFlowstep) => {
      expect(flowstep).toEqual(deserializedFlowstep);
      expect(service.data.view).toEqual(flowstep);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${flow.id}/steps/${step.id}` && request.method === 'GET');
    });
    request.flush(step, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should create flowstep by item', (done) => {
    const deserializedFlowsList = (<IBackendFlow[]> fixtureFlows.data).map(flow => FlowService._deserializeFlow(_.cloneDeep(flow)));
    const flow = deserializedFlowsList[0];
    const step = flow.steps[0];

    service.create(flow.id, step).subscribe((flowstep: IFlowstep) => {
      expect(flowstep).toEqual(step);
      expect(service.data.edit).toEqual(step);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${flow.id}/steps` && request.method === 'POST');
    });
    request.flush(fixtureFlows.data[0].steps[0], {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should update flowstep by item with id', (done) => {
    const deserializedFlowsList = (<IBackendFlow[]> fixtureFlows.data).map(flow => FlowService._deserializeFlow(_.cloneDeep(flow)));
    const flow = deserializedFlowsList[0];
    const step = flow.steps[0];

    service.update(flow.id, step.id, { name: step.name }).subscribe((flowstep: IFlowstep) => {
      expect(flowstep).toEqual(step);
      expect(service.data.view).toEqual(step);
      done();
    });

    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${flow.id}/steps/${step.id}` && request.method === 'PUT');
    });
    request.flush(fixtureFlows.data[0].steps[0], {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should delete flowstep by item', (done) => {
    const flow = fixtureFlows.data[0];
    const step = (<any> flow).steps[0];

    service.get(flow.id, step.id).subscribe(() => {
      expect(service.data.view).toEqual(FlowstepService._deserializeFlowstep(step));
      service.delete(flow.id, step).subscribe((data: IObjectId) => {
        expect(service.data.view).toBeNull();
        expect(data.id).toEqual(step.id);
        done();
      });
    });
    const getTequest = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${flow.id}/steps/${step.id}` && request.method === 'GET');
    });
    getTequest.flush(step, {
      status: 200,
      statusText: 'OK',
    });
    const deleteTequest = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}flows/${flow.id}/steps/${step.id}` && request.method === 'DELETE');
    });
    deleteTequest.flush({ id: step.id }, {
      status: 200,
      statusText: 'OK',
    });
  });
});
