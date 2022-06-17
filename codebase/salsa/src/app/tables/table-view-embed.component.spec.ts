import { HttpClientTestingModule } from '@angular/common/http/testing';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { Router, RouterModule } from '@angular/router';

import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { IAsset } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { ProcessService } from '../core/services/process.service';
import { UserService } from '../core/services/user.service';
import { ProjectContext } from '../library/project.context';
import { ProjectService } from '../library/project.service';

import { TableViewEmbeddableComponent } from './table-view-embed.component';
import { ITable } from './table.interface';
import { TableService } from './table.service';
import { TablesModule } from './tables.module';

let activeTable = {
  table: <ITable> {
    id: '101',
    ownerId: '1',
    datasetId: '101',
    name: 'TableA',
    status: ITable.Status.ACTIVE,
    datasetType: ITable.DatasetType.SOURCE,
    created: '2016-04-04 04:04',
    updated: '2016-07-07 07:07',
    options: { // experimental
      indices: ['1', '2', '3'],
    },
    columns: [
      {
        name: 'ColumnA',
        displayName: 'Column A',
        dataType: ITable.ColumnDataType.INTEGER,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
      {
        name: 'ColumnB',
        displayName: 'Column B',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
      {
        name: 'ColumnC',
        displayName: 'Column C',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
    ],
  },
  dataset: {
    id: '101',
    count: 6,
    data: [
      [1, 'John101',  'Doe'   ],
      [2, 'Jane101',  'Doe'   ],
      [3, 'Tom101',   'Dowie' ],
      [4, 'Tammy101', 'Dowie' ],
      [5, 'Peter101', 'Dole'  ],
      [6, 'Panny101', 'Dole'  ],
    ],
  },
};

let savingTable = {
  table: <ITable> {
    id: '101',
    ownerId: '1',
    datasetId: '101',
    name: 'TableA',
    status: ITable.Status.SAVING,
    datasetType: ITable.DatasetType.SOURCE,
    created: '2016-04-04 04:04',
    updated: '2016-07-07 07:07',
    options: { // experimental
      indices: ['1', '2', '3'],
    },
    columns: [
      {
        name: 'ColumnA',
        displayName: 'Column A',
        dataType: ITable.ColumnDataType.INTEGER,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
      {
        name: 'ColumnB',
        displayName: 'Column B',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
      {
        name: 'ColumnC',
        displayName: 'Column C',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CONTINUOUS,
        align: ITable.ColumnAlign.LEFT,
      },
    ],
  },
  dataset: {
    id: '101',
    count: 6,
    data: [
      // convention: keys are table columns ids
      [1, 'John101',  'Doe'   ],
      [2, 'Jane101',  'Doe'   ],
      [3, 'Tom101',   'Dowie' ],
      [4, 'Tammy101', 'Dowie' ],
      [5, 'Peter101', 'Dole'  ],
      [6, 'Panny101', 'Dole'  ],
    ],
  },
};

class MockRouter {
  routerState = {
    snapshot: {
      url: 'url',
    },
  };
}

class MockTableService {
  data: any;
  _data = activeTable;

  constructor() {
    this.data = {
      view: null,
    };
  }

  list() {
  }

  get(id: string) {
    return Observable.of(this._data.table);
  }

  getStatistic(id: string) {
    return Observable.empty();
  }

  getDataset() {
    return Observable.of(this._data.dataset);
  }
}

class MockProcessService {
  observable: ReplaySubject<IProcess>;
  _process: IProcess = {
    id: 'processId1',
    ownerId: 'ownerId1',
    target: IAsset.Type.TABLE,
    targetId: '101',
    status: IProcess.Status.RUNNING,
    progress: 0,
    estimate: 6000,
    created: '2016-05-05 01:01',
    started: '2016-05-05 05:05',
    completed: '2016-05-05 05:05',
    jobType: IProcess.JobType.TABULAR_TRAIN,
  };
  data = {
    targets: {
      tables: {
        '101': this._process,
      },
    },
  };

  constructor() {
    this.observable = new ReplaySubject<IProcess>(1);
  }

  getByTarget(id: string, type: string) {
    return this.observable;
  }

  subscribeByTarget(id: string, type: string, fn: Function) {
    this.observable.subscribe(data => {
      fn(data);
    });
  }
}

class MockUserService {
  token() {
    return '';
  }
}

describe('TableViewEmbeddableComponent', () => {
  let fixture: ComponentFixture<TableViewEmbeddableComponent>,
    component: TableViewEmbeddableComponent,
    tableService = new MockTableService(),
    processService = new MockProcessService(),
    userService = new MockUserService(),
    router = new MockRouter();

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: ProcessService, useValue: processService },
        { provide: Router, useValue: router },
        { provide: UserService, useValue: userService },
        ProjectContext,
        ProjectService,
      ],
      imports: [
        BrowserModule,
        CoreUIModule.forRoot(),
        CoreModule.forRoot(),
        TablesModule.forRoot(),
        RouterModule.forRoot([]),
        HttpClientTestingModule,
      ],
    });
    TestBed.overrideComponent(TableViewEmbeddableComponent, { // THIS OVERRIDES SERVICES PROVIDED DIRECTLY INTO COMPONENT
      set: {
        providers: [
          { provide: TableService, useValue: tableService },
        ],
      },
    });
    fixture = TestBed.createComponent(TableViewEmbeddableComponent);
    component = fixture.componentInstance;
  });

  it('should load Table with Active status and create form representation of it', (done: any) => {
    component.id = '1';
    let spy: jasmine.Spy = spyOn(tableService, 'getDataset').and.callThrough();
    tableService._data = activeTable;
    let changes: SimpleChanges = { id: new SimpleChange(null, '1', true) };
    component.ngOnChanges(changes); // @TODO after https://github.com/angular/angular/issues/9866
    fixture.detectChanges();
    expect(spy.calls.first().args[1]).toEqual(
      new FormGroup({
        order: new FormControl(),
        page: new FormControl(1),
        page_size: new FormControl(config.table.view.pageSize.tableViewEmbed),
      }).value,
    );

    done();
  });
  it('should process table in status saving and update table list', (done: any) => {
    const
      spySubscribe = spyOn(processService, 'subscribeByTarget').and.callThrough(),
      spyTables = spyOn(tableService, 'list');
    tableService._data = savingTable;
    let changes: SimpleChanges = { id: new SimpleChange(null, '1', true) };
    component.ngOnChanges(changes); // @TODO after https://github.com/angular/angular/issues/9866
    fixture.detectChanges();
    router.routerState.snapshot.url = 'anotherUrl';
    processService.observable.next(processService._process); // emulate process.subscribe and getByTarget both
    expect(processService.subscribeByTarget).toHaveBeenCalled();
    expect(tableService.list).toHaveBeenCalled();
    expect(spySubscribe.calls.count()).toBe(1);
    expect(spyTables.calls.count()).toBe(1);
    done();
  });
});
