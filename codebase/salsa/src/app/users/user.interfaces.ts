import { IUser } from '../core/interfaces/user.interface';

export enum UserRole {
  USER = 'USER',
  ADMIN = 'ADMIN',
}

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  DEACTIVATED = 'DEACTIVATED',
}

export interface IUMUser extends IUser {
  username: string;
  status: UserStatus;
  created: string;
  updated: string;
}

export interface IUserCreateRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: UserRole;
}

export interface IUserUpdateRequest {
  username?: string;
  email?: string;
  password?: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
}

export interface IUserSearchParams {
  page?: number;
  page_size?: number;
  order?: string;
  firstName?: string;
  lastName?: string;
}
