# Contributing

We do not accept external pull requests at this time. You can use
[GitHub Issues](https://github.com/Perdolique/pole-parkla/issues) to report bugs or
suggest ideas.

## Bug reports

Include:

- the app version, device model, and Android or iOS version;
- the steps needed to reproduce the problem;
- what you expected and what happened;
- relevant screenshots or error details, if available.

Remove personal contact details, private report data, and credentials from public
reports. Use demonstration data when a screenshot or photograph helps explain a bug.

## Feature ideas

Describe the problem you want to solve and how the idea would help. Check existing
issues before opening a new one.

## Repository access

Only invited repository collaborators can open pull requests. Everyone can use
Issues to report bugs or suggest ideas.

The repository owner sets **Settings > General > Features > Pull requests** to
**Collaborators only** and keeps **Issues** enabled with creation allowed for everyone.
Do not use repository-wide interaction limits to implement this policy, because
those limits also restrict Issues.

## Development checks

Build instructions are in the [development guide](docs/development.md),
[worker/README.md](worker/README.md),
and [site/README.md](site/README.md).

GitHub Actions checks Android lint, unit tests, and debug APK builds, Worker lint,
types, tests, and dry-run packaging, plus website types, generated images, build,
and SEO tests. CI does not deploy services or call paid AI providers.
Execution of the iOS test suites is deferred.
