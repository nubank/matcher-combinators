# Releasing

Anybody with write access to this repository can release a new version and deploy it to Clojars. To do this, first make sure your local master is sync'd with master on github:

```bash
git checkout master
git pull
```

Now run this command:
```
./release.sh
```

The `release.sh` script creates a git tag with the project's current version and pushes it
to github. This will trigger a GithubAction that tests and uploads JAR files to
Clojars.

### Credentials

Credentials are configured as github secrets: `CLOJARS_USERNAME` and
`CLOJARS_PASSWD`.
