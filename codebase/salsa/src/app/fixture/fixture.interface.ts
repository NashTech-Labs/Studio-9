import { HttpClient, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';

import { IAlbum, IPicture, IPicturePredictedTag, IVideo } from '../albums/album.interface';
import { IBackendList, IListRequest, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { IUser } from '../core/interfaces/user.interface';
import { BinaryDataset } from '../datasets/dataset.interfaces';
import { IDataset } from '../tables/dataset.interface';
import { ITable, TTableValue } from '../tables/table.interface';
import { ICVModelSummary } from '../train/cv-model.interface';
import { IModelSummary, IModelTrainSummary, ITabularModel } from '../train/model.interface';
import { UserStatus } from '../users/user.interfaces';

import { FixtureService } from './fixture.service';

export const MAX_MODEL_PROGRESS_ITERATIONS = 100;
export const PROCESS_TICK_INTERVAL = 700;

type DataFunction<T> = (client: HttpClient) => Observable<T[]>;

export interface IFixtureData<T> {
  data: DataFunction<T> | T[];
  options: { indices: string[] };
}

export interface IFixtureUser extends IUser {
  username: string;
  password: string;
  token: string;
  status: UserStatus;
  created: string;
  updated: string;
  _isRealUser?: boolean;
}

export interface IFixtureProcess extends IProcess {
  _expectedDuration?: number;
  _speed?: number;
  _touched?: boolean;
}

export interface IFixturePicture extends IPicture {
  cloneId?: number;
  fixtureTags?: IPicturePredictedTag[];
  fixtureCaption?: string;
}

export interface IFixtureDataset extends IBackendList<IDataset> {
  id: TObjectId;
  csvPath?: string;
  _pending?: Observable<IFixtureDataset>;
}

export interface IFixtureModelEquation {
  type: 'logistic';
  scales: { [k: string]: [number, number] }; // [scale, mean]
  intercept: number;
  categorical: [string, string, number][]; // [name, expected, coeff]
  continuous: [string, number][]; // [name, coeff]
  threshold: number;
  answers: [TTableValue, TTableValue];
}

export interface IFixtureModelTrainSummary extends IModelTrainSummary {
  id: TObjectId;
}

export interface IFixtureTable extends ITable {
  derivedModelSummary?: IModelSummary;
  derivedModelEquation?: IFixtureModelEquation;
  importanceDescription?: { name: string; description: string; }[];
}

export interface IFixtureTabularModel extends ITabularModel {
  fixtureEquation?: IFixtureModelEquation;
}

export interface IFixtureAlbum extends IAlbum {
  fixtureSummary?: ICVModelSummary;
  fixtureVideo?: IVideo;
}

export interface IFixturePictureSearchParams extends IListRequest {
  labels?: string;
  augmentations?: string;
}

export interface IFixtureServiceRoute {
  url: string;
  method: string;
  handler: (
    this: FixtureService,
    params: any,
    user: IFixtureUser,
    next: () => Observable<HttpResponse<any>>,
  ) => any | Observable<any> | HttpResponse<any> | Observable<HttpResponse<any>>;
}

export interface IFixtureBinaryDataset extends BinaryDataset {
  __files: BinaryDataset.File[];
}
