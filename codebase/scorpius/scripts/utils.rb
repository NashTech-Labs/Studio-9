#!/usr/bin/env ruby

def dockers
  %w[ dockers/deployment ]
end

def filter_pattern
  /dockers\/[\w-]*/
end

def changes
  all = %x[git diff --name-only $TRAVIS_COMMIT_RANGE].split("\n")
  all
    .select { |path| !path.include? "README.md" }
    .select { |path| path[/[\w-]*\/.*/] }
end

def all_folders 
  changes
    .select { |f| f[filter_pattern] }
    .map { |f| f[filter_pattern] }.uniq
end

def dockers_to_build  
  all_folders & dockers
end

def print_env
  puts "TRAVIS_BRANCH: #{ENV['TRAVIS_BRANCH']}"
  puts "TRAVIS_TAG: #{ENV['TRAVIS_TAG']}"
  puts "TRAVIS_PULL_REQUEST: #{ENV['TRAVIS_PULL_REQUEST']}"
  puts "TRAVIS_COMMIT_RANGE #{ENV['TRAVIS_COMMIT_RANGE']}"
end
