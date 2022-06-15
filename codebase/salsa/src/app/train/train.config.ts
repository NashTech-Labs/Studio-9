import { describeEnum } from '../utils/misc';

import { CVModelType, ICVModel } from './cv-model.interface';
import { IModelTrainSummary, ITabularModel, IVariableImportance } from './model.interface';

export type ITrainTechniqueParameterDefinition = {
  type: 'continuous';
  name: string;
  title: string;
  min: number;
  max: number;
  step?: number;
} | {
  type: 'categorical';
  name: string;
  title: string;
  options?: number[] | string[];
};

export const trainConfig = {
  model: {
    class: describeEnum(ITabularModel.Class, {
      labels: {
        REGRESSION: 'Linear Regression',
        BINARY_CLASSIFICATION: 'Bin. Classification',
        CLASSIFICATION: 'Classification',
      },
    }),
    status: describeEnum(ITabularModel.Status, {
      labels: {
        ACTIVE: 'Done',
        TRAINING: 'Training',
        PREDICTING: 'Predicting',
        ERROR: 'Error',
        CANCELLED: 'Cancelled',
      },
    }),
    trainState: describeEnum(IModelTrainSummary.TrainState, {
      labels: {
        TRAINING: 'Training',
        REFINING: 'Refining',
        COMPLETE: 'Complete',
      },
    }),
    variableImportanceDecision: describeEnum(IVariableImportance.Decision, {
      labels: {
        REJECTED: 'Rejected',
        CONFIRMED: 'Confirmed',
        TENTATIVE: 'Tentative',
        SHADOW: 'Shadow Attribute',
      },
    }),
    pipelineStage: describeEnum(ITabularModel.PipelineStage, {
      labels: {
        OUTLIERS_TREATMENT: 'Outlier Treatment',
        DESKEWING: 'DeSkewing',
        IMPUTATION: 'Imputation',
        NORMALIZATION: 'Normalization',
        MULTICOLINEARITY_TREATMENT: 'Multicolinearity',
        MODEL_PRIMITIVES: 'Model Primitives',
      },
      techniques: {
        OUTLIERS_TREATMENT: [ITabularModel.StageTechnique.OT_STDDEV, ITabularModel.StageTechnique.OT_KMEANS],
        DESKEWING: [ITabularModel.StageTechnique.DS_BOXCOX, ITabularModel.StageTechnique.DS_MANLY, ITabularModel.StageTechnique.DS_BOXCOX_MOD],
        IMPUTATION: [
          ITabularModel.StageTechnique.IM_KNN,
          ITabularModel.StageTechnique.IM_LINEAR,
          ITabularModel.StageTechnique.IM_EXPONENT,
          ITabularModel.StageTechnique.IM_POLYNOM,
        ],
        NORMALIZATION: [ITabularModel.StageTechnique.NM_ZSCORE, ITabularModel.StageTechnique.NM_MINMAX, ITabularModel.StageTechnique.NM_INDEX],
        MULTICOLINEARITY_TREATMENT: [ITabularModel.StageTechnique.MC_PCA],
        MODEL_PRIMITIVES: [
          ITabularModel.StageTechnique.MP_GLM,
          ITabularModel.StageTechnique.MP_GLM_ELASTIC_NET,
          ITabularModel.StageTechnique.MP_RANDOM_FOREST,
          ITabularModel.StageTechnique.MP_SVM,
          ITabularModel.StageTechnique.MP_XGBOOST,
          ITabularModel.StageTechnique.MP_DECISION_TREE,
          ITabularModel.StageTechnique.MP_DNN,
        ],
      },
      defaultTechniques: { // null means all available
        OUTLIERS_TREATMENT: null,
        DESKEWING: null,
        IMPUTATION: null,
        NORMALIZATION: null,
        MULTICOLINEARITY_TREATMENT: null,
        MODEL_PRIMITIVES: [ITabularModel.StageTechnique.MP_GLM],
      },
    }),
    pipelineOrder: [
      ITabularModel.PipelineStage.OUTLIERS_TREATMENT,
      ITabularModel.PipelineStage.DESKEWING,
      ITabularModel.PipelineStage.IMPUTATION,
      ITabularModel.PipelineStage.NORMALIZATION,
      ITabularModel.PipelineStage.MULTICOLINEARITY_TREATMENT,
      ITabularModel.PipelineStage.MODEL_PRIMITIVES,
    ],
    requiredStages: [
      ITabularModel.PipelineStage.MODEL_PRIMITIVES,
    ],
    stageTechnique: describeEnum(ITabularModel.StageTechnique, {
      labels: {
        OT_STDDEV: 'Standard Deviation',
        OT_KMEANS: 'K Means Probability Percentile',

        DS_BOXCOX: 'Box Cox',
        DS_MANLY: 'Manly',
        DS_BOXCOX_MOD: 'Modified Box Cox',

        IM_KNN: 'KNN',
        IM_LINEAR: 'Linear',
        IM_EXPONENT: 'Exponential',
        IM_POLYNOM: 'Polynomial',

        NM_ZSCORE: 'Z-score Normalizer',
        NM_MINMAX: 'Min-Max Normalizer',
        NM_INDEX: 'Index Normalizer',

        MC_PCA: 'PCA',

        MP_GLM: 'GLM (Logistic/Linear)',
        MP_GLM_ELASTIC_NET: 'GLM - Elastic Net',
        MP_RANDOM_FOREST: 'Random Forest',
        MP_SVM: 'Support Vector Machine (SVM)',
        MP_XGBOOST: 'XGBoost',
        MP_DECISION_TREE: 'Decision Tree',
        MP_DNN: 'Deep Neural Network',
      },
      params: describeEnum.ensureType<ITabularModel.StageTechnique, ITrainTechniqueParameterDefinition[]>({
        OT_STDDEV: [
          {
            type: 'continuous',
            name: 'sigma',
            title: 'Sigma (SD)',
            min: 0.5,
            max: 3,
          },
        ],
        OT_KMEANS: [
          {
            type: 'continuous',
            name: 'min_percentile',
            title: 'Min Percentile',
            min: 0,
            max: 0.2,
          },
        ],

        DS_BOXCOX: [
          {
            type: 'continuous',
            name: 'lambda',
            title: 'lambda',
            min: 0,
            max: 1,
          },
        ],
        DS_MANLY: [
          {
            type: 'continuous',
            name: 'lambda',
            title: 'lambda',
            min: 0,
            max: 1,
          },
        ],
        DS_BOXCOX_MOD: [
          {
            type: 'continuous',
            name: 'lambda',
            title: 'lambda',
            min: 0,
            max: 1,
          },
        ],

        IM_KNN: [],
        IM_LINEAR: [],
        IM_EXPONENT: [],
        IM_POLYNOM: [
          {
            type: 'categorical',
            name: 'order',
            title: 'order',
            options: [1, 2, 3, 4, 5, 6, 7],
          },
        ],

        NM_ZSCORE: [],
        NM_MINMAX: [],
        NM_INDEX: [
          {
            type: 'categorical',
            name: 'operator',
            title: 'Index Operator',
            options: ['multiply', 'divide'],
          },
        ],

        MC_PCA: [
          {
            type: 'continuous',
            name: 'pcs_number',
            title: 'Number of PCs',
            min: 0.03,
            max: 0.99,
          },
        ],

        MP_GLM: [],
        MP_GLM_ELASTIC_NET: [
          {
            type: 'continuous',
            name: 'alpha',
            title: 'alpha (lasso/L1)',
            min: 0.0,
            max: 1.0,
          },
          {
            type: 'continuous',
            name: 'lambda',
            title: 'lambda (ridge/L2)',
            min: 0.001,
            max: 100.0,
            step: 0.001,
          },
        ],
        MP_RANDOM_FOREST: [
          {
            type: 'continuous',
            name: 'size_term_nodes',
            title: 'size_term_nodes',
            min: 0.00001,
            max: 0.1,
            step: 0.00001,
          },
          {
            type: 'continuous',
            name: 'trees_number',
            title: 'Number of trees',
            min: 100,
            max: 500,
            step: 1, // integer value
          },
          {
            type: 'continuous',
            name: 'multiplier',
            title: 'multiplier',
            min: 1.0,
            max: 2.0,
          },
        ],
        MP_SVM: [
          {
            type: 'continuous',
            name: 'c',
            title: 'C (Penalty)',
            min: 1.0,
            max: 2.0,
          },
          {
            type: 'categorical',
            name: 'kernel',
            title: 'kernel',
            options: ['linear', 'rbf', 'poly', 'sigmoid'],
          },
          {
            type: 'continuous',
            name: 'degree',
            title: 'degree',
            min: 1.0,
            max: 3.0,
          },
          {
            type: 'continuous',
            name: 'gamma',
            title: 'gamma',
            min: 1.0,
            max: 2.0,
          },
        ],
        MP_XGBOOST: [
          {
            type: 'continuous',
            name: 'rate',
            title: 'Learning Rate',
            min: 2 / 1000,
            max: 10 / 100,
            step: 2 / 1000,
          },
          {
            type: 'continuous',
            name: 'row_sampling',
            title: 'Row Sampling',
            min: 0.5,
            max: 1.0,
          },
          {
            type: 'continuous',
            name: 'col_sampling',
            title: 'Column Sampling',
            min: 0.4,
            max: 1.0,
          },
          {
            type: 'continuous',
            name: 'min_leaf_weight',
            title: 'Min leaf weight',
            min: 0.1,
            max: 3.0,
          },
          {
            type: 'continuous',
            name: 'max_tree_depth',
            title: 'max_tree_depth (integer)',
            min: 4,
            max: 10,
            step: 1, // integer value
          },
        ],
        MP_DECISION_TREE: [],
        MP_DNN: [],
      }),
    }),
  },
  cvModel: {
    fileExtensions: ['.bin'],
    status: describeEnum(ICVModel.Status, {
      labels: {
        ACTIVE: 'Done',
        TRAINING: 'Training',
        SAVING: 'Saving',
        PREDICTING: 'Predicting',
        ERROR: 'Error',
        CANCELLED: 'Cancelled',
      },
    }),
    modelType: describeEnum(CVModelType.Type, {
      labels: {
        TL: 'Transfer Learning',
        CUSTOM: 'Custom',
      },
    }),
    tlType: describeEnum(CVModelType.TLType, {
      labels: {
        CLASSIFICATION: 'Classifier',
        LOCALIZATION: 'Detector',
        AUTOENCODER: 'AutoEncoder',
      },
    }),
  },
  hints: {
    validationHelpText: `Unless any of these datasets defined manually, <br />
      system will select some training dataset part to use for validation.`,
    cvPipelineHelpText: `
      One Step Pipeline = Train a Complete Model from scratch.<br />
      Two Step Pipeline = Select a Pre-Trained/Train new Feature Extractor Model PLUS Train the classifier/detector.
    `,
    cvTuneFeatureExtractorText: `
      If <u>Yes</u> Feature Extractor layers are also trained while classifier/detector train is happening.<br />
      If <u>No</u> Feature Extractor layers are frozen and are not trained along with classifier/detector.
    `,
    defaultVisualThresholdHelpText: `Defines a minimal threshold to filter out bounding box predictions based on the class confidence score`,
    iouThresholdHelpText: `Intersection over Union threshold defines how much overlap between a prediction and the ground truth is considered a match`,
    inputSizeHelpText: `The input size of the model can be changed with this parameter. All images that are passed to the model are resized to these dimensions`,
    featureExtractorLearningRate: `Learning rate applicable to the Backbone during training`,
    modelLearningRate: `Learning rate applicable to the Consumer during training`,
  },
  inputSizes: [
    { width: 512, height: 512 },
    { width: 608, height: 608 },
  ],
};
