package org.cyb.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class PathTemplateTest {

    @Test
    public void should_return_empty_if_path_not_matched() {
        PathTemplate template = new PathTemplate("/users");

        assertTrue(template.match("/orders").isEmpty());
    }

    @Test
    public void should_return_match_result_if_path_matched() {
        PathTemplate template = new PathTemplate("/users");
        UriTemplate.MatchResult result = template.match("/users/1").get();

        assertEquals("/users", result.getMatched());
        assertEquals("/1", result.getRemaining());
        assertTrue(result.getMatchedPathParameters().isEmpty());
    }

    @Test
    public void should_return_match_result_if_path_with_variable_matched() {
        PathTemplate template = new PathTemplate("/users/{id}");
        UriTemplate.MatchResult result = template.match("/users/1").get();

        assertEquals("/users/1", result.getMatched());
        assertNull(result.getRemaining());
        assertFalse(result.getMatchedPathParameters().isEmpty());
        assertEquals("1", result.getMatchedPathParameters().get("id"));
    }

    @Test
    public void should_return_empty_if_not_match_given_pattern() {
        PathTemplate template = new PathTemplate("/users/{id:[0-9]+}");

        assertTrue(template.match("/users/id").isEmpty());
    }

    @Test
    public void should_extract_variable_value_by_given_pattern() {
        PathTemplate template = new PathTemplate("/users/{id:[0-9]+}");
        UriTemplate.MatchResult result = template.match("/users/1").get();

        assertEquals("1", result.getMatchedPathParameters().get("id"));
    }

    @Test
    public void should_throw_illegal_argument_exception_if_variable_redefined() {
        assertThrows(IllegalArgumentException.class, () -> new PathTemplate("/users/{id:[0-9]+}/{id}"));
    }

    @ParameterizedTest
    @CsvSource({"/users/1234567890/order,/{resources}/1234567890/{action},/users/{id}/order","/users/1,/users/{id:[0-9]+},/users/{id}", "/users/1234,/users/1234,/users/{id}"})
    public void first_pattern_should_be_smaller_than_second(String path, String smallerUri, String largerUri) {
        PathTemplate smaller = new PathTemplate(smallerUri);
        PathTemplate larger = new PathTemplate(largerUri);
        UriTemplate.MatchResult lhs = smaller.match(path).get();
        UriTemplate.MatchResult rhs = larger.match(path).get();
        assertTrue(lhs.compareTo(rhs) < 0);
        assertTrue(rhs.compareTo(lhs) > 0);
    }
}
