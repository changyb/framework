package org.cyb.rest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

interface UriTemplate {
    interface MatchResult extends Comparable<MatchResult> {
        String getMatched();

        String getRemaining();

        Map<String, String> getMatchedPathParameters();
    }

    Optional<MatchResult> match(String path);
}

class UriTemplateString implements UriTemplate {

    private static final String LeftBracket = "\\{";
    private static final String RightBracket = "}";
    private static final String VariableName = "\\w[\\w\\.-]*";
    private static final String NonBrackets = "[^\\{}]+";
    private final static Pattern variable = Pattern.compile(LeftBracket
            + group(VariableName)
            + group(":" + group(NonBrackets)) + "?"
            + RightBracket);
    private static final int variablePatternGroup = 3;
    public static final String defaultVariablePattern = "([^/]+?)";
    public static final int variableNameGroup = 1;

    private final Pattern pattern;
    private final List<String> variables = new ArrayList<>();
    private int variableGroupStartFrom;

    private static String group(String pattern) {
        return "(" + pattern + ")";
    }

    public UriTemplateString(String template) {
        pattern = Pattern.compile(group(variable(template)) + "(/.*)?");
        variableGroupStartFrom = 2;
    }

    private String variable(String template) {
        return variable.matcher(template).replaceAll(result -> {
            String variableName = result.group(variableNameGroup);
            String pattern = result.group(variablePatternGroup);

            if (variables.contains(variableName)) {
                throw new IllegalArgumentException("duplicate variable" +
                        variableName);
            }
            variables.add(variableName);
            return pattern == null ?
                    defaultVariablePattern : group(pattern);
        });
    }

    @Override
    public Optional<MatchResult> match(String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Map<String, String> parameters = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            parameters.put(variables.get(i), matcher.group(variableGroupStartFrom + i));
        }

        int count = matcher.groupCount();

        return Optional.of(new MatchResult() {
            @Override
            public String getMatched() {
                return matcher.group(1);
            }

            @Override
            public String getRemaining() {
                return matcher.group(count);
            }

            @Override
            public Map<String, String> getMatchedPathParameters() {
                return parameters;
            }

            @Override
            public int compareTo(MatchResult o) {
                return 0;
            }
        });
    }
}

