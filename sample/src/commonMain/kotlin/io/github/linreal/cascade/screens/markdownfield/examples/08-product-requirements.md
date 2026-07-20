# Scheduled report delivery

## Problem

Operations teams export the same dashboard every Monday and email it manually.
They need a reliable scheduled delivery without giving every recipient access
to the workspace.

## Goals

- Schedule weekly or monthly delivery
- Deliver CSV and PDF attachments
- Show the next delivery time in the workspace timezone
- Keep an audit record for 90 days

## User flow

1. Open a saved report.
2. Choose **Schedule delivery**.
3. Configure recipients and cadence.
   - Validate every recipient before saving.
   - Warn when the report contains restricted fields.
4. Review the next three delivery dates.
5. Save and send a confirmation email.

## Non-goals

- Arbitrary cron expressions
- Delivery to chat integrations
- Editing the report query from the scheduling dialog

## Success measure

At least 60% of teams that repeatedly export a report adopt a schedule within
four weeks of availability.

