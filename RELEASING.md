# Releasing

To release a new version in Clojars, you need to run 

```bash
./release.sh
```

This command creates a git tag with the project's current version and pushes it
to github. This will trigger a GithubAction that tests and uploads JAR files to
Clojars.

### Credentials

Credentials are configured as github secrets: `CLOJARS_USERNAME` and
`CLOJARS_PASSWD`.
