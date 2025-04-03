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
package org.spongepowered.configurate.kotlin

import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.GenericTypeReflector.erase
import io.leangen.geantyref.TypeFactory
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.FieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.objectmapping.ObjectMapper.Factory
import org.spongepowered.configurate.util.Types.combinedAnnotations
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.AnnotatedType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.*

private val dataClassMapperFactory =
    ObjectMapper.factoryBuilder().addDiscoverer(DataClassFieldDiscoverer).build()

/**
 * Get an object mapper factory with standard capabilities and settings, except for the added
 * ability to interpret data clasess with a [dataClassFieldDiscoverer].
 */
fun objectMapperFactory(): Factory {
    return dataClassMapperFactory
}

/**
 * Get a field discoverer that can determine field information from data classes.
 *
 * @sample [org.spongepowered.configurate.kotlin.examples.createLoader]
 */
fun dataClassFieldDiscoverer(): FieldDiscoverer<*> {
    return DataClassFieldDiscoverer
}

/** Get an object mapper for the type [T] using the default object mapper factory */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> objectMapper(): ObjectMapper<T> {
    return objectMapperFactory()[typeOf<T>().javaType] as ObjectMapper<T>
}

/** Get an object mapper bound to the instance of [T], resolving type parameters */
inline fun <reified T> T.toNode(target: ConfigurationNode) {
    return objectMapperFactory().get<T>().save(this, target)
}

/**
 * A field discoverer that gathers definitions from kotlin `data` classes.
 *
 * Note: Type use annotations are not handled correctly in Kotlin at the moment. To use these
 * annotations, the `-Xemit-jvm-type-annotations` compiler option should be used (available Kotlin
 * 1.4+ only).
 *
 * See [KT-39369](https://youtrack.jetbrains.com/issue/KT-39369) for details.
 */
private object DataClassFieldDiscoverer : FieldDiscoverer<MutableMap<KParameter, Any?>> {
    override fun <V> discover(
        target: AnnotatedType,
        collector: FieldDiscoverer.FieldCollector<MutableMap<KParameter, Any?>, V>,
    ): FieldDiscoverer.InstanceFactory<MutableMap<KParameter, Any?>>? {
        val klass = erase(target.type).kotlin
        if (!klass.isData) {
            return null
        }

        val constructor = klass.primaryConstructor ?: return null
        constructor.javaMethod

        val annotatedTypes = constructor.javaConstructor!!.annotatedParameterTypes
        val properties = klass.memberProperties
        constructor.parameters.asSequence().zip(annotatedTypes.asSequence()).forEach { (param, type) ->
            val resolvedType = GenericTypeReflector.resolveType(type, target).normalize()
            val field = properties.first { it.name == param.name }

            @Suppress("UNCHECKED_CAST")
            collector.accept(
                param.name,
                resolvedType,
                combinedAnnotations(
                    param.type.javaElement,
                    param.javaElement,
                    field.javaField
                ), // type, backing field, etc
                // deserializer
                { intermediate, arg, implicitProvider ->
                    if (arg != null) {
                        intermediate[param] = arg
                    } else if (!param.isOptional) {
                        intermediate[param] = implicitProvider.get()
                    }
                },
                // serializer
                { (field as KProperty1<V, *>).get(it) },
            )
        }

        return object : FieldDiscoverer.InstanceFactory<MutableMap<KParameter, Any?>> {
            override fun begin(): MutableMap<KParameter, Any?> {
                return mutableMapOf()
            }

            override fun complete(intermediate: MutableMap<KParameter, Any?>): Any {
                return constructor.callBy(intermediate)
            }

            override fun canCreateInstances(): Boolean = true
        }
    }
}

// thanks kotlin :(

/**
 * 移除类型中不必要的通配符.
 */
private fun AnnotatedType.normalize(): AnnotatedType {
    if (type !is ParameterizedType) {
        return this
    }

    val paramType = type as ParameterizedType
    val rawType = paramType.rawType
    val typeArgs = paramType.actualTypeArguments

    // 检查是否有需要处理的通配符类型
    val hasWildcards = typeArgs.any { it is WildcardType }
    if (!hasWildcards) {
        return this
    }

    // 处理通配符
    val normalizedArgs = typeArgs.map { arg ->
        when {
            // 处理有界通配符
            arg is WildcardType &&
                arg.lowerBounds.isEmpty() &&
                arg.upperBounds.size == 1 &&
                arg.upperBounds[0] != Object::class.java
                -> {
                // 有上界的通配符 (即 ? extends E) - 使用其上界
                GenericTypeReflector.annotate(arg.upperBounds[0])
            }
            // 处理无界通配符 (即 * 或 ?)
            arg is WildcardType &&
                arg.lowerBounds.isEmpty() &&
                (arg.upperBounds.isEmpty() || arg.upperBounds[0] == Object::class.java)
                -> {
                // 保留通配符特性
                GenericTypeReflector.annotate(arg)
            }
            // 处理下界通配符
            arg is WildcardType &&
                arg.lowerBounds.isNotEmpty()
                -> {
                // 有下界的通配符 (即 ? super E)
                GenericTypeReflector.annotate(arg)
            }

            else -> {
                // 其他类型直接处理
                GenericTypeReflector.annotate(arg)
            }
        }
    }.toTypedArray()

    // 创建一个新的处理过的类型
    val newType = TypeFactory.parameterizedAnnotatedClass(
        rawType as Class<*>,
        annotations,
        *normalizedArgs
    )

    return newType
}

/** Get a kotlin annotated element as a Java one */
private val KAnnotatedElement.javaElement: AnnotatedElement
    get() {
        if (this is KProperty<*>) {
            val javaType = this.javaField ?: this.javaGetter
            if (javaType != null) {
                return javaType
            }
        } else if (this is KFunction<*>) {
            val javaType = this.javaMethod ?: this.javaConstructor
            if (javaType != null) {
                return javaType
            }
        }

        return WrappedElement(this)
    }

private class WrappedElement(private val backing: KAnnotatedElement) : AnnotatedElement {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Annotation> getAnnotation(annotationClass: Class<T>): T? {
        return backing.annotations.firstOrNull { it.annotationClass.java == annotationClass } as T?
    }

    override fun getAnnotations(): Array<Annotation> {
        return backing.annotations.toTypedArray()
    }

    override fun getDeclaredAnnotations(): Array<Annotation> = this.annotations
}
