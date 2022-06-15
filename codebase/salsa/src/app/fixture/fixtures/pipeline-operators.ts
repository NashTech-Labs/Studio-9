import { IAsset } from '../../core/interfaces/common.interface';
import { PipelineOperator } from '../../pipelines/pipeline.interfaces';
import { IFixtureData } from '../fixture.interface';

const albumType = {
  definition: 'studio9.library.albums.Album',
  parents: [],
  typeArguments: [],
};

const dataLoaderType = {
  definition: 'torch.utils.data.dataloader.DataLoader',
  parents: [],
  typeArguments: [],
};

const featureExtractorType = {
  definition: 'ml_lib.feature_extractors.FeatureExtractor.FeatureExtractor',
  parents: [],
  typeArguments: [],
};

const dataInfoType = {
  definition: 'studio9.operators.DataInfo',
  parents: [],
  typeArguments: [],
};

const transformationType = {
  definition: 'studio9.ml.cv.dataset.transformations.Transformation',
  parents: [],
  typeArguments: [],
};

const baseModelType = {
  definition: 'studio9.ml.cv.transfer_learning.detector.BaseModel',
  parents: [],
  typeArguments: [],
};

const detectionModelType = {
  definition: 'studio9.ml.cv.transfer_learning.detector.DetectionModel',
  parents: [
    baseModelType,
  ],
  typeArguments: [],
};

const predictionModelType = {
  definition: 'studio9.ml.cv.transfer_learning.detector.PredictionModel',
  parents: [
    baseModelType,
  ],
  typeArguments: [],
};

const neuralClassificationModelType = {
  definition: 'studio9.ml.cv.transfer_learning.classificator.NeuralClassificationModel',
  parents: [
    baseModelType,
  ],
  typeArguments: [],
};

const nonNeuralClassificationModelType = {
  definition: 'studio9.ml.cv.transfer_learning.classificator.NonNeuralClassificationModel',
  parents: [
    baseModelType,
  ],
  typeArguments: [],
};

const odResultType = {
  definition: 'studio9.operators.PredictionResult',
  parents: [],
  typeArguments: [],
};

