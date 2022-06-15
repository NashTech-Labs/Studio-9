import { IAlbum } from '../../albums/album.interface';
import { IAsset, IBackendList, IListRequest, IObjectId, TObjectId } from '../../core/interfaces/common.interface';
import {
  IOnlineTriggeredJob,
  IOnlineTriggeredJobCreate,
  IOnlineTriggeredJobUpdate,
} from '../../deploy/online-job.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const onlineJobsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'online-jobs$',
    method: 'GET',
    handler: function (this, params: IListRequest): IBackendList<IOnlineTriggeredJob> {
      const jobs = this.collections.jobs;
      const resultset = jobs.chain();

      return this.prepareListResponse(resultset, params);
    },
  },
  {
    url: 'online-jobs$',
    method: 'POST',
    handler: function (this, params: IOnlineTriggeredJobCreate & { 1: TObjectId }, user): IOnlineTriggeredJob {
      const jobs = this.collections.jobs;
      if (params.target.type === IAsset.Type.MODEL
        && params.options.type === IOnlineTriggeredJob.JobType.ONLINE_PREDICTION
      ) {
        const job: IOnlineTriggeredJob = {
          id: Date.now().toString(),
          ownerId: user.id,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          status: IOnlineTriggeredJob.Status.IDLE,
          options: {
            type: IOnlineTriggeredJob.JobType.ONLINE_PREDICTION,
          },
          name: params.name,
          target: params.target,
          enabled: params.enabled,
        };
        return jobs.insertOne(job);
      } else if (params.target.type === IAsset.Type.CV_MODEL
        && params.options.type === IOnlineTriggeredJob.JobType.ONLINE_CV_PREDICTION
      ) {
        const cvparams: IOnlineTriggeredJob.CVModelCreateOptions = params.options;
        const albums = this.collections.albums;
        const album = {
          id: Date.now().toString(),
          ownerId: user.id,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          status: IAlbum.Status.ACTIVE,
          name: cvparams.outputAlbumName,
          type: IAlbum.Type.DERIVED,
          inLibrary: true,
          labelMode: IAlbum.LabelMode.CLASSIFICATION,
        };
        albums.insertOne(album);
        const job: IOnlineTriggeredJob = {
          id: Date.now().toString(),
          ownerId: user.id,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          status: IOnlineTriggeredJob.Status.IDLE,
          options: {
            type: IOnlineTriggeredJob.JobType.ONLINE_CV_PREDICTION,
            inputBucketId: cvparams.inputBucketId,
            inputImagesPath: cvparams.inputImagesPath,
            outputAlbumId: album.id,
          },
          name: params.name,
          target: params.target,
          enabled: params.enabled,
        };
        return jobs.insertOne(job);
      } else {
        throw new Error('Unknown target/job type');
      }
    },
  },
  {
    url: 'online-jobs/([\\w\\-]+)$',
    method: 'GET',
    handler: function (this, params: { 1: TObjectId }, user): IOnlineTriggeredJob {
      const id = params[1];
      const jobs = this.collections.jobs;
      const job = jobs.findOne({ id: id, ownerId: user.id });
      if (!job) {
        throw new Error('Job Not Found');
      }
      return job;
    },
  },
  {
    url: 'online-jobs/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: IOnlineTriggeredJobUpdate & { 1: TObjectId }, user): IOnlineTriggeredJob {
      const id = params[1];
      const jobs = this.collections.jobs;
      const job = jobs.findOne({ id: id, ownerId: user.id });
      if (!job) {
        throw new Error('Job Not Found');
      }

      ['name', 'enabled'].forEach(prop => {
        if (params[prop] !== undefined) {
          job[prop] = params[prop];
        }
      });

      return jobs.update(job);
    },
  },
  {
    url: 'online-jobs/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params: { 1: TObjectId }, user): IObjectId {
      const id = params[1];
      const jobs = this.collections.jobs;
      const job = jobs.findOne({ id: id, ownerId: user.id });

      if (!job) {
        throw new Error('Job Not Found');
      }

      jobs.remove(job);

      return { id: id };
    },
  },
];
