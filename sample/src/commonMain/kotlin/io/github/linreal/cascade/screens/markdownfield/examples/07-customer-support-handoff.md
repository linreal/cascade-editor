# Escalation: workspace members cannot accept invitations

## Customer impact

Acme Field Services has 14 pending invitations. New members see **Invitation
expired** immediately, although the links were created today.

## What support checked

- Workspace is active and below its seat limit
- Invitation records exist and have future expiry timestamps
- Reproduces on Android, iOS, and web
- Creating a second invitation does not help

## Engineering handoff

The API response contains `expiresAt` in local server time without an offset.
Clients parse it as UTC, which makes the invitation appear expired in UTC+10.

> Please preserve the existing invitation tokens while repairing timestamps;
> the customer has already distributed the links.

Contact: [support@acme.example](mailto:support@acme.example)

