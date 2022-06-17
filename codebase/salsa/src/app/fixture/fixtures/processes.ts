import { IAsset } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { IFixtureData } from '../fixture.interface';

export const processes: IFixtureData<IProcess> = {
  data: [
    // for Prediction
    // TODO: for what this process?
    {
      id: 'modelId3',
      ownerId: 'ownerId1',
      target: IAsset.Type.MODEL,
      targetId: 'modelId3',
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:01', // Date
      started: '2016-01-01 01:01', // Date
      jobType: IProcess.JobType.TABULAR_TRAIN,
    },
    {
      id: 'modelId2',
      ownerId: 'ownerId1',
      target: IAsset.Type.MODEL,
      targetId: 'modelId2',
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:02', // Date
      started: '2016-01-01 01:02', // Date
      jobType: IProcess.JobType.TABULAR_TRAIN,
    },
    {
      id: 'cvModelId2',
      ownerId: 'ownerId1',
      target: IAsset.Type.CV_MODEL,
      targetId: 'cvModelId2',
      status: IProcess.Status.COMPLETED, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:05', // Date
      started: '2016-01-01 01:05', // Date
      completed: '2016-02-01 01:05', // Date
      jobType: IProcess.JobType.CV_MODEL_TRAIN,
    },
    {
      id: 'cvModelId1',
      ownerId: 'ownerId1',
      target: IAsset.Type.CV_MODEL,
      targetId: 'cvModelId1',
      status: IProcess.Status.FAILED, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-02 02:01', // Date
      started: '2016-01-02 02:01', // Date
      jobType: IProcess.JobType.CV_MODEL_TRAIN,
      cause: `Line 1\nLine 2\nLine 2\nLine 2\nLine 2\nLine 2\nLine 2\n`,
    },
    // for Replays
    {
      id: 'flowId2',
      ownerId: 'ownerId1',
      target: IAsset.Type.FLOW,
      targetId: 'flowId2', // id of replay
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-02 01:01', // Date
      started: '2016-01-02 01:01', // Date
      jobType: IProcess.JobType.CV_MODEL_PREDICT,
    },
    {
      id: 'flowId3',
      ownerId: 'ownerId1',
      target: IAsset.Type.FLOW,
      targetId: 'flowId3', // id of replay
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-02 01:02', // Date
      started: '2016-01-02 01:02', // Date
      jobType: IProcess.JobType.CV_MODEL_PREDICT,
    },
    {
      id: 'tableId101',
      ownerId: 'ownerId1',
      target: IAsset.Type.TABLE,
      targetId: 'tableId101',
      status: IProcess.Status.FAILED, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:01', // Date
      started: '2016-01-01 01:01', // Date
      jobType: IProcess.JobType.TABULAR_UPLOAD,
      cause: 'Here goes the fail reason',
    },
    {
      id: 'experiment1',
      ownerId: 'ownerId1',
      target: IAsset.Type.EXPERIMENT,
      targetId: 'experiment1',
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:05', // Date
      started: '2016-01-01 01:05', // Date
      completed: '2016-02-01 01:05', // Date
      jobType: IProcess.JobType.GENERIC_EXPERIMENT,
    },
    {
      id: 'experiment5',
      ownerId: 'ownerId1',
      target: IAsset.Type.EXPERIMENT,
      targetId: 'experiment5',
      status: IProcess.Status.RUNNING, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:05', // Date
      started: '2016-01-01 01:05', // Date
      completed: '2016-02-01 01:05', // Date
      jobType: IProcess.JobType.GENERIC_EXPERIMENT,
    },
    {
      id: 'experiment9',
      ownerId: 'ownerId1',
      target: IAsset.Type.EXPERIMENT,
      targetId: 'experiment9',
      status: IProcess.Status.FAILED, // experimental
      progress: 0.5, // from 0 to 1
      estimate: 30, // experimental // seconds?
      created: '2016-01-01 01:05', // Date
      started: '2016-01-01 01:05', // Date
      completed: '2016-02-01 01:05', // Date
      jobType: IProcess.JobType.GENERIC_EXPERIMENT,
    },
  ],
  options: {
    indices: ['id'],
  },
};

