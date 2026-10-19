package com.emocio.analysefoot.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stocke le carton sous sa forme courte (Y, R, Y/R), comme dans les données sources. */
@Converter(autoApply = true)
public class CardConverter implements AttributeConverter<Card, String> {

    @Override
    public String convertToDatabaseColumn(Card card) {
        return card == null ? null : card.getCode();
    }

    @Override
    public Card convertToEntityAttribute(String code) {
        return code == null ? null : Card.fromCode(code);
    }
}
