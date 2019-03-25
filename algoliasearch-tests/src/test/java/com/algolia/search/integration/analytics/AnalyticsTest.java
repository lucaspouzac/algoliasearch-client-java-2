package com.algolia.search.integration.analytics;

import static com.algolia.search.integration.AlgoliaIntegrationTestExtension.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.algolia.search.AnalyticsClient;
import com.algolia.search.SearchIndex;
import com.algolia.search.exceptions.AlgoliaApiException;
import com.algolia.search.integration.AlgoliaIntegrationTestExtension;
import com.algolia.search.integration.models.AlgoliaObject;
import com.algolia.search.models.analytics.*;
import com.algolia.search.models.indexing.BatchIndexingResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@ExtendWith({AlgoliaIntegrationTestExtension.class})
class AnalyticsTest {

  private SearchIndex<AlgoliaObject> index1;
  private SearchIndex<AlgoliaObject> index2;
  private String index1Name;
  private String index2Name;
  private static AnalyticsClient analyticsClient;

  AnalyticsTest() {
    analyticsClient = new AnalyticsClient(ALGOLIA_APPLICATION_ID_1, ALGOLIA_API_KEY_1);
    index1Name = getTestIndexName("ab_testing");
    index2Name = getTestIndexName("ab_testing_dev");
    index1 = searchClient.initIndex(index1Name, AlgoliaObject.class);
    index2 = searchClient.initIndex(index2Name, AlgoliaObject.class);
  }

  @AfterAll
  static void afterAll() {
    analyticsClient.close();
  }

  @Test
  void abTestingTest() {
    String now =
        ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String testName = String.format("java-%s-%s", now, userName);

    ABTests abTests = analyticsClient.getABTestsAsync().join();

    if (abTests.getAbtests() != null) {
      List<ABTestResponse> abTestsToDelte =
          abTests.getAbtests().stream()
              .filter(
                  x ->
                      x.getName().contains("java-")
                          && !x.getName().contains(String.format("java-%s", now)))
              .collect(Collectors.toList());

      for (ABTestResponse abtest : abTestsToDelte) {
        analyticsClient.deleteABTestAsync(abtest.getAbTestID());
      }
    }

    CompletableFuture<BatchIndexingResponse> saveObjectFuture1 =
        index1.saveObjectAsync(new AlgoliaObject("one", "value"));
    CompletableFuture<BatchIndexingResponse> saveObjectFuture2 =
        index2.saveObjectAsync(new AlgoliaObject("one", "value"));

    OffsetDateTime utcNow = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).withSecond(0);

    ABTest abtest =
        new ABTest(
            testName,
            Arrays.asList(
                new Variant(index1Name, 60, "a description"), new Variant(index2Name, 40, null)),
            utcNow.plusDays(1));

    saveObjectFuture1.join().waitTask();
    saveObjectFuture2.join().waitTask();

    AddABTestResponse addAbTest = analyticsClient.addABTestAsync(abtest).join();
    long abTestID = addAbTest.getAbTestID();
    index1.waitTask(addAbTest.getTaskID());

    ABTest abTestToCheck = analyticsClient.getABTestAsync(abTestID).join();

    // Assert that the objects are deeply equal
    assertThat(abTestToCheck.getVariants())
        .usingRecursiveComparison()
        .isEqualTo(abtest.getVariants());
    assertThat(abTestToCheck.getEndAt()).isEqualTo(abtest.getEndAt());
    assertThat(abTestToCheck.getName()).isEqualTo(abtest.getName());

    ABTests listABTests = analyticsClient.getABTestsAsync().join();
    assertThat(listABTests.getAbtests()).isNotNull();

    Optional<ABTestResponse> result =
        listABTests.getAbtests().stream().filter(x -> x.getAbTestID() == abTestID).findFirst();

    // Assert that the objects are deeply equal;
    assertThat(result.get().getVariants())
        .usingRecursiveComparison()
        .isEqualTo(abtest.getVariants());
    assertThat(result.get().getEndAt()).isEqualTo(abtest.getEndAt());
    assertThat(result.get().getName()).isEqualTo(abtest.getName());

    StopAbTestResponse stopAbTestResponse = analyticsClient.stopABTestAsync(abTestID).join();

    // Assert that the ABTest was stopped
    ABTestResponse stoppedAbTest = analyticsClient.getABTestAsync(abTestID).join();
    assertThat(Objects.equals(stoppedAbTest.getStatus(), "stopped"));

    DeleteAbTestResponse deleteAbTest = analyticsClient.deleteABTestAsync(abTestID).join();
    index1.waitTask(deleteAbTest.getTaskID());

    assertThatThrownBy(() -> analyticsClient.getABTestAsync(abTestID).join())
        .hasCauseInstanceOf(AlgoliaApiException.class)
        .hasMessageContaining("ABTestID not found");
  }
}
