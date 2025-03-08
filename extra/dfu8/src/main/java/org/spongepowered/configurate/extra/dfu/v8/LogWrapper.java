/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.extra.dfu.v8;

import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.LoggerFactory;

abstract class LogWrapper { // so we can use the same impl across different MC versions

    private static final boolean HAS_SLF4J;

    static {
        boolean slf4j = true;
        try {
            Class.forName("org.slf4j.Logger");
        } catch (final ClassNotFoundException ex) {
            slf4j = false;
        }

        HAS_SLF4J = slf4j;

        logger(LogWrapper.class).debug("Using logger {} for configurate-dfu", slf4j ? "SLF4J" : "Log4J 2");
    }

    static LogWrapper logger(final Class<?> clazz) {
        if (HAS_SLF4J) {
            return new Slf4j(clazz);
        } else {
            throw new UnsupportedOperationException("SLF4J not present");
        }
    }

    public abstract void debug(String pattern, @Nullable Object... params);

    public abstract void debug(Supplier<String> messageSupplier, Throwable ex);

    static final class Slf4j extends LogWrapper {
        private final org.slf4j.Logger logger;

        Slf4j(final Class<?> clazz) {
            this.logger = LoggerFactory.getLogger(clazz);
        }

        @Override
        public void debug(final String pattern, final Object... params) {
            this.logger.debug(pattern, params);
        }

        @Override
        public void debug(final Supplier<String> messageSupplier, final Throwable ex) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug(messageSupplier.get(), ex);
            }
        }

    }

}
