import { BinaryDataset } from '../../datasets/dataset.interfaces';
import {
  IFixtureBinaryDataset,
  IFixtureData,
} from '../fixture.interface';

export const binaryDatasets: IFixtureData<IFixtureBinaryDataset> = {
  data: [
    {
      id: 'dataset1',
      ownerId: 'ownerId1',
      name: 'Dataset 1',
      created: '1990-04-25 00:00:00',
      updated: '1990-04-25 00:00:00',
      status: BinaryDataset.Status.IDLE,
      __files: [
        {
          filename: 'dirname-123/file1.jpg',
          filepath: 'datasets/dataset1/files/download?a=1',
          filesize: 123,
          modified: '1990-04-25 00:00:00',
        },
        {
          filename: 'dirname (2)/file2.jpg',
          filepath: 'datasets/dataset1/files/download?a=2',
          filesize: 123,
          modified: '1990-04-25 00:00:00',
        },
        {
          filename: 'dirname (2)/file3.jpg',
          filepath: 'datasets/dataset1/files/download?a=3',
          filesize: 123,
          modified: '1990-04-25 00:00:00',
        },
        {
          filename: 'file, name\'",',
          filepath: 'datasets/dataset1/files/download?a=4',
          filesize: 123,
          modified: '1990-04-25 00:00:00',
        },
      ],
    },
    {
      id: 'dataset2',
      ownerId: 'ownerId1',
      name: 'Dataset 2',
      created: '1990-04-25 00:00:00',
      updated: '1990-04-25 00:00:00',
      status: BinaryDataset.Status.IDLE,
      __files: [],
    },
  ],
  options: {
    indices: ['id', 'ownerId'],
  },
};
