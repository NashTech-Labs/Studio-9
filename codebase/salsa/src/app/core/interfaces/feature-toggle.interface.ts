export const enum Feature {
  // to be extended by feature providers
}

export class FeaturesConfig {
  constructor(
    readonly mode: 'whitelist' | 'blacklist',
    readonly features: Feature[],
  ) {}
}
