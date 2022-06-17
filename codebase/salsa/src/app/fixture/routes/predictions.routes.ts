import 'rxjs/add/operator/map';

import config from '../../config';
import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { IPrediction, IPredictionCreate, IPredictionStatus } from '../../play/prediction.interface';
import { ITable, ITableColumn } from '../../tables/table.interface';
import { MiscUtils } from '../../utils/misc';
import { IFixtureDataset, IFixtureServiceRoute } from '../fixture.interface';

import { fixtureModelEquationFunction } from './models.routes';
import { getDataset } from './tables.routes';

export const predictionsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'predictions$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.predictions, IAsset.Type.PREDICTION, params, user);
    },
  },
  {
    url: 'predictions/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const predictions = this.collections.predictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      if (!prediction) { throw new Error('Prediction Not found'); }

      return prediction;
    },
  },
  {
    url: 'predictions/([\\w\\-]+)/save$',
    method: 'POST',
    handler: function(this, params) {
      return params[1];
    },
  },
  {
    url: 'predictions$',
    method: 'POST',
    handler: function(this, params: IPredictionCreate, user) {
      const models = this.collections.models;
      const tables = this.collections.tables;
      const experiments = this.collections.experiments;
      const model = models.findOne({ id: params.modelId, ownerId: user.id });
      const input = tables.findOne({ id: params.input, ownerId: user.id });

      if (!input) {
        throw new Error('Table Not found');
      }

      if (!model) {
        throw new Error('Model Not found');
      }


      const experiment = experiments.findOne({ id: model.experimentId, ownerId: user.id });

      if (!experiment) {
        throw new Error('Experiment Not found');
      }

      const table = tables.findOne({ id: params.input, ownerId: user.id });

      if (!table) {
        throw new Error('Table Not found');
      }

      const newTableColumns: ITableColumn[] = [
        {
          name: `${model.responseColumn.name}_predicted`,
          displayName: `${model.responseColumn.displayName} predicted`,
          dataType: model.responseColumn.dataType,
          variableType: model.responseColumn.variableType,
          columnType: config.table.column.columnType.values.METRIC,
        },
        {
          name: 'predicted_probabilities_class_1',
          displayName: `${model.responseColumn.displayName} probability`,
          dataType: ITable.ColumnDataType.DOUBLE,
          variableType: ITable.ColumnVariableType.CONTINUOUS,
          columnType: config.table.column.columnType.values.METRIC,
        },
        ...table.columns,
      ];

      return getDataset(this, table).map(dataset => {
        const responseColumnIndex = table.columns.findIndex(_ => _.name === model.responseColumn.name);
        const tableColumnIndices = [...table.columns]
          .map(({name}) => table.columns.findIndex(_ => _.name === name));

        const fixtureFunction = fixtureModelEquationFunction(model.fixtureEquation || table.derivedModelEquation);

        const newDataset: IFixtureDataset = {
          id: 'ds_' + Date.now().toString(),
          data: dataset.data.map(row => {
            const predicted = fixtureFunction
              ? fixtureFunction(table.columns.reduce((acc, column, i) => {
                acc[column.name] = row[i];
                return acc;
              }, {}))
              : row[responseColumnIndex];
            const predictedProbability = predicted ? MiscUtils.random(660, 1000) : MiscUtils.random(0, 659);
            return [predicted, predictedProbability / 1000, ...tableColumnIndices.map(_ => row[_])];
          }),
          count: dataset.count,
        };

        this.collections.datasets.insertOne(newDataset);

        const newTable: ITable = Object.assign({}, table, {
          id: 't_' + Date.now().toString(),
          name: params.outputTableName || params.name,
          datasetType: ITable.DatasetType.DERIVED,
          status: ITable.Status.SAVING,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          columns: newTableColumns,
          datasetId: newDataset.id,
        });

        delete newTable['$loki'];
        tables.insertOne(newTable);

        const prediction: IPrediction = Object.assign({
          id: 'm_' + Date.now().toString(),
          ownerId: user.id,
          status: IPredictionStatus.RUNNING,
          output: newTable.id,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
        }, params);

        const processes = this.collections.processes;
        processes.insertOne({
          id: 'm_' + Date.now().toString(),
          ownerId: user.id,
          target: IAsset.Type.PREDICTION,
          targetId: prediction.id,
          progress: 0,
          status: IProcess.Status.RUNNING,
          created: new Date().toISOString(),
          started: new Date().toISOString(),
          jobType: IProcess.JobType.CV_MODEL_PREDICT,
        });

        /* processes.insertOne({
           id: 't_' + Date.now().toString(),
           ownerId: user.id,
           target: IAsset.Type.TABLE,
           targetId: prediction.output,
           progress: 0,
           status: IProcess.Status.QUEUED,
           created: new Date().toISOString(),
           updated: new Date().toISOString(),
         });*/

        return this.collections.predictions.insertOne(prediction);
      });
    },
  },
  // TODO: implement POST /predictions/import
  {
    url: 'predictions/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const predictions = this.collections.predictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name'].forEach(prop =>
        params[prop] !== undefined && (prediction[prop] = params[prop]),
      );

      predictions.update(prediction);
      return prediction;
    },
  },
  {
    url: 'predictions/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IObjectId {
      const id = params[1];
      const predictions = this.collections.predictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      if (!prediction) { throw new Error('Not found'); }

      predictions.remove(prediction);

      return { id: id };
    },
  },

];
