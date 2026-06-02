# Contributing to alf.io in catarinas

> [!IMPORTANT]
> This guide is specific to contributions in the `catarinas` organization of the `alf.io` project.

## GitHub Flow Overview

We use a simplified GitHub Flow: feature branches are created from `main`, changes are submitted via Pull Requests, and merged after passing CI checks and code review.

## Branch Naming Style
You must name the branch according to the issue topic you are doing. For example, if your issue is a documentation change (docs), you should name your branch `docs/your-branch-name`. If your issue is a bug fix, you should name your branch `fix/your-branch-name`. If your issue is a new feature (implementation), you should name your branch `feature/your-branch-name`.

> [!TIP]
> Use the issue name as the baseline for your branch name to maintain clarity and consistency.

## Workflow Explanation

1. Clone the repository locally.
2. Create a new branch from `main`:
   ```bash
   git switch main
   git pull origin main
   git switch -c feature/my-feature
   ```
3. Make your changes following and commit them with clear messages using [conventional commits](https://gist.github.com/qoomon/5dfcdf8eec66a051ecd85625518cfd13) style:
   ```bash
   git add .
   git commit -m "feat: add new feature X"
   ```
4. Push your branch and open a Pull Request against `main` using organization templates.
6. Ensure all CI checks pass.
7. Ask @christianmz565 or @gustadev121 for a review.
8. Address any feedback and update the PR until it is approved.
9. Once approved and CI checks are green, the PR can be merged.

## Common Situations Guide

1. **Branch lifecycle:** Keep branches short-lived (1-3 days max) and sync regularly with `main` to avoid merge conflicts.
    ```bash
    git switch feature/my-feature
    git fetch origin
    git rebase origin/main
    ```

2. **Conflict resolution:** When conflicts occur:

    ```bash
    git switch main
    git pull origin main
    git switch feature/my-feature
    git rebase main
    # Resolve conflicts
    git add .
    git rebase --continue
    git push origin feature/my-feature --force
    ```

3. **Hotfix:** For urgent production fixes:

    ```bash
    git switch main
    git switch -c hotfix/fix-urgent-issue
    # Make fixes
    git push origin hotfix/fix-urgent-issue
    ```

## Best Practices

- Always branch from an up-to-date `main`.
- Keep commits small and focused.
- Write tests for new functionality.
- Ensure `gradlew build` passes before opening a PR.
- All CI pipelines must be green before merging.
- Do not reduce the overall test coverage.
- Use descriptive PR titles and link the related issue.
- Respect the project's code style and conventions.