package com.example.backbase.model;

import java.util.List;

/**
 * Cursor-based pagination response.
 * nextCursor is base64(createdAt_epochMs:id) of the last item on this page.
 * Null nextCursor means no more pages.
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}
