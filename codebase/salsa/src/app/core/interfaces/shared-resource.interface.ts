import { IAsset, IListRequest, TObjectId } from './common.interface';

export interface ISharedResource<T extends IAsset.Type = IAsset.Type> extends IAsset {
  recipientEmail?: string;
  recipientId?: TObjectId;
  assetType: T;
  assetId: TObjectId;
}

export interface ISharedResourceCreate<T extends IAsset.Type = IAsset.Type> {
  assetId: TObjectId;
  assetType: T;
  assetName: string;
  recipientEmail: string;
}

export interface ISharedResourceListRequest extends IListRequest {
  asset_type: IAsset.Type;
  asset_id: string;
}
