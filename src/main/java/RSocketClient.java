package com.morencorps.pocket;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(TestServerSupport.class)
public @interface RSocketClient {

    Class[] value();

}