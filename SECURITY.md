# Security Policy

Canvos holds a Canvas access token, which gives access to a student's whole Canvas account, so
security reports are taken seriously.

## Supported versions

Only the latest release gets security fixes.

## Reporting a vulnerability

**Please don't open a public issue.** Report it privately through
[GitHub's private vulnerability reporting](https://github.com/Rafat-Aman/canvos/security/advisories/new).

Include what's affected, how to reproduce it, and the impact you expect. You should get a reply
within 7 days. Once a fix is released, you'll be credited in the release notes unless you'd rather not be.

## What counts

Examples of what to report:

- The token being sent to any host other than the user's Canvas site, or being logged or written in plain text.
- Ways for other apps on the phone to read the token, offline content or session.
- Content from Canvas (pages, HTML, links) that can run code in, or take over, the app's web views.

Problems in Canvas itself should go to [Instructure](https://www.instructure.com/canvas/security).
