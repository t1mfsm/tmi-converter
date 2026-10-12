package ru.example.tmi.sspp;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Пакет ССПП ТМИ типа INFO (п. 3.1 документа ССПП). Неизменяемый.
 *
 * <p>Ограничения формата проверяются при создании: тип источника не длиннее
 * 255 байтов, номер источника и длина данных умещаются в {@code UShort},
 * количество свойств в {@code Byte}.
 */
public final class SsppInfoPacket {

    private final String sourceType;
    private final int sourceNumber;
    private final Instant receiveTime;
    private final byte[] data;
    private final List<SsppProperty> properties;

    private SsppInfoPacket(Builder builder) {
        this.sourceType = Objects.requireNonNull(builder.sourceType, "sourceType");
        if (sourceType.getBytes(StandardCharsets.UTF_8).length > SsppFormat.MAX_BYTE) {
            throw new IllegalArgumentException("Тип источника длиннее 255 байтов: " + sourceType);
        }
        if (builder.sourceNumber < 0 || builder.sourceNumber > SsppFormat.MAX_USHORT) {
            throw new IllegalArgumentException("Номер источника вне диапазона UShort: " + builder.sourceNumber);
        }
        this.sourceNumber = builder.sourceNumber;
        this.receiveTime = Objects.requireNonNull(builder.receiveTime, "receiveTime");
        Objects.requireNonNull(builder.data, "data");
        if (builder.data.length > SsppFormat.MAX_USHORT) {
            throw new IllegalArgumentException("Длина данных больше 65535: " + builder.data.length);
        }
        this.data = builder.data.clone();
        if (builder.properties.size() > SsppFormat.MAX_BYTE) {
            throw new IllegalArgumentException("Свойств больше 255: " + builder.properties.size());
        }
        this.properties = Collections.unmodifiableList(new ArrayList<>(builder.properties));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSourceType() {
        return sourceType;
    }

    public int getSourceNumber() {
        return sourceNumber;
    }

    public Instant getReceiveTime() {
        return receiveTime;
    }

    public int getDataLength() {
        return data.length;
    }

    /**
     * Копия поля «Данные».
     */
    public byte[] getData() {
        return data.clone();
    }

    /**
     * Свойства пакета в порядке записи.
     */
    public List<SsppProperty> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "SsppInfoPacket{" + sourceType + "_" + sourceNumber
                + ", receiveTime=" + receiveTime
                + ", dataLength=" + data.length
                + ", properties=" + properties + "}";
    }

    /**
     * Построитель {@link SsppInfoPacket}.
     */
    public static final class Builder {

        private String sourceType;
        private int sourceNumber;
        private Instant receiveTime;
        private byte[] data = new byte[0];
        private final List<SsppProperty> properties = new ArrayList<>();

        private Builder() {
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder sourceNumber(int sourceNumber) {
            this.sourceNumber = sourceNumber;
            return this;
        }

        public Builder receiveTime(Instant receiveTime) {
            this.receiveTime = receiveTime;
            return this;
        }

        /**
         * Данные пакета; массив копируется при вызове {@link #build()}.
         */
        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder addProperty(SsppProperty property) {
            properties.add(Objects.requireNonNull(property, "property"));
            return this;
        }

        public SsppInfoPacket build() {
            return new SsppInfoPacket(this);
        }
    }
}
