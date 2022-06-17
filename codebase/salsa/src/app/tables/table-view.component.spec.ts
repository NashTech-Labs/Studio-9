import { APP_BASE_HREF } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserModule } from '@angular/platform-browser';
import { ActivatedRoute, RouterModule } from '@angular/router';

import { ReplaySubject } from 'rxjs/ReplaySubject';

import { FlowService } from '../compose/flow.service';
import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { ProcessService } from '../core/services/process.service';
import { UserService } from '../core/services/user.service';
import { AclService } from '../services/acl.service';

import { TableViewComponent } from './table-view.component';
import { TableService } from './table.service';
import { TablesModule } from './tables.module';

class MockFlowService {
  data = {
    viewTables: [],
  };
}

class MockActivatedRoute {
  params: any;

  constructor() {
    this.params = new ReplaySubject(1);
  }
}

class MockUserService {
  data: any;

  constructor() {
    this.data = {
      observable: new ReplaySubject<any>(1),
    };
  }

  token() {
    return '';
  }

  getUser() {
    return { id: '' };
  }
}

class MockTableService {
  observables: any;
  data: any;

  constructor() {
    this.observables = {
      view: {
        subscribe: () => {},
      },
    };
    this.data = {
      view: {
        id: '101',
        ownerId: '1',
        datasetId: '101',
        name: 'TableA',
        status: config.table.status.values.ACTIVE,
        created: '2016-04-04 04:04',
        updated: '2016-07-07 07:07',
        options: {
          indexes: ['1', '2', '3'],
        },
        columns: [
          {
            id: '1',
            name: 'ColumnA',
            dataType: 'Integer',
            variableType: 'Continuous',
          },
          {
            id: '2',
            name: 'ColumnB',
            dataType: 'String',
            variableType: 'Continuous',
          },
          {
            id: '3',
            name: 'ColumnC',
            dataType: 'String',
            variableType: 'Continuous',
          },
        ],
      },
    };
  }

  view() {
  }

  exportUrl() {
    return '';
  }

  getDataset(id: string, data: any) {
    let o = new ReplaySubject(1);
    o.next({
      data: [],
    });

    return o;
  }

  get(id: string) { return this.data.view; }
}


class MockProcessService {
}

describe('Functionallity of TableViewComponent ', () => {
  let route = new MockActivatedRoute(),
    userService = new MockUserService(),
    flowService = new MockFlowService(),
    tableService = new MockTableService(),
    processService = new MockProcessService(),
    fixture: ComponentFixture<TableViewComponent>,
    component: TableViewComponent,
    spy: jasmine.Spy;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: UserService, useValue: userService },
        { provide: FlowService, useValue: flowService },
        { provide: TableService, useValue: tableService },
        { provide: ProcessService, useValue: processService },
        AclService,
        { provide: APP_BASE_HREF, useValue: '/' },
      ],
      imports: [
        BrowserModule,
        CoreUIModule.forRoot(),
        CoreModule.forRoot(),
        TablesModule.forRoot(),
        RouterModule.forRoot([]),
      ],
    });
    TestBed.overrideComponent(TableViewComponent, { set: { template: '' } }); //@todo with template routerLink cause an error
    fixture = TestBed.createComponent(TableViewComponent);
    component = fixture.componentInstance;
  });
  it('should getDataset after form value changes', () => {
    spy = spyOn(tableService, 'getDataset').and.callThrough();
    fixture.detectChanges();
    component.table = tableService.data.view;
    component.tableViewForm.controls['order'].setValue('-ColumnB');
    expect(tableService.getDataset).toHaveBeenCalledWith('101', { order: '-ColumnB', page: 1, page_size: config.table.view.pageSize.tableView });
    expect(spy.calls.count()).toBe(1);
  });

});
