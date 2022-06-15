import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { tables as fixtureTables } from '../fixture/fixtures/tables';

import { ITable, ITableColumn } from './table.interface';
import { TableColumnDisplayNamePipe, TableColumnSelectOptionsPipe } from './tables.pipes';

describe('TableColumnSelectOptionsPipe', () => {
  let pipe: TableColumnSelectOptionsPipe;

  beforeEach(() => {
    pipe = new TableColumnSelectOptionsPipe();
  });

  it('should transform null', () => {
    expect(pipe.transform(null)).toEqual([]);
  });

  it('should transform columns as AppSelectOptionData', () => {
    const table: ITable = fixtureTables.data[0];
    expect(pipe.transform(table.columns)).toEqual(table.columns.map((column: ITableColumn): AppSelectOptionData => {
      return {
        id: column.name,
        text: column.displayName,
      };
    }));
  });
});

describe('TableColumnDisplayNamePipe', () => {
  let pipe: TableColumnDisplayNamePipe;

  beforeEach(() => {
    pipe = new TableColumnDisplayNamePipe();
  });

  it('should transform name into displayName', () => {
    const table: ITable = fixtureTables.data[0];
    expect(pipe.transform(table.columns[0].name, table)).toEqual(table.columns[0].displayName);
  });

  it('should transform undefined into name', () => {
    const table: ITable = fixtureTables.data[0];
    expect(pipe.transform('notInTable', table)).toEqual('notInTable');
  });
});
