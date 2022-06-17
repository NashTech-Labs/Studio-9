// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular/cli'],
    plugins: [
      require('karma-jasmine'),
      // require('karma-chrome-launcher'),
      require('karma-phantomjs-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage-istanbul-reporter'),
      require('karma-junit-reporter'),
      require('@angular/cli/plugins/karma')
    ],
    client:{
      clearContext: false, // leave Jasmine Spec Runner output visible in browser
      captureConsole: false
    },
    reporters: ['progress', 'junit'],
    coverageIstanbulReporter: {
      reports: ['html', 'cobertura', 'text-summary'],
      fixWebpackSourcePaths: true,
      thresholds: {
        statements: 64.0
      }
    },
    junitReporter: {
      outputDir: 'junitTests',
      useBrowserName: false,
      outputFile: 'junitTests.xml'
    },
    angularCli: {
      environment: 'dev'
    },
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    browsers: ['PhantomJS'],
    singleRun: true,
    browserDisconnectTimeout: 60000,
    browserNoActivityTimeout: 100000,
    retryLimit: 3
  });
};
