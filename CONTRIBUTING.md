# Contributing to alf.io

Thank you for considering a contribution to alf.io! This document describes how we work together, from picking an issue to getting a pull request merged.
The process applies to everyone, whether or not you use AI tools to write your code.

For setting up your development environment, see [Contributing to alf.io](README.md#contributing-to-alfio) in the README.

## The process

1. **Find something to work on.** Look at the [open issues](https://github.com/alfio-event/alf.io/issues) and see if there are any you think you can contribute to.
   If you have a problem with alf.io, or you'd like to implement a new feature, feel free to open a new issue for it and ask to be the implementer.
2. **Ask to work on it.** Comment on the issue stating that you'd like to contribute to it, and optionally why. If you comment on a non-assigned issue, there's a higher chance that you'll get approval.
3. **Wait for the assignment.** If you are approved, the project maintainers will assign the issue to you.
   If there is no visible progress on an assigned issue for 30 days, the maintainers may unassign it and offer it to someone else.
4. **Agree on a plan before writing code.** Before starting anything, summarize in a comment on the issue what you're going to do to solve the issue or implement the feature. The project maintainers will read it and discuss it with you if necessary.
   We thank you for your time: agreeing on a solution before implementing it is the most effective way to make use of the time you're gifting to the project.
5. **Work on your fork.** When you're happy with the result, open a pull request to start the review process.
6. **Commit as yourself.** All commits MUST come from your user. We don't allow LLM agents to co-own a commit (see [AI-assisted contributions](#ai-assisted-contributions)).
7. **Add tests.** Your pull request must contain at least one integration test if your code touches the backend, and an E2E test if it touches the frontend (see [Tests](#tests)).
8. **Address the automated code review.** We currently use CodeRabbit, plus SonarCloud and CodeQL. Fix the issues they report, or reply explaining why a comment doesn't apply.
9. **Work with us on the review.** We may suggest changes to your code, and we're happy to discuss them with you. We're all here for the same reason: to make alf.io useful for its users and keep it easy to maintain for years to come.

Pull requests that skip steps 1–4 will be closed with a link to this document. Don't worry: you can reopen them once the process has been followed.

### Small fixes

Typos, wording improvements in the documentation and broken links can go straight to a pull request. Every code change, however small, needs an issue.

Translations are managed on [POEditor](README.md#translation-contributors-poeditor), please don't send them as pull requests.

## AI-assisted contributions

You are welcome to use AI tools to write your contribution. However:

- **You are responsible for every line of the pull request.** You must understand the code you submit and be able to explain it during the review. "The AI wrote it" is not an answer to a review comment.
- **No AI co-authors.** Commits must be authored and committed by your own user, not by a bot or agent identity, and must not contain `Co-Authored-By:` trailers naming an AI tool or agent. Many agents add these trailers by default, so please check your commits before pushing.
  Pull requests that don't respect this rule must be rewritten before they can be reviewed.
- **Tell us how you used it.** Add one sentence to the pull request description stating whether and how you used AI tools. There is no penalty for saying yes: it just helps the reviewers.

To remove the trailer from your last commit, run `git commit --amend` and delete the line. To remove all the `Co-Authored-By:` trailers from the commits of your branch (note: this removes human co-authors too):

```shell
git rebase main --exec 'git log -1 --format=%B | grep -vi "^co-authored-by:" | git commit --amend --no-verify -F -'
```

## Tests

### Integration tests (backend)

If your code touches the backend (including the templates in `src/main/resources/alfio/templates`), your pull request must contain at least one integration test that exercises the new behavior.
An integration test boots the Spring context against a real PostgreSQL database, using [TestContainers](https://testcontainers.org). See the existing `*IntegrationTest` classes for examples.

Unit tests are welcome, but they don't replace the integration test.

### E2E tests (frontend)

If your code touches the frontend (`frontend/admin` or `frontend/public`), your pull request must also contain an E2E test. E2E tests live in `src/test/java/alfio/e2e`, and are excluded from the regular build.

Every E2E test class declares the payment methods it needs in `requiredPaymentProxies()`. Declare only the ones your test actually uses, so that it can run on an organization where the other payment providers are not configured.

#### Running E2E tests locally

When run locally, E2E tests use Chrome, so you need to have it installed. You also need:

- a running alf.io instance (e.g. started with `./gradlew bootRun`)
- an admin user, and an organization with the payment providers required by the tests configured.
  Bank Transfer is enough for most tests, Stripe (a free account in test mode, with the "Enable SCA" option turned on) is needed only by the tests that declare `PaymentProxy.STRIPE`

Then set the following environment variables:

| Variable             | Description                                                                 |
|----------------------|-----------------------------------------------------------------------------|
| `E2E_SERVER_URL`     | URL of the alf.io instance, e.g. `http://localhost:8080`                    |
| `E2E_ADMIN_USERNAME` | username of the admin user                                                  |
| `E2E_ADMIN_PASSWORD` | password of the admin user                                                  |
| `E2E_ORGANIZATION`   | (optional) name of the organization, defaults to the first one available    |

#### Proving that you've run them

Run the E2E tests added or modified by your pull request, and save the output:

```shell
./gradlew e2eTest --tests 'alfio.e2e.YourNewE2ETest' -Pverbose --console=plain 2>&1 | tee e2e-test.log
```

Check that the log doesn't contain any credentials, then attach `e2e-test.log` to the pull request description.

If you can't set up a payment provider required by your tests (for example, because Stripe is not available in your country), say so in the pull request, and the maintainers will run those tests for you.

In any case, the maintainers may run the whole E2E suite on BrowserStack before merging your pull request.
