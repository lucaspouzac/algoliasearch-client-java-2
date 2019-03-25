package com.algolia.search;

import com.algolia.search.exceptions.AlgoliaApiException;
import com.algolia.search.exceptions.AlgoliaRetryException;
import com.algolia.search.exceptions.AlgoliaRuntimeException;
import com.algolia.search.exceptions.LaunderThrowable;
import com.algolia.search.helpers.HmacShaHelper;
import com.algolia.search.helpers.QueryStringHelper;
import com.algolia.search.models.HttpMethod;
import com.algolia.search.models.RequestOptions;
import com.algolia.search.models.apikeys.*;
import com.algolia.search.models.common.CallType;
import com.algolia.search.models.common.Log;
import com.algolia.search.models.common.Logs;
import com.algolia.search.models.indexing.*;
import com.algolia.search.models.indexing.BatchOperation;
import com.algolia.search.models.indexing.BatchRequest;
import com.algolia.search.models.indexing.SearchResult;
import com.algolia.search.models.mcm.*;
import com.algolia.search.models.personalization.GetStrategyResponse;
import com.algolia.search.models.personalization.SetStrategyRequest;
import com.algolia.search.models.personalization.SetStrategyResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

@SuppressWarnings("WeakerAccess")
public final class SearchClient {

  private final HttpTransport transport;
  private final AlgoliaConfig config;

  public SearchClient(@Nonnull String applicationID, @Nonnull String apiKey) {
    this(new SearchConfig(applicationID, apiKey));
  }

  public SearchClient(@Nonnull SearchConfig config) {
    this(config, new AlgoliaHttpRequester(config));
  }

  public SearchClient(@Nonnull SearchConfig config, @Nonnull IHttpRequester httpRequester) {

    Objects.requireNonNull(httpRequester, "An httpRequester is required.");
    Objects.requireNonNull(config, "A configuration is required.");
    Objects.requireNonNull(config.getApplicationID(), "An ApplicationID is required.");
    Objects.requireNonNull(config.getApiKey(), "An API key is required.");

    if (config.getApplicationID().trim().length() == 0) {
      throw new NullPointerException("ApplicationID can't be empty.");
    }

    if (config.getApiKey().trim().length() == 0) {
      throw new NullPointerException("APIKey can't be empty.");
    }

    this.config = config;
    this.transport = new HttpTransport(config, httpRequester);
  }

  /** Close the underlying Http Client */
  public void close() {
    transport.close();
  }

  /**
   * Get the index object initialized (no server call needed for initialization)
   *
   * @param indexName The name of the Algolia index
   * @throws NullPointerException When indexName is null or empty
   */
  public SearchIndex<?> initIndex(@Nonnull String indexName) {

    if (indexName == null || indexName.trim().length() == 0) {
      throw new NullPointerException("The index name is required");
    }

    return new SearchIndex<>(transport, config, indexName, Object.class);
  }

  /**
   * Get the index object initialized (no server call needed for initialization)
   *
   * @param indexName The name of the Algolia index
   * @param klass class of the object in this index
   * @param <T> the type of the objects in this index
   * @throws NullPointerException When indexName is null or empty
   */
  public <T> SearchIndex<T> initIndex(@Nonnull String indexName, @Nonnull Class<T> klass) {

    if (indexName == null || indexName.trim().length() == 0) {
      throw new NullPointerException("The index name is required");
    }

    Objects.requireNonNull(klass, "A class is required.");

    return new SearchIndex<>(transport, config, indexName, klass);
  }

  /**
   * Retrieve one or more objects, potentially from different indices, in a single API call.
   *
   * @param queries The query object
   * @param klass Class of the data to retrieve
   * @param <T> Type of the data to retrieve
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public <T> MultipleGetObjectsResponse<T> multipleGetObjects(
      List<MultipleGetObject> queries, Class<T> klass) throws AlgoliaRuntimeException {
    return multipleGetObjects(queries, klass, null);
  }

  /**
   * Retrieve one or more objects, potentially from different indices, in a single API call.
   *
   * @param queries The query object
   * @param klass Class of the data to retrieve
   * @param requestOptions Options to pass to this request
   * @param <T> Type of the data to retrieve
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public <T> MultipleGetObjectsResponse<T> multipleGetObjects(
      List<MultipleGetObject> queries, Class<T> klass, RequestOptions requestOptions)
      throws AlgoliaRuntimeException {
    return LaunderThrowable.unwrap(multipleGetObjectsAsync(queries, klass, requestOptions));
  }

  /**
   * Retrieve one or more objects, potentially from different indices, in a single API call.
   *
   * @param queries The query object
   * @param klass Class of the data to retrieve
   * @param <T> Type of the data to retrieve
   */
  public <T> CompletableFuture<MultipleGetObjectsResponse<T>> multipleGetObjectsAsync(
      List<MultipleGetObject> queries, Class<T> klass) {
    return multipleGetObjectsAsync(queries, klass, null);
  }

