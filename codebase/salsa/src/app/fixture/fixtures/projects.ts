import { IProject } from '../../core/interfaces/project.interface';
import { IFixtureData } from '../fixture.interface';

export const projects: IFixtureData<IProject> = {
  data: [
    {
      id: 'project1',
      name: 'Project Number One',
      ownerId: 'ownerId1',
      created: '2016-01-01 01:01',
      updated: '2016-05-05 05:55',
      folders: [
        {
          id: 'f1',
          path: 'folder1',
        },
        {
          id: 'f2',
          path: 'folder2',
        },
      ],
    },
    {
      id: 'project2',
      name: 'Project Number Two',
      ownerId: 'ownerId1',
      created: '2016-01-01 10:01',
      updated: '2016-05-05 22:00',
      folders: [
        {
          id: 'f1',
          path: 'folder1',
        },
        {
          id: 'f6',
          path: 'folder1/folder6',
        },
        {
          id: 'f7',
          path: 'folder1/folder7',
        },
        {
          id: 'f2',
          path: 'folder1/folder2',
        },
        {
          id: 'f3',
          path: 'folder1/folder2/folder3',
        },
        {
          id: 'f4',
          path: 'folder1/folder2/folder3/folder4',
        },
        {
          id: 'f5',
          path: 'folder1/folder2/folder3/folder4/folder5',
        },
        {
          id: 'f8',
          path: 'folder8',
        },
      ],
    },
    {
      id: 'project3',
      name: 'Project Number Three',
      ownerId: 'ownerId1',
      created: '2016-02-03 01:01',
      updated: '2016-06-05 05:05',
      folders: [],
    },
    {
      id: 'project4',
      name: 'Project Number Four',
      ownerId: 'ownerId1',
      created: '2016-01-22 21:01',
      updated: '2016-07-05 03:05',
      folders: [],
    },
  ],
  options: {
    indices: ['id', 'ownerId'],
  },
};
