#!/usr/bin/env ruby

def changes
  all = %x[git diff --name-only $TRAVIS_COMMIT_RANGE].split("\n")
  all
    .select { |path| !path.include? "scripts" }
    .select { |path| path[/^nginx\/.*/] }
end

def all_folders
  changes.map { |f| f[/[\w]*/] }.uniq
end

def dockers_to_build
  all_folders
end

def print_env
  puts "TRAVIS_BRANCH: #{ENV['TRAVIS_BRANCH']}"
  puts "TRAVIS_TAG: #{ENV['TRAVIS_TAG']}"
  puts "TRAVIS_PULL_REQUEST: #{ENV['TRAVIS_PULL_REQUEST']}"
  puts "TRAVIS_COMMIT_RANGE #{ENV['TRAVIS_COMMIT_RANGE']}"
end

def export_path
  path = File.dirname(__FILE__)
  File.open("#{path}/export-build-env.sh", 'w') { |file| file.write("export BUILD_NGINX=true") }
end

