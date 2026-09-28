package com.commercex.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercex.common.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

    @ParameterizedTest
    @CsvSource({
            "Nike Air Max 90,        nike-air-max-90",
            "'  Crème Brûlée!! ',    creme-brulee",
            "Men's T-Shirts & Tops,  men-s-t-shirts-tops",
            "---a---b---,            a-b"
    })
    void slugifies(String input, String expected) {
        assertThat(Slugs.slugify(input)).isEqualTo(expected);
    }

    @Test
    void truncatesWithoutTrailingHyphen() {
        String slug = Slugs.slugify("word ".repeat(100));
        assertThat(slug.length()).isLessThanOrEqualTo(Slugs.MAX_LENGTH);
        assertThat(slug).doesNotEndWith("-");
    }

    @Test
    void rejectsNamesWithoutSlugCharactersAndInvalidExplicitSlugs() {
        assertThatThrownBy(() -> Slugs.slugify("!!!")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Slugs.resolve("Bad Slug", "x")).isInstanceOf(ValidationException.class);
        assertThat(Slugs.resolve("good-slug", "Ignored")).isEqualTo("good-slug");
        assertThat(Slugs.resolve(" ", "From Name")).isEqualTo("from-name");
    }
}
