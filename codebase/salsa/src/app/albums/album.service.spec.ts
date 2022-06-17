import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, inject } from '@angular/core/testing';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IListRequest } from '../core/interfaces/common.interface';
import { INotification } from '../core/interfaces/notification.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { StorageService } from '../core/services/storage.service';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { SharedResourceServiceMock } from '../mocks/shared-resource.service.mock';
import { StorageServiceMock } from '../mocks/storage.service.mock';
import { MiscUtils } from '../utils/misc';

import {
  IAlbum,
  IAlbumCreate,
  IAlbumImportFromS3,
  IAlbumImportLabelsFromS3,
  IAlbumUpdate,
  IAlbumUploadParams,
  IPicture,
  IPictureSave,
  IPictureSearchParams,
} from './album.interface';
import { AlbumService } from './album.service';

describe('AlbumService', () => {
  const params: IListRequest = {
    page: 1,
    page_size: 5,
  };
  let service: AlbumService,
    executeSpy: jasmine.Spy;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        {
          provide: EventService,
          useClass: EventServiceMock,
        },
        {
          provide: SharedResourceService,
          useClass: SharedResourceServiceMock,
        },
        {
          provide: AppHttp,
          useClass: AppHttpMock,
        },
        {
          provide: StorageService,
          useClass: StorageServiceMock,
        },
        ProcessService,
        AlbumService,
      ],
    });
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
    service = TestBed.get(AlbumService);
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return a list of albums', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['albums', params]);
  }));

  it('should create an album', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IAlbumCreate = {
      name: 'new Album',
      labelMode: IAlbum.LabelMode.LOCALIZATION,
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['albums', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.CREATE_ALBUM, {
      name: 'new Album',
      labelMode: IAlbum.LabelMode.LOCALIZATION,
    }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
  }));

  it('should get an album', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callThrough();
    service.get('albumId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);
  }));


  it('should update an album', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IAlbumUpdate = {
      name: 'updated Album',
      labelMode: IAlbum.LabelMode.CLASSIFICATION,
    };
    service.update('updateAlbumId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['albums/updateAlbumId', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_ALBUM, { id: 'updateAlbumId' }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
  }));

  it('should upload picture to an album', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpUploadSpy = spyOn(http, 'upload').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    let file: File;
    try {
      file = new File([''], 'filename.txt', { type: 'text/plain' });
    } catch (_) {
      const f = new Blob(['The content of your file']);
      file = <File> Object.assign({}, f, { name: 'filename.txt' });
    }

    const data: IAlbumUploadParams = {
      filename: 'filename',
      file,
    };

    createNotificationSpy.and.returnValue(<INotification> { options: { timeout: 5 } });

    service.uploadPicture('updateAlbumId', data).subscribe();
    expect(httpUploadSpy.calls.count()).toBe(1);
    expect(httpUploadSpy.calls.mostRecent().args[0]).toEqual('albums/updateAlbumId/uploadPicture');
    expect(httpUploadSpy.calls.mostRecent().args[1]).toEqual(data.file);
    expect(httpUploadSpy.calls.mostRecent().args[2]).toEqual({ filename: data.filename });

    expect(executeSpy.calls.count()).toBe(1);

    expect(emitEventSpy.calls.count()).toBe(3);
    expect(emitEventSpy.calls.argsFor(0)).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.argsFor(1)).toEqual([IEvent.Type.UPDATE_ALBUM, { id: 'updateAlbumId' }]);
    expect(emitEventSpy.calls.argsFor(2)).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, { albumId: 'updateAlbumId' }]);
  }));

  it('should upload labels an album', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpUploadSpy = spyOn(http, 'upload').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    let file: File;
    try {
      file = new File([''], 'filename.txt', { type: 'text/plain' });
    } catch (_) {
      const f = new Blob(['The content of your file']);
      file = <File> Object.assign({}, f, { name: 'filename.txt' });
    }

    createNotificationSpy.and.returnValue(<INotification> { options: { timeout: 5 } });

    service.uploadLabels('updateAlbumId', file).subscribe();
    expect(httpUploadSpy.calls.count()).toBe(1);
    expect(httpUploadSpy.calls.mostRecent().args[0]).toEqual('albums/updateAlbumId/uploadLabels');
    expect(httpUploadSpy.calls.mostRecent().args[1]).toEqual(file);

    expect(executeSpy.calls.count()).toBe(1);

    expect(emitEventSpy.calls.count()).toBe(3);
    expect(emitEventSpy.calls.argsFor(0)).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.argsFor(1)).toEqual([IEvent.Type.UPDATE_ALBUM, { id: 'updateAlbumId' }]);
    expect(emitEventSpy.calls.argsFor(2)).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, { albumId: 'updateAlbumId' }]);
  }));

  it('should import labels from S3', inject([AppHttp, EventService], (http: AppHttp, eventService: EventService) => {
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IAlbumImportLabelsFromS3 = {
      AWSS3BucketName: 'AWSS3BucketName',
      AWSAccessKey: 'AWSAccessKey',
      AWSSecretKey: 'AWSSecretKey',
      AWSSessionToken: 'AWSSessionToken',
      S3CSVPath: 'S3CSVPath',
    };
    service.importLabelsFromS3('albumId', data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['albums/albumId/importLabelsFromS3', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(3);
    expect(emitEventSpy.calls.argsFor(0)).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.argsFor(1)).toEqual([IEvent.Type.UPDATE_ALBUM, { id: 'albumId' }]);
    expect(emitEventSpy.calls.argsFor(2)).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, { albumId: 'albumId' }]);
  }));

  it('should import from AWS', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpUploadSpy = spyOn(http, 'upload').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    let file: File;
    try {
      file = new File([''], 'filename.txt', { type: 'text/plain' });
    } catch (_) {
      const f = new Blob(['The content of your file']);
      file = <File> Object.assign({}, f, { name: 'filename.txt' });
    }

    createNotificationSpy.and.returnValue(<INotification> { options: { timeout: 5 } });

    const data: IAlbumImportFromS3 = {
      AWSS3BucketName: 'AWSS3BucketName',
      AWSRegion: 'AWSRegion',
      AWSAccessKey: 'AWSAccessKey',
      AWSSecretKey: 'AWSSecretKey',
      AWSSessionToken: 'AWSSessionToken',
      S3ImagesPath: 'S3ImagesPath',
      S3CSVPath: 'S3CSVPath',
      labels: file,
      applyLogTransformation: true,
    };

    service.importPicturesFromS3('updateAlbumId', data).subscribe();
    expect(httpUploadSpy.calls.count()).toBe(1);
    expect(httpUploadSpy.calls.mostRecent().args[0]).toEqual('albums/updateAlbumId/importPicturesFromS3');
    expect(httpUploadSpy.calls.mostRecent().args[1]).toEqual(file);

    expect(executeSpy.calls.count()).toBe(1);

    expect(emitEventSpy.calls.count()).toBe(3);
    expect(emitEventSpy.calls.argsFor(0)).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.argsFor(1)).toEqual([IEvent.Type.UPDATE_ALBUM, { id: 'updateAlbumId' }]);
    expect(emitEventSpy.calls.argsFor(2)).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, { albumId: 'updateAlbumId' }]);
  }));

  it('should delete album', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IAlbum = {
      id: 'albumId',
      ownerId: 'ownerId1',
      name: 'Album Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      type: IAlbum.Type.SOURCE,
      status: IAlbum.Status.ACTIVE,
      labelMode: IAlbum.LabelMode.LOCALIZATION,
    };
    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['albums/' + data.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_ALBUM_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_ALBUM, { id: 'albumId' }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
  }));

  it('should return a picture list', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const album: IAlbum = {
      id: 'albumId',
      ownerId: 'ownerId1',
      name: 'Album Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      type: IAlbum.Type.SOURCE,
      status: IAlbum.Status.ACTIVE,
      labelMode: IAlbum.LabelMode.LOCALIZATION,
    };
    const params: IPictureSearchParams = {
      labels: ['a', 'b', 'c'],
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: (...args) => Observable.of([args[0], args[1]]),
      };
    });
    service.pictureList(album, params).subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(['albums/albumId/pictures', params]));
  }));

  it('should getPicture', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: (...args) => Observable.of([args[0], args[1]]),
      };
    });
    service.getPicture('albumId', 'pictureId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);
  }));

  it('should saveTags', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IPictureSave = {
      tags: [
        {
          label: 'label',
          area: {
            top: 0,
            left: 0,
            height: 100,
            width: 100,
          },
        },
      ],
      caption: 'Caption',
    };
    service.saveTags('albumId', 'pictureId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args[0]).toEqual('albums/albumId/pictures/pictureId');
    expect(httpPutSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, {albumId: 'albumId'}]);

    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_PICTURE, { id: 'pictureId', albumId: 'albumId' }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
  }));

  it('should getTags', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callThrough();
    service.getTags('albumId').subscribe();
    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);
  }));

  it('should deletePicture', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const album: IAlbum = {
      id: 'albumId',
      ownerId: 'ownerId1',
      name: 'Album Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      type: IAlbum.Type.SOURCE,
      status: IAlbum.Status.ACTIVE,
      labelMode: IAlbum.LabelMode.LOCALIZATION,
    };
    const picture: IPicture = {
      id: 'pictureId',
      albumId: 'albumId',
      filename: 'filename',
      filepath: 'path',
      filesize: 1,
      tags: [],
    };
    service.deletePicture(album, picture).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['albums/' + album.id + '/pictures/' + 'pictureId']);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_PICTURE_LIST, {albumId: album.id}]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_PICTURE, {id: picture.id, albumId: album.id}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
  }));

  it('should exportUrl', (done) => inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const getSharedAccessParamsSpy = spyOn(sharedResourceService, 'getSharedAccessParams').and
      .returnValue(Observable.of({ shared_resource_id: 'resourceId' }));
    service.exportUrl('albumId', 'testtoken').subscribe((url) => {
      expect(url).toBe(`${config.api.base}albums/albumId/export?access_token=testtoken&shared_resource_id=resourceId`);
      expect(getSharedAccessParamsSpy.calls.count()).toBe(1);
      expect(getSharedAccessParamsSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);
      done();
    });
  })());

  it('should download', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const currentUrl = `${config.api.base}albums/albumId/export?access_token=testtoken&shared_resource_id=resourceId`;
    const downloadSpy = spyOn(MiscUtils, 'downloadUrl').and.returnValue(Observable.of(true));
    const getSharedAccessParamsSpy = spyOn(sharedResourceService, 'getSharedAccessParams').and.callThrough();
    getSharedAccessParamsSpy.and.returnValue(Observable.of({ shared_resource_id: 'resourceId' }));
    service.download('albumId', 'testtoken');
    expect(getSharedAccessParamsSpy.calls.count()).toBe(1);
    expect(getSharedAccessParamsSpy.calls.mostRecent().args).toEqual(['ALBUM', 'albumId']);
    expect(downloadSpy).toHaveBeenCalledWith(currentUrl, 'albumId.csv');
  }));

  it('should allow merging active source classification albums', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(true);
  });

  it('should allow merging active source localization albums', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.LOCALIZATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.LOCALIZATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(true);
  });

  it('should disallow merging derived albums', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.DERIVED,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(false);
  });

  it('should disallow merging train-result albums', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.TRAINRESULTS,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(false);
  });

  it('should disallow merging non-active albums', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.SAVING,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(false);
  });

  it('should disallow merging albums with different labelMode', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
      {
        id: 'albumId2',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.LOCALIZATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(false);
  });

  it('should disallow merging albums if there is less than two of them', () => {
    const albums: IAlbum[] = [
      {
        id: 'albumId',
        ownerId: 'ownerId1',
        name: 'Album Name',
        updated: '2017-04-06T03:15:23.570Z',
        created: '2017-04-06T03:15:23.570Z',
        type: IAlbum.Type.SOURCE,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
      },
    ];

    expect(service.isMergeAvailable(albums)).toBe(false);
  });
});
