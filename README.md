# ghc-github-auth

Companion login methods for the **bundled JetBrains GitHub plugin**, for organizations
whose **OAuth App access restrictions** block the "JetBrains IDE Integration" OAuth app.

## The problem

The GitHub plugin in JetBrains IDEs offers two logins:

- **OAuth** — uses JetBrains' own OAuth app ("JetBrains IDE Integration"). If your
  organization enables [OAuth App access restrictions](https://docs.github.com/en/organizations/managing-oauth-access-to-your-organizations-data/about-oauth-app-access-restrictions)
  and has not approved that app, the resulting token cannot see any private org repo.
  Pull Requests then fail with:

  ```
  Could not resolve to a Repository with the name '<org>/<repo>'
  ```

- **Personal Access Token** — works, but some organizations cap PAT lifetime at
  a few days, which means re-creating a token constantly.

Meanwhile the GitHub CLI (`gh`) and VS Code use OAuth apps that are commonly
allow-listed, so their tokens work fine — the failure is app allow-listing, not
the OAuth protocol.

## What this plugin does

Adds two alternative logins under **Tools → GHC GitHub Auth** that store their token
into the bundled GitHub plugin's normal account store (`GHAccountManager`), so
Pull Requests, Gists and clone integration work as usual:

1. **Log in to GitHub with gh CLI Token** — runs `gh auth token` and reuses your
   locally authenticated GitHub CLI token (OAuth app: *GitHub CLI*, scopes
   `repo gist workflow read:org`, no fixed expiry).
2. **Log in to GitHub with Device Flow...** — runs the
   [GitHub OAuth Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow)
   against an OAuth app **client id you provide** (bring-your-own-app; device flow
   needs no client secret). Use an app your organization has approved, with
   *Device flow* enabled in the app settings.

### Notes on token scope

Both methods request the classic-OAuth scopes `repo gist workflow read:org` — the
same set the plugin's built-in OAuth login asks for. The `repo` scope is
account-wide read/write; classic OAuth has no per-repository granularity. If you
need least-privilege tokens, that requires a GitHub App integration
(tracked upstream as [IJPL-238890](https://youtrack.jetbrains.com/issue/IJPL-238890)).

## Build

```powershell
.\gradlew.bat buildPlugin      # -> build\distributions\ghc-github-auth-<version>.zip
.\gradlew.bat runIde           # smoke-test in a sandbox IDE
```

Targets IntelliJ-platform IDEs 2025.1+ with the GitHub plugin enabled.

## Install

`Settings → Plugins → ⚙ → Install Plugin from Disk...` and pick the zip from
`build\distributions\`.

## License

[MIT](LICENSE)
