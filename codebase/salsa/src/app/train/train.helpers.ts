import { IAlbum } from '../albums/album.interface';
import { AppSelectOptionsType } from '../core-ui/components/app-select.component';

import { CVModelType } from './cv-model.interface';
import { CommonTrainParams } from './cvtl-train.interfaces';

export function getResultLabelModeByModelType(modelType: CVModelType): IAlbum.LabelMode {
  if (modelType.labelMode) {
    return modelType.labelMode || null;
  } else if (modelType.type === CVModelType.Type.TL) {
    switch (modelType.tlType) {
      case CVModelType.TLType.CLASSIFICATION:
        return IAlbum.LabelMode.CLASSIFICATION;
      case CVModelType.TLType.LOCALIZATION:
        return IAlbum.LabelMode.LOCALIZATION;
    }
  }

  return null;
}

export function isTLModelType(modelType: CVModelType): modelType is CVModelType.TL {
  return modelType.type === CVModelType.Type.TL;
}

export function isCustomModelType(modelType: CVModelType): modelType is CVModelType.Custom {
  return modelType.type === CVModelType.Type.CUSTOM;
}

function generateId(item: CommonTrainParams.InputSize) {
  return item.width + '*' + item.height;
}

export function prepareInputSizeOptions(
  inputSizes: CommonTrainParams.InputSize[],
): AppSelectOptionsType {
  return inputSizes.map(item => {
    return {
      id: generateId(item),
      text: item.width + '*' + item.height,
    };
  });
}

export function prepareInputSizeValueMap(
  inputSizes: CommonTrainParams.InputSize[],
): { [optionId: string]: CommonTrainParams.InputSize } {
  return inputSizes.reduce((acc, item) => {
    const id = generateId(item);
    acc[id] = item;
    return acc;
  }, {});
}
