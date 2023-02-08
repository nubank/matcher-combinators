#/bin/bash

project_version="$(lein project-version | tail -n1)"

git tag "$project_version"
git push origin "$project_version"
