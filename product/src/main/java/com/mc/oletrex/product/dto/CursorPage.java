package com.mc.oletrex.product.dto;

import java.util.List;

/**
 * One page of a cursor-paginated list.
 *
 * @param items      the items on this page
 * @param nextCursor pass as ?cursor= to get the next page; null on the last page
 * @param hasMore    true if there is a next page
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
}
