import * as _ from 'lodash';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';

import { TObjectId } from './interfaces/common.interface';
import { IProject, IProjectFolder } from './interfaces/project.interface';

export class WithProjectSelectionHelpers {
  readonly prepareProjectOptions = function(projects: IProject[]): AppSelectOptionData<TObjectId>[] {
    return projects ? projects.map(project => ({
      id: project.id,
      text: project.name,
    })) : [];
  };

  readonly prepareFolderOptions = function(projects: IProject[], selectedProject: TObjectId): AppSelectOptionData<TObjectId>[] {
    const folders: IProjectFolder[] = (projects || [])
      .filter(project => project.id === selectedProject).map(project => project.folders)[0] || [];

    return _.sortBy(folders.map(folder => ({
      id: folder.id,
      text: folder.path,
    })), 'text');
  };
}