export const pipelineOperators: IFixtureData<PipelineOperator> = {
  data: [
    {
      id: 'select_album',
      name: 'Select Album',
      className: 'SelectAlbum',
      moduleName: 'studio9.pipelines.operators.selectors',
      packageName: 's9-operators',
      category: 'SELECTOR',
      inputs: [],
      outputs: [
        { type: albumType, description: 'Album' },
      ],
      params: [
        { name: 'album_id', caption: 'Album', multiple: false, assetType: IAsset.Type.ALBUM, type: 'assetReference' },
      ],
    },
    {
      id: 'save_album',
      name: 'Save Album',
      className: 'SaveAlbum',
      moduleName: 'studio9.pipelines.operators.save_album',
      packageName: 's9-operators',
      category: 'SAVER',
      inputs: [
        { name: 'album', description: 'Loaded album', type: albumType, covariate: true },
      ],
      outputs: [],
      params: [
        { name: 'name', caption: 'Album name', multiple: false, options: [], type: 'string' },
        { name: 'description', caption: 'Album description', multiple: false, options: [], type: 'string' },
      ],
    },
    {
      id: 'split_album',
      name: 'Split Album',
      className: 'SplitAlbum',
      moduleName: 'studio9.pipelines.operators.',
      packageName: 's9-operators',
      category: 'ALBUM_TRANSFORMER',
      inputs: [
        { name: 'album', description: 'Loaded album', type: albumType, covariate: true },
      ],
      outputs: [
        { type: albumType, description: 'Train Album' },
        { type: albumType, description: 'Test Album' },
      ],
      params: [
        { name: 'size', caption: 'Size of a first album', type: 'float', defaults: [ 0.8 ], min: 0, max: 1 },
      ],
    },
    {
      id: 'transform_album',
      name: 'Transform Album',
      className: 'TransformAlbum',
      moduleName: 'studio9.pipelines.operators.',
      packageName: 's9-operators',
      category: 'ALBUM_TRANSFORMER',
      inputs: [
        { name: 'album', description: 'Album', type: albumType, covariate: true },
        { name: 'transformation', description: 'Transformation', type: transformationType, covariate: true },
      ],
      outputs: [
        { type: albumType, description: 'Transformed album' },
      ],
      params: [
        { name: 'bloat_factor', caption: 'Bloat factor', type: 'int', defaults: [0], multiple: false },
        { name: 'augment', caption: 'Augment', type: 'boolean', defaults: [true] },
      ],
    },
    {
      id: 'fix_channels',
      name: 'Fix Channels',
      className: 'FixChannels',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [
        { name: 'channels', caption: 'Number of channels', type: 'int', multiple: false },
      ],
    },
    {
      id: 'convert_to_float32',
      name: 'Convert to Float32',
      className: 'ConvertToFloat32',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'box_relative_coordinates',
      name: 'Box Relative Coordinates',
      className: 'BoxRelativeCoordinates',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'resize_and_pad',
      name: 'Resize and Pad',
      className: 'ResizeAndPad',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [
        { name: 'width', caption: 'Width', type: 'int', multiple: false },
        { name: 'height', caption: 'Height', type: 'int', multiple: false },
      ],
    },
    {
      id: 'normalize_by_max',
      name: 'Normalize by Max',
      className: 'NormalizeByMax',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'random_horizontal_flip',
      name: 'Random Horizontal Flip',
      className: 'RandomHorizontalFlip',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'random_vertical_flip',
      name: 'Random Vertical Flip',
      className: 'RandomVerticalFlip',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'rotate_image',
      name: 'Rotate Image',
      className: 'RotateImage',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'shear_image',
      name: 'Shear Image',
      className: 'ShearImage',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'salt_pepper',
      name: 'Salt Pepper',
      className: 'SaltPepper',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'choose_one',
      name: 'Choose One',
      className: 'ChooseOne',
      moduleName: 'studio9.pipelines.operators.transformations',
      packageName: 's9-operators',
      category: 'TRANSFORMER',
      inputs: [
        { name: 'transformation1', description: '', type: transformationType, covariate: true },
        { name: 'transformation2', description: '', type: transformationType, covariate: true },
      ],
      outputs: [
        { type: transformationType, description: '' },
      ],
      params: [],
    },
    {
      id: 'create_dataloader',
      name: 'Create DataLoader',
      className: 'CreateDataLoader',
      moduleName: 'studio9.pipelines.operators.dataloader',
      packageName: 's9-operators',
      category: 'DATA_PREPARATION',
      inputs: [
        { name: 'album', description: '', type: albumType, covariate: true },
        { name: 'transformation', description: '', type: transformationType, covariate: true, optional: true },
        { name: 'data_augmentation_transformation', description: '', type: transformationType, covariate: true, optional: true },
      ],
      outputs: [
        { type: dataLoaderType, description: 'Data loader object' },
        { type: dataInfoType, description: 'Data info' },
      ],
      params: [],
    },
    {
      id: 'create_fe',
      name: 'MobilenetV2 CNN',
      className: 'MobilenetV2',
      moduleName: 'studio9.pipelines.operators.cnn',
      packageName: 's9-operators',
      category: 'FEATURE_EXTRACTOR',
      inputs: [],
      outputs: [
        { type: featureExtractorType, description: 'Feature Extractor' },
      ],
      params: [
        {
          name: 'take_feature_extractor_from',
          caption: 'Take feature extractor from',
          multiple: false,
          options: ['Existing model', 'New model'],
          defaults: ['New model'],
          type: 'string',
        },
        {
          name: 'id_feature_extractor',
          caption: 'Select the model to retrieve the feature extractor',
          multiple: false,
          conditions: { take_feature_extractor_from: { values: ['Existing model'] } },
          assetType: IAsset.Type.CV_MODEL,
          type: 'assetReference',
        },
        { name: 'width_multiplier', caption: 'Width multiplier', type: 'float', defaults: [ 1.0 ], multiple: false, conditions: { take_feature_extractor_from: { values: ['New model'] } } },
        { name: 'pooling_layer', caption: 'Add pooling layer', type: 'boolean', defaults: [ true ], multiple: false, conditions: { take_feature_extractor_from: { values: ['New model'] } } },
      ],
    },
    {
      id: 'create_vgg16_rfb_fe',
      name: 'VGG16 RFB CNN',
      className: 'VGG16RFB',
      moduleName: 'studio9.pipelines.operators.cnn',
      packageName: 's9-operators',
      category: 'FEATURE_EXTRACTOR',
      inputs: [],
      outputs: [
        { type: featureExtractorType, description: 'Feature Extractor' },
      ],
      params: [
        {
          name: 'take_feature_extractor_from',
          caption: 'Take feature extractor from',
          multiple: false,
          options: ['Existing model', 'New model'],
          defaults: ['New model'],
          type: 'string',
        },
        {
          name: 'id_feature_extractor',
          caption: 'Select the model to retrieve the feature extractor',
          multiple: false,
          conditions: { take_feature_extractor_from: { values: ['Existing model'] } },
          assetType: IAsset.Type.CV_MODEL,
          type: 'assetReference',
        },
        { name: 'batch_normalization', caption: 'Enable batch normalization', type: 'boolean', defaults: [ false], multiple: false, conditions: { take_feature_extractor_from: { values: ['New model'] } } },
      ],
    },
    {
      id: 'append_feature_fusion',
      name: 'Append Feature Fusion',
      className: 'AppendFeatureFusion',
      moduleName: 'studio9.pipelines.operators.append_fe_fu',
      packageName: 's9-operators',
      category: 'FEATURE_TRANSFORMER',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
      ],
      outputs: [
        { type: featureExtractorType, description: '' },
      ],
      params: [],
    },
    {
      id: 'rfb_cell',
      name: 'Append RFB Cell',
      className: 'AppendRFBCell',
      moduleName: 'studio9.pipelines.operators.rfbcell',
      packageName: 's9-operators',
      category: 'FEATURE_TRANSFORMER',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
      ],
      outputs: [
        { type: featureExtractorType, description: '' },
      ],
      params: [],
    },
    {
      id: 'create_ssd_detector',
      name: 'Create SSD Detector',
      className: 'CreateSSDDetector',
      moduleName: 'studio9.pipelines.operators.detectors',
      packageName: 's9-operators',
      category: 'DETECTOR',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
        { name: 'data_info', type: dataInfoType, description: '', covariate: true },
      ],
      outputs: [
        { type: detectionModelType, description: '' },
      ],
      params: [],
    },
    {
      id: 'create_fcn_classifier',
      name: 'Create FCN Classifier',
      className: 'CreateFCNClassifier',
      moduleName: 'studio9.pipelines.operators.detectors',
      packageName: 's9-operators',
      category: 'CLASSIFIER',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
        { name: 'data_info', type: dataInfoType, description: '', covariate: true },
      ],
      outputs: [
        { type: neuralClassificationModelType, description: '' },
      ],
      params: [],
    },
    {
      id: 'create_freescale_classifier',
      name: 'Create FreeScale Classifier',
      className: 'CreateFreeScaleClassifier',
      moduleName: 'studio9.pipelines.operators.detectors',
      packageName: 's9-operators',
      category: 'CLASSIFIER',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
        { name: 'data_info', type: dataInfoType, description: '', covariate: true },
      ],
      outputs: [
        { type: neuralClassificationModelType, description: '' },
      ],
      params: [],
    },
    {
      id: 'create_kpcn_mnl_classifier',
      name: 'Create KPCN MNL Classifier',
      className: 'CreateKPCNMNLClassifier',
      moduleName: 'studio9.pipelines.operators.detectors',
      packageName: 's9-operators',
      category: 'CLASSIFIER',
      inputs: [
        { name: 'feature_extractor', type: featureExtractorType, description: '', covariate: true },
        { name: 'data_info', type: dataInfoType, description: '', covariate: true },
      ],
      outputs: [
        { type: nonNeuralClassificationModelType, description: '' },
      ],
      params: [
        { name: 'components', type: 'int', caption: 'Number of main components', defaults: [ 600 ], multiple: false },
      ],
    },
    {
      id: 'learn_detection_model',
      name: 'Learn Detection Model',
      className: 'LearnDetectionModel',
      moduleName: 'studio9.pipelines.operators.learners',
      packageName: 's9-operators',
      category: 'LEARNER',
      inputs: [
        { name: 'model', type: detectionModelType, description: '', covariate: true },
        { name: 'train_dataloader', type: dataLoaderType, description: '', covariate: true },
        { name: 'validate_dataloader', type: dataLoaderType, description: '', covariate: true, optional: true },
      ],
      outputs: [
        { type: detectionModelType, description: '' },
      ],
      params: [
        {
          name: 'max_epoch',
          caption: 'Maximum number of epochs for training',
          multiple: false,
          defaults: [10],
          type: 'int',
        }, {
          name: 'optimizer',
          caption: 'Select optimizer',
          multiple: false,
          options: ['SGD', 'Adam'],
          defaults: ['SGD'],
          type: 'string',
        }, {
          name: 'lr',
          caption: 'Learning rate',
          multiple: false,
          defaults: [0.0010000000474974513],
          type: 'float',
        }, {
          name: 'finetune',
          caption: 'Finetune of feature extractor',
          multiple: false,
          defaults: [true],
          type: 'boolean',
        }, {
          name: 'finetune_lr',
          caption: 'Finetune learning rate',
          multiple: false,
          conditions: { finetune: { value: true } },
          defaults: [0.0010000000474974513],
          type: 'float',
        }, {
          name: 'momentum',
          caption: 'Momentum factor',
          multiple: false,
          conditions: { optimizer: { values: ['SGD'] } },
          defaults: [0],
          type: 'float',
        }, {
          name: 'nesterov',
          caption: 'Enables Nesterov momentum',
          multiple: false,
          conditions: { optimizer: { values: ['SGD'] } },
          defaults: [false],
          type: 'boolean',
        }, {
          name: 'betas',
          caption: 'Betas coefficients',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          options: [],
          defaults: ['(0.9, 0.999)'],
          type: 'string',
        }, {
          name: 'eps',
          caption: 'Epsilon coefficients',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          defaults: [9.99999993922529e-9],
          type: 'float',
        }, {
          name: 'amsgrad',
          caption: 'Whether to use the AMSGrad variant',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          defaults: [false],
          type: 'boolean',
        }, {
          name: 'scheduler_type',
          caption: 'Scheduler Type',
          multiple: false,
          options: ['ReduceLROnPlateau', 'StepLR'],
          defaults: ['ReduceLROnPlateau'],
          type: 'string',
        }, {
          name: 'warmup_period',
          caption: 'A warmup period for scheduler',
          multiple: false,
          defaults: [0],
          type: 'int',
        }, {
          name: 'patience',
          caption: 'Patience period',
          multiple: false,
          defaults: [10],
          type: 'int',
        }, {
          name: 'step_size',
          caption: 'Step size',
          multiple: false,
          conditions: { scheduler_type: { values: ['StepLR'] } },
          defaults: [20],
          type: 'int',
        }, {
          name: 'gamma',
          caption: 'Gamma coefficient',
          multiple: false,
          conditions: { scheduler_type: { values: ['StepLR'] } },
          defaults: [0.5],
          type: 'float',
        },
      ],
    },
    {
      id: 'learn_non_neural_classification_model',
      name: 'Learn Non-neural Classification Model',
      className: 'LearnNonNeuralClassificationModel',
      moduleName: 'studio9.pipelines.operators.learners',
      packageName: 's9-operators',
      category: 'LEARNER',
      inputs: [
        { name: 'model', type: nonNeuralClassificationModelType, description: '', covariate: true },
        { name: 'train_dataloader', type: dataLoaderType, description: '', covariate: true },
        { name: 'validate_dataloader', type: dataLoaderType, description: '', covariate: true, optional: true },
      ],
      outputs: [
        { type: nonNeuralClassificationModelType, description: '' },
      ],
      params: [],
    },
    {
      id: 'learn_neural_classification_model',
      name: 'Learn Neural Classification Model',
      className: 'LearnNeuralClassificationModel',
      moduleName: 'studio9.pipelines.operators.learners',
      packageName: 's9-operators',
      category: 'LEARNER',
      inputs: [
        { name: 'model', type: neuralClassificationModelType, description: '', covariate: true },
        { name: 'train_dataloader', type: dataLoaderType, description: '', covariate: true },
        { name: 'validate_dataloader', type: dataLoaderType, description: '', covariate: true, optional: true },
      ],
      outputs: [
        { type: neuralClassificationModelType, description: '' },
      ],
      params: [
        {
          name: 'max_epoch',
          caption: 'Maximum number of epochs for training',
          multiple: false,
          defaults: [10],
          type: 'int',
        }, {
          name: 'optimizer',
          caption: 'Select optimizer',
          multiple: false,
          options: ['SGD', 'Adam'],
          defaults: ['SGD'],
          type: 'string',
        }, {
          name: 'lr',
          caption: 'Learning rate',
          multiple: false,
          defaults: [0.0010000000474974513],
          type: 'float',
        }, {
          name: 'finetune',
          caption: 'Finetune of feature extractor',
          multiple: false,
          defaults: [true],
          type: 'boolean',
        }, {
          name: 'finetune_lr',
          caption: 'Finetune learning rate',
          multiple: false,
          conditions: { finetune: { value: true } },
          defaults: [0.0010000000474974513],
          type: 'float',
        }, {
          name: 'momentum',
          caption: 'Momentum factor',
          multiple: false,
          conditions: { optimizer: { values: ['SGD'] } },
          defaults: [0],
          type: 'float',
        }, {
          name: 'nesterov',
          caption: 'Enables Nesterov momentum',
          multiple: false,
          conditions: { optimizer: { values: ['SGD'] } },
          defaults: [false],
          type: 'boolean',
        }, {
          name: 'betas',
          caption: 'Betas coefficients',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          options: [],
          defaults: ['(0.9, 0.999)'],
          type: 'string',
        }, {
          name: 'eps',
          caption: 'Epsilon coefficients',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          defaults: [9.99999993922529e-9],
          type: 'float',
        }, {
          name: 'amsgrad',
          caption: 'Whether to use the AMSGrad variant',
          multiple: false,
          conditions: { optimizer: { values: ['Adam'] } },
          defaults: [false],
          type: 'boolean',
        }, {
          name: 'scheduler_type',
          caption: 'Scheduler Type',
          multiple: false,
          options: ['ReduceLROnPlateau', 'StepLR'],
          defaults: ['ReduceLROnPlateau'],
          type: 'string',
        }, {
          name: 'warmup_period',
          caption: 'A warmup period for scheduler',
          multiple: false,
          defaults: [0],
          type: 'int',
        }, {
          name: 'patience',
          caption: 'Patience period',
          multiple: false,
          defaults: [10],
          type: 'int',
        }, {
          name: 'step_size',
          caption: 'Step size',
          multiple: false,
          conditions: { scheduler_type: { values: ['StepLR'] } },
          defaults: [20],
          type: 'int',
        }, {
          name: 'gamma',
          caption: 'Gamma coefficient',
          multiple: false,
          conditions: { scheduler_type: { values: ['StepLR'] } },
          defaults: [0.5],
          type: 'float',
        },
      ],
    },
    {
      id: 'create_prediction_model',
      name: 'Create Prediction Model',
      className: 'CreatePredictionModel',
      moduleName: 'studio9.pipelines.operators.models',
      packageName: 's9-operators',
      category: 'SAVER',
      inputs: [
        { name: 'model', type: baseModelType, description: '', covariate: true },
        { name: 'transformation', type: transformationType, description: '', covariate: true, optional: true },
      ],
      outputs: [
        { type: predictionModelType, description: '' },
      ],
      params: [],
    },
    {
      id: 'calculate_dc_map_score',
      name: 'Calculate DC_MAP Score',
      className: 'CalculateDCMapScore',
      moduleName: 'studio9.pipelines.operators.metric',
      packageName: 's9-operators',
      category: 'METRIC',
      inputs: [
        { name: 'album', type: albumType, description: '', covariate: true },
        { name: 'predictions', type: odResultType, description: '', covariate: true },
      ],
      outputs: [
      ],
      params: [],
    },
    {
      id: 'calculate_praf_matrix',
      name: 'Calculate PRAF Matrix',
      className: 'CalculatePRAFMatrix',
      moduleName: 'studio9.pipelines.operators.metric',
      packageName: 's9-operators',
      category: 'METRIC',
      inputs: [
        { name: 'album', type: albumType, description: '', covariate: true },
        { name: 'predictions', type: odResultType, description: '', covariate: true },
      ],
      outputs: [
      ],
      params: [],
    },
    {
      id: 'save_model',
      name: 'Save Model',
      className: 'SaveModel',
      moduleName: 'studio9.pipelines.operators.models',
      packageName: 's9-operators',
      category: 'SAVER',
      inputs: [
        { name: 'model', type: baseModelType, description: '', covariate: true },
      ],
      outputs: [
      ],
      params: [
        { name: 'name', caption: 'Model name', type: 'string', options: [] },
      ],
    },
    {
      id: 'predict_2step',
      name: 'Predict 2 Step',
      className: 'Predict2Step',
      moduleName: 'studio9.pipelines.operators.predict2step',
      packageName: 's9-operators',
      category: 'PREDICTOR',
      inputs: [
        { name: 'album', type: albumType, description: 'Album', covariate: true },
        { name: 'detection_model', type: baseModelType, description: 'Detection model', covariate: true },
        { name: 'classification_model', type: baseModelType, description: 'Classification model', covariate: true },
      ],
      outputs: [
        { type: odResultType, description: '' },
      ],
      params: [],
    },
    {
      id: 'predict_1step',
      name: 'Predict 1 Step',
      className: 'Predict1Step',
      moduleName: 'studio9.pipelines.operators.predict1step',
      packageName: 's9-operators',
      category: 'PREDICTOR',
      inputs: [
        { name: 'album', type: albumType, description: 'Album', covariate: true },
        { name: 'model', type: baseModelType, description: 'Trained model', covariate: true },
      ],
      outputs: [
        { type: odResultType, description: '' },
      ],
      params: [],
    },
    {
      id: 'add_prediction_to_album',
      name: 'Add prediction to album',
      className: 'AddODResultToAlbum',
      moduleName: 'studio9.pipelines.operators.?',
      packageName: 's9-operators',
      category: 'ALBUM_TRANSFORMER',
      inputs: [
        { name: 'prediction', type: odResultType, description: 'Prediction Result', covariate: true },
        { name: 'album', type: albumType, description: 'Album', covariate: true },
      ],
      outputs: [
        { type: albumType, description: 'Album with predictions' },
      ],
      params: [],
    },
    {
      id: 'select_model',
      name: 'Select Model',
      className: 'SelectModel',
      moduleName: 'studio9.pipelines.operators.models',
      packageName: 's9-operators',
      category: 'SELECTOR',
      inputs: [
      ],
      outputs: [
        { type: baseModelType, description: 'Model' },
      ],
      params: [
        { name: 'model', caption: 'CV Model', multiple: false, assetType: IAsset.Type.CV_MODEL, type: 'assetReference' },
      ],
    },
    //{
    //  id: '',
    //  name: '',
    //  className: '',
    //  moduleName: 'studio9.pipelines.operators.?',
    //  packageName: 's9-operators',
    //  category: '',
    //  inputs: [
    //  ],
    //  outputs: [
    //  ],
    //  params: [],
    //},
  ],
  options: {
    indices: ['id'],
  },
};
