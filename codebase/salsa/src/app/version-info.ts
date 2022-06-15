export const versionInfo: {
  tag: string;
  hash: string;
  semverString?: string;
} = (() => {
  try {
    // tslint:disable-next-line:no-var-requires
    return require('../../git-version.json');
  } catch {
    // In dev the file might not exist:
    return { tag: '2.2.0', hash: 'dev' };
  }
})();
