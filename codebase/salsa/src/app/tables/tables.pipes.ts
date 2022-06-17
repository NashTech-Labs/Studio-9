import { DecimalPipe } from '@angular/common';
import { Pipe, PipeTransform } from '@angular/core';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';

import { ITable, ITableColumn } from './table.interface';

@Pipe({name: 'tableColumnSelectOptions'})
export class TableColumnSelectOptionsPipe implements PipeTransform {
  /**
   * @param {ITableColumn[]} value
   * @returns {AppSelectOptionData[]}
   */
  transform(value: ITableColumn[]): AppSelectOptionData[] {
    return  value ? value.map((item: ITableColumn) => {
      return {
        id: item.name,
        text: item.displayName || item.name,
      };
    }) : [];
  }
}

@Pipe({name: 'tableColumnDisplayName'})
export class TableColumnDisplayNamePipe implements PipeTransform {
  /**
   * @param {string} columnName
   * @param {ITable} table
   * @returns {string}
   */
  transform(columnName: string, table: ITable): string {
    const column = table.columns.find(column => column.name === columnName);
    return column ? column.displayName || columnName : columnName;
  }
}

// this pipe shows number only if i't full representation is longer than formatted one
@Pipe({ name: 'tableNumberTitle' })
export class TableNumberTitlePipe implements PipeTransform {
  decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  transform(value: string, pattern: string = '1.0-3'): string {
    try {
    const float = parseFloat(value);
      const str = String(float);
      const decimal = this.decimalPipe.transform(float, pattern);
      return decimal.length < str.length ? str : '';
    } catch (e) {
      return '';
    }
  }
}
