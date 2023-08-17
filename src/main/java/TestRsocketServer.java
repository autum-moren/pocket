package com.morencorps.pocket;

import com.google.protobuf.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.Payload;
import io.rsocket.ipc.MetadataDecoder;
import io.rsocket.ipc.MutableRouter;
import io.rsocket.rpc.AbstractRSocketService;
import io.rsocket.util.ByteBufPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

public class TestRsocketServer extends AbstractRSocketService {

    public static Map<String, RouteData> routes = new HashMap<>();
    public static Map<String, Boolean> routeParallelInfo = new HashMap<>();

    public static final BlockingQueue<Context> contexts = new LinkedBlockingQueue<>();

    private static final Function<MessageLite, Payload> serializer = message -> {
        int length = message.getSerializedSize();
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(length);

        try {
            message.writeTo(CodedOutputStream.newInstance(byteBuf.internalNioBuffer(0, length)));
            byteBuf.writerIndex(length);
            return ByteBufPayload.create(byteBuf);
        } catch (Throwable var5) {
            byteBuf.release();
            throw new RuntimeException(var5);
        }
    };

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";

//    private Map<String, Context> parallelContext;   route/json req

    //payload.getMetadata содержит route, но как раскрыть?
    //TODO если сервер поднимать не как часть springContext, сюда приходят запросы
    @Override
    public Mono<Payload> requestResponse(Payload payload) {
    var decoder = new DefaultMetadataExtractor();
    var rt = decoder.extract(payload, MimeType.valueOf("message/x.rsocket.routing.v0"));
        System.out.println();
        return Mono.empty();
    }

    @Override
    public Class<?> getServiceClass() {
        return this.getClass();
    }

    @Override
    public void selfRegister(MutableRouter mutableRouter) {
        routes.forEach((route, resClass) -> mutableRouter.withRequestResponseRoute(route, this::executor));
    }

    //TODO изменить assert
    @SneakyThrows
    private Mono<Payload> executor(Payload payload, MetadataDecoder.Metadata decoded) {
        var data = routes.get(decoded.route());
        Assertions.assertNotNull(data);
        Assertions.assertNotNull(data.clsRequest);
        Assertions.assertNotNull(data.clsResponse);
        var ctx = contexts.take();
        Assertions.assertNotNull(ctx);
        Assertions.assertNotNull(ctx.jsonResponse);
        Assertions.assertNotNull(ctx.jsonRequest);
        try {
            checkRequest(payload, decoded.route(), data.clsRequest, ctx.jsonRequest);
            var msg = ProtoJsonUtil.fromJson(ctx.jsonResponse, data.clsResponse);
            Assertions.assertNotNull(msg);
            return Mono.just(msg)
                    .map(serializer);
        } catch (Throwable e) {
            System.out.println(CYAN + "POCKED:" + RED + " error -> " + e.getMessage() + RESET);
            return Mono.empty(); //TODO почему то error не отрабатывает, попозже выяснить
        }
    }

    private void checkRequest(Payload payload, String route, Class<? extends Message> reqClass, String expectedJsonRequest) throws
            InvocationTargetException, IllegalAccessException, IOException, NoSuchMethodException {
        var method = reqClass.getMethod("parseFrom", CodedInputStream.class);
        var is = CodedInputStream.newInstance(payload.getData());
        var actualRequestObj = method.invoke(null, is); //actual req
        var expectedRequestObject = ProtoJsonUtil.fromJson(expectedJsonRequest, reqClass); //expected obj
        if (!actualRequestObj.equals(expectedRequestObject)) { //сравниваем объекты req
            String actual = ProtoJsonUtil.toJson((Message) actualRequestObj); //actual json
            String expected = ProtoJsonUtil.toJson((Message) expectedRequestObject);
            System.out.println(CYAN + "------------------POCKED------------------" + RESET);
            System.out.println(CYAN + "MISMATCH REQUEST - " + route + RESET);
            System.out.println(GREEN + "EXPECTED:\n" + expected + RED + "\nACTUAL:\n" + actual + RESET);
            System.out.println(CYAN + "------------------POCKED------------------" + RESET);
            throw new RuntimeException("POCKED: mismatch request");
        }
        //TODO возможно придется пропускать некоторые значения(время)
    }

    @AllArgsConstructor
    public static class RouteData {

        public Class<? extends Message> clsRequest;
        public Class<? extends Message> clsResponse;
    }

    @Getter
    @AllArgsConstructor
    public static class Context {
        private String route;
        private String jsonRequest;
        private String jsonResponse;
    }

}
