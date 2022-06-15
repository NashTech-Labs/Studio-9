import { IAlbum, IPicturePredictedTag } from '../../albums/album.interface';
import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { ICVPrediction, ICVPredictionCreate, ICVPredictionStatus } from '../../play/cv-prediction.interface';
import { ICVModel } from '../../train/cv-model.interface';
import { IFixturePicture, IFixtureServiceRoute } from '../fixture.interface';

export const cvPredictionsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'cv-predictions$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.cvPredictions, IAsset.Type.CV_PREDICTION, params, user);
    },
  },
  {
    url: 'cv-predictions/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const predictions = this.collections.cvPredictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      if (!prediction) { throw new Error('Prediction Not found'); }

      return prediction;
    },
  },
  {
    url: 'cv-predictions/([\\w\\-]+)/save$',
    method: 'POST',
    handler: function(this, params) {
      return params[1];
    },
  },
  {
    url: 'cv-predictions$',
    method: 'POST',
    handler: function(this, params: ICVPredictionCreate, user) {
      const predictions = this.collections.cvPredictions;

      const albums = this.collections.albums;
      const album = albums.findOne({ id: params.input, ownerId: user.id });
      const model: ICVModel = this.collections.cvModels.findOne({ id: params.modelId, ownerId: user.id });

      if (!album) {
        throw new Error('Album Not found');
      }

      if (!model) {
        throw new Error('Model Not found');
      }

      const newAlbum = Object.assign({}, album, {
        id: Date.now().toString(),
        name: params.outputAlbumName,
        type: IAlbum.Type.DERIVED,
        status: IAlbum.Status.ACTIVE,
        labelMode: IAlbum.LabelMode.LOCALIZATION, // @TODO IMPLEMENT LATER
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        video: album.fixtureVideo
          ? Object.assign({}, album.fixtureVideo)
          : undefined,
      });

      delete newAlbum['$loki'];
      albums.insertOne(newAlbum);

      this.collections.pictures.findObjects({ albumId: album.id }).forEach((picture: IFixturePicture) => {
        const newPicture = Object.assign({}, picture, {
          id: picture.id + '_' + Date.now().toString(),
          albumId: newAlbum.id,
          predictedTags: 'fixtureTags' in picture ? picture.fixtureTags.map((tag: IPicturePredictedTag): IPicturePredictedTag => {
            return {
              label: tag.label,
              confidence: tag.confidence || 0.9 + Math.random() * 0.1,
              area: Object.assign({}, tag.area),
            };
          }) : [],
        });
        delete newPicture['$loki'];
        this.collections.pictures.insertOne(newPicture);
      });

      const data: ICVPrediction = Object.assign({}, params, {
        id: Date.now().toString(),
        ownerId: user.id,
        status: ICVPredictionStatus.RUNNING,
        output: newAlbum.id,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
      });

      const processes = this.collections.processes;
      processes.insertOne({
        id: 'p_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.CV_PREDICTION,
        targetId: data.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.CV_MODEL_PREDICT,
        _expectedDuration: 5000,
      });

      /*processes.insertOne({
       id: 'a_' + Date.now().toString(),
       ownerId: user.id,
       target: IAsset.Type.ALBUM,
       targetId: data.output,
       progress: 0,
       status: IProcess.Status.RUNNING,
       created: new Date().toISOString(),
       updated: new Date().toISOString(),
       });*/

      if (params.evaluate) {
        //TODO: data.summary = {};
      }

      return predictions.insertOne(data);
    },
  },
  // TODO: implement POST /predictions/import
  {
    url: 'cv-predictions/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const predictions = this.collections.cvPredictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name'].forEach(prop =>
        params[prop] !== undefined && (prediction[prop] = params[prop]),
      );

      predictions.update(prediction);
      return prediction;
    },
  },
  {
    url: 'cv-predictions/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const predictions = this.collections.cvPredictions;
      const prediction = predictions.findOne({id: id, ownerId: user.id});

      if (!prediction) { throw new Error('Not found'); }

      predictions.remove(prediction);

      return { id: id };
    },
  },

];
