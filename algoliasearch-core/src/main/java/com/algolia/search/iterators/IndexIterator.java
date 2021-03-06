package com.algolia.search.iterators;

import com.algolia.search.SearchIndex;
import com.algolia.search.models.RequestOptions;
import com.algolia.search.models.indexing.BrowseIndexQuery;
import com.algolia.search.models.indexing.BrowseIndexResponse;
import java.util.Iterator;
import java.util.Objects;
import javax.annotation.Nonnull;

@SuppressWarnings("WeakerAccess")
public class IndexIterator<E> implements Iterator<E> {

  private final SearchIndex<E> index;
  private final RequestOptions requestOptions;
  private final BrowseIndexQuery query;
  private String currentCursor;
  private Iterator<E> currentIterator = null;
  private boolean isFirstRequest = true;

  public IndexIterator(@Nonnull SearchIndex<E> index) {
    this(index, new BrowseIndexQuery().setHitsPerPage(1000));
  }

  public IndexIterator(@Nonnull SearchIndex<E> index, @Nonnull BrowseIndexQuery query) {
    this(index, query, null);
  }

  public IndexIterator(
      @Nonnull SearchIndex<E> index,
      @Nonnull BrowseIndexQuery query,
      RequestOptions requestOptions) {

    Objects.requireNonNull(index, "Index is required");
    Objects.requireNonNull(query, "Query is required");

    this.index = index;
    this.query = query;
    this.requestOptions = requestOptions;
  }

  @Override
  public boolean hasNext() {

    if (isFirstRequest) {
      browseAndSetInnerState();
      isFirstRequest = false;
    }

    if (currentCursor != null && !currentIterator.hasNext()) {
      browseAndSetInnerState();
    }

    return currentIterator != null && currentIterator.hasNext();
  }

  @Override
  public E next() {
    if (currentIterator == null || !currentIterator.hasNext()) {
      browseAndSetInnerState();
      isFirstRequest = false;
    }
    return currentIterator.next();
  }

  private void browseAndSetInnerState() {
    BrowseIndexResponse<E> result = doQuery(query, requestOptions);
    currentIterator = result.getHits().iterator();
    currentCursor = result.getCursor();
    query.setCursor(result.getCursor());
  }

  BrowseIndexResponse<E> doQuery(BrowseIndexQuery query, RequestOptions requestOptions) {
    return index.browseFrom(query, requestOptions);
  }
}
