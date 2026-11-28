# AGENTS.md

alf.io is a ticket reservation system: a Spring Boot backend (Java 25, Gradle) with a PostgreSQL database and two frontends.

Before opening a pull request, read [CONTRIBUTING.md](CONTRIBUTING.md). It covers the issue-first process, the required tests and the pull request description.

## Commits

Commits are authored by the human contributor. Write commit messages without `Co-Authored-By:` trailers or any other AI attribution, even if your default configuration adds them.

## Layout

- `src/main/java/alfio`: backend. `controller` → `manager` (business logic) → `repository` (database access).
- `frontend/public`: public (attendee-facing) frontend, Angular.
- `frontend/admin`: admin frontend, Lit + Shoelace, built with Vite. A single page application below `/admin`.
- `src/main/resources/alfio/templates`: email and PDF templates. A change here counts as a backend change.

## Code style

Code is read far more often than it is written, so prefer the readable form over the compact one, in every language (Java, TypeScript, JavaScript embedded in tests).

- **No ternary operator** (`a ? b : c`): write an `if`/`else` instead, even when the ternary would fit on one line.

## Backend conventions

- **Database access** goes through `ch.digitalfondue.npjt` interfaces: an `@QueryRepository` interface with SQL in `@Query` annotations and parameters bound with `@Bind`. Follow the existing repositories in `alfio.repository`.
- **JSON** uses Jackson 3 (`tools.jackson.*`) or `alfio.util.Json`. Jackson 2 is on the classpath for third-party libraries only; the `forbiddenApis` check fails the build on `com.fasterxml.jackson.*` imports.
- **Lombok** is restricted by `lombok.config`: only `@Getter`, `@Setter`, `@AllArgsConstructor`, `@Data` and `@Delegate` are allowed. Write constructors, builders, loggers and `toString` by hand.
- **License header**: every `.java` and `.sql` file starts with the GPL header from `config/HEADER`, and the build fails without it. Run `./gradlew licenseFormat` to add it to new files.

## Database migrations

Migrations are Flyway scripts in `src/main/resources/alfio/db/PGSQL`.

- **Schema changes** go in a new versioned script. Look at the most recent ones to pick the next version number and follow the naming (e.g. `V206_2.0.0.60__FIX_PURCHASE_CONTEXT_FIELD_DESCRIPTION.sql`). Applied scripts are immutable: change the schema with a new script.
- **Views** are recreated after every migration by the `afterMigrateApplied__NNN_VIEW_*.sql` callbacks. Each script drops and recreates its view, so to change a view, edit its callback script in place.

## Building and testing

The integration tests use TestContainers, so they need a working Docker (or Podman) environment.

- Full build, as CI runs it: `./gradlew build`
- One test class: `./gradlew test --tests 'alfio.manager.SomeIntegrationTest'`
- Integration tests extend `BaseIntegrationTest` and are annotated with `@AlfioIntegrationTest`. Use an existing `*IntegrationTest` as a template.
- E2E tests (`src/test/java/alfio/e2e`) run against a live server and are excluded from `./gradlew build`. See the E2E section of CONTRIBUTING.md for the setup.
  - Run them with `./e2e-test.sh` (e.g. `./e2e-test.sh --tests 'alfio.e2e.SettingsE2ETest'`): it opens a real Chrome window on a virtual display. Don't run `./gradlew e2eTest` directly, and don't make Chrome headless: headless hides failures that happen in CI, and on the desktop a covered Chrome window freezes its animations, so the tests time out at random.
  - Run them as a non-administrator, as CI does (an organization owner), unless the test needs an administrator.
- Local server: `./gradlew bootRun`. It also starts the Vite dev server for `frontend/admin`.

## Frontends

Both frontends use pnpm. Gradle installs Node and pnpm itself, so `./gradlew build` works without a local Node setup.

## Admin frontend design

Before any work in `frontend/admin`, read `docs/admin-style-guide.md`: its
principles, the reusable building blocks, and the rules for forms, choices,
buttons, dialogs, dates, CSV import and routing. Review your result
against it before reporting.

## Translations

Translations (`src/main/resources/alfio/i18n`) are managed on POEditor. When adding a feature, add the new keys to the English file (`public.properties`) and leave the other languages to POEditor.

- One file holds the texts of the public frontend, the admin (keys starting with `admin.`), the e-mails and the server's validation errors (`error.*`).
- The server reads every text with `MessageFormat`: write an apostrophe twice (`''`) and use `{0}`, `{1}` for the values. `FormattersTest` checks every language.
- The admin UI has no hard-coded text: see the Translations section of `docs/admin-style-guide.md`.
