#!/usr/bin/env node

// This script runs operations *synchronously* which is normally not the best
// approach, but it keeps things simple.

const { gitDescribeSync } = require('git-describe');
const { writeFileSync } = require('fs');

const gitInfo = gitDescribeSync();
const versionInfoJson = JSON.stringify(gitInfo, null, 2);

writeFileSync('git-version.json', versionInfoJson);
