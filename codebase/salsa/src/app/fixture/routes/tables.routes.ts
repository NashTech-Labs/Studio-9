import { HttpClient } from '@angular/common/http';

import * as _ from 'lodash';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/observable/throw';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/publishReplay';
import { Observable } from 'rxjs/Observable';

import { IAsset, IBackendList } from '../../core/interfaces/common.interface';
import { IDataset } from '../../tables/dataset.interface';
import {
  IScatterSummary,
  IScatterSummaryChart,
  IScatterSummaryChartRow,
  ITable,
  ITableColumn,
  ITableColumnHistogram,
  ITableColumnStats,
  ITableId,
  ITableUpdate,
  TTableValue,
} from '../../tables/table.interface';
import { Csv } from '../../utils/backend';
import { MiscUtils } from '../../utils/misc';
import { IFixtureDataset, IFixtureServiceRoute } from '../fixture.interface';

export const getDataset = _.memoize(function(service, table: ITable): Observable<IBackendList<IDataset>> {
  const dataset: IFixtureDataset = service.collections.datasets.findOne({id: table.datasetId});
  if (!dataset) {
    return Observable.throw('Dataset Not found');
  }
  if (dataset.csvPath && (!dataset.data || !dataset.data.length)) {
    if (dataset._pending) {
      return dataset._pending;
    }
    const observable = service.injector.get(HttpClient).get(dataset.csvPath, {
      observe: 'body',
      responseType: 'text',
    }).map(resp => {
      const rows = Csv.toArray(resp).slice(1).map(row => {
        return table.columns.map((column, i) => {
          if (column.dataType === ITable.ColumnDataType.INTEGER ||
            column.dataType === ITable.ColumnDataType.LONG) {
            return parseInt(row[i]);
          } else if (column.dataType === ITable.ColumnDataType.DOUBLE) {
            return parseFloat(row[i]);
          }

          return row[i];
        });
      });
      Object.assign(dataset, {
        data: rows,
        count: rows.length,
      });
      return dataset;
    }).share();

    Object.assign(dataset, {
      _pending: observable,
    });

    return observable;
  }

  return Observable.of(dataset);
}, (service, table: ITable) => table.id);

export const getTableStatistics = _.memoize(function (service, table: ITable) {
  const HISTOGRAM_WIDTH = 10;

  return getDataset(service, table).map(dataset => {
    if (!dataset || !dataset.data || !dataset.data.length) {
      return;
    }
    const cols = [[]];
    dataset.data.forEach((row) => {
      row.forEach((value, index) => {
        if (!cols[index]) {
          cols[index] = [];
        }
        cols[index].push(value);
      });
    });
    const stats = cols.map((col: number[], i): ITableColumnStats => {
      if (table.columns[i].variableType === ITable.ColumnVariableType.CONTINUOUS) {
        col = col.filter(_ => !isNaN(_));
        const bucketsCount = Math.min(col.length, HISTOGRAM_WIDTH);
        const min = Math.min(...col);
        const max = Math.max(...col);
        const step = (max - min) / bucketsCount;
        const buckets = col.reduce((acc, value) => {
          const idx = Math.min(Math.max(0, Math.floor((value - min) / step)), bucketsCount - 1);
          acc[idx]++;
          return acc;
        }, new Array(bucketsCount).fill(0));

        const histogram: ITableColumnHistogram = buckets.map((count, idx) => {
          return {
            min: min + step * idx,
            max: min + step * (idx + 1),
            count: count,
          };
        });

        return {
          columnName: table.columns[i].name,
          min: min,
          max: max,
          avg: MiscUtils.average(col),
          std: MiscUtils.standardDeviation(col),
          stdPopulation: MiscUtils.standardDeviation(col),
          histogram,
        };
      } else {
        const valuesFrequency = <[TTableValue, number][]> _.chain(col)
          .countBy()
          .toPairs()
          .sortBy<[TTableValue, number]>([_ => _[1]])
          .reverse()
          .value();

        const histogram: ITableColumnHistogram = valuesFrequency.reduce((acc, [value, count]) => {
          if (acc.length < HISTOGRAM_WIDTH) {
            acc.push({ value, count });
          } else {
            acc[HISTOGRAM_WIDTH - 1].value = null;
            acc[HISTOGRAM_WIDTH - 1].count += count;
          }
          return acc;
        }, <ITableColumnHistogram> []);

        return {
          columnName: table.columns[i].name,
          uniqueCount: valuesFrequency.length,
          mostFrequentValue: valuesFrequency[0][0],
          histogram,
        };
      }
    });
    const tableStats = {
      id: table.id,
      status: table.status,
      stats: stats,
    };
    service.collections.tableStatistics.insertOne(tableStats);

    return tableStats;
  }).publishReplay().refCount();
}, (service, table: ITable) => table.id);

