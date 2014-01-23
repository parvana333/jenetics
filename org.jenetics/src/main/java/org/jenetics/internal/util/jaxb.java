/*
 * Java Genetic Algorithm Library (@__identifier__@).
 * Copyright (c) @__year__@ Franz Wilhelmstötter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author:
 *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmx.at)
 */
package org.jenetics.internal.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.transform.dom.DOMSource;

import org.jscience.mathematics.number.Float64;
import org.w3c.dom.Element;

import org.jenetics.internal.util.model.ModelType;
import org.jenetics.util.Function;
import org.jenetics.util.StaticObject;

/**
 * JAXB helper methods.
 *
 * @author <a href="mailto:franz.wilhelmstoetter@gmx.at">Franz Wilhelmstötter</a>
 * @version @__version__@ &mdash; <em>$Date$</em>
 * @since @__version__@
 */
public class jaxb extends StaticObject {
	private jaxb() {}

	public static final JAXBContext CONTEXT = newContext();

	private static JAXBContext newContext() {
		try {
			return JAXBContext.newInstance(
				"org.jenetics:org.jenetics.internal.util"
			);
		} catch (JAXBException e) {
			throw new AssertionError(e);
		}
	}

	// Identity XmlAdapter.
	private static final
	XmlAdapter<Object, Object> ID_XML_ADAPTER = new XmlAdapter<Object, Object>() {
		@Override public Object unmarshal(final Object value) {
			return value;
		}
		@Override public Object marshal(final Object value) {
			return value;
		}
	};

	private static final Map<Class<?>, XmlAdapter<? extends Object, ? extends Object>>
	xmlAdapterCache = new HashMap<>();
	static {
		xmlAdapterCache.put(Float64.class, model.Float64Model.Adapter);
		xmlAdapterCache.put(model.Float64Model.class, model.Float64Model.Adapter);
	}

	/**
	 * Return the an {@code XmlAdapter} for the given {@code vale}. If no
	 * adapter could be found, and identity adapter is returned.
	 *
	 * @param value the object for which to find an {@code XmlAdapter}
	 * @return the {@code XmlAdapter} for the given object, or an identity
	 *         adapter if no one can be found.
	 */
	public static XmlAdapter<Object, Object> adapterFor(final Object value) {
		final Class<?> cls = value instanceof Class<?> ?
			(Class<?>)value : value.getClass();

		synchronized (xmlAdapterCache) {
			if (!xmlAdapterCache.containsKey(cls)) {
				xmlAdapterCache.put(cls, newXmlAdapter(cls));
			}

			return (XmlAdapter<Object, Object>)xmlAdapterCache.get(cls);
		}
	}

	public static Class<?> modelTypeFor(final Object value) {
		final Object adapter = adapterFor(value);
		final ModelType ma = adapter.getClass().getAnnotation(ModelType.class);
		if (ma != null) {
			return ma.value();
		}
		return value instanceof Class<?> ? (Class<?>)value : value.getClass();
	}

	@SuppressWarnings("unchecked")
	private static XmlAdapter<Object, Object> newXmlAdapter(final Class<?> cls) {
		final List<Class<?>> classes = reflect.allDeclaredClasses(cls);

		XmlAdapter<Object, Object> adapter = ID_XML_ADAPTER;
		for (int i = 0; i < classes.size() && adapter == ID_XML_ADAPTER; ++i) {
			if (XmlAdapter.class.isAssignableFrom(classes.get(i))) {
				try {
					adapter = (XmlAdapter<Object, Object>)classes.get(i).newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					// ignore exception
				}
			}
		}

		return adapter;
	}

	/**
	 * Checks, whether the given object is an JAXB model or has an model adapter
	 * defined.
	 *
	 * @param value the object used for the model check.
	 * @return {@code true} if the given object has/is an JAXB model,
	 *         {@code false} otherwise.
	 */
	public static boolean hasModel(final Object value) {
		final Class<?> cls = value instanceof Class<?> ?
			(Class<?>)value : value.getClass();

		return cls.isAnnotationPresent(XmlJavaTypeAdapter.class) ||
			cls.isAnnotationPresent(XmlRootElement.class) ||
			cls.isAnnotationPresent(XmlType.class);
	}

	public static <V, B> Function<B, V> marshaller(final XmlAdapter<V, B> adapter) {
		return new Function<B, V>() {
			@Override
			public V apply(final B value) {
				try {
					return adapter.marshal(value);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public static <V, B> Function<V, B> unmarshaller(final XmlAdapter<V, B> adapter) {
		return new Function<V, B>() {
			@Override
			public B apply(final V value) {
				try {
					return adapter.unmarshal(value);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public static <V, B> Function<B, V> Marshaller(final Object value) {
		return (Function<B, V>) marshaller(adapterFor(value));
	}

	public static final Function<Object, Object> Unmarshaller =
	new Function<Object, Object>() {
		@Override
		public Object apply(final Object value) {
			Object result = value;
			if (value instanceof Element) {
				final Element element = (Element)value;

				try {
					final Class<?> type = modelTypeFor(
						Class.forName(element.getNodeName())
					);

					final DOMSource source = new DOMSource(element);
					final JAXBElement jaxbElement = CONTEXT.createUnmarshaller()
						.unmarshal(source, type);

					result = jaxb.adapterFor(jaxbElement.getValue())
						.unmarshal(jaxbElement.getValue());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			return result;
		}
	};

}
