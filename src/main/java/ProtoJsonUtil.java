package com.morencorps.pocket;

import com.google.protobuf.AbstractMessage.Builder;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public final class ProtoJsonUtil {

    //TODO это необходимо для Any, нужно встроить в настройки или попытаться считывать класс из json и каждый раз формировать эту штуку
    private final static JsonFormat.TypeRegistry tp = JsonFormat.TypeRegistry.newBuilder()
             //TODO некое странное поведение, достаточно указать однин класс и все остальное будет работать, возможно связано с обнаруженным путем к proto классам(они в одном месте)
            .build();

    public static String toJson(MessageOrBuilder messageOrBuilder) throws IOException {
        JsonFormat.printer().usingTypeRegistry(tp).print(messageOrBuilder);
        return JsonFormat.printer().print(messageOrBuilder);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Message> T fromJson(String json, Class<T> clazz) throws IOException {
        Builder builder = null;
        try {
            builder = (Builder) clazz.getMethod("newBuilder").invoke(null);

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | NoSuchMethodException | SecurityException e) {
            return null;
        }

        JsonFormat.parser().ignoringUnknownFields().usingTypeRegistry(tp).merge(json, builder);
        return (T) builder.build();
    }
}
