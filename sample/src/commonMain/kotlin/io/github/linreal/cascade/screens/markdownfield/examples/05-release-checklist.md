# Android 3.4.0 release checklist

## Before the build

- [x] Version name and code updated
- [x] Release notes reviewed
- [ ] Translation freeze confirmed
- [ ] Database migration tested from the last two production versions
- [ ] Crash-free sessions above 99.8%

---

## Staged rollout

1. Publish to internal testing.
2. Promote to 5% after smoke tests pass.
3. Hold for 24 hours and review crashes, ANRs, and login failures.
4. Promote to 25%, then 50%, then 100%.

## Rollback signals

- Login failure rate increases by more than **0.5 percentage points**
- Crash-free sessions fall below **99.5%**
- Sync queue age exceeds *ten minutes* at p95

> Do not pause solely for an increase in handled network errors during rollout.

