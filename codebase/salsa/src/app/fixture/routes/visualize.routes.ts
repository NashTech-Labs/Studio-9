import * as _ from 'lodash';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/map';
import { Observable } from 'rxjs/Observable';

import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { IExperimentFull } from '../../experiments/experiment.interfaces';
import { ITable, ITableColumn, TTableValue } from '../../tables/table.interface';
import { ITabularTrainPipeline, ITabularTrainResult } from '../../train/model.interface';
import { MiscUtils } from '../../utils/misc';
import { IDashboard, IDashboardCreate } from '../../visualize/dashboard.interface';
import {
  GeospatialDataRequest,
  GeospatialDataResponse,
  TabularDataRequest,
  TabularDataResponse,
} from '../../visualize/visualize.interface';
import { IFixtureServiceRoute, IFixtureTable, IFixtureTabularModel } from '../fixture.interface';

import { fixtureModelEquationFunction } from './models.routes';
import { getDataset } from './tables.routes';

export const visualizeRoutes: IFixtureServiceRoute[] = [
  {
    url: 'visualize/tabular-data$',
    method: 'POST',
    handler: function(this, params: TabularDataRequest, user): TabularDataResponse | Observable<TabularDataResponse> {
      if (!params.aggregations /*|| !params.groupBy*/) {
        return {
          columns: [],
          data: [],
          count: 0,
        };
      }

      const tables = this.collections.tables;

      let columns: ITableColumn[],
        data: Observable<(number | string)[][]>;

      if (params.asset.type === IAsset.Type.MODEL) {
        const models = this.collections.models;
        const model: IFixtureTabularModel = models.findOne({ id: params.asset.id, ownerId: user.id });
        if (!model) {
          throw new Error('Model Not found');
        }

        const experiments = this.collections.experiments;
        const experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult> = experiments.findOne({id: model.experimentId}) as any;
        if (!experiment) {
          throw new Error('Experiment Not found');
        }

        const table: IFixtureTable = tables.findOne({ id: experiment.pipeline.input });
        if (!model) {
          throw new Error('Input Table Not found');
        }

        const fixtureFunction = fixtureModelEquationFunction(
          model.fixtureEquation || table.derivedModelEquation,
        ) || function() { return Math.round(Math.random()); };

        columns = [...model.predictorColumns, model.responseColumn];

        const recursiveGenerator = function(head: (number | string)[]): (number | string)[][] {
          const predictingColumn = model.predictorColumns[head.length];

          if (!predictingColumn) {
            return [[...head, fixtureFunction(model.predictorColumns.reduce((obj, column, i) => {
              obj[column.name] = head[i];
              return obj;
            }, {}))]];
          }

          const generator = params.generators.find(_ => _.columnName === predictingColumn.name);

          if (!generator) {
            return [];
          }

          let out = [];

          if ('steps' in generator) {
            const {min, max, steps} = generator;
            const step = steps > 1 ? (max - min) / (steps - 1) : 0;
            for (let i = 1; i <= steps; i++) {
              let value = min + step * i;
              if (predictingColumn.dataType === ITable.ColumnDataType.INTEGER) {
                value = Math.round(value);
              }
              out = out.concat(recursiveGenerator([...head, value]));
            }
          } else {
            generator.values.forEach(value => {
              out = out.concat(recursiveGenerator([...head, value]));
            });
          }

          return out;
        };

        data = Observable.of(recursiveGenerator([]));
      } else if (params.asset.type === IAsset.Type.TABLE) {
        const table: ITable = tables.findOne({ id: params.asset.id, ownerId: user.id });
        if (!table) {
          throw new Error('Table Not found');
        }

        columns = table.columns;
        data = getDataset(this, table).map(dataset => dataset.data);
      }

      return data.map(data => {
        const attributesList = params.groupBy;
        const metricsList = params.aggregations.map(_ => _.columnName);
        const metricFunction = params.aggregations.map(_ => _.aggregator);
        const filtersList = params.filters.map(_ => _.columnName);
        /*if (!attributesList.length) {
          throw new Error('No atrributes');
        }*/
        if (!metricsList.length) {
          throw new Error('No metrics');
        }
        const attributesIndexes = [],
          metricsIndexes = [],
          filtersIndexes = [];
        filtersList.forEach((filterName) => {
          columns.forEach((column, i) => {
            if (column.name === filterName) {
              filtersIndexes.push(i);
            }
          });
        });
        attributesList.forEach((attr) => {
          columns.forEach((column, i) => {
            if (column.name === attr) {
              attributesIndexes.push(i);
            }
          });
        });
        metricsList.forEach((metric) => {
          columns.forEach((column, i) => {
            if (column.name === metric) {
              metricsIndexes.push(i);
            }
          });
        });
        const total: {
          [p: string]: [TTableValue[], number[][]],
        } = {};

        data.forEach(row => {
          const notMatched = !!params.filters.find((filter, i) => {
            const index = filtersIndexes[i];
            const column = columns[index];
            const value = row[index];
            if (column.variableType === ITable.ColumnVariableType.CATEGORICAL && filter.type === 'categorical') {
              return filter.values.indexOf(value) < 0;
            } else if (column.variableType === ITable.ColumnVariableType.CONTINUOUS && filter.type === 'continuous') {
              return filter.min > value || filter.max < value;
            } else {
              return true;
            }
          });

          if (notMatched) {
            return;
          }

          const attributes = attributesIndexes.map((_, i) => {
            const groups = params.groups.filter(group => group.columnName === attributesList[i]);
            const group = groups.find(group => group.values && group.values.indexOf(<string> row[_]) > -1);
            if (group) {
              return group.mergedValue;
            }
            return row[_];
          });
          const metrics: number[] = metricsIndexes.map((index, i) => {
            const column = columns[index];
            if (column.dataType === ITable.ColumnDataType.LONG ||
              column.dataType === ITable.ColumnDataType.DOUBLE ||
              column.dataType === ITable.ColumnDataType.INTEGER) {
              //noinspection JSRedundantSwitchStatement
              switch (metricFunction[i]) {
                case 'COUNT':
                  return 1;
                default:
                  return <number> row[index];
              }
            }
            return 1;
          });
          const label: string = attributes.join('|');

          if (!(label in total)) {
            total[label] = [attributes, metrics.map(_ => [_])];
          } else {
            metrics.forEach((value, i) => {
              total[label][1][i].push(value);
            });
          }
        });

        const results: TTableValue[][] = [];
        Object.keys(total).forEach(key => {
          const [attributes, metricsRaw] = total[key];
          if (metricFunction.length <= 2 && metricFunction.every(func => func === IDashboard.DashboardAggregationType.NO_AGGREGATE)) {
            const metrics = _.zip(...metricsRaw);
            metrics.forEach(metric => {
              results.push(attributes.concat(metric));
            });
          } else {
            const metrics = metricsRaw.map((values, i) => {
              values = values.filter(_ => !isNaN(_) && _ !== null);
              switch (metricFunction[i]) {
                case IDashboard.DashboardAggregationType.SUM:
                  return MiscUtils.sum(values);
                case IDashboard.DashboardAggregationType.MIN:
                  return Math.min(...values);
                case IDashboard.DashboardAggregationType.MAX:
                  return Math.max(...values);
                case IDashboard.DashboardAggregationType.AVG:
                  return MiscUtils.average(values);
                case IDashboard.DashboardAggregationType.COUNT:
                  return values.length;
                default:
                  throw new Error('Unsupported');
              }
            });
            results.push(attributes.concat(metrics));
          }
        });

        if (params.limit) {
          results.splice(params.limit);
        }

        const sortedResults = results.sort((a, b) => {
          for (let i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
              return 1;
            } else if (a[i] < b[i]) return -1;
          }
          return 0;
        });

        return {
          columns: attributesIndexes.map(_ => columns[_]).concat(metricsIndexes.map(_ => columns[_])).map(_ => {
            return Object.assign({
              displayName: _.name,
            }, _);
          }),
          data: sortedResults,
          count: results.length,
        };
      });
    },
  },
  {
    url: 'visualize/geo-data$',
    method: 'POST',
    handler: function<T extends GeospatialDataRequest.Mode>(
      this, params: GeospatialDataRequest<T>,
    ): GeospatialDataResponse<GeospatialDataRequest.Mode> {
      //noinspection JSRedundantSwitchStatement
      switch (params.mode) {
        case GeospatialDataRequest.Mode.CV_PREDICTION:
          // hardcoded for COR-2584         
          let result: [string, string, number, number, number, number, number][] = [
            ['annotated_image_1.tif', 'aircraft', 14.9454454836511, -23.4856285699343, 14.9451059204737, -23.4852890067569, 0.92],
            ['annotated_image_1.tif', 'aircraft', 14.9444833879818, -23.4860813208375, 14.9440872309415, -23.4855719760714, 0.98],
            ['annotated_image_2.tif', 'aircraft', -33.39776002321208, -70.79733752268567, -33.398440362292504, -70.7964716365833, 0.97],
            ['annotated_image_2.tif', 'aircraft', -33.39717245764262, -70.797306598182, -33.397729098708425, -70.79662625910157, 0.96],
          ];
          const total = result.length;
          let matches;
          if (params.query && params.query.where) {
            matches = params.query.where.match(/target\s*=\s*(?:['"])([^'"]*)(?:['"])?/i);
            if (matches) {
              result = result.filter(_ => _[1] === matches[1]);
            }
            matches = params.query.where.match(/confidence\s*>=\s*([0-9.]+)?/i);
            if (matches) {
              result = result.filter(_ => _[6] >= parseFloat(matches[1]));
            }
          }
          return {
            data: result,
            count: total,
          };
        default:
          throw new Error('Not implemented');
      }
    },
  },
  {
    url: 'dashboards$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.dashboards, IAsset.Type.DASHBOARD, params, user);
    },
  },
  {
    url: 'dashboards/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const dashboards = this.collections.dashboards;
      const dashboard =  dashboards.findOne({id: id, ownerId: user.id});

      if (!dashboard) { throw new Error('Dashboard Not found'); }

      return dashboard;
    },
  },
  {
    url: 'dashboards/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IObjectId {
      const id = params[1];
      const dashboards = this.collections.dashboards;
      const dashboard = dashboards.findOne({ id: id, ownerId: user.id });
      if (!dashboard) {
        throw new Error('Not found');
      }
      dashboards.remove(dashboard);
      return { id: id };
    },
  },
  {
    url: 'dashboards$',
    method: 'POST',
    handler: function(this, params: IDashboardCreate, user) {
      const dashboards = this.collections.dashboards;
      const dashboard: IDashboard = Object.assign({
        id: Date.now().toString(),
        ownerId: user.id,
        status: IDashboard.Status.IDLE,
        updated: new Date().toISOString(),
        created: new Date().toISOString(),
      }, params);
      dashboards.insertOne(dashboard);
      console.log('json', JSON.stringify(dashboard));
      return dashboard;
    },
  },
  {
    url: 'dashboards/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const dashboards = this.collections.dashboards;
      const dashboard = dashboards.findOne({ id: id, ownerId: user.id });
      if (!dashboard) {
        throw new Error('Dashboard Not found');
      }
      $.extend(dashboard, params, { updated: Date.now() });
      ['metrics', 'attributes', 'chartFilters', 'generators', 'generators', 'widgets', 'chartGenerators'].forEach(key => {
        dashboard[key] = params[key];
      });
      dashboards.update(dashboard);
      console.log('json', JSON.stringify(dashboard));
      return dashboard;
    },
  },
];
