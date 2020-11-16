/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wildfly.security.json.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * JSON serializer for strings or arrays.
 *
 * @author <a href="fjuma@redhat.com">Farah Juma</a>
 */
public class StringOrArraySerializer extends JsonSerializer<Object> {
    @Override
    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        String[] array = (String[]) o;
        if (array == null) {
            jsonGenerator.writeNull();
        } else if (array.length == 1) {
            jsonGenerator.writeString(array[0]);
        } else {
            jsonGenerator.writeStartArray();
            for (String s : array) {
                jsonGenerator.writeString(s);
            }
            jsonGenerator.writeEndArray();
        }
    }
}
