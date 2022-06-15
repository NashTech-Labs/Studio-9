import { InjectionToken, Type } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { IColumnSortConfig } from '../core-ui/directives/grid-sort.directive';
import { IAsset, IAssetService, TObjectId } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';

export interface LibrarySectionDefinition<T extends IAsset, D = any> {
  assetType: IAsset.Type;
  icon: string;
  inProjects: boolean;
  service: IAssetService<T, any>;
  reloadOn?: IEvent.Type;
  baseRoute?: string[];
  viewComponent?: any;
  statusesDefinition: IAsset.StatusesDescription;
  completeStatus: string;
  actions: {[action: string]: {
    name: string;
    title?: string;
    iconClass: string;
  }};
  loadAsyncData?: () => Observable<D>;
  columns?: (IColumnSortConfig & {
    get: (item: T, asyncData?: D) => string;
  })[];
  selectorColumns?: (IColumnSortConfig & {
    get: (item: T, asyncData?: D) => string;
  })[];
  features?: Feature[];
  sharable?: boolean;
  sidebarActions?: LibrarySectionDefinition.SidebarAction[];
  bulkOperations?: LibrarySectionDefinition.BulkOperation<T>[];
}

export const LIBRARY_SECTIONS: InjectionToken<LibrarySectionDefinition<IAsset>[]> =
  new InjectionToken('LibrarySectionDefinition');

export namespace LibrarySectionDefinition {
  export interface ModalComponent {
    open(itemId?: TObjectId): Observable<any>;
  }

  export interface BulkModalComponent<T extends IAsset> {
    open(items: T[]): Observable<any>;
  }

  export interface SidebarAction {
    caption: string;
    navigateTo?: string[];
    modalClass?: Type<ModalComponent>; // TODO: remove this hack, open modals from service with onClick
  }

  export interface BulkOperation<T extends IAsset> {
    name: string;
    description?: string;
    iconClass: string;
    isAvailable?: (items: T[]) => boolean;
    modalClass?: Type<BulkModalComponent<T>>; // TODO: remove this hack, open modals from service with onClick
    onClick?: (items: IAsset[]) => void;
  }
}
