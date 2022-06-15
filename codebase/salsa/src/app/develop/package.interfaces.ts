import { TObjectId } from '../core/interfaces/common.interface';
import { ParameterDefinition } from '../core/interfaces/params.interface';
import { PipelineOperator } from '../pipelines/pipeline.interfaces';

export interface IPackage {
  id: string;
  name: string;
  created: string;
  ownerId?: TObjectId;
  description?: string;
  version?: string;
  location: string;
  s9ProjectId?: string;
  isPublished: boolean;
  pipelineOperators?: PipelineOperator[];
  primitives?: CVTLModelPrimitive[];
}

export enum CVTLModelPrimitiveType {
  UTLP = 'UTLP',
  CLASSIFIER = 'CLASSIFIER',
  DETECTOR = 'DETECTOR',
}

export interface CVTLModelPrimitive {
  name: string;
  description: string;
  moduleName: string;
  className: string;
  operatorType: CVTLModelPrimitiveType;
  params?: ParameterDefinition[];
}

export interface IPackageSearchParams {
  ownerId?: TObjectId;
  search?: string;
  s9ProjectId?: TObjectId;
  page?: number;
  page_size?: number;
  order?: string;
}

export interface IPackageCreate {
  name?: string;
  version: string;
  description?: string;
  analyzePipelineOperators?: boolean;
}

export interface IPackagePublish {
  pipelineOperators: {
    id: string;
    categoryId: string;
  }[];
}
