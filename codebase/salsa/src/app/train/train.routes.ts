import { IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { FeatureToggleRoute } from '../core/services/feature-toggle.service';

export const trainRoutes: FeatureToggleRoute[] = [
  {
    path: 'train',
    features: [Feature.TRAIN_MODULE],
    redirectTo: '/desk/experiments',
  },
];

export const trainModuleAssetURLMap = {
  [IAsset.Type.MODEL]: ['/desk', 'library', 'models'],
  [IAsset.Type.CV_MODEL]: ['/desk', 'library', 'cv-models'],
};
