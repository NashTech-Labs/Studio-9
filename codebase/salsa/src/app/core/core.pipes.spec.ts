import { users as fixtureUsers } from '../fixture/fixtures/users';

import { CombineProcessesPipe } from './components/process-indicator.component';
import { AppUserNamePipe } from './core.pipes';
import { IAsset } from './interfaces/common.interface';
import { IProcess } from './interfaces/process.interface';
import { IUser } from './interfaces/user.interface';

describe('AppUserNamePipe', () => {
  let pipe: AppUserNamePipe;

  beforeEach(() => {
    pipe = new AppUserNamePipe();
  });

  it('should transform non empty user', () => {
    const user: IUser = fixtureUsers.data[0];
    expect(pipe.transform(user)).toEqual(`${user.firstName} ${user.lastName}`);
  });

  it('should transform w/o user', () => {
    expect(pipe.transform(null)).toEqual(`Unknown`);
  });

  it('should transform w/o user + loading', () => {
    expect(pipe.transform(null, true)).toEqual(`...`);
  });

  it('should transform non empty user + loading', () => {
    const user: IUser = fixtureUsers.data[0];
    expect(pipe.transform(user, true)).toEqual(`...`);
  });
});

describe('CombineProcessesPipe', () => {
  let pipe: CombineProcessesPipe;

  beforeEach(() => {
    pipe = new CombineProcessesPipe();
  });


  it('should transform zero processes', () => {
    const processes: IProcess[] = [];
    expect(pipe.transform(processes)).toBeNull();
  });

  it('should transform one completed process', () => {
    const processes: IProcess[] = [
      {
        id: 'foo',
        ownerId: 'owner',
        targetId: 'bar',
        started: '2018-12-01T00:01:00',
        status: IProcess.Status.COMPLETED,
        target: IAsset.Type.TABLE,
        progress: 1,
        created: '2018-12-01T00:00:00',
        jobType: IProcess.JobType.TABULAR_PREDICT,
      },
    ];
    expect(pipe.transform(processes)).toEqual({
      id: 'foo',
      ownerId: 'owner',
      targetId: 'bar',
      started: '2018-12-01T00:01:00',
      status: IProcess.Status.COMPLETED,
      target: IAsset.Type.TABLE,
      progress: 1,
      created: '2018-12-01T00:00:00',
      jobType: IProcess.JobType.TABULAR_PREDICT,
    });
  });

  it('should transform one non-completed process', () => {
    const processes: IProcess[] = [
      {
        id: 'foo',
        ownerId: 'owner',
        targetId: 'bar',
        started: '2018-12-01T00:01:00',
        status: IProcess.Status.RUNNING,
        target: IAsset.Type.TABLE,
        progress: 1,
        created: '2018-12-01T00:00:00',
        jobType: IProcess.JobType.TABULAR_PREDICT,
      },
    ];
    expect(pipe.transform(processes)).toEqual({
      id: 'foo',
      ownerId: 'owner',
      targetId: 'bar',
      started: '2018-12-01T00:01:00',
      status: IProcess.Status.RUNNING,
      target: IAsset.Type.TABLE,
      progress: 1,
      created: '2018-12-01T00:00:00',
      jobType: IProcess.JobType.TABULAR_PREDICT,
    });
  });

  it('should transform two processes', () => {
    const processes: IProcess[] = [
      {
        id: 'foo',
        ownerId: 'owner',
        targetId: 'bar',
        started: '2018-12-01T00:01:00',
        status: IProcess.Status.COMPLETED,
        target: IAsset.Type.TABLE,
        progress: 1,
        created: '2018-12-01T00:00:00',
        jobType: IProcess.JobType.TABULAR_PREDICT,
      },
      {
        id: 'foo1',
        ownerId: 'owner',
        targetId: 'bar',
        started: '2018-12-01T01:01:00',
        status: IProcess.Status.RUNNING,
        target: IAsset.Type.TABLE,
        progress: 0.4,
        created: '2018-12-01T01:00:00',
        jobType: IProcess.JobType.TABULAR_PREDICT,
      },
    ];
    expect(pipe.transform(processes)).toEqual({
      id: 'foo1',
      ownerId: 'owner',
      targetId: 'bar',
      started: '2018-12-01T01:01:00',
      status: IProcess.Status.RUNNING,
      target: IAsset.Type.TABLE,
      progress: 0.7,
      created: '2018-12-01T00:00:00',
      jobType: IProcess.JobType.TABULAR_PREDICT,
    });
  });
});
