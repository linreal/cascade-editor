# Add offline retry for comment uploads

Comment uploads currently fail permanently when the device loses connectivity
after the user presses Send. Queue the request locally and retry it when the
network becomes available.

## Acceptance criteria

- [ ] A pending comment appears immediately with a `Sending` state
- [ ] Retrying preserves the client-generated comment ID
- [ ] A successful retry changes the state to `Sent`
- [ ] A permanent 4xx response changes the state to `Failed`
- [ ] Tapping Retry does not create a duplicate comment

## Implementation notes

- Store pending work by workspace
  - Encrypt the comment body at rest
  - Delete the queue entry after server acknowledgement
- Use exponential backoff capped at five minutes
- Keep retry scheduling outside the Compose layer

The worker should make this decision:

```
if (response.isSuccessful) markSent(commentId)
else if (response.isRetryable) scheduleRetry(commentId)
else markFailed(commentId)
```

