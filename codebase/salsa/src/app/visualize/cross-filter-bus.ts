import { BehaviorSubject } from 'rxjs/BehaviorSubject';

import { TObjectId } from '../core/interfaces/common.interface';

import { IDashboardXFilter } from './dashboard.interface';
import { TabularDataRequest } from './visualize.interface';

export interface CrossFilterValue {
  tableId: TObjectId;
  filter: TabularDataRequest.Filter;
}

export class CrossFilterBus extends BehaviorSubject<CrossFilterValue[]> {
  private _crossFilters: IDashboardXFilter[];

  constructor() {
    super([]);
  }

  get crossFilters(): IDashboardXFilter[] {
    return this._crossFilters;
  }

  set crossFilters(value: IDashboardXFilter[]) {
    this._crossFilters = value;
    const items = this.value.filter(item => {
      return this._crossFilters.findIndex(_ => _.tableId === item.tableId && _.columnName === item.filter.columnName) >= 0;
    });
    super.next(items);
  }

  push(item: CrossFilterValue): void {
    if (this._crossFilters.find(_ => _.tableId === item.tableId && _.columnName === item.filter.columnName)) {
      const items = this.value.filter(_ => {
        return _.tableId !== item.tableId || _.filter.columnName !== item.filter.columnName;
      });

      items.push(item);

      super.next(items);
    }
  }
}
