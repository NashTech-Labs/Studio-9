import * as _ from 'lodash';

import {
  IAlbum,
  IAlbumAugmentParams,
  IAlbumClone,
  IAlbumCreate,
  IAlbumImportLabelsFromS3,
  IAlbumTagsSummary,
  IPictureAugmentation,
} from '../../albums/album.interface';
import { IAsset, IListRequest, IObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { MiscUtils } from '../../utils/misc';
import { IFixturePicture, IFixturePictureSearchParams, IFixtureServiceRoute } from '../fixture.interface';

export const albumsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'albums$',
    method: 'GET',
    handler: function(this, params: IListRequest, user) {
      return this.serveAssetListRequest(this.collections.albums, IAsset.Type.ALBUM, params, user);
    },
  },
  {
    url: 'albums$',
    method: 'POST',
    handler: function(this, params: IAlbumCreate, user) {
      const albums = this.collections.albums;

      const album: IAlbum = Object.assign({}, params, {
        id: Date.now().toString(),
        ownerId: user.id,
        updated: new Date().toISOString(),
        created: new Date().toISOString(),
        status: IAlbum.Status.ACTIVE,
        type: IAlbum.Type.SOURCE,
      });

      albums.insertOne(album);

      if (params.copyPicturesFrom && params.copyPicturesFrom.length) {
        const pictures = this.collections.pictures.chain()
          .find({ albumId: { '$in': params.copyPicturesFrom} })
          .where(picture => !params.copyOnlyLabelledPictures || !!picture.tags.length)
          .data();

        pictures.forEach(picture => {
          const copiedPicture = Object.assign({}, picture, {
            albumId: album.id,
            id: `${album.id}_${picture.id}`,
          });
          delete copiedPicture['$loki'];

          this.collections.pictures.insert(copiedPicture);
        });
      }

      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.getAssetWithACL(
        this.collections.albums,
        IAsset.Type.ALBUM,
        params[1],
        user,
        params['shared_resource_id'],
      );
    },
  },
  {
    url: 'albums/([\\w\\-]+)/save$',
    method: 'POST',
    handler: function (this, params: { name: string }, user): IAlbum {
      const id = params[1];
      const albums = this.collections.albums;
      const album = albums.findOne({ id: id, ownerId: user.id });

      const duplicateAlbum = albums.find({'id': {'$ne': id}, name: params.name, ownerId: user.id});

      if (duplicateAlbum.length) {
        throw new Error(`Album with ${params.name} already exists`);
      }

      if (!album) {
        throw new Error('Album Not found');
      }

      $.extend(true, album, params, {updated: Date.now(), inLibrary: true});
      albums.update(album);

      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/copy',
    method: 'POST',
    handler: function (this, params: IAlbumClone, user): IAlbum {
      const albumId = params[1];
      const albums = this.collections.albums;
      const oldAlbum = this.getAssetWithACL(
        albums,
        IAsset.Type.ALBUM,
        albumId,
        user,
        params['shared_resource_id'],
      );
      if (!oldAlbum) {
        throw new Error('Album Not found');
      }

      const album: IAlbum = Object.assign({}, oldAlbum, {
        id: Date.now().toString(),
        name: params.name || oldAlbum.name,
        ownerId: user.id,
        updated: new Date().toISOString(),
        created: new Date().toISOString(),
        status: IAlbum.Status.ACTIVE,
        type: IAlbum.Type.SOURCE,
      });
      delete album['$loki'];

      albums.insertOne(album);

      const pictures = this.collections.pictures.chain()
        .find({ albumId: oldAlbum.id })
        .where(picture => {
          return params.pictureIds
            ? params.pictureIds.includes(picture.id)
            : (!params.copyOnlyLabelledPictures || !!picture.tags.length);
        })
        .data();

      pictures.forEach(picture => {
        const copiedPicture = Object.assign({}, picture, {
          albumId: album.id,
          id: `${album.id}_${picture.id}`,
          tags: oldAlbum.type === IAlbum.Type.SOURCE ? picture.tags : picture.predictedTags,
          caption: oldAlbum.type === IAlbum.Type.SOURCE ? picture.caption : picture.predictedCaption,
          predictedTags: null,
          predictedCaption: null,
        });
        delete copiedPicture['$loki'];

        this.collections.pictures.insert(copiedPicture);
      });

      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/augment',
    method: 'POST',
    handler: function (this, params: IAlbumAugmentParams, user): IAlbum {
      const albumId = params[1];
      const albums = this.collections.albums;
      const processes = this.collections.processes;
      const oldAlbum = albums.findOne({ id: albumId, ownerId: user.id });
      if (!oldAlbum) {
        throw new Error('Album Not found');
      }

      const album: IAlbum = Object.assign({}, oldAlbum, {
        id: Date.now().toString(),
        name: params.outputName || oldAlbum.name,
        ownerId: user.id,
        updated: new Date().toISOString(),
        created: new Date().toISOString(),
        status: IAlbum.Status.SAVING,
        type: IAlbum.Type.SOURCE,
      });
      delete album['$loki'];

      albums.insertOne(album);

      const pictures = this.collections.pictures.chain()
        .find({ albumId: oldAlbum.id })
        .data();

      pictures.forEach(picture => {
        const copiedPicture: IFixturePicture = Object.assign({}, picture, {
          albumId: album.id,
          originalPictureId: picture.id,
          id: `${album.id}_${picture.id}`,
          tags: oldAlbum.type === IAlbum.Type.SOURCE ? picture.tags : picture.predictedTags,
          caption: oldAlbum.type === IAlbum.Type.SOURCE ? picture.caption : picture.predictedCaption,
          predictedTags: null,
          predictedCaption: null,
          augmentationsApplied: [
            <IPictureAugmentation.RotationAugmentation> {
              augmentationType: IAlbum.AugmentationType.ROTATION,
              angle: MiscUtils.random(1, 350),
              resize: true,
            },
          ],
        });
        delete copiedPicture['$loki'];

        this.collections.pictures.insert(copiedPicture);
      });

      processes.insertOne({
        id: 'a_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.ALBUM,
        targetId: album.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });

      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/tags$',
    method: 'GET',
    handler: function (this, params, user): IAlbumTagsSummary {
      const albumId = params[1];
      const albums = this.collections.albums;
      const album = this.getAssetWithACL(
        albums,
        IAsset.Type.ALBUM,
        albumId,
        user,
        params['shared_resource_id'],
      );
      if (!album) {
        throw new Error('Album Not found');
      }
      const pictures = this.collections.pictures;
      const albumPictures = pictures.findObjects({albumId: albumId });
      const tags: string[] = _.flatten(albumPictures.map(picture => {
        if (picture.tags.length) {
          return _.uniq(picture.tags.map(tag => tag.label));
        }
        return [''];
      }));
      const uniqueTags = _.uniq(tags).sort();
      return uniqueTags.map(tag => {
        return {
          label: tag,
          count: tags.reduce((acc, item) => {
            if (item === tag) {
              acc++;
            }
            return acc;
          }, 0),
        };
      });
    },
  },
  {
    url: 'albums/([\\w\\-]+)/uploadPicture$',
    method: 'POST',
    handler: function(this, params, user) {
      const albumId = params[1];
      const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const processes = this.collections.processes;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      let picture = pictures.findOne({albumId: albumId });
      if (!picture) {
        picture = pictures.findOne({ albumId: 'cifar100' });
      }
      const newPicture = Object.assign({}, picture, {
        id: picture.id + '_' + Date.now().toString(),
        albumId: albumId,
        caption: '',
        tags: [],
      });
      delete newPicture['$loki'];
      pictures.insertOne(newPicture);
      album.status = IAlbum.Status.UPLOADING;
      albums.update(album);
      //UPLOADING PROCESS
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.ALBUM,
        targetId: album.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });
      return newPicture;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/importPicturesFromS3$',
    method: 'POST',
    handler: function(this, params, user) {
      const albumId = params[1];
      const pictures = this.collections.pictures;
      const processes = this.collections.processes;
      const albums = this.collections.albums;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      let picture = pictures.findOne({albumId: albumId });
      if (!picture) {
        picture = pictures.findOne({ albumId: 'cifar100' });
      }
      const newPicture = Object.assign({}, picture, {
        id: picture.id + '_' + Date.now().toString(),
        albumId: albumId,
        caption: '',
        tags: [],
      });
      delete newPicture['$loki'];
      pictures.insertOne(newPicture);
      album.status = IAlbum.Status.UPLOADING;
      albums.update(album);
      //UPLOADING PROCESS
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.ALBUM,
        targetId: album.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });
      return newPicture;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/importLabelsFromS3',
    method: 'POST',
    handler: function (this, params: IAlbumImportLabelsFromS3, user): IAlbum {
      const albumId = params[1];
      const albums = this.collections.albums;
      const processes = this.collections.processes;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      album.status = IAlbum.Status.UPLOADING; // here it comes
      albums.update(album);
      //UPLOADING PROCESS
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.ALBUM,
        targetId: album.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });
      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/uploadLabels$',
    method: 'POST',
    handler: function (this, params, user): IAlbum {
      const albumId = params[1];
      //const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const processes = this.collections.processes;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      album.status = IAlbum.Status.UPLOADING; // here it comes
      albums.update(album);
      //UPLOADING PROCESS
      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.ALBUM,
        targetId: album.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });
      return album;
    },
  },
  {
    url: 'albums/importVideoFromS3$',
    method: 'POST',
    handler: function(this) {
      throw new Error('Not implemented');
    },
  },
  {
    url: 'albums/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const albums = this.collections.albums;
      const album = albums.findOne({ id: id, ownerId: user.id });

      const duplicateAlbum = albums.find({'id': {'$ne': id}, name: params.name, ownerId: user.id});

      if (duplicateAlbum.length) {
        throw new Error(`Album with ${params.name} already exists`);
      }

      if (!album) {
        throw new Error('Album Not found');
      }
      $.extend(true, album, params, { updated: Date.now() });
      albums.update(album);

      return album;
    },
  },
  {
    url: 'albums/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const albumId = params[1];
      const albums = this.collections.albums;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      albums.remove(album);
      return { id: albumId };
    },
  },
  {
    url: 'albums/([\\w\\-]+)/pictures$',
    method: 'GET',
    handler: function(this, params: IFixturePictureSearchParams, user) {
      const albumId = params[1];
      const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const album = this.getAssetWithACL(
        albums,
        IAsset.Type.ALBUM,
        albumId,
        user,
        params['shared_resource_id'],
      );
      if (!album) {
        throw new Error('Album Not found');
      }
      const resultset = pictures.chain().find({ albumId: album.id }).sort((p1: IFixturePicture, p2: IFixturePicture) => {
        const l1 = (album.type !== IAlbum.Type.SOURCE) ? p1.predictedTags.length : p1.tags.length;
        const l2 = (album.type !== IAlbum.Type.SOURCE) ? p2.predictedTags.length : p2.tags.length;
        const cloneId1 = p1.cloneId || 0;
        const cloneId2 = p2.cloneId || 0;

        if (cloneId1 !== cloneId2) {
          return cloneId1 < cloneId2 ? -1 : 1;
        } else if (l1 && !l2) {
          return 1;
        } else if (!l1 && l2) {
          return -1;
        } else if (p1.filename === p2.filename) {
          return 0;
        } else {
          return p1.filename > p2.filename ? -1 : 1;
        }
      });
      //unlabeled === ''
      //no labels choosen no propery labels
      const labels = params.hasOwnProperty('labels') ? params.labels.split(',') : [];
      const augmentations = params.hasOwnProperty('augmentations') ? params.augmentations.split(',') : [];
      const result = resultset.where((obj) => {
        return (
          labels.length === 0 ||
          (obj.tags.length === 0 && labels.includes('')) ||
          obj.tags.map(_ => _.label).filter(label => labels.includes(label)).length > 0
        ) && (
          augmentations.length === 0 ||
          ((obj.augmentationsApplied || []).length === 0 && augmentations.includes('')) ||
          (obj.augmentationsApplied || []).filter(_ => augmentations.includes(_.augmentationType)).length > 0
        );
      });
      return this.prepareListResponse(
        result,
        Object.assign({}, params, { order: null }),
        'filename',
      );
    },
  },
  {
    url: 'albums/([\\w\\-]+)/pictures/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const albumId = params[1];
      const pictureId = params[2];
      const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const album = this.getAssetWithACL(
        albums,
        IAsset.Type.ALBUM,
        albumId,
        user,
        params['shared_resource_id'],
      );
      if (!album) {
        throw new Error('Album Not found');
      }
      const picture = pictures.findOne({ id: pictureId, albumId: albumId });
      if (!picture) {
        throw new Error('Picture Not found');
      }
      return picture;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/pictures/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const albumId = params[1];
      const pictureId = params[2];
      const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const album = this.getAssetWithACL(
        albums,
        IAsset.Type.ALBUM,
        albumId,
        user,
        params['shared_resource_id'],
      );
      if (!album) {
        throw new Error('Album Not found');
      }
      const picture = pictures.findOne({ id: pictureId, albumId: albumId });
      if (!picture) {
        throw new Error('Picture Not found');
      }
      $.extend(true, picture, params, { updated: Date.now() });
      pictures.update(picture);
      return picture;
    },
  },
  {
    url: 'albums/([\\w\\-]+)/pictures/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const albumId = params[1];
      const pictureId = params[2];
      const pictures = this.collections.pictures;
      const albums = this.collections.albums;
      const album = albums.findOne({ id: albumId, ownerId: user.id });
      if (!album) {
        throw new Error('Album Not found');
      }
      const picture = pictures.findOne({ id: pictureId, albumId: albumId });
      if (!picture) {
        throw new Error('Picture Not found');
      }
      pictures.remove(picture);

      return { id: picture.id };
    },
  },
  {
    url: 'albums/([\\w\\-]+)/export$', // or .../download // ../file
    method: 'GET',
    handler: function (params) {
      const id = params[1];
      const albums = this.collections.albums;
      const album = albums.findOne({ id: id });
      if (!album) {
        throw new Error('Exporting Album Not Found');
      }
      const pictures = this.collections.pictures.findObjects({ albumId: album.id });
      return [['filename', 'label'], ...pictures.map((picture: IFixturePicture) => {
        return picture.tags.map(tag => {
          return [
            `"${String(picture.filename).replace(/"/g, '""')}"`, //filename
            `"${String(tag.label).replace(/"/g, '""')}"`, //label
          ];
        }).join(',');
      })].join('\n');
    },
  },
  {
    url: 'config/cv-data-augmentation-defaults$',
    method: 'GET',
    handler: function(this) {
      return [
        {
          augmentationType: IAlbum.AugmentationType.ROTATION,
          angles: [45, 90, 135, 180, 270],
          resize: true,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.SHEARING,
          angles: [15, 30],
          resize: true,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.NOISING,
          noiseSignalRatios: [0.15, 0.3, 0.45, 0.6, 0.75],
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.ZOOM_IN,
          ratios: [1.2, 1.5, 1.75, 2.0, 2.5],
          resize: true,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.ZOOM_OUT,
          ratios: [0.2, 0.5, 0.33, 0.5, 0.7],
          resize: true,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.OCCLUSION,
          occAreaFractions: [0.05, 0.1, 0.25, 0.5, 0.65],
          mode: 'ZERO',
          isSARAlbum: false,
          targetWindowSize: 32,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.TRANSLATION,
          translateFractions: [0.1, 0.2, 0.3, 0.4],
          mode: 'CONSTANT',
          resize: false,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.SALT_PEPPER,
          knockoutFractions: [0.05, 0.1, 0.2, 0.3],
          pepperProbability: 0.5,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.MIRRORING,
          flipAxes: [0, 1, 2],
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.CROPPING,
          cropAreaFractions: [0.25, 0.36, 0.49, 0.64],
          cropsPerImage: 1,
          resize: false,
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.BLURRING,
          sigmas: [0.5, 1.0, 2.0, 4.0],
          bloatFactor: 1,
        },
        {
          augmentationType: IAlbum.AugmentationType.PHOTO_DISTORT,
          alphaMin: 0.5,
          alphaMax: 1.5,
          deltaMax: 18.0,
          bloatFactor: 1,
        },
      ];
    },
  },
];
