package com.commercex.common.text;

import com.commercex.common.error.ValidationException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL slugs: lowercase ASCII words separated by single hyphens, e.g. {@code nike-air-max-90}. */
public final class Slugs {

    public static final String PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final int MAX_LENGTH = 160;

    private static final Pattern SLUG = Pattern.compile(PATTERN);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+|-+$)");

    private Slugs() {
    }

    public static String slugify(String text) {
        String ascii = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        if (slug.length() > MAX_LENGTH) {
            slug = EDGE_HYPHENS.matcher(slug.substring(0, MAX_LENGTH)).replaceAll("");
        }
        if (slug.isEmpty()) {
            throw new ValidationException("slug", "cannot be derived from the name; provide one explicitly");
        }
        return slug;
    }

    /** Uses the explicit slug when given, otherwise derives one from the name. */
    public static String resolve(String explicitSlug, String name) {
        if (explicitSlug == null || explicitSlug.isBlank()) {
            return slugify(name);
        }
        String slug = explicitSlug.trim();
        if (slug.length() > MAX_LENGTH || !SLUG.matcher(slug).matches()) {
            throw new ValidationException("slug", "must be lowercase letters, digits and single hyphens");
        }
        return slug;
    }
}
