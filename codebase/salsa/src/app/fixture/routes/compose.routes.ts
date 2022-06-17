import * as _ from 'lodash';

import {
  IBackendFlow,
  IBackendFlowUpdate,
  IBackendFlowstep,
  IFlow,
  IFlowInput,
  IFlowOutput,
  IFlowstep,
} from '../../compose/flow.interface';
import { IAsset, IObjectId, TObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { ITable } from '../../tables/table.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const getFlowInputs = function (service, flow: IBackendFlow): IFlowInput[] {
  const tables = service.collections.tables;
  const outputIds: TObjectId[] = flow.steps.map(_ => _.output);
  return flow.steps.reduce((acc, step: IBackendFlowstep) => {
    return _.uniq(acc.concat(step.input));
  }, []).reduce((acc, inputId) => {
    if (inputId && !outputIds.includes(inputId)) {
      const inputTable = tables.findOne({ id: inputId });
      if (!inputTable) {
        throw new Error('Input Table in flowstep not found');
      }
      acc.push({
        tableId: inputTable.id,
        tableName: inputTable.name,
        columns: inputTable.columns,
      });
    }
    return acc;
  }, []);
};

export const getFlowOutputs = function (service, flow: IBackendFlow): IFlowOutput[] {
  const tables = service.collections.tables;
  return flow.steps.reduce((acc, step: IBackendFlowstep) => {
    if (step.output) {
      const outputTable = tables.findOne({ id: step.output });
      if (!outputTable) {
        throw new Error('Output Table in flowstep not found');
      }
      acc.push({
        tableId: outputTable.id,
        tableName: outputTable.name,
      });
    }
    return acc;
  }, []);
};

