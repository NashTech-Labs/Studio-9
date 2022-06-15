import { Observable } from 'rxjs/Observable';

import { TObjectId } from './common.interface';

export interface INotification {
  id: TObjectId;
  type: string; // see config.notification.type
  created: number; // timestamp
  options?: INotificationOptions; // reserved
  actions?: INotificationActions; // reserved
  data: INotificationData; // aka payload
  dataAsync?: Observable<INotificationData>; // aka payload async
}

export interface INotificationOptions {
  timeout?: number; // milliseconds
  dismissButton?: boolean;
  dismissOnClick?: boolean;
  onDismiss?: any; // function;
  pauseOnHover?: boolean;
  //combineDuplications: boolean;
  //...
}

export interface INotificationActions {
  foo: string; // reserved
}

export interface INotificationData {
  level?: string; // reserved / see config.notification.level
  text?: string;
  html?: string;
  file?: File;
  files?: File[];
  progress?: number;
  cancel?: () => void;
  canCancel?: boolean;
}
