# Retry budget notes

The retry budget limits amplification during a partial outage.[^budget] For a
request with attempt number $n$, the delay grows exponentially until it reaches
the configured ceiling.

$$
delay(n) = min(base * 2^n, maximum)
$$

Jitter should be applied after calculating the ceiling so clients do not retry
in lockstep.[^jitter]

[^budget]: A bounded number of retries shared across one logical operation.
[^jitter]: A random adjustment applied to spread retry traffic over time.

