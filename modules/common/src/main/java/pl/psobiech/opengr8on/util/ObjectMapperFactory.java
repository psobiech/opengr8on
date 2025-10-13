/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;

/**
 * Shared serialization object mappers
 */
public final class ObjectMapperFactory {
    /**
     * Shared XML mapper
     */
    public static final XmlMapper XML = ObjectMapperFactory.create(XmlMapper.class);

    /**
     * Shared JSON mapper
     */
    public static final JsonMapper JSON = ObjectMapperFactory.create(JsonMapper.class);

    private ObjectMapperFactory() {
        // NOP
    }

    public static <M extends ObjectMapper> M create(Class<M> clazz) {
        try {
            final M objectMapperInstance = clazz.getConstructor().newInstance();

            return configureJacksonObjectMapper(objectMapperInstance);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }

    private static <M extends ObjectMapper> M configureJacksonObjectMapper(M mapper) {
        mapper.registerModule(new JavaTimeModule())
              .registerModule(new ParameterNamesModule())
              .registerModule(new JacksonXmlModule());

        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(false));

        mapper.setVisibility(
                mapper.getSerializationConfig()
                      .getDefaultVisibilityChecker()
                      .withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        );

        return mapper;
    }
}
