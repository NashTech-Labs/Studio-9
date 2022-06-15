import config from '../../config';
import { IAsset } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { IReplayCreate } from '../../play/replay.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

import { getFlowOutputs } from './compose.routes';

export const replaysRoutes: IFixtureServiceRoute[] = [
  {
    url: 'replays$', //create
    method: 'POST',
    handler: function (this, params: IReplayCreate, user) {
      const flows = this.collections.flows;
      const replays = this.collections.replays;
      const tables = this.collections.tables;
      const processes = this.collections.processes;

      const originalFlow = flows.findOne({ id: params['flowId'], ownerId: user.id });
      // create flow entity
      const newFlow = Object.assign({}, originalFlow, {
        id: Date.now().toString(),
        name: params['name'],
        status: config.flow.status.values.RUNNING,
      });
      delete newFlow['$loki'];

      const newReplay = Object.assign({}, {
        id: Date.now().toString(),
        ownerId: user.id,
        status: config.replay.status.values.RUNNING,
        originalFlowId: originalFlow.id,
        flowId: newFlow.id,
        name: newFlow.name,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
      });

      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.FLOW,
        targetId: newFlow.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.CV_MODEL_PREDICT,
      });

      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.REPLAY,
        targetId: newReplay.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.CV_MODEL_PREDICT,
      });

      const mappedOutputTables: { originalId: string; newId: string }[] = [];
      const outputs = getFlowOutputs(this, originalFlow);
      outputs.forEach(output => {
        const requestedName = params.outputTableNames.find((item) => item.tableId === output.tableId);
        const table = tables.findOne({ id: output.tableId });
        const newOutputTable = Object.assign({}, table, {
          id: Date.now().toString() + '_' + output.tableId,
          status: config.table.status.values.ACTIVE,
          name: requestedName.newTableName,
        });
        delete newOutputTable['$loki'];
        tables.insertOne(newOutputTable);
        mappedOutputTables.push({ originalId: requestedName.tableId, newId: newOutputTable.id });
      });

      newFlow.steps = originalFlow.steps.map(step => {
        const mappedInputTableIds = step.input.map(id => {
          const externalMapping = params['columnMappings'].find(_ => _.sourceColumn.tableId === id);
          const internalMapping = mappedOutputTables.find(_ => _.originalId === id);
          return internalMapping ? internalMapping.newId : externalMapping.mappedColumn.tableId;
        });
        const mappedOutputTable = mappedOutputTables.find(_ => _.originalId === step.output);
        if (!mappedOutputTable) {
          throw new Error('Mapped Output Table Not Found');
        }
        return Object.assign({}, step, {
          input: mappedInputTableIds,
          output: mappedOutputTable.newId,
        });
      });
      newFlow.tables = newFlow.steps.reduce((acc, step) => {
        if (step.input && step.input.length) {
          step.input.forEach(tableId => {
            if (!acc.includes(tableId)) {
              acc.push(tableId);
            }
          });
        }
        if (step.output && !acc.includes(step.output)) {
          acc.push(step.output);
        }
        return acc;
      }, []);
      flows.insertOne(newFlow);

      return replays.insertOne(newReplay);
    },
  },
  {
    url: 'replays$', //list
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.replays, IAsset.Type.REPLAY, params, user);
    },
  },
  {
    url: 'replays/([\\w\\-]+)$', //one
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const replays = this.collections.replays;
      const replay = replays.findOne({id: id, ownerId: user.id});
      if (replay) {
        return replay;
      } else {
        throw new Error('Not found');
      }
    },
  },
  {
    url: 'replays/([\\w\\-]+)$', //one
    method: 'DELETE',
    handler: function (this, params, user) {
      const id = params[1];
      const replays = this.collections.replays;
      const replay = replays.findOne({ id: id, ownerId: user.id });
      if (!replay) {
        throw new Error('Not found');
      }
      replays.remove(replay);
      return { id: id };
    },
  },

];
