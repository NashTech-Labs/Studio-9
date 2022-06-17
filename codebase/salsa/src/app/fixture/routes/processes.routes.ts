import * as _ from 'lodash';

import { IAlbum } from '../../albums/album.interface';
import config from '../../config';
import { IAsset } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { BinaryDataset } from '../../datasets/dataset.interfaces';
import { IFixtureServiceRoute } from '../fixture.interface';

export const processesRoutes: IFixtureServiceRoute[] = [
  {
    url: 'processes$',
    method: 'GET',
    handler: function(this, params, user) {
      const processes = this.collections.processes;
      const resultset = processes.chain().find({ownerId: user.id});

      return this.prepareListResponse(resultset, params);
    },
  },
  {
    url: 'processes/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const processes = this.collections.processes;
      return processes.findOne({id: id, ownerId: user.id});
    },
  },
  ...config.asset.list.map((assetType: IAsset.Type) => {
    return {
      url: `${config.asset.aliasesPlural[assetType]}/([\\w\\-]+)/process$`,
      method: 'GET',
      handler: function(this, params) {
        const id = params[1];
        const processes = this.collections.processes.find({targetId: id, target: assetType}); // ownerId: user.id

        return _.sortBy(processes, _ => _.meta.created).pop();
      },
    };
  }),
  {
    url: 'processes/([\\w\\-]+)/cancel$',
    method: 'POST',
    handler: function(this, params, user) {
      const id = params[1];
      const processes = this.collections.processes;
      const models = this.collections.models;
      const cvModels = this.collections.cvModels;
      const albums = this.collections.albums;
      const experiments = this.collections.experiments;
      const datasets = this.collections.binaryDatasets;
      const process = processes.findOne({id: id, ownerId: user.id});
      if (!process) {
        throw new Error('Process was not found');
      }
      process.status = IProcess.Status.CANCELLED;
      processes.update(process);
      let entity;
      switch (process.target) {
        case IAsset.Type.MODEL:
          entity = models.findOne({ id: process.targetId, ownerId: user.id });
          entity.status = config.model.status.values.CANCELLED;
          models.update(entity);
          break;
        case IAsset.Type.CV_MODEL:
          entity = cvModels.findOne({ id: process.targetId, ownerId: user.id });
          entity.status = config.cvModel.status.values.CANCELLED;
          cvModels.update(entity);
          break;
        case IAsset.Type.ALBUM:
          entity = albums.findOne({ id: process.targetId, ownerId: user.id });
          entity.status = IAlbum.Status.ACTIVE;
          albums.update(entity);
          break;
        case IAsset.Type.EXPERIMENT:
          entity = experiments.findOne({ id: process.targetId, ownerId: user.id });
          entity.status = config.experiments.status.values.CANCELLED;
          cvModels.update(entity);
          break;
        case IAsset.Type.DATASET:
          entity = datasets.findOne({ id: process.targetId, ownerId: user.id });
          entity.status = BinaryDataset.Status.IDLE;
          datasets.update(entity);
          break;
      }
      return { id: entity.id };
    },
  },
];
