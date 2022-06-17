import { UserRole } from '../../users/user.interfaces';

import { IObjectId, TObjectId } from './common.interface';

export interface IUser {
  id: TObjectId;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
}

export type IUsersMap = {
  [key: string]: IUser;
};

export interface IUserStats {
  tablesCount: number;
  flowsCount: number;
  modelsCount: number;
  binaryDatasetsCount: number;
  pipelinesCount: number;
  projectsCount: number;
  cvModelsCount: number;
  cvPredictionsCount: number;
  tabularPredictionsCount: number;
  albumsCount: number;
  experimentsCount: number;
  s9ProjectsCount: number;
}

export interface IEmailConfirmationRequest {
  orgId: string;
  userId: IObjectId;
  activationCode: string;
}