export const composeRoutes: IFixtureServiceRoute[] = [
  {
    url: 'flows$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.flows, IAsset.Type.FLOW, params, user);
    },
  },
  {
    url: 'flows$',
    method: 'POST',
    handler: function (this, params: IBackendFlowUpdate, user): IBackendFlow {
      const flows = this.collections.flows;

      // create flow entity
      const data = Object.assign({}, params, {
        id: Date.now().toString(),
        ownerId: user.id,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        status: IFlow.Status.NEW,
        steps: [],
        tables: [],
        inLibrary: true,
      });

      return flows.insertOne(data as IBackendFlow);
    },
  },
  {
    url: 'flows/import$',
    method: 'POST',
    handler: function(this) {
      // import saved flow
      // return false;
    },
  },
  {
    url: 'flows/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const flows = this.collections.flows;
      const flow = flows.findOne({id: id, ownerId: user.id});

      if (!flow) { throw new Error('Flow Not found'); }

      return flow;
    },
  },
  {
    url: 'flows/([\\w\\-]+)/inputs$',
    method: 'GET',
    handler: function (this, params, user) {
      const id = params[1];
      const flows = this.collections.flows;
      const flow = flows.findOne({id: id, ownerId: user.id});
      if (!flow) {
        throw new Error('Flow Not Found');
      }
      return getFlowInputs(this, flow);
    },
  },
  {
    url: 'flows/([\\w\\-]+)/outputs',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const flows = this.collections.flows;
      const flow = flows.findOne({id: id, ownerId: user.id});
      if (!flow) {
        throw new Error('Flow Not Found');
      }
      return getFlowOutputs(this, flow);
    },
  },
  {
    url: 'flows/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: IBackendFlowUpdate, user): IBackendFlow {
      const id = params[1];
      const flows = this.collections.flows;
      const flow = flows.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name', 'description'].forEach(prop =>
        params[prop] !== undefined && (flow[prop] = params[prop]),
      );

      flows.update(flow);
      return flow;
    },
  },
  {
    url: 'flows/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const flows = this.collections.flows;

      const flow = flows.findOne({id: id, ownerId: user.id});

      if (!flow) { throw new Error('Flow Not found'); }

      // RESERVED // predictions.removeWhere({flowId: flow.id});
      flows.remove(flow);

      return {id: id};
    },
  },
  {
    url: 'flows/([\\w\\-]+)/tables$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const flows = this.collections.flows;
      const tables = this.collections.tables;

      const flow = flows.findOne({id: id, ownerId: user.id});
      if (!flow) {
        throw new Error('Flow Not Found');
      }
      return {
        data: tables.find({id: {$in : flow.tables}}),
      };
    },
  },
  {
    url: 'flows/([\\w\\-]+)/tables/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const tableId = params[2];
      const flows = this.collections.flows;
      const tables = this.collections.tables;

      const flow: IBackendFlow = flows.findOne({id: id, ownerId: user.id});
      const table = tables.findOne({id: tableId, ownerId: user.id});

      if (!flow) { throw new Error('Flow Not found'); } // TODO: specify later
      if (!table) { throw new Error('Table Not found'); } // TODO: specify later

      // add table id
      if (flow.tables.indexOf(tableId) === -1) {
        flow.tables.push(tableId);
      }
      flows.update(flow);

      return {
        data: tables.find({id: {$in : flow.tables}}),
      };
    },
  },
  {
    url: 'flows/([\\w\\-]+)/tables/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user) {
      const id = params[1];
      const tableId = params[2];
      const flows = this.collections.flows;
      const tables = this.collections.tables;

      const flow = flows.findOne({id: id, ownerId: user.id});
      const table = tables.findOne({id: tableId, ownerId: user.id});

      if (!flow) { throw new Error('Flow Not found'); } // TODO: specify later
      if (!table) { throw new Error('Table Not found'); } // TODO: specify later

      // remove table id
      const index = flow.tables.indexOf(tableId);
      if (index > -1) {
        flow.tables.splice(index);
      }
      flows.update(flow);

      return {
        data: tables.find({id: {$in : flow.tables}}),
      };
    },
  },
  {
    url: 'flows/([\\w\\-]+)/steps$',
    method: 'POST',
    handler: function (this, params: IBackendFlowstep.Create, user) {
      const id = params[1];
      const flows = this.collections.flows;
      const tables = this.collections.tables;
      const datasets = this.collections.datasets;

      const flow = flows.findOne({ id: id, ownerId: user.id });

      // validate
      if (!flow) {
        throw new Error('Flow Not found');
      } // TODO: specify later

      const input = params.inputIDs;
      if (!input || !input.length) {
        throw new Error('Input table(s) not found');
      } // TODO: specify later

      const inputTables = tables.find({ id: { $in: input } });
      if (inputTables.length !== input.length) {
        throw new Error('Input table(s) not found');
      }

      // the table to clone (that's a mockup backend, right?)
      const inputTable = inputTables[0];

      // create dataset entity
      const dataset = datasets.findOne({ id: inputTable.datasetId });
      const newDataset = datasets.insertOne({
        id: Date.now().toString(),
        data: dataset.data,
        count: dataset.count,
      });

      // create table entity
      const table = {
        id: Date.now().toString(),
        ownerId: user.id,
        datasetId: newDataset.id,
        datasetType: ITable.DatasetType.DERIVED,
        name: params.outputName,
        status: ITable.Status.SAVING,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        columns: inputTable.columns,
      };
      tables.insertOne(table);

      // create flowstep entity
      const dataFlowstep = <IBackendFlowstep> {
        id: Date.now().toString(),
        name: params.name,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        input: params.inputIDs,
        output: table.id,
        status: IFlowstep.Status.DONE,
        transformer: params.transformer,
      };

      flow.steps.push(dataFlowstep);
      flow.tables.push(inputTable.id);
      flow.tables.push(table.id);
      flow.tables = _.uniq(flow.tables);
      flows.update(flow);

      const processes = this.collections.processes;
      processes.insertOne({
        id: 't_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.TABLE,
        targetId: table.id,
        progress: null,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.TABULAR_TRAIN,
      });

      processes.insertOne({
        id: 'f_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.FLOW,
        targetId: flow.id,
        progress: null,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.TABULAR_PREDICT,
      });

      return dataFlowstep; // don't "pop"
    },
  },
  {
    url: 'flows/([\\w\\-]+)/steps/([\\w\\-]+)$',
    method: 'GET',
    handler: function (this, params, user) {
      const id = params[1];
      const stepId = params[2];
      const flows = this.collections.flows;
      const flow = flows.findOne({ id: id, ownerId: user.id });

      if (!flow) {
        throw new Error('Flow Not found');
      }

      const step = flow.steps.filter(item => {
        return item.id === stepId;
      }).pop();

      if (!step) {
        throw new Error('Step Not found');
      }

      return step;
    },
  },
  {
    url: 'flows/([\\w\\-]+)/steps/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: IBackendFlowstep.Update, user): IBackendFlowstep {
      const id = params[1];
      const stepId = params[2];
      const flows = this.collections.flows;
      const flow = flows.findOne({ id: id, ownerId: user.id });

      if (!flow) {
        throw new Error('Flow Not found');
      }

      const step = flow.steps.find(item => {
        return item.id === stepId;
      });

      const stepIndex = flow.steps.findIndex(item => {
        return item.id === stepId;
      });

      if (!step) {
        throw new Error('Step Not found');
      }

      step.name = params.name;
      flow.steps.splice(stepIndex, 1, step);
      flows.update(flow);
      return step;
    },
  },
  {
    url: 'flows/([\\w\\-]+)/steps/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const stepId = params[2];
      const flows = this.collections.flows;
      const flow = flows.findOne({ id: id, ownerId: user.id });

      if (!flow) {
        throw new Error('Flow Not found');
      }

      const stepIndex = flow.steps.findIndex(item => {
        return item.id === stepId;
      });

      if (stepIndex === -1) {
        throw new Error('Step Not found');
      }
      flow.steps.splice(stepIndex, 1);
      flows.update(flow);
      return { id: stepId };
    },
  },
  {
    url: 'flows/([\\w\\-]+)/export$',
    method: 'GET',
    handler: function(this) {
      // TODO: download flow details (similar to share)
      // return false;
    },
  },
  {
    url: 'flows/([\\w\\-]+)/import$',
    method: 'POST',
    handler: function(this) {
      // TODO: upload flow details (similar to share)
      // return false;
    },
  },
  {
    url: 'flows/([\\w\\-]+)/steps/([\\w\\-]+)/clusters$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[2];
      //noinspection JSRedundantSwitchStatement
      switch (id) {
        case 'step5':
          return {
            centers: [
              [30.809091666666664, 0],
              [33.496368965517235, 1],
            ],
            points: [
              [30.6746000000, 0],
              [33.3549000000, 1],
              [34.7346000000, 1],
              [33.5107000000, 1],
              [33.5502000000, 1],
              [33.4757000000, 1],
              [34.6976000000, 1],
              [33.3609000000, 1],
              [30.5863000000, 0],
              [32.3371000000, 1],
              [33.5101000000, 1],
              [30.7054000000, 0],
              [33.5193000000, 1],
              [32.3399000000, 1],
              [33.4433000000, 1],
              [30.6910000000, 0],
              [32.3897000000, 1],
              [33.4929000000, 1],
              [32.3522000000, 1],
              [32.4509000000, 1],
              [30.6784000000, 0],
              [30.6414000000, 0],
              [30.6974000000, 0],
              [31.0332000000, 0],
              [33.5162000000, 1],
              [30.4112000000, 0],
              [34.0143000000, 1],
              [32.2934000000, 0],
              [34.0229000000, 1],
              [32.4208000000, 1],
              [30.6002000000, 0],
              [33.1740000000, 1],
              [33.3591000000, 1],
              [34.8046000000, 1],
              [33.7176000000, 1],
              [34.8470000000, 1],
              [34.5331000000, 1],
              [30.6966000000, 0],
              [32.2757000000, 1],
              [33.4987000000, 1],
              [34.6907000000, 1],
            ],
          };
        default:
          return {
            centers: [
              [30.809091666666664, -87.97075833333334, 0],
              [33.496368965517235, -86.64008275862068, 1],
            ],
            points: [
              [30.6746000000, -88.0902000000, 0],
              [33.3549000000, -86.9919000000, 1],
              [34.7346000000, -86.6032000000, 1],
              [33.5107000000, -86.8821000000, 1],
              [33.5502000000, -86.6463000000, 1],
              [33.4757000000, -86.7962000000, 1],
              [34.6976000000, -86.5835000000, 1],
              [33.3609000000, -86.9974000000, 1],
              [30.5863000000, -88.1956000000, 0],
              [32.3371000000, -86.2521000000, 1],
              [33.5101000000, -86.8812000000, 1],
              [30.7054000000, -88.1364000000, 0],
              [33.5193000000, -86.8078000000, 1],
              [32.3399000000, -86.2159000000, 1],
              [33.4433000000, -86.8337000000, 1],
              [30.6910000000, -88.0708000000, 0],
              [32.3897000000, -87.0762000000, 1],
              [33.4929000000, -86.8279000000, 1],
              [32.3522000000, -86.3283000000, 1],
              [32.4509000000, -85.0069000000, 1],
              [30.6784000000, -88.1140000000, 0],
              [30.6414000000, -88.1467000000, 0],
              [30.6974000000, -88.1340000000, 0],
              [31.0332000000, -87.4066000000, 0],
              [33.5162000000, -86.7424000000, 1],
              [30.4112000000, -87.6005000000, 0],
              [34.0143000000, -86.0145000000, 1],
              [32.2934000000, -87.7977000000, 0],
              [34.0229000000, -85.9888000000, 1],
              [32.4208000000, -85.7154000000, 1],
              [30.6002000000, -87.9033000000, 0],
              [33.1740000000, -87.5276000000, 1],
              [33.3591000000, -86.7341000000, 1],
              [34.8046000000, -87.6629000000, 1],
              [33.7176000000, -85.8147000000, 1],
              [34.8470000000, -87.6587000000, 1],
              [34.5331000000, -86.9992000000, 1],
              [30.6966000000, -88.0533000000, 0],
              [32.2757000000, -86.6130000000, 1],
              [33.4987000000, -86.7879000000, 1],
              [34.6907000000, -86.5726000000, 1],
            ],
          };
      }
    },
  },
  {
    url: 'dataset/sql/validate$',
    method: 'POST',
    handler: function(this) {
      return {};
    },
  },
  {
    url: 'dataset/sql/validateinsert$',
    method: 'POST',
    handler: function(this) {
      return {};
    },
  },
];
