package com.emocio.analysefoot.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    private final CardConverter converter = new CardConverter();

    @Test
    void mapsSourceCodes() {
        assertThat(Card.fromCode("Y")).isEqualTo(Card.YELLOW);
        assertThat(Card.fromCode("R")).isEqualTo(Card.RED);
        assertThat(Card.fromCode("Y/R")).isEqualTo(Card.SECOND_YELLOW);
        assertThatThrownBy(() -> Card.fromCode("B")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void converterStoresShortCode() {
        for (Card card : Card.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(card))).isEqualTo(card);
        }
        assertThat(converter.convertToDatabaseColumn(Card.SECOND_YELLOW)).isEqualTo("Y/R");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
