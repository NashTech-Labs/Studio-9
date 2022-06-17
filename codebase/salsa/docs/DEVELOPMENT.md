# DEVELOPMENT



### Coding

- please, create branches for every task/feature
- please, create demo view for newly added features 
(or place it in te proper page/view if it's already added. many of them are not yet)



### Specifications

- data models: are changed, not so dramatically from v1.0. more detailed 
description will be lated, please, check /src/app/fixtures for now.
- api endpoints: see "data models" point. detailed description is temporarily [here](http://sigent.com/insilico.api/#/).



### Temp
- types/interfaces: many objects are not frozen yet.
so no worries about strict typing for core data entities for now.



### Typings
- to include external dependency (3rd party library from 'node_modules'), example:
```
typings install dt~codemirror --save --global
```



### Conventions
(draft)

A. 
arranging import statements. to preserve order and group into blocks
(so we go from global to local stuff):
```
// general libs
import lodash from 'lodash'

// framework libs
import { Component } from 'angular...'

// app libs
import { AppService } from '../services/...'
(app libs are usually also ordered: constants/services/directives/components/...)
```


