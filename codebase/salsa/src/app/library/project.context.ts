import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';

import { TObjectId } from '../core/interfaces/common.interface';

export class ProjectContext {
  private state = new BehaviorSubject<[TObjectId, TObjectId]>([null, null]);

  set(projectId: TObjectId, folderId?: TObjectId): void {
    this.state.next([projectId, folderId]);
  }

  get(): [TObjectId, TObjectId] {
    return this.state.value;
  }

  get value(): Observable<[TObjectId, TObjectId]> {
    return this.state.asObservable();
  }
}
