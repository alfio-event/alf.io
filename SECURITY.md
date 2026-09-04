# Security Policy

## Supported Versions

We support the latest major release, which is at the moment **2.0-M5**.
In case of direct or transitive vulnerability in that code, we'll release a new version as soon as possible. 
Please follow us on [Twitter](https://twitter.com/alfio_event) for release announcements, and keep your alf.io updated!

| Version | Supported          |
| ------- | ------------------ |
| 2.0-M5  | :white_check_mark: |
| 2.0-M4  | :x: |
| 2.0-M3  | :x:                |
| 2.0-M2  | :x:                |
| 2.0-M1  | :x:                |

## Note about the main branch

We consider the "main" branch to be always a "Work in Progress", and as such it might contain vulnerabilities.
Before each release we'll run an additional scan on [SonarCloud](https://sonarcloud.io/summary/overall?id=alfio-event_alf.io) and fix all the security-releated findings.
That branch should not be deployed in production. If you do that, you're on your own.


## Reporting a Vulnerability

Please report vulnerabilities through [GitHub Security Advisories](https://github.com/alfio-event/alf.io/security/advisories/new).

To help us evaluate reports efficiently, please include enough information to reproduce and validate the issue, including:

- the affected version(s);
- clear reproduction steps or a working proof of concept;
- the prerequisites required to exploit the issue;
- the expected security impact in a realistic alf.io deployment.

Reports consisting only of automated scanner output, AI-generated analysis, or theoretical attack scenarios without a reproducible security impact may be closed without further investigation.

## CVE assignment and severity assessment

A confirmed security vulnerability does **not automatically result in a CVE being requested**.

The alf.io maintainers evaluate each vulnerability individually and may request a CVE when we believe the issue has a significant real-world security impact on alf.io users.

When making this decision, we consider factors such as:

- exploitability;
- required privileges and user interaction;
- affected data or functionality;
- impact on confidentiality, integrity, or availability;
- consequences for a typical production deployment.

Low-impact issues, defense-in-depth improvements, findings requiring unrealistic or highly privileged preconditions, and issues with negligible practical impact will generally be fixed without requesting a CVE.

The severity assigned by a researcher, including a CVSS score, does not by itself determine whether alf.io will request a CVE. The final assessment of the impact on alf.io is made by the project maintainers.

If you have any questions, you can reach out to `security @ alf.io` (remove spaces). We'll reply as soon as possible.

Thank you for sharing responsibly!
