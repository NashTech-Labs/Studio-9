import { HttpClient } from '@angular/common/http';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';

import { TObjectId } from '../../core/interfaces/common.interface';
import { IFixtureData, IFixturePicture } from '../fixture.interface';

import { mstarTest } from './mstar_test';
import { mstarTrain } from './mstar_train';

export const pictures: IFixtureData<IFixturePicture> = {
  data: (client: HttpClient) => {
    return Observable.forkJoin(
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/base.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/voc0712-pictures.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/voc2007-pictures.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/voc2012-pictures.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/cars_video-pictures.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/prediction_set-pictures.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/CAD_22k.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/mstar_partial_train.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/mstar_partial_test.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/ec19_classification_train.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/ec19_classification_test.json')),
      client.get('https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Localization/xview_l_train.json'),
      client.get('https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Localization/Test2/xview_test.json'),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/xView.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/dota.json')),
      client.get(require('file-loader?name=assets/fixtures/[name].[hash].json!./pictures/mosaic.json')),
      client.get('https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Localization/train_labeled.json'),
      client.get('https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Localization/test_labeled.json'),
      client.get('https://s3.amazonaws.com/dev.studio9.ai/demo-data/Demo_output/demo_output_frames.json'),
    ).map((rawData: any[]) => {
      const [
        basePictures,
        voc0712Pictures,
        voc2007Pictures,
        voc2012Pictures,
        carsPictures,
        predictionSetPictures,
        CAD22kPictures,
        mStarPartialTrainPictures,
        mStarPartialTestPictures,
        ec19classificationTrain,
        ec19classificationTest,
        ec19localizationTrain,
        ec19localizationTestRaw,
        xViewPictures,
        dotaPictures,
        mosaicPictures,
        ec19localizationTrainOld,
        ec19localizationTestOld,
        demoOutputFrames,
      ] = rawData;

      const ec19localizationTest = loadLocalizationPicturesFromJSON(ec19localizationTestRaw, 'ec19l_test', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Localization/Test2');
      const xviewPred2Pictures = [
        '11236.png', '11392.png', '11470.png', '1191.png', '1374.png', '14374.png', '1444.png', '14598.png', '1466.png',
        '14688.png', '14692.png', '15230.png', '16802.png', '17168.png', '17359.png', '17626.png', '17685.png',
        '17909.png', '18331.png', '18491.png', '18570.png', '192.png', '19759.png', '20704.png', '22289.png',
        '22345.png', '22431.png', '22983.png', '2703.png',
      ];

      const data: IFixturePicture[] = basePictures.concat(
        voc0712Pictures,
        voc2007Pictures,
        voc2012Pictures,
        carsPictures,
        predictionSetPictures,
        loadPicturesFromJSON(CAD22kPictures, 'cad_22k', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/CAD_22k'),
        loadPicturesFromJSON(mStarPartialTrainPictures, 'mstar_partial_train', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_NEW_DATASET/Train'),
        loadPicturesFromJSON(mStarPartialTestPictures, 'mstar_partial_test', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_NEW_DATASET/Test'),
        loadPicturesFromJSON(ec19classificationTrain, 'ec19c_train', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Classification/Train'),
        loadPicturesFromJSON(ec19classificationTest, '1568189266412', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Classification/Test', true),
        loadPicturesFromJSON(ec19classificationTest, 'ec19c_test', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/EC19/Classification/Test'),
        ec19localizationTrain,
        ec19localizationTest,
        ec19localizationTest.map((picture: IFixturePicture) => ({...picture, albumId: '1568193021734', predictedTags: picture.fixtureTags || picture.tags.map(tag => ({...tag, confidence: Math.random() * 0.4 + 0.4}))})),
        loadPicturesFromJSON(mStarPartialTrainPictures, 'MSTAR_Train', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_NEW_DATASET/Train'),
        loadPicturesFromJSON(mStarPartialTestPictures, 'MSTAR_Test', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_NEW_DATASET/Test'),
        loadLocalizationPicturesFromJSON(xViewPictures, 'space_net', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/xView/cropped_data/images'),
        loadLocalizationPicturesFromJSON(dotaPictures, 'dota', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/DOTA/images'),
        loadLocalizationPicturesFromJSON(mosaicPictures, 'mosaic', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/mosaic/images'),
        ec19localizationTrainOld.map(_ => ({..._, albumId: 'ec19l_train_old'})),
        ec19localizationTestOld.map(_ => ({..._, albumId: 'ec19l_test_old'})),
        loadLocalizationPicturesFromJSON(demoOutputFrames, 'demo_output_frames', 'https://s3.amazonaws.com/dev.studio9.ai/demo-data/Demo_output/frames'),
        ec19localizationTest.filter(_ => xviewPred2Pictures.includes(_.filename)).map(_ => ({..._, tags: [], albumId: 'xview_pred_2'})),
      );

      pushMStarPictures(data);
      rearrangePicturesForTrain(data, 'cifar10', 'cifar10_test', 'cifar10_nl');
      rearrangePicturesForTrain(data, 'cifar100', 'cifar100_test', 'cifar100_nl');

      return data;
    });
  },
  options: {
    indices: [
      'id',
      'albumId',
    ],
  },
};

// MSTAR_Chips
function pushMStarPictures(data: IFixturePicture[]) {
  [
    [
      'bmp2_tank',
      587,
    ],
    [
      'btr70_transport',
      196,
    ],
    [
      't72_tank',
      582,
    ],
  ].forEach(([label, count]: [string, number]) => {
    for (let i = 1; i <= count; i++) {
      data.push({
        caption: null,
        tags: [
          {
            'area': {
              'width': 128,
              'top': 0,
              'height': 128,
              'left': 0,
            },
            'label': label,
          },
        ],
        fixtureTags: [
          Object.assign({
            'area': {
              'width': 128,
              'top': 0,
              'height': 128,
              'left': 0,
            },
            'label': label,
            'confidence': 0.9 + Math.random() * 0.1,
          }, mstarTest[`${label}_${i}`] || {}),
        ],
        'predictedCaption': null,
        'predictedTags': [],
        'filename': `TEST_images/${label}_${i}.jpg`,
        'filepath': `https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_Chips/TEST_images/${label}_${i}.jpg`,
        'albumId': 'mstar_test',
        'filesize': 3096,
        'id': `mstar_test_${label}_${i}`,
      });
    }
  });

  [
    [
      'bmp2_tank',
      698,
    ],
    [
      'btr70_transport',
      233,
    ],
    [
      't72_tank',
      691,
    ],
  ].forEach(([label, count]: [string, number]) => {
    for (let i = 1; i <= count; i++) {
      data.push({
        caption: null,
        tags: [
          {
            'area': {
              'width': 128,
              'top': 0,
              'height': 128,
              'left': 0,
            },
            'label': label,
          },
        ],
        fixtureTags: [
          Object.assign({
            'area': {
              'width': 128,
              'top': 0,
              'height': 128,
              'left': 0,
            },
            'label': label,
            'confidence': 0.9 + Math.random() * 0.1,
          }, mstarTrain[`${label}_${i}`] || {}),
        ],
        'predictedCaption': null,
        'predictedTags': [],
        'filename': `TRAIN_images/${label}_${i}.jpg`,
        'filepath': `https://s3.amazonaws.com/dev.studio9.ai/demo-data/MSTAR_Chips/TRAIN_images/${label}_${i}.jpg`,
        'albumId': 'mstar_train',
        'filesize': 3096,
        'id': `mstar_train_${label}_${i}`,
      });
    }
  });
}

function rearrangePicturesForTrain(data: IFixturePicture[], baseAlbum: TObjectId, testAlbum: TObjectId, predictAlbum: TObjectId) {
  data.filter(_ => _.albumId === baseAlbum && !!_.tags.length).slice(0, 20).forEach(picture => {
    Object.assign(picture, {
      albumId: predictAlbum,
      fixtureTags: picture.tags,
      fixtureCaption: picture.caption,
      caption: '',
      tags: [],
    });
  });

  const albumPictures = data.filter(_ => _.albumId === baseAlbum);
  albumPictures.slice(0, Math.floor(albumPictures.length / 2)).forEach(picture => {
    Object.assign(picture, {
      albumId: testAlbum,
    });

    /*
      const newPicture = {
        ...picture,
        id: `t_${picture.id}`,
        albumId: testAlbum,
      };

      pictures.data.push(newPicture);
    */
  });
}

function loadPicturesFromJSON(json: any[][], albumId: string, basePath: string, predictedAlbum: boolean = false): IFixturePicture[] {
  return json.map((row, idx) => {
    const [filesize, filename, label, filepath, predictedLabel, confidence] = row;
    const fixtureTags = [predictedLabel].filter(_ => _).map(_ => {
      return {
        label: _,
        confidence: confidence,
      };
    });
    const predictedTags = predictedAlbum ? fixtureTags : [];
    return {
      caption: null,
      tags: [label].filter(_ => _).map(_ => {
        return {
          label: _,
        };
      }),
      fixtureTags: fixtureTags,
      predictedCaption: null,
      predictedTags: predictedTags,
      filename: filename,
      filepath: `${basePath}/${filepath || filename}`,
      albumId,
      filesize,
      id: `${albumId}_${idx}`,
    };
  });
}

function loadLocalizationPicturesFromJSON(json: any[][], albumId: string, basePath: string): IFixturePicture[] {
  const pictures = json.reduce<{[f: string]: IFixturePicture}>((dict, row, idx) => {
    const [
      filesize, filename,
      label, xMin, yMin, xMax, yMax,
      filepath,
      confidence,
    ] = row;

    const tags = [label].filter(_ => _ && !confidence).map(_ => {
      return {
        label: _,
        area: {
          top: yMin,
          left: xMin,
          height: yMax - yMin,
          width: xMax - xMin,
        },
      };
    });
    const fixtureTags = [label].filter(_ => _ && confidence).map(_ => {
      return {
        label: _,
        area: {
          top: yMin,
          left: xMin,
          height: yMax - yMin,
          width: xMax - xMin,
        },
        confidence: confidence,
      };
    });

    if (!dict[filename]) {
      dict[filename] = {
        tags: tags,
        fixtureTags: fixtureTags,
        predictedTags: [],
        filename: filename,
        filepath: `${basePath}/${filepath || filename}`,
        albumId,
        filesize,
        id: `${albumId}_${idx}`,
      };
    } else {
      dict[filename].tags = dict[filename].tags.concat(tags);
      dict[filename].fixtureTags = dict[filename].fixtureTags.concat(fixtureTags);
    }

    return dict;
  }, {});

  return Object.keys(pictures).map(_ => {
    const picture = pictures[_];
    if (!picture.fixtureTags.length) {
      delete picture.fixtureTags;
    }
    return picture;
  });
}
