# Token refresh design

The client refreshes an access token once, then shares the result with every
request waiting on the same expired token.

```kotlin
private val refreshMutex = Mutex()

suspend fun validToken(): Token = refreshMutex.withLock {
    token.takeUnless(Token::isExpired) ?: api.refresh(refreshToken)
}
```

The `kotlin` info string is meaningful to renderers but has no field in
`BlockType.Code`, so the default profile must preserve this fence opaquely.

