import * as moment from 'moment';

import { IAsset } from '../../core/interfaces/common.interface';
import { CVTLModelPrimitiveType, IPackage } from '../../develop/package.interfaces';
import { PipelineOperator } from '../../pipelines/pipeline.interfaces';
import {
  IFixtureData,
} from '../fixture.interface';

const primitiveInputString: PipelineOperator.Input = {
  name: 'Primitive string',
  type: 'string',
  covariate: false,
};

const primitiveOutputString: PipelineOperator.Output = {
  description: 'Primitive string',
  type: 'string',
};

export const packages: IFixtureData<IPackage> = {
  data: [
    {
      id: 'package1',
      name: 's9-project-1',
      created: moment().add(-10, 'days').toString(),
      ownerId: 'ownerId1',
      description: 'Description Package 1',
      version: '1.1.0',
      location: '/location/package1',
      s9ProjectId: 's9Project1',
      isPublished: false,
      primitives: [
        {
          name: 'Operator1',
          description: 'Description Operator1',
          moduleName: 'Module Name Operator1',
          className: 'Class Name Operator1',
          operatorType: CVTLModelPrimitiveType.UTLP,
        },
        {
          name: 'Operator2',
          description: 'Description Operator2',
          moduleName: 'Module Name Operator2',
          className: 'Class Name Operator2',
          operatorType: CVTLModelPrimitiveType.CLASSIFIER,
          params: [
            {
              name: 'num_hidden_layers',
              multiple: false,
              defaults: [0],
              type: 'int',
            },
            {
              name: 'leaky_neg_slope',
              multiple: false,
              defaults: [0],
              type: 'float',
            },
            {
              name: 'kernel_size',
              multiple: false,
              defaults: [3],
              type: 'int',
            },
            {
              name: 'bias',
              multiple: false,
              defaults: [false],
              type: 'boolean',
            },
          ],
        },
        {
          name: 'Operator3',
          description: 'Description Operator3',
          moduleName: 'Module Name Operator3',
          className: 'Class Name Operator3',
          operatorType: CVTLModelPrimitiveType.DETECTOR,
          params: [
            {
              name: 'test',
              caption: 'test description parameter',
              multiple: true,
              defaults: [1, 2, 3],
              type: 'int',
            },
          ],
        },
      ],
      pipelineOperators: [{
        id: 'pipelineOperator1',
        name: 'Single in / single out operator',
        className: 'class1',
        moduleName: 'module1',
        packageName: 'package1',
        category: 'OTHER',
        inputs: [
          primitiveInputString,
        ],
        outputs: [
          primitiveOutputString,
        ],
        params: [
          {
            name: 'param1',
            type: 'string',
            options: ['option1', 'option2'],
          },
          {
            name: 'Asset ref param',
            type: 'assetReference',
            assetType: IAsset.Type.CV_MODEL,
            conditions: {
              'param1': {
                values: ['option1'],

              },
            },
          },
          {
            name: 'Asset ref param multi',
            type: 'assetReference',
            multiple: true,
            assetType: IAsset.Type.TABLE,
            conditions: {
              'param1': {
                values: ['option2'],
              },
            },
          },
          {
            name: 'drop-down',
            type: 'string',
            options: ['option1', 'option2'],
          },
        ],
      }],
    },
    {
      id: 'package2',
      name: 's9-project-2',
      created: moment().add(-11, 'days').toString(),
      ownerId: 'ownerId1',
      description: 'Description Package 2',
      version: '1.1.3',
      location: '/location/package2',
      s9ProjectId: 's9Project2',
      isPublished: true,
      primitives: [],
      pipelineOperators: [],
    },
    {
      id: 'package3',
      name: 's9-project-3',
      created: moment().add(-9, 'days').toString(),
      ownerId: 'ownerId1',
      description: 'Description Package 3',
      version: '2.1.0',
      location: '/location/package3',
      s9ProjectId: 's9Project3',
      isPublished: true,
      primitives: [
        {
          name: 'Operator 4',
          description: 'Description Operator 4',
          moduleName: 'Module Name Operator 4',
          className: 'Class Name Operator 4',
          operatorType: CVTLModelPrimitiveType.CLASSIFIER,
        },
        {
          name: 'Operator 5',
          description: 'Description Operator 5',
          moduleName: 'Module Name Operator 5',
          className: 'Class Name Operator 5',
          operatorType: CVTLModelPrimitiveType.CLASSIFIER,
        },
      ],
      pipelineOperators: [],
    },
    {
      id: 'package4',
      name: 's9-project-4',
      created: moment().add(-8, 'days').toString(),
      ownerId: 'ownerId2',
      description: 'Description Package 4',
      version: '1.3.1',
      location: '/location/package4',
      s9ProjectId: 's9Project4',
      isPublished: true,
      primitives: [],
      pipelineOperators: [],
    },
  ],
  options: {
    indices: ['id', 'name', 'ownerId'],
  },
};
