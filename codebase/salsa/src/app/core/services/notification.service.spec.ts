import { TestBed, inject } from '@angular/core/testing';

import { ReplaySubject } from 'rxjs/ReplaySubject';

import config from '../../config';

import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        NotificationService,
      ],
    }),
  );

  it('Should find none in empty list', inject([NotificationService], (service: NotificationService) => {
    expect(service.find('1')).toBeUndefined();
  }));

  it('Should find something and create complicated notification', inject([NotificationService], (service: NotificationService) => {
    const notificationDataObservable = new ReplaySubject();
    service.create({ id: '1', type: config.notification.type.values.WARNING, dataAsync: notificationDataObservable });
    const notificationData = {
      text: 'Reading',
      progress: 0,
    };
    const notification = service.data[0];
    expect(notification.type).toEqual('WARNING');
    notificationDataObservable.next(Object.assign({}, notificationData));
    notificationDataObservable.subscribe(() => {
      expect(notification.data).toEqual(notificationData);
    });
  }));

  it('Should create simple notification', inject([NotificationService], (service: NotificationService) => {
    service.create('text');
    const notification = service.data[0];
    expect(notification.data).toEqual({ level: 'info', text: 'text' });
    expect(notification.type).toEqual('DEFAULT');
    expect(notification.options).toEqual({
      timeout: config.notification.defaults.timeout,
      dismissButton: config.notification.defaults.dismissButton,
      dismissOnClick: config.notification.defaults.dismissOnClick,
      onDismiss: config.notification.defaults.onDismiss,
      pauseOnHover: config.notification.defaults.pauseOnHover,
    });
  }));

  /*it('Should update notification', inject([NotificationService], (service: NotificationService) => {
    service.create('text');
    const notification = service.data[0];
    expect(service.update(notification.id, notification)).toEqual(notification);
  }));*/

  it('Should delete notifications', inject([NotificationService], (service: NotificationService) => {
    service.create('text');
    service.create('text1');
    const notification1 = service.data[0];
    const notification2 = service.data[1];
    service.delete(notification1.id);
    expect(service.data).toEqual([notification2]);
  }));
});
