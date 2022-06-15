import { ICVPrediction, ICVPredictionStatus } from '../../play/cv-prediction.interface';

export const cvPredictions = {
  data: <ICVPrediction[]> [
    {
      id: 'cvpredictionId1',
      ownerId: 'ownerId1',
      modelId: 'cvModelId1',
      name: 'Cifar 10 prediction',
      status: ICVPredictionStatus.DONE,
      input: 'cifar10',
      output: 'cifar10predicted',
      created: '2016-01-01 01:01',
      updated: '2016-05-05 05:05',
      predictionTimeSpentSummary: {
        dataLoadingTime: 123,
        modelLoadingTime: 1234,
        pipelineDetails: [
          {time: 101, description: 'FCN 2-layer Classifier'},
          {time: 354, description: 'FCN 1-layer Classifier'},
        ],
        predictionTime: 123,
        tasksQueuedTime: 11,
        totalJobTime: 222,
      },
      evaluationTimeSpentSummary: {
        dataLoadingTime: 321,
        modelLoadingTime: 4321,
        pipelineDetails: [
          {time: 101, description: 'FCN 2-layer Classifier'},
          {time: 354, description: 'FCN 1-layer Classifier'},
        ],
        scoreTime: 412,
        tasksQueuedTime: 333,
        totalJobTime: 142,
      },
    },
  ],
  options: {
    indices: ['id'],
  },
};