export const tablesRoutes: IFixtureServiceRoute[] = [
  {
    url: 'tables$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.tables, IAsset.Type.TABLE, params, user);
    },
  },
  {
    url: 'tables/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const table = this.collections.tables.findOne({id: id});
      if (!table) {
        throw new Error('Table Not found');
      }

      if (table.ownerId === user.id) {
        return table;
      }
      if (params.shared_resource_id) {
        const share = this.collections.shares.findOne({
          id: params.shared_resource_id,
          recipientId: user.id,
        });
        //noinspection JSRedundantSwitchStatement
        switch (share && share.assetType) {
          case IAsset.Type.TABLE:
            if (share.assetId === table.id) {
              return table;
            }
            break;
        }
      }

      throw new Error('Access denied');
    },
  },

  {
    // TODO: refacor
    url: 'tables/import/csv$',
    method: 'POST',
    handler: function(this, params, user) {
      const tables = this.collections.tables;
      const datasets = this.collections.datasets;

      const parsedCsv: string[][] = Csv.toArray(params.file),
        responseColumns: ITableColumn[] = [],
        originalCols = parsedCsv[0],
        originalData: string[][] = parsedCsv.slice(1)
          .filter((row: string[]) => row.length === originalCols.length);

      for (let i = 0; i < originalCols.length; i++) {
        const isNumeric = originalData.every((row: string[]) => {
          const value = Number.parseFloat(row[i]);
          return String(row[i]).match(/^-?[\d.]+$/)
            && !Number.isNaN(value)
            && Number.isFinite(value);
        });

        const isInteger = isNumeric && originalData.every((row: string[]) => {
          return Number.isInteger(Number.parseFloat(row[i]));
        });

        const col: ITableColumn = {
          name: originalCols[i].toLowerCase().replace(/[^\w]/, '_'),
          displayName: originalCols[i],
          // http://stackoverflow.com/questions/18082/validate-decimal-numbers-in-javascript-isnumeric
          dataType: isInteger
            ? ITable.ColumnDataType.INTEGER
            : (isNumeric
              ? ITable.ColumnDataType.DOUBLE
              : ITable.ColumnDataType.STRING),
          variableType: isNumeric
            ? ITable.ColumnVariableType.CONTINUOUS
            : ITable.ColumnVariableType.CATEGORICAL,
        };
        responseColumns.push(col);
      }

      const filteredData: IDataset[] = originalData.map(row => {
        return responseColumns.map((column, i) => {
          if (column.dataType === ITable.ColumnDataType.INTEGER ||
            column.dataType === ITable.ColumnDataType.LONG) {
            return parseInt(row[i]);
          } else if (column.dataType === ITable.ColumnDataType.DOUBLE) {
            return parseFloat(row[i]);
          }

          return row[i];
        });
      });

      // create dataset and table entities
      const dataset = datasets.insertOne({
        id: Date.now().toString(),
        data: filteredData,
        count: filteredData.length,
      });

      return tables.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        datasetId: dataset.id,
        datasetType: ITable.DatasetType.SOURCE,
        name: params.name.replace(/\.[^/.]+$/, ''),
        columns: responseColumns,
        status: ITable.Status.ACTIVE,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
      });
    },
  },
  {
    url: 'tables/([\\w\\-]+)/save$',
    method: 'POST',
    handler: function (this, params: { name: string }, user): ITable {
      const id = params[1];
      const tables = this.collections.tables;
      const table = tables.findOne({id: id, ownerId: user.id});

      const duplicateTable = tables.find({'id': {'$ne': id}, name: params.name, ownerId: user.id});

      if (duplicateTable.length) {
        throw new Error(`Table with ${params.name} already exists`);
      }

      if (!table) {
        throw new Error('Table Not found');
      }

      Object.assign(table, params, {inLibrary: true});

      tables.update(table);
      return table;
    },
  },
  {
    url: 'tables/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: ITableUpdate, user): ITable {
      const id = params[1];
      const tables = this.collections.tables;
      const table = tables.findOne({id: id, ownerId: user.id});

      const duplicateTable = tables.find({'id': {'$ne': id}, name: params.name, ownerId: user.id});

      if (duplicateTable.length) {
        throw new Error(`Table with ${params.name} already exists`);
      }

      if (!table) {
        throw new Error('Table Not found');
      }

      Object.assign(table, params);

      tables.update(table);
      return table;
    },
  },
  {
    url: 'tables/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): ITableId {
      const id = params[1];
      const tables = this.collections.tables;
      const datasets = this.collections.datasets;

      const table = tables.findOne({id: id, ownerId: user.id});

      if (!table) { throw new Error('Table Not found'); }

      datasets.removeWhere({id: table.datasetId});
      // TODO: remove the table id from flows (flow.tables)
      tables.remove(table);

      return {id: id};
    },
  },
  {
    url: 'tables/([\\w\\-]+)/data$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const tables = this.collections.tables;

      const table = tables.findOne({id: id});
      if (!table) { throw new Error('Table Not found'); } // TODO: specify later

      return getDataset(this, table).map(dataset => {
        const page = parseInt(params['page']) || 1;
        const pageSize = parseInt(params['page_size']) || 20;
        let order = params['order'];
        //const search = params['search'];

        // make array of arrays from array of objects (lokijs limitations)
        let data = dataset.data;

        /* RESERVED. apply search (filtering)
        search && (data = data.filter(item =>
          true
        ));*/

        const count = data.length;

        // apply ordering
        if (order) {
          order = order.split(',');
          data.sort((a, b) => {
            return order
              .map((o) => {
                let dir = 1;
                if (o[0] === '-') {
                  dir = -1;
                  o = o.substring(1);
                }
                const orderColumn = table.columns.map(column => column.name).indexOf(o);
                if (a[orderColumn] > b[orderColumn]) return dir;
                if (a[orderColumn] < b[orderColumn]) return -(dir);
                return 0;
              })
              .reduce((p, n) => {
                return p ? p : n;
              }, 0);
          });
        }

        // apply paging
        pageSize && (data = data.slice((page - 1) * pageSize, page * pageSize));

        return {
          count: count,
          data: data,
        };
      });
    },
  },
  {
    url: 'tables/([\\w\\-]+)/values$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const tables = this.collections.tables;

      const table = tables.findOne({id: id});
      if (!table) { throw new Error('Table Not found'); } // TODO: specify later

      return getDataset(this, table).map(dataset => {
        const search = params['search'].toLowerCase();
        const columnName = params['column_name'];
        const limit = params['limit'];

        const columnIndex = table.columns.findIndex((column) => {
          return column.name === columnName;
        });

        if (columnIndex === -1) {
          throw new Error('Column not found');
        }
        const data = dataset.data;
        const values = data.reduce((arr, row) => {
          const currentValue = row[columnIndex];
          if (currentValue !== undefined && arr.indexOf(currentValue) === -1 && String(currentValue).toLowerCase().indexOf(search.toString()) === 0) {
            arr.push(currentValue);
          }
          return arr;
        }, []);
        const resultSet = limit ? values.slice(0, limit) : values;
        return { data: resultSet, count: resultSet.length };
      });
    },
  },
  {
    url: 'tables/([\\w\\-]+)/export$', // or .../download // ../file
    method: 'GET',
    handler: function (params) {
      const id = params[1];
      const tables = this.collections.tables;
      const table = tables.findOne({ id: id });
      if (!table) {
        throw new Error('Exporting Table Not Found');
      }
      return getDataset(this, table).map(dataset => {
        if (!dataset || !dataset.data || !dataset.data.length) {
          throw new Error('Exporting Dataset Not Found');
        }
        return [table.columns.map(column => column.displayName), ...dataset.data]
          .map((row: string[]) => {
            return row.map((value) => {
              return `"${String(value).replace(/"/g, '""')}"`;
            }).join(',');
          }).join('\n');
      });
    },
  },
  {
    url: 'tables/([\\w\\-]+)/stats$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const tableStatistics = this.collections.tableStatistics;

      const tableStatistic = tableStatistics.findOne({ id: id });
      if (tableStatistic) {
        return tableStatistic;
      }

      const table: ITable = this.collections.tables.findOne({id});
      return getTableStatistics(this, table);
    },
  },
  {
    url: 'tables/([\\w\\-]+)/scatter-summary$',
    method: 'GET',
    handler: function (this, params: any, user) {
      const tableId = params[1];
      const tables = this.collections.tables;

      const table = tables.findOne({ id: tableId, ownerId: user.id });
      if (!table) {
        throw new Error('Table Not Found');
      }

      return Observable.forkJoin(
        getDataset(this, table),
        getTableStatistics(this, table),
      ).map(([dataset, tableStatistic]): IScatterSummary => {
        if (!tableStatistic) {
          throw new Error('Stats For Table Not Found');
        }

        if (!dataset || !dataset.data || !dataset.data.length) {
          return;
        }

        const columnIndexes = table.columns
          .reduce((acc, column, i) => {
            if (column.variableType === ITable.ColumnVariableType.CONTINUOUS) {
              const stats = tableStatistic.stats.find(_ => _.columnName === column.name);
              if (stats && stats.max > stats.min) {
                acc.push(i);
              }
            }
            return acc;
          }, []);

        const quantizationSteps = Math.min(dataset.data.length, params['quantizationSteps'] || 20);

        const buckets: number[][][][] = columnIndexes.map((col1, i) => {
          return columnIndexes.slice(0, i).map(() => {
            return new Array(quantizationSteps).fill(0).map(() => new Array(quantizationSteps).fill(0));
          });
        });

        const statsIndexed = columnIndexes.map(i => {
          return tableStatistic.stats.find(_ => _.columnName === table.columns[i].name);
        }).map(_ => {
          const {min, max} = _;
          const step = ((max - min) / quantizationSteps) || 1;
          return {min, max, step};
        });

        const pairs = columnIndexes.reduce((acc, ci, i) => {
          return acc.concat(columnIndexes.slice(0, i).map((cj, j) => [i, j]));
        }, []);

        dataset.data.forEach(row => {
          const rowQuantized = columnIndexes.map(_ => <number> row[_]).map((value, i) => {
            const {min, step} = statsIndexed[i];

            return Math.min(Math.max(0, Math.floor((value - min) / step)), quantizationSteps - 1);
          });

          pairs.forEach(([i, j]) => {
            const iValue = rowQuantized[i];
            const jValue = rowQuantized[j];
            if (!isNaN(iValue) && !isNaN(jValue)) {
              buckets[i][j][iValue][jValue]++;
            }
          });
        });

        const data: IScatterSummaryChart[] = pairs.map(([i, j]): IScatterSummaryChart => {
          const {min: iMin, step: iStep} = statsIndexed[i];
          const {min: jMin, step: jStep} = statsIndexed[j];

          return {
            column1: table.columns[columnIndexes[j]].name,
            column2: table.columns[columnIndexes[i]].name,
            values: buckets[i][j].reduce<IScatterSummaryChartRow[]>((acc1, vals, iValue) => {
              return vals.reduce((acc2, count, jValue) => {
                /* if (count > 0) { */ // TODO: draw all rects on UI
                acc2.push({
                  value1: jValue * jStep + jMin,
                  value2: iValue * iStep + iMin,
                  count,
                });
                /* } */
                return acc2;
              }, acc1);
            }, []),
          };
        });

        // COLUMNS IN DATA SHOULD BE SORTED BY THEIR APPEARANCE IN "COLUMNS" array
        return {
          quantizationSteps: quantizationSteps,
          columns: columnIndexes.map(_ => table.columns[_]),
          data: data,
        };
      });
    },
  },

  {
    url: 'tables/([\\w\\-]+)/stats/process$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const processes = this.collections.processes;
      return processes.findOne({targetId: id, target: IAsset.Type.TABLE}); // ownerId: user.id
    },
  },

];
