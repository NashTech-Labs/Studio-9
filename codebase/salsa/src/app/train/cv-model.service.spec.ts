import { TestBed, async } from '@angular/core/testing';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IAsset } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { StorageService } from '../core/services/storage.service';
import { getMockCVModel, getMockProcess } from '../mocks/mocks';
import { MiscUtils } from '../utils/misc';

import { ICVModel, ICVModelUpdate } from './cv-model.interface';
import { CVModelService } from './cv-model.service';

describe('CVModelService', () => {
  const params = {page: 1, page_size: 5};
  let service: CVModelService,
    executeSpy: jasmine.Spy,
    http: jasmine.SpyObj<AppHttp>,
    sharedResourceService: jasmine.SpyObj<SharedResourceService>,
    notificationService: jasmine.SpyObj<NotificationService>,
    eventService: jasmine.SpyObj<EventService>,
    processService: jasmine.SpyObj<ProcessService>;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AppHttp,
          useValue: jasmine.createSpyObj(['get', 'post', 'execute', 'put', 'delete']),
        },
        {
          provide: StorageService,
          useValue: jasmine.createSpyObj([
            'get',
            'set',
            'remove',
            'clear',
          ]),
        },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj([
            'find',
            'create',
            'update',
            'delete',
          ]),
        },
        {
          provide: SharedResourceService,
          useValue: jasmine.createSpyObj([
            'withSharedAccess',
            'setHierarchyLink',
          ]),
        },
        {
          provide: EventService,
          useValue: jasmine.createSpyObj([
            'subscribe',
            'emit',
            'filter',
          ]),
        },
        {
          provide: ProcessService,
          useValue: jasmine.createSpyObj([
            'find',
            'get',
            'getByTarget',
            'cancel',
            'observe',
            'subscribeByTarget',
          ]),
        },
        CVModelService,
      ],
    });
    eventService = TestBed.get(EventService);
    eventService.filter.and.returnValue(Observable.of({data: {target: null}, type: config.asset.values.CV_MODEL}));
    sharedResourceService = TestBed.get(SharedResourceService);
    service = TestBed.get(CVModelService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
    http = TestBed.get(AppHttp);
    notificationService = TestBed.get(NotificationService);
    processService = TestBed.get(ProcessService);
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of cv-models', async(() => {
    const expectedResult = {data: [getMockCVModel({id: 'cvModelId'})], count: 1};
    http.get.and.returnValue(Observable.of(expectedResult));
    service.list(params).subscribe((result) => {
      expect(result).toEqual(expectedResult);
      expect(http.get).toHaveBeenCalledTimes(1);
      expect(http.get).toHaveBeenCalledWith('cv-models', params);
    });
  }));

  it('should not create cv-model', async(() => {
    service.create(null).subscribe(
      () => {
        fail('Unwanted branch');
      },
      () => {},
      () => {
        fail('Unwanted branch');
      },
    );
  }));

  it('should get cv-model', async(() => {
    const expectedResult: ICVModel = getMockCVModel({id: 'cvModelId'});
    const getSpy = jasmine.createSpy('get').and.returnValue(Observable.of(expectedResult));
    sharedResourceService.withSharedAccess.and.returnValue({
      get: getSpy,
    });
    service.get(expectedResult.id).subscribe((result) => {
      expect(result).toEqual(expectedResult);
      expect(getSpy).toHaveBeenCalledTimes(1);
      expect(getSpy).toHaveBeenCalledWith(`cv-models/${expectedResult.id}`);
      expect(sharedResourceService.withSharedAccess).toHaveBeenCalledTimes(1);
      expect(sharedResourceService.withSharedAccess).toHaveBeenCalledWith('CV_MODEL', expectedResult.id);
    });
  }));

  it('should update cv-model', async(() => {
    const expectedResult: ICVModel = getMockCVModel({id: 'modelId', name: 'updated CVModel Name'});
    const data: ICVModelUpdate = {
      name: 'updated CVModel Name',
    };
    http.put.and.returnValue(Observable.of(expectedResult));
    service.update(expectedResult.id, data).subscribe((result) => {
      expect(result).toEqual(expectedResult);
      expect(http.put).toHaveBeenCalledTimes(1);
      expect(http.put).toHaveBeenCalledWith(`cv-models/${expectedResult.id}`, data);
      expect(executeSpy).toHaveBeenCalledTimes(1);
      expect(executeSpy.calls.first().args[0]).toEqual(Observable.of(expectedResult));
      expect(eventService.emit).toHaveBeenCalledTimes(2);
      expect(eventService.emit.calls.first().args).toEqual([IEvent.Type.UPDATE_CV_MODEL_LIST]);
      expect(eventService.emit.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_CV_MODEL, {id: expectedResult.id}]);
      expect(notificationService.create).toHaveBeenCalledTimes(1);
      expect(notificationService.create).toHaveBeenCalledWith('Model updated: ' + expectedResult.name);
    });
  }));

  it('should delete cv-model', async(() => {
    const expectedResult = getMockCVModel();
    http.delete.and.returnValue(Observable.of(expectedResult));
    service.delete(expectedResult).subscribe((result) => {
      expect(result).toEqual(expectedResult);
      expect(http.delete).toHaveBeenCalledTimes(1);
      expect(http.delete).toHaveBeenCalledWith(`cv-models/${expectedResult.id}`);
      expect(eventService.emit).toHaveBeenCalledTimes(2);
      expect(eventService.emit.calls.first().args).toEqual([IEvent.Type.UPDATE_CV_MODEL_LIST]);
      expect(eventService.emit.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_CV_MODEL, {id: expectedResult.id}]);
      expect(notificationService.create).toHaveBeenCalledTimes(1);
      expect(notificationService.create).toHaveBeenCalledWith('Model deleted: ' + expectedResult.name);
    });
  }));

  it('should return URL for exportUrl', () => {
    const cvModelId = 'cvModelId1';
    const expectedResult = Observable.of('string');
    const getSpy = jasmine.createSpy('get').and.returnValue(expectedResult);
    sharedResourceService.withSharedAccess.and.returnValue({
      get: getSpy,
    });
    expect(service.exportUrl(cvModelId)).toEqual(expectedResult);
    expect(sharedResourceService.withSharedAccess).toHaveBeenCalledTimes(1);
    expect(sharedResourceService.withSharedAccess).toHaveBeenCalledWith(IAsset.Type.CV_MODEL, cvModelId);
  });

  it('should trigger downloadUrl on download', async(() => {
    const expectedResult = Observable.of('string');
    const getSpy = jasmine.createSpy('get').and.returnValue(expectedResult);
    sharedResourceService.withSharedAccess.and.returnValue({
      get: getSpy,
    });
    const spy = spyOn(MiscUtils, 'downloadUrl').and.returnValue(Observable.of(true));
    service.download('cvModelId1').subscribe((result) => {
      expect(spy).toHaveBeenCalledWith(`string`, 'cvModelId1.bin');
      expect(result).toEqual(true);
    });
  }));

  it('should return no Active process for active model', () => {
    expect(service.getActiveProcess(getMockCVModel({status: ICVModel.Status.ACTIVE}))).toEqual(Observable.of(null));
  });

  it('should get Active process for progressing model', () => {
    const expectedResult = getMockProcess();
    processService.getByTarget.and.returnValue(Observable.of(expectedResult));
    expect(service.getActiveProcess(getMockCVModel({status: ICVModel.Status.TRAINING}))).toEqual(Observable.of(expectedResult));
  });
});
