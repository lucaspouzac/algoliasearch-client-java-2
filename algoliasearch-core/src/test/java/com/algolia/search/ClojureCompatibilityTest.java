package com.algolia.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ClojureCompatibilityTest {

  private static SearchClient searchClient;

  @AfterAll
  static void close() throws IOException {
    searchClient.close();
  }

  @Test
  void reflectionVisibilityForIndex() {
    searchClient =
        new SearchClient(new SearchConfig.Builder("appid", "apikey").build(), new DummyRequester());
    SearchIndex<String> index = searchClient.initIndex("indexName", String.class);

    assertThat(SearchIndex.class.getMethods())
        .filteredOn(m -> Modifier.isPublic(m.getModifiers()))
        .allMatch(
            m -> {
              try {
                Object[] parameters = new Object[m.getParameterCount()];
                m.invoke(index, parameters);
              } catch (InvocationTargetException | IllegalArgumentException ignored) {
              } catch (IllegalAccessException e) {
                fail("IllegalAccessException, can not call method from reflexion `%s`, %s", m, e);
              }
              return true;
            });
  }
}
