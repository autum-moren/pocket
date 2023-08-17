package com.morencorps.pocket;

import com.google.protobuf.Message;
import io.rsocket.rpc.annotations.internal.Generated;
import io.rsocket.rpc.annotations.internal.GeneratedMethod;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class TestServerSupport implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    //TODO необходимы проверки, в особенности проверка указанных классов в @RSocketClient(что бы соответствовали)

    private Disposable disposable;

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        //TODO поддержка без @Bean
//        RSocketServer anoServer = extensionContext.getTestClass()
//                .orElseThrow(() -> new RuntimeException("TEST CLASS IS NULL"))
//                .getAnnotation(RSocketServer.class);
//        var server = new TestRsocketServer();
//        var rSocketServer = io.rsocket.core.RSocketServer.create();
//        rSocketServer.acceptor((connectionSetupPayload, rSocket) -> Mono.just(new TestRsocketServer()));
//        disposable = rSocketServer.bind(TcpServerTransport.create(anoServer.value()))
//                .subscribe();

        RSocketClient rSocketClient = extensionContext.getTestClass()
                .orElseThrow(() -> new RuntimeException("TEST CLASS IS NULL"))
                .getAnnotation(RSocketClient.class);
        TestRsocketServer.routes.putAll(getRouteMap(List.of(rSocketClient.value())));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        TestRsocketServer.routes.clear();
//        disposable.dispose();
    }

    private Map<String, TestRsocketServer.RouteData> getRouteMap(List<Class> classes) {
        return classes.stream()
                .map(this::getRouteEntry)
                .flatMap(Collection::stream)
                .collect(toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

    }

    private List<AbstractMap.SimpleEntry<String, TestRsocketServer.RouteData>> getRouteEntry(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GeneratedMethod.class))
                .filter(method -> method.getParameterTypes().length == 1)
                .map(method -> new AbstractMap.SimpleEntry<>(getRoute(clazz, method), createDate(method)))
                .collect(Collectors.toList());
    }

    private String getRoute(Class<?> clazz, Method method) {
        var idName = clazz.getAnnotation(Generated.class).idlClass().getSimpleName();
        return idName + "." + StringUtils.capitalize(method.getName()); //TODO инфу нужно брать из field, так как может отличаться реестр
    }

    private TestRsocketServer.RouteData createDate(Method method) {
        var requestClass = (Class<? extends Message>) method.getParameterTypes()[0];
        var responseClazz = (Class<? extends Message>) method.getAnnotation(GeneratedMethod.class).returnTypeClass();
        return new TestRsocketServer.RouteData(requestClass, responseClazz);
    }

    private enum Stage {
        REQUEST,
        RESPONSE,
        BETWEEN
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        var pathScript = extensionContext.getTestMethod()
                .orElseThrow(() -> new RuntimeException("TEST METHOD IS NULL"))
                .getAnnotation(RScript.class);
        //TODO проверка .scr
        File file = ResourceUtils.getFile("classpath:" + pathScript.value());
        AtomicReference<String> route = new AtomicReference<>();
        StringBuilder requestBuilder = new StringBuilder();
        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<Stage> stage = new AtomicReference<>();
        try (Stream<String> stream = Files.lines(file.toPath())) {
            stream.forEach(str -> {
                if (str.startsWith(">>>")) {
                    stage.set(Stage.REQUEST);
                    route.set(str.replaceAll(">>>", "").trim()); //Строка: Rsocket class + rpc method
                } else if (str.startsWith("<<<")) {
                    stage.set(Stage.RESPONSE);
                } else if (str.trim().equals("---")) { //окончание блока req/res
                    if (stage.get() == Stage.RESPONSE) { //если res формируем контекст и кладем в очередь
                        //TODO json валидация req/res
                        var ctx = new TestRsocketServer.Context(route.get(), requestBuilder.toString(), responseBuilder.toString());
                        TestRsocketServer.contexts.add(ctx);
                        route.set(null);//Очистка
                        requestBuilder.setLength(0);
                        responseBuilder.setLength(0);
                    }
                    stage.set(Stage.BETWEEN); //помечаем что идет пространство между req/res
                } else if (stage.get() == Stage.BETWEEN) {
//TODO в скрипт можно вставить [IGNORE: correlationId, timestamp] тут обработать и не проверять поле в последствии
                } else if (stage.get() == Stage.REQUEST) {
                    requestBuilder.append(str);
                } else if (stage.get() == Stage.RESPONSE) {
                    responseBuilder.append(str);
                } else {
                    throw new RuntimeException("PARSER ERROR, unknown stage");
                }
            });
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        TestRsocketServer.contexts.clear();
    }
}
