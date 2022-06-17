import { PipelineOperator } from '../../../pipelines/pipeline.interfaces';
import { IFixtureData } from '../../fixture.interface';


export const operatorCategories: IFixtureData<PipelineOperator.Category> = {
  data: [
    {
      id: 'SELECTOR',
      name: 'Selectors',
      icon: 'selector',
    },
    {
      id: 'SAVER',
      name: 'Savers',
      icon: 'saver',
    },
    {
      id: 'ALBUM_TRANSFORMER',
      name: 'Album Transformers',
      icon: 'transformer',
    },
    {
      id: 'TRANSFORMER',
      name: 'Transformers',
      icon: 'transformer',
    },
    {
      id: 'FEATURE_TRANSFORMER',
      name: 'Feature Transformers',
      icon: 'feature-transformer',
    },
    {
      id: 'FEATURE_EXTRACTOR',
      name: 'CV Neural Network',
      icon: 'feature-extractor',
    },
    {
      id: 'LEARNER',
      name: 'Learners',
      icon: 'learner',
    },
    {
      id: 'DETECTOR',
      name: 'Detectors',
      icon: 'detector',
    },
    {
      id: 'DECODER',
      name: 'Decoders',
      icon: 'decoder',
    },
    {
      id: 'OTHER',
      name: 'Other',
      icon: 'other',
    },
    {
      id: 'CLASSIFIER',
      name: 'Classifiers',
      icon: 'classifier',
    },
    {
      id: 'PREDICTOR',
      name: 'Predictors',
      icon: 'predictor',
    },
    {
      id: 'DATA_PREPARATION',
      name: 'Data Preparation',
      icon: 'data-preparer',
    },
    {
      id: 'PIPELINE_OPERATOR',
      name: 'Pipeline Operators',
      icon: 'unknown',
    },
    {
      id: 'METRIC',
      name: 'Metrics',
      icon: 'metrics',
    },
  ],
  options: {
    indices: ['id'],
  },
};
