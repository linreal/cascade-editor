# Mobile weekly sync — 5 August

## Attendees

- Ana
- Daria
- Minh
- Sam

## Decisions

1. Ship the new editor to internal users on Wednesday.
2. Keep analytics names platform-neutral.
3. Treat an interrupted migration as recoverable.

## Discussion

The iOS host is ready for the new serializer. Android still needs the database
migration benchmark. Desktop can remain behind the `editor_v2` flag for one
additional week.

> A release is blocked if migration recovery requires clearing user data.

## Actions

- [ ] Ana: publish the rollout dashboard
- [x] Daria: verify the iOS archive
- [ ] Minh: run the 100k-document migration benchmark
- [ ] Sam: update the [support playbook](../support/editor-v2.md)

