package com.oct.invoicesystem.shared.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
public class EncryptionAttributeConverter implements AttributeConverter<String, String> {

    private static EncryptionUtil encryptionUtil;

    @Autowired
    public void setEncryptionUtil(EncryptionUtil util) {
        EncryptionAttributeConverter.encryptionUtil = util;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        if (encryptionUtil == null) {
            throw new IllegalStateException("EncryptionUtil is not initialized");
        }
        return encryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (encryptionUtil == null) {
            throw new IllegalStateException("EncryptionUtil is not initialized");
        }
        return encryptionUtil.decrypt(dbData);
    }
}