  /**
   * Retrieve one or more objects, potentially from different indices, in a single API call.
   *
   * @param queries The query object
   * @param klass Class of the data to retrieve
   * @param requestOptions Options to pass to this request
   * @param <T> Type of the data to retrieve
   */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<MultipleGetObjectsResponse<T>> multipleGetObjectsAsync(
      List<MultipleGetObject> queries, Class<T> klass, RequestOptions requestOptions) {

    Objects.requireNonNull(queries, "Queries is required");
    Objects.requireNonNull(klass, "Class is required");

    MultipleGetObjectsRequest request = new MultipleGetObjectsRequest(queries);

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/indexes/*/objects",
            CallType.READ,
            request,
            MultipleGetObjectsResponse.class,
            klass,
            requestOptions)
        .thenComposeAsync(
            resp -> {
              CompletableFuture<MultipleGetObjectsResponse<T>> r = new CompletableFuture<>();
              r.complete(resp);
              return r;
            },
            config.getExecutor());
  }

  /**
   * Perform multiple write operations, potentially targeting multiple indices, in a single API
   * call.
   *
   * @param operations The batch operations to process. It could be on multiple indices with
   *     multiple actions
   */
  public <T> MultipleIndexBatchIndexingResponse multipleBatch(
      @Nonnull List<BatchOperation<T>> operations) {
    return LaunderThrowable.unwrap(multipleBatchAsync(operations, null));
  }

  /**
   * Perform multiple write operations, potentially targeting multiple indices, in a single API
   * call.
   *
   * @param operations The batch operations to process. It could be on multiple indices with
   *     multiple action
   * @param requestOptions Options to pass to this request
   */
  public <T> MultipleIndexBatchIndexingResponse multipleBatch(
      @Nonnull List<BatchOperation<T>> operations, RequestOptions requestOptions) {
    return LaunderThrowable.unwrap(multipleBatchAsync(operations, requestOptions));
  }

  /**
   * Perform multiple write operations, potentially targeting multiple indices, in a single API
   * call.
   *
   * @param operations The batch operations to process. It could be on multiple indices with
   *     multiple action
   */
  public <T> CompletableFuture<MultipleIndexBatchIndexingResponse> multipleBatchAsync(
      @Nonnull List<BatchOperation<T>> operations) {
    return multipleBatchAsync(operations, null);
  }

  /**
   * Perform multiple write operations, potentially targeting multiple indices, in a single API
   * call.
   *
   * @param operations The batch operations to process. It could be on multiple indices with
   *     multiple action
   * @param requestOptions Options to pass to this request
   */
  public <T> CompletableFuture<MultipleIndexBatchIndexingResponse> multipleBatchAsync(
      @Nonnull List<BatchOperation<T>> operations, RequestOptions requestOptions) {

    Objects.requireNonNull(operations, "Operations are required");

    BatchRequest request = new BatchRequest<>(operations);

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/indexes/*/batch",
            CallType.WRITE,
            request,
            MultipleIndexBatchIndexingResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setWaitConsumer(this::waitTask);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * This method allows to send multiple search queries, potentially targeting multiple indices, in
   * a single API call.
   *
   * @param request The request
   * @param klass The class of the expected results
   * @param <T> Type of the expected results
   */
  public <T> MultipleQueriesResponse<T> multipleQueries(
      @Nonnull MultipleQueriesRequest request, @Nonnull Class<T> klass) {
    return LaunderThrowable.unwrap(multipleQueriesAsync(request, klass, null));
  }

  /**
   * This method allows to send multiple search queries, potentially targeting multiple indices, in
   * a single API call.
   *
   * @param request The request
   * @param klass The class of the expected results
   * @param requestOptions Options to pass to this request
   * @param <T> Type of the expected results
   */
  public <T> MultipleQueriesResponse<T> multipleQueries(
      @Nonnull MultipleQueriesRequest request,
      @Nonnull Class<T> klass,
      RequestOptions requestOptions) {
    return LaunderThrowable.unwrap(multipleQueriesAsync(request, klass, requestOptions));
  }

  /**
   * This method allows to send multiple search queries, potentially targeting multiple indices, in
   * a single API call.
   *
   * @param request The request
   * @param klass The class of the expected results
   * @param <T> Type of the expected results
   */
  public <T> CompletableFuture<MultipleQueriesResponse<T>> multipleQueriesAsync(
      @Nonnull MultipleQueriesRequest request, @Nonnull Class<T> klass) {
    return multipleQueriesAsync(request, klass, null);
  }

  /**
   * This method allows to send multiple search queries, potentially targeting multiple indices, in
   * a single API call.
   *
   * @param request The request
   * @param klass The class of the expected results
   * @param requestOptions Options to pass to this request
   * @param <T> Type of the expected results
   */
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<MultipleQueriesResponse<T>> multipleQueriesAsync(
      @Nonnull MultipleQueriesRequest request,
      @Nonnull Class<T> klass,
      RequestOptions requestOptions) {

    Objects.requireNonNull(request, "Request is required");
    Objects.requireNonNull(klass, "A Class is required");

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/indexes/*/queries",
            CallType.READ,
            request,
            MultipleQueriesResponse.class,
            klass,
            requestOptions)
        .thenComposeAsync(
            resp -> {
              CompletableFuture<MultipleQueriesResponse<T>> r = new CompletableFuture<>();
              r.complete(resp);
              return r;
            },
            config.getExecutor());
  }

  /**
   * Make a copy of the settings of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   */
  public CompletableFuture<CopyResponse> copySettingsAsync(
      @Nonnull String sourceIndex, @Nonnull String destinationIndex) {
    return copyIndexAsync(
        sourceIndex, destinationIndex, Collections.singletonList(CopyScope.SETTINGS), null);
  }

  /**
   * Make a copy of the settings of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<CopyResponse> copySettingsAsync(
      @Nonnull String sourceIndex,
      @Nonnull String destinationIndex,
      RequestOptions requestOptions) {
    return copyIndexAsync(
        sourceIndex, destinationIndex, Collections.singletonList(CopyScope.SETTINGS), null);
  }

  /**
   * Make a copy of the rules of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   */
  public CompletableFuture<CopyResponse> copyRulesAsync(
      @Nonnull String sourceIndex, @Nonnull String destinationIndex) {
    return copyIndexAsync(
        sourceIndex, destinationIndex, Collections.singletonList(CopyScope.RULES), null);
  }

  /**
   * Make a copy of the rules of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<CopyResponse> copyRulesAsync(
      @Nonnull String sourceIndex,
      @Nonnull String destinationIndex,
      RequestOptions requestOptions) {
    return copyIndexAsync(
        sourceIndex, destinationIndex, Collections.singletonList(CopyScope.RULES), null);
  }

  /**
   * Make a copy of the synonyms of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   */
  public CompletableFuture<CopyResponse> copySynonymsAsync(
      @Nonnull String sourceIndex, @Nonnull String destinationIndex) {
    return copyIndexAsync(
        sourceIndex, destinationIndex, Collections.singletonList(CopyScope.SYNONYMS), null);
  }

  /**
   * Make a copy of the synonyms of an index
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<CopyResponse> copySynonymsAsync(
      @Nonnull String sourceIndex,
      @Nonnull String destinationIndex,
      RequestOptions requestOptions) {
    return copyIndexAsync(
        sourceIndex,
        destinationIndex,
        Collections.singletonList(CopyScope.SYNONYMS),
        requestOptions);
  }

  /**
   * Make a copy of an index, in the given scope.
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   */
  public CompletableFuture<CopyResponse> copyIndexAsync(
      @Nonnull String sourceIndex, @Nonnull String destinationIndex) {
    return copyIndexAsync(sourceIndex, destinationIndex, null, null);
  }

  /**
   * Make a copy of an index, in the given scope.
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   * @param scopes Scope of the copy
   */
  public CompletableFuture<CopyResponse> copyIndexAsync(
      @Nonnull String sourceIndex, @Nonnull String destinationIndex, List<String> scopes) {
    return copyIndexAsync(sourceIndex, destinationIndex, scopes, null);
  }

  /**
   * Make a copy of an index in the given scope.
   *
   * @param sourceIndex The source index to copy from
   * @param destinationIndex the destination index
   * @param scopes Scope of the copy
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<CopyResponse> copyIndexAsync(
      @Nonnull String sourceIndex,
      @Nonnull String destinationIndex,
      List<String> scopes,
      RequestOptions requestOptions) {

    Objects.requireNonNull(sourceIndex, "The source index is required.");
    Objects.requireNonNull(destinationIndex, "The destination index is required.");

    if (sourceIndex.trim().length() == 0) {
      throw new AlgoliaRuntimeException("destination index must not be empty.");
    }

    if (destinationIndex.trim().length() == 0) {
      throw new AlgoliaRuntimeException("destination index must not be empty.");
    }

    CopyToRequest request =
        new CopyToRequest()
            .setDestination(destinationIndex)
            .setScope(scopes)
            .setOperation(MoveType.COPY);

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/indexes/" + QueryStringHelper.urlEncodeUTF8(sourceIndex) + "/operation",
            CallType.WRITE,
            request,
            CopyResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setIndexName(sourceIndex);
              resp.setWaitConsumer(this::waitTask);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * List all existing indexes
   *
   * @return A List of the indices and their metadata
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public List<IndicesResponse> listIndices() throws AlgoliaRuntimeException {
    return listIndices(null);
  }

  /**
   * List all existing indexes
   *
   * @param requestOptions Options to pass to this request
   * @return A List of the indices and their metadata
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public List<IndicesResponse> listIndices(RequestOptions requestOptions)
      throws AlgoliaRuntimeException {
    return LaunderThrowable.unwrap(listIndicesAsync(requestOptions));
  }

  /**
   * List asynchronously all existing indexes
   *
   * @return A List of the indices and their metadata
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<List<IndicesResponse>> listIndicesAsync() {
    return listIndicesAsync(null);
  }

  /**
   * List asynchronously all existing indexes
   *
   * @param requestOptions Options to pass to this request
   * @return A List of the indices and their metadata
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<List<IndicesResponse>> listIndicesAsync(RequestOptions requestOptions) {
    return transport
        .executeRequestAsync(
            HttpMethod.GET, "/1/indexes", CallType.READ, ListIndicesResponse.class, requestOptions)
        .thenApplyAsync(ListIndicesResponse::getIndices, config.getExecutor());
  }

  /**
   * List all existing user keys with their associated ACLs
   *
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public List<ApiKey> listApiKeys() throws AlgoliaRuntimeException {
    return listApiKeys(null);
  }

  /**
   * List all existing user keys with their associated ACLs
   *
   * @param requestOptions Options to pass to this request
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public List<ApiKey> listApiKeys(RequestOptions requestOptions) throws AlgoliaRuntimeException {
    return LaunderThrowable.unwrap(listApiKeysAsync(requestOptions));
  }

  /**
   * List asynchronously all existing user keys with their associated ACLs
   *
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<List<ApiKey>> listApiKeysAsync() {
    return listApiKeysAsync(null);
  }

  /**
   * List asynchronously all existing user keys with their associated ACLs
   *
   * @param requestOptions Options to pass to this request
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<List<ApiKey>> listApiKeysAsync(RequestOptions requestOptions) {
    return transport
        .executeRequestAsync(
            HttpMethod.GET, "/1/keys", CallType.READ, null, ApiKeys.class, requestOptions)
        .thenApplyAsync(ApiKeys::getKeys, config.getExecutor());
  }

  /**
   * Get the permissions of an API Key.
   *
   * @param apiKey The API key to retrieve
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public ApiKey getApiKey(@Nonnull String apiKey) throws AlgoliaRuntimeException {
    return LaunderThrowable.unwrap(getApiKeyAsync(apiKey, null));
  }

  /**
   * Get the permissions of an API Key.
   *
   * @param apiKey The API key to retrieve
   * @param requestOptions Options to pass to this request
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public ApiKey getApiKey(@Nonnull String apiKey, RequestOptions requestOptions)
      throws AlgoliaRuntimeException {
    return LaunderThrowable.unwrap(getApiKeyAsync(apiKey, requestOptions));
  }

  /**
   * Get the permissions of an API Key.
   *
   * @param apiKey The API key to retrieve
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<ApiKey> getApiKeyAsync(@Nonnull String apiKey) {
    return getApiKeyAsync(apiKey, null);
  }

  /**
   * Get the permissions of an API Key.
   *
   * @param apiKey The API key to retrieve
   * @param requestOptions Options to pass to this request
   * @throws AlgoliaRetryException When the retry has failed on all hosts
   * @throws AlgoliaApiException When the API sends an http error code
   * @throws AlgoliaRuntimeException When an error occurred during the serialization
   */
  public CompletableFuture<ApiKey> getApiKeyAsync(
      @Nonnull String apiKey, RequestOptions requestOptions) {

    Objects.requireNonNull(apiKey, "An API key is required.");

    if (apiKey.trim().length() == 0) {
      throw new AlgoliaRuntimeException("objectID must not be empty.");
    }

    return transport.executeRequestAsync(
        HttpMethod.GET, "/1/keys/" + apiKey, CallType.READ, null, ApiKey.class, requestOptions);
  }

  /**
   * Add a new API Key with specific permissions/restrictions
   *
   * @param acl The api with the restrictions/permissions to add
   */
  public CompletableFuture<AddApiKeyResponse> addApiKeyAsync(@Nonnull ApiKey acl) {
    return addApiKeyAsync(acl, null);
  }

  /**
   * Add a new API Key with specific permissions/restrictions
   *
   * @param acl The api with the restrictions/permissions to add
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<AddApiKeyResponse> addApiKeyAsync(
      @Nonnull ApiKey acl, RequestOptions requestOptions) {
    Objects.requireNonNull(acl, "An API key is required.");

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/keys",
            CallType.WRITE,
            acl,
            AddApiKeyResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setGetApiKeyFunction(this::getApiKey);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * Delete an existing API Key
   *
   * @param apiKey The API Key to delete
   */
  public CompletableFuture<DeleteApiKeyResponse> deleteApiKeyAsync(@Nonnull String apiKey) {
    return deleteApiKeyAsync(apiKey, null);
  }

  /**
   * Delete an existing API Key
   *
   * @param apiKey The API Key to delete
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<DeleteApiKeyResponse> deleteApiKeyAsync(
      @Nonnull String apiKey, RequestOptions requestOptions) {
    Objects.requireNonNull(apiKey, "An API key is required.");

    if (apiKey.trim().length() == 0) {
      throw new AlgoliaRuntimeException("API key must not be empty.");
    }

    return transport
        .executeRequestAsync(
            HttpMethod.DELETE,
            "/1/keys/" + apiKey,
            CallType.WRITE,
            DeleteApiKeyResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setKey(apiKey);
              resp.setGetApiKeyFunction(this::getApiKey);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * Update the permissions of an existing API Key.
   *
   * @param request The API key to update
   */
  public CompletableFuture<UpdateApiKeyResponse> updateApiKeyAsync(@Nonnull ApiKey request) {
    return updateApiKeyAsync(request, null);
  }

  /**
   * Update the permissions of an existing API Key.
   *
   * @param request The API key to update
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<UpdateApiKeyResponse> updateApiKeyAsync(
      @Nonnull ApiKey request, RequestOptions requestOptions) {
    Objects.requireNonNull(request, "An API key is required.");

    if (request == null || request.getValue().trim().length() == 0) {
      throw new AlgoliaRuntimeException("objectID must not be empty.");
    }

    return transport
        .executeRequestAsync(
            HttpMethod.PUT,
            "/1/keys/" + request.getValue(),
            CallType.WRITE,
            request,
            UpdateApiKeyResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setPendingKey(request);
              resp.setGetApiKeyFunction(this::getApiKey);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * Restore the given API Key
   *
   * @param apiKey The given API Key
   */
  public CompletableFuture<RestoreApiKeyResponse> restoreApiKeyAsync(@Nonnull String apiKey) {
    return restoreApiKeyAsync(apiKey, null);
  }

  /**
   * Restore the given API Key
   *
   * @param apiKey The given API Key
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<RestoreApiKeyResponse> restoreApiKeyAsync(
      @Nonnull String apiKey, RequestOptions requestOptions) {

    Objects.requireNonNull(apiKey, "An API Key is required.");

    if (apiKey.trim().length() == 0) {
      throw new AlgoliaRuntimeException("API Key must not be empty.");
    }

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/keys/" + apiKey + "/restore",
            CallType.WRITE,
            RestoreApiKeyResponse.class,
            requestOptions)
        .thenApplyAsync(
            resp -> {
              resp.setKey(apiKey);
              resp.setGetApiKeyFunction(this::getApiKey);
              return resp;
            },
            config.getExecutor());
  }

  /**
   * Generate a virtual API Key without any call to the server.
   *
   * @param parentAPIKey API key to generate from.
   * @param restriction Restriction to add the key
   * @throws Exception if an error occurs during the encoding
   */
  public String generateSecuredAPIKey(
      @Nonnull String parentAPIKey, SecuredApiKeyRestriction restriction) throws Exception {
    return HmacShaHelper.generateSecuredApiKey(parentAPIKey, restriction);
  }

  /**
   * Get the logs of the latest search and indexing operations You can retrieve the logs of your
   * last 1,000 API calls. It is designed for immediate, real-time debugging.
   */
  public CompletableFuture<List<Log>> getLogsAsync() {
    return getLogsAsync(0, 10, null);
  }

  /**
   * Get the logs of the latest search and indexing operations You can retrieve the logs of your
   * last 1,000 API calls. It is designed for immediate, real-time debugging.
   *
   * @param offset Specify the first entry to retrieve (0-based, 0 is the most recent log entry).
   * @param length Specify the maximum number of entries to retrieve starting at the offset. Maximum
   *     allowed value: 1,000.
   */
  public CompletableFuture<List<Log>> getLogsAsync(int offset, int length) {
    return getLogsAsync(offset, length, null);
  }

  /**
   * Get the logs of the latest search and indexing operations You can retrieve the logs of your
   * last 1,000 API calls. It is designed for immediate, real-time debugging.
   *
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<List<Log>> getLogsAsync(RequestOptions requestOptions) {
    return getLogsAsync(0, 10, requestOptions);
  }

  /**
   * Get the logs of the latest search and indexing operations You can retrieve the logs of your
   * last 1,000 API calls. It is designed for immediate, real-time debugging.
   *
   * @param offset Specify the first entry to retrieve (0-based, 0 is the most recent log entry).
   * @param length Specify the maximum number of entries to retrieve starting at the offset. Maximum
   *     allowed value: 1,000.
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<List<Log>> getLogsAsync(
      int offset, int length, RequestOptions requestOptions) {

    if (requestOptions == null) {
      requestOptions = new RequestOptions();
    }

    requestOptions.addExtraQueryParameters("offset", Integer.toString(offset));
    requestOptions.addExtraQueryParameters("length", Integer.toString(length));

    return transport
        .executeRequestAsync(
            HttpMethod.GET, "/1/logs", CallType.READ, null, Logs.class, requestOptions)
        .thenApplyAsync(Logs::getLogs, config.getExecutor());
  }

  /** List the clusters available in a multi-clusters setup for a single appID */
  public CompletableFuture<ListClustersResponse> listClustersAsync() {
    return listClustersAsync(null);
  }

  /**
   * List the clusters available in a multi-clusters setup for a single appID
   *
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<ListClustersResponse> listClustersAsync(RequestOptions requestOptions) {
    return transport.executeRequestAsync(
        HttpMethod.GET, "/1/clusters", CallType.READ, ListClustersResponse.class, requestOptions);
  }

  /**
   * Search for userIDs The data returned will usually be a few seconds behind real-time, because
   * userID usage may take up to a few seconds propagate to the different cluster
   *
   * @param query The query to search for userIDs
   */
  public CompletableFuture<SearchResult<UserId>> searchUserIDsAsync(
      @Nonnull SearchUserIdsRequest query) {
    return searchUserIDsAsync(query, null);
  }

  /**
   * Search for userIDs The data returned will usually be a few seconds behind real-time, because
   * userID usage may take up to a few seconds propagate to the different cluster
   *
   * @param query The query to search for userIDs
   * @param requestOptions Options to pass to this request
   */
  @SuppressWarnings("unchecked")
  public CompletableFuture<SearchResult<UserId>> searchUserIDsAsync(
      @Nonnull SearchUserIdsRequest query, RequestOptions requestOptions) {

    Objects.requireNonNull(query, "A query is required");

    return transport
        .executeRequestAsync(
            HttpMethod.POST,
            "/1/clusters/mapping/search",
            CallType.READ,
            query,
            SearchResult.class,
            UserId.class,
            requestOptions)
        .thenComposeAsync(
            resp -> {
              CompletableFuture<SearchResult<UserId>> r = new CompletableFuture<>();
              r.complete(resp);
              return r;
            },
            config.getExecutor());
  }

  /** List the userIDs assigned to a multi-clusters appID. */
  public CompletableFuture<ListUserIdsResponse> listUserIDsAsync() {
    return listUserIDsAsync(0, 10, null);
  }

  /**
   * List the userIDs assigned to a multi-clusters appID.
   *
   * @param page The page number to request
   * @param hitsPerPage Number of hits per page
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<ListUserIdsResponse> listUserIDsAsync(
      int page, int hitsPerPage, RequestOptions requestOptions) {

    if (requestOptions == null) {
      requestOptions = new RequestOptions();
    }

    requestOptions.addExtraQueryParameters("page", String.valueOf(page));
    requestOptions.addExtraQueryParameters("hitsPerPage", String.valueOf(hitsPerPage));

    return listUserIDsAsync(requestOptions);
  }

  /**
   * List the userIDs assigned to a multi-clusters appID.
   *
   * @param requestOptions Options to pass to this request
   */
  CompletableFuture<ListUserIdsResponse> listUserIDsAsync(RequestOptions requestOptions) {
    return transport.executeRequestAsync(
        HttpMethod.GET,
        "/1/clusters/mapping",
        CallType.READ,
        ListUserIdsResponse.class,
        requestOptions);
  }

  /**
   * Returns the userID data stored in the mapping.
   *
   * @param userID The userID in the mapping
   */
  public UserId getUserID(@Nonnull String userID) {
    return LaunderThrowable.unwrap(getUserIDAsync(userID));
  }

  /**
   * Returns the userID data stored in the mapping.
   *
   * @param userID The userID in the mapping
   */
  public CompletableFuture<UserId> getUserIDAsync(@Nonnull String userID) {
    Objects.requireNonNull(userID, "The userID is required.");
    return getUserIDAsync(userID, null);
  }

  /**
   * Returns the userID data stored in the mapping.
   *
   * @param userID The userID in the mapping
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<UserId> getUserIDAsync(
      @Nonnull String userID, RequestOptions requestOptions) {
    Objects.requireNonNull(userID, "The userID is required.");

    if (userID.trim().length() == 0) {
      throw new AlgoliaRuntimeException("userID must not be empty.");
    }

    return transport.executeRequestAsync(
        HttpMethod.GET,
        "/1/clusters/mapping/" + QueryStringHelper.urlEncodeUTF8(userID),
        CallType.READ,
        UserId.class,
        requestOptions);
  }

  /**
   * Get the top 10 userIDs with the highest number of records per cluster. The data returned will
   * usually be a few seconds behind real-time, because userID usage may take up to a few seconds to
   * propagate to the different clusters.
   */
  public CompletableFuture<TopUserIdResponse> getTopUserIDAsync() {
    return getTopUserIDAsync(null);
  }

  /**
   * Get the top 10 userIDs with the highest number of records per cluster. The data returned will
   * usually be a few seconds behind real-time, because userID usage may take up to a few seconds to
   * propagate to the different clusters.
   *
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<TopUserIdResponse> getTopUserIDAsync(RequestOptions requestOptions) {
    return transport.executeRequestAsync(
        HttpMethod.GET,
        "/1/clusters/mapping/top",
        CallType.READ,
        TopUserIdResponse.class,
        requestOptions);
  }

  public RemoveUserIdResponse removeUserID(@Nonnull String userId) {
    return LaunderThrowable.unwrap(removeUserIDAsync(userId, null));
  }

  /**
   * Remove a userID and its associated data from the multi-clusters.
   *
   * @param userId userID
   */
  public CompletableFuture<RemoveUserIdResponse> removeUserIDAsync(@Nonnull String userId) {
    return removeUserIDAsync(userId, null);
  }

  /**
   * Remove a userID and its associated data from the multi-clusters.
   *
   * @param userId userID
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<RemoveUserIdResponse> removeUserIDAsync(
      @Nonnull String userId, RequestOptions requestOptions) {
    Objects.requireNonNull(userId, "userId key is required.");

    if (userId.trim().length() == 0) {
      throw new AlgoliaRuntimeException("userId must not be empty.");
    }

    if (requestOptions == null) {
      requestOptions = new RequestOptions();
    }

    requestOptions.addExtraHeader("X-Algolia-USER-ID", userId);

    return transport.executeRequestAsync(
        HttpMethod.DELETE,
        "/1/clusters/mapping",
        CallType.WRITE,
        RemoveUserIdResponse.class,
        requestOptions);
  }

  /**
   * Assign or Move a userID to a cluster. The time it takes to migrate (move) a user is
   * proportional to the amount of data linked to the userID.
   *
   * @param userId The userID
   * @param clusterName The name of the cluster
   */
  public CompletableFuture<AssignUserIdResponse> assignUserIDAsync(
      @Nonnull String userId, @Nonnull String clusterName) {
    return assignUserIDAsync(userId, clusterName, null);
  }

  /**
   * Assign or Move a userID to a cluster. The time it takes to migrate (move) a user is
   * proportional to the amount of data linked to the userID.
   *
   * @param userId The userID
   * @param clusterName The name of the cluster
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<AssignUserIdResponse> assignUserIDAsync(
      @Nonnull String userId, @Nonnull String clusterName, RequestOptions requestOptions) {
    Objects.requireNonNull(userId, "userId key is required.");
    Objects.requireNonNull(clusterName, "clusterName key is required.");

    if (userId.trim().length() == 0) {
      throw new AlgoliaRuntimeException("userId must not be empty.");
    }

    if (clusterName.trim().length() == 0) {
      throw new AlgoliaRuntimeException("clusterName must not be empty.");
    }

    if (requestOptions == null) {
      requestOptions = new RequestOptions();
    }

    requestOptions.addExtraHeader("X-Algolia-USER-ID", userId);

    AssignUserIdRequest request = new AssignUserIdRequest(clusterName);

    return transport.executeRequestAsync(
        HttpMethod.POST,
        "/1/clusters/mapping",
        CallType.WRITE,
        request,
        AssignUserIdResponse.class,
        requestOptions);
  }

  /** Returns the personalization strategy of the application */
  public CompletableFuture<GetStrategyResponse> getPersonalizationStrategyAsync() {
    return getPersonalizationStrategyAsync(null);
  }

  /**
   * Returns the personalization strategy of the application
   *
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<GetStrategyResponse> getPersonalizationStrategyAsync(
      RequestOptions requestOptions) {
    return transport.executeRequestAsync(
        HttpMethod.GET,
        "/1/recommendation/personalization/strategy",
        CallType.READ,
        GetStrategyResponse.class,
        requestOptions);
  }

  /**
   * This command configures the personalization strategy
   *
   * @param request The personalization strategy>
   */
  public CompletableFuture<SetStrategyResponse> setPersonalizationStrategyAsync(
      @Nonnull SetStrategyRequest request) {
    return setPersonalizationStrategyAsync(request, null);
  }

  /**
   * This command configures the personalization strategy
   *
   * @param request The personalization strategy>
   * @param requestOptions Options to pass to this request
   */
  public CompletableFuture<SetStrategyResponse> setPersonalizationStrategyAsync(
      @Nonnull SetStrategyRequest request, RequestOptions requestOptions) {
    Objects.requireNonNull(request, "request key is required.");

    return transport.executeRequestAsync(
        HttpMethod.POST,
        "/1/recommendation/personalization/strategy",
        CallType.WRITE,
        request,
        SetStrategyResponse.class,
        requestOptions);
  }

  /**
   * Wait for a task to complete before executing the next line of code, to synchronize index
   * updates. All write operations in Algolia are asynchronous by design.
   *
   * @param indexName The indexName to wait on
   * @param taskID The Algolia taskID
   */
  public void waitTask(@Nonnull String indexName, long taskID) {
    waitTask(indexName, taskID, 100, null);
  }

  /**
   * Wait for a task to complete before executing the next line of code, to synchronize index
   * updates. All write operations in Algolia are asynchronous by design.
   *
   * @param indexName The indexName to wait on
   * @param taskID The Algolia taskID
   * @param timeToWait The time to wait between each call
   */
  public void waitTask(@Nonnull String indexName, long taskID, int timeToWait) {
    waitTask(indexName, taskID, timeToWait, null);
  }

  /**
   * Wait for a task to complete before executing the next line of code, to synchronize index
   * updates. All write operations in Algolia are asynchronous by design.
   *
   * @param indexName The indexName to wait on
   * @param taskID The Algolia taskID
   * @param requestOptions Options to pass to this request
   */
  public void waitTask(@Nonnull String indexName, long taskID, RequestOptions requestOptions) {
    waitTask(indexName, taskID, 100, requestOptions);
  }

  /**
   * Wait for a task to complete before executing the next line of code, to synchronize index
   * updates. All write operations in Algolia are asynchronous by design.
   *
   * @param indexName The indexName to wait on
   * @param taskID The Algolia taskID
   * @param timeToWait The time to wait between each call
   * @param requestOptions Options to pass to this request
   */
  public void waitTask(
      @Nonnull String indexName, long taskID, int timeToWait, RequestOptions requestOptions) {

    Objects.requireNonNull(indexName, "The index name is required.");

    SearchIndex indexToWait = initIndex(indexName);
    indexToWait.waitTask(taskID, timeToWait, requestOptions);
  }
}