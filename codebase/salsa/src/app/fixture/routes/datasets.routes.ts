import * as _ from 'lodash';

import { IAsset, IBackendList } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { BinaryDataset } from '../../datasets/dataset.interfaces';
import { IFixtureBinaryDataset, IFixtureServiceRoute } from '../fixture.interface';

export const datasetsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'datasets$',
    method: 'GET',
    handler: function (this, params, user) {
      return this.serveAssetListRequest(this.collections.binaryDatasets, IAsset.Type.DATASET, params, user);
    },
  },
  {
    url: 'datasets/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params) {
      const id = params[1];
      const models = this.collections.binaryDatasets;
      const model = models.findOne({ id: id });

      if (!model) {
        throw new Error('Dataset Not found: ' + id);
      }

      return model;
    },
  },
  {
    url: 'datasets$',
    method: 'POST',
    handler: function (this, params, user) {
      const models = this.collections.binaryDatasets;

      const newModel: IFixtureBinaryDataset = Object.assign(
        {
          id: Date.now().toString(),
          ownerId: user.id,
          name: null,
          status: BinaryDataset.Status.IDLE,
          created: Date.now().toString(),
          updated: Date.now().toString(),
        },
        params,
      );

      return models.insertOne(newModel);
    },
  },
  {
    url: 'datasets/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params) {
      const id = params[1];
      const models = this.collections.binaryDatasets;
      const model = models.findOne({ id: id });
      if (!model) {
        throw new Error('Dataset Not found');
      }


      // update (specific properties only)
      [
        'name',
        'description',
      ].forEach(prop =>
        params[prop] !== undefined && (model[prop] = params[prop]),
      );

      models.update(model);

      return model;
    },
  },
  {
    url: 'datasets/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params) {
      const id = params[1];
      const models = this.collections.binaryDatasets;
      const model = models.findOne({ id: id });
      if (!model) {
        throw new Error('Dataset Not found');
      }

      models.remove(model);

      return model;
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/ls$',
    method: 'GET',
    handler: function (this, params): IBackendList<BinaryDataset.File> {
      const id = params[1];
      const models = this.collections.binaryDatasets;
      const model: IFixtureBinaryDataset = models.findOne({ id: id });
      if (!model) {
        throw new Error('Dataset Not found');
      }

      const files = model.__files || [];

      return {
        data: files,
        count: files.length,
      };
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/files/(.*?)$',
    method: 'DELETE',
    handler: function (this, params, user): void {
      const datasetId = params[1];
      const models = this.collections.binaryDatasets;
      const model = _.cloneDeep(models.findOne({ id: datasetId, ownerId: user.id }));
      if (!model) {
        throw new Error('Dataset Not found');
      }

      const fileId = decodeURI(params[2]);
      const fileIndex = model.__files.findIndex(file => file.filename === fileId);
      if (fileIndex === -1) {
        throw new Error('File was not found: ' + fileId);
      }

      model.__files.splice(fileIndex, 1);
      models.update(model);

      return;
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/download$',
    method: 'GET',
    handler: function (this, params, user): string {
      return 'file data';
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/files/download(.*?)$',
    method: 'GET',
    handler: function (this, params, user): string {
      return 'file data';
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/files',
    method: 'POST',
    handler: function(this, params, user) {
      const datasetId = params[1];
      const models = this.collections.binaryDatasets;
      const model = _.cloneDeep(models.findOne({ id: datasetId, ownerId: user.id }));
      if (!model) {
        throw new Error('Dataset Not found');
      }

      const newFile = Object.assign(
        {},
        {
          filename: Date.now().toString(),
          filepath: 'uploaded/files/path/123.xyz',
          filesize: 123,
          modified: Date.now().toString(),
        },
      );

      if (!model.__files) {
        model.__files = [];
      }

      model.__files.push(newFile);
      models.update(model);

      return;
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/import',
    method: 'POST',
    handler: function(this, params, user) {
      const datasetId = params[1];
      const models = this.collections.binaryDatasets;
      const model = _.cloneDeep(models.findOne({ id: datasetId, ownerId: user.id }));
      if (!model) {
        throw new Error('Dataset Not found');
      }

      model.status = BinaryDataset.Status.IMPORTING;
      models.update(model);

      const processes = this.collections.processes;
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.DATASET,
        targetId: model.id,
        progress: 0,
        jobType: IProcess.JobType.DATASET_IMPORT,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
      });

      return;
    },
  },
  {
    url: 'datasets/([\\w\\-]+)/export',
    method: 'POST',
    handler: function(this, params, user) {
      const datasetId = params[1];
      const models = this.collections.binaryDatasets;
      const model = _.cloneDeep(models.findOne({ id: datasetId, ownerId: user.id }));
      if (!model) {
        throw new Error('Dataset Not found');
      }

      model.status = BinaryDataset.Status.EXPORTING;
      models.update(model);

      const processes = this.collections.processes;
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.DATASET,
        targetId: model.id,
        progress: 0,
        jobType: IProcess.JobType.DATASET_EXPORT,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
      });

      return;
    },
  },
];
