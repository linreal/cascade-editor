---
title: Offline-first architecture
tags: [android, sync, architecture]
status: published
---

# Offline-first architecture

The local database is the source of truth for rendering. Network responses are
transactions that update local state; screens never render directly from an
HTTP response.

![Offline-first data flow](../assets/offline-first-flow.png)

## Key rule

> Reads come from local storage. Writes update local storage before scheduling
> synchronization.

