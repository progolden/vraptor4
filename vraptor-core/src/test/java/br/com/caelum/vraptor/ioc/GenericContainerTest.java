/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package br.com.caelum.vraptor.ioc;

import static br.com.caelum.vraptor.VRaptorMatchers.canHandle;
import static br.com.caelum.vraptor.VRaptorMatchers.hasOneCopyOf;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.enterprise.inject.spi.CDI;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import br.com.caelum.vraptor.controller.ControllerMethod;
import br.com.caelum.vraptor.converter.BooleanConverter;
import br.com.caelum.vraptor.converter.ByteConverter;
import br.com.caelum.vraptor.converter.Converter;
import br.com.caelum.vraptor.converter.EnumConverter;
import br.com.caelum.vraptor.converter.IntegerConverter;
import br.com.caelum.vraptor.converter.LocaleBasedCalendarConverter;
import br.com.caelum.vraptor.converter.LocaleBasedDateConverter;
import br.com.caelum.vraptor.converter.LocaleBasedDoubleConverter;
import br.com.caelum.vraptor.converter.LocaleBasedFloatConverter;
import br.com.caelum.vraptor.converter.LocaleBasedPrimitiveDoubleConverter;
import br.com.caelum.vraptor.converter.LocaleBasedPrimitiveFloatConverter;
import br.com.caelum.vraptor.converter.LongConverter;
import br.com.caelum.vraptor.converter.PrimitiveBooleanConverter;
import br.com.caelum.vraptor.converter.PrimitiveByteConverter;
import br.com.caelum.vraptor.converter.PrimitiveIntConverter;
import br.com.caelum.vraptor.converter.PrimitiveLongConverter;
import br.com.caelum.vraptor.converter.PrimitiveShortConverter;
import br.com.caelum.vraptor.converter.ShortConverter;
import br.com.caelum.vraptor.converter.jodatime.LocalDateConverter;
import br.com.caelum.vraptor.converter.jodatime.LocalTimeConverter;
import br.com.caelum.vraptor.core.Converters;
import br.com.caelum.vraptor.core.MethodInfo;
import br.com.caelum.vraptor.core.RequestInfo;
import br.com.caelum.vraptor.deserialization.Deserializer;
import br.com.caelum.vraptor.deserialization.Deserializers;
import br.com.caelum.vraptor.http.route.Route;
import br.com.caelum.vraptor.http.route.Router;
import br.com.caelum.vraptor.interceptor.InterceptorRegistry;
import br.com.caelum.vraptor.ioc.cdi.CDIBasedContainer;
import br.com.caelum.vraptor.ioc.cdi.Code;
import br.com.caelum.vraptor.ioc.fixture.ControllerInTheClasspath;
import br.com.caelum.vraptor.ioc.fixture.ConverterInTheClasspath;
import br.com.caelum.vraptor.ioc.fixture.InterceptorInTheClasspath;

/**
 * Acceptance test that checks if the container is capable of giving all
 * required components.
 *
 * @author Guilherme Silveira
 */
public abstract class GenericContainerTest {

	protected ContainerProvider provider;
	private Container currentContainer;

	protected abstract ContainerProvider getProvider();
	protected abstract <T> T executeInsideRequest(WhatToDo<T> execution);

	@Before
	public void setup() throws Exception {
		getStartedProvider();
		currentContainer = getCurrentContainer();
	}

	protected void getStartedProvider() {
		provider = getProvider();
		provider.start();
	}

	@After
	public void tearDown() {
		provider.stop();
		provider = null;
	}

	@Test
	public void canProvideJodaTimeConverters() {
		executeInsideRequest(new WhatToDo<String>() {
			@Override
			public String execute(RequestInfo request, int counter) {
				assertNotNull(getFromContainerInCurrentThread(LocalDateConverter.class, request));
				assertNotNull(getFromContainerInCurrentThread(LocalTimeConverter.class, request));
				Converters converters = getFromContainerInCurrentThread(Converters.class, request);
				assertTrue(converters.existsFor(LocalDate.class));
				assertTrue(converters.existsFor(LocalTime.class));
				return null;
			}
		});
	}

	protected <T> void checkAvailabilityFor(final boolean shouldBeTheSame, final Class<T> component) {
		T firstInstance = getFromContainer(component);
		T secondInstance = executeInsideRequest(new WhatToDo<T>() {
			@Override
			public T execute(RequestInfo request, final int counter) {
				provider.provideForRequest(request);
				ControllerMethod secondMethod = mock(ControllerMethod.class, "rm" + counter);
				Container secondContainer = currentContainer;
				secondContainer.instanceFor(MethodInfo.class).setControllerMethod(secondMethod);
				return instanceFor(component, secondContainer);
			}
		});

		checkSimilarity(component, shouldBeTheSame, firstInstance, secondInstance);
	}

	protected <T> T getFromContainer(final Class<T> componentToBeRetrieved) {
		return executeInsideRequest(new WhatToDo<T>() {
			@Override
			public T execute(RequestInfo request, final int counter) {
				return getFromContainerInCurrentThread(componentToBeRetrieved, request);
			}
		});
	}

	protected <T> T getFromContainerAndExecuteSomeCode(final Class<T> componentToBeRetrieved,final Code<T> code) {
		return executeInsideRequest(new WhatToDo<T>() {
			@Override
			public T execute(RequestInfo request, final int counter) {
				T bean = getFromContainerInCurrentThread(componentToBeRetrieved, request,code);
				return bean;
			}
		});
	}

	protected <T> T getFromContainerInCurrentThread(final Class<T> componentToBeRetrieved, RequestInfo request) {
		provider.provideForRequest(request);
		return instanceFor(componentToBeRetrieved, currentContainer);
	}

	private CDIBasedContainer getCurrentContainer() {
		return CDI.current().select(CDIBasedContainer.class).get();
	}

	protected <T> T getFromContainerInCurrentThread(final Class<T> componentToBeRetrieved, RequestInfo request,final Code<T> code) {
		provider.provideForRequest(request);
		T bean = instanceFor(componentToBeRetrieved, currentContainer);
		code.execute(bean);
		return bean;
	}

	protected void checkSimilarity(Class<?> component, boolean shouldBeTheSame, Object firstInstance,
			Object secondInstance) {

		if (shouldBeTheSame) {
			MatcherAssert.assertThat("Should be the same instance for " + component.getName(), firstInstance,
					is(equalTo(secondInstance)));
		} else {
			MatcherAssert.assertThat("Should not be the same instance for " + component.getName(), firstInstance,
					is(not(equalTo(secondInstance))));
		}
	}

	protected void checkAvailabilityFor(boolean shouldBeTheSame, Collection<Class<?>> components) {
		for (Class<?> component : components) {
			checkAvailabilityFor(shouldBeTheSame, component);
		}
	}

	@Test
	public void shoudRegisterResourcesInRouter() {
		Router router = getFromContainer(Router.class);
		Matcher<Iterable<? super Route>> hasItem = hasItem(canHandle(ControllerInTheClasspath.class,
				ControllerInTheClasspath.class.getDeclaredMethods()[0]));
		assertThat(router.allRoutes(), hasItem);
	}

	@Test
	public void shoudRegisterConvertersInConverters() {
		executeInsideRequest(new WhatToDo<Converters>() {
			@Override
			public Converters execute(RequestInfo request, final int counter) {
				provider.provideForRequest(request);
				Converters converters = currentContainer.instanceFor(Converters.class);
				Converter<?> converter = converters.to(Void.class);
				assertThat(converter, is(instanceOf(ConverterInTheClasspath.class)));
				return null;
			}
		});
	}

	/**
	 * Check if exist {@link Deserializer} registered in VRaptor for determined Content-Types.
	 */
	@Test
	public void shouldReturnAllDefaultDeserializers() {

		executeInsideRequest(new WhatToDo<Void>(){

			@Override
			public Void execute(RequestInfo request, int counter) {

				provider.provideForRequest(request);
				Deserializers deserializers = currentContainer.instanceFor(Deserializers.class);
				List<String> types = asList("application/json", "json", "application/xml",
					"xml", "text/xml", "application/x-www-form-urlencoded");

				for (String type : types) {
					assertThat("deserializer not found: " + type,
							deserializers.deserializerFor(type, currentContainer), is(notNullValue()));
				}
				return null;
			}
		});
	}

	@Test
	public void shouldReturnAllDefaultConverters() {
		executeInsideRequest(new WhatToDo<Void>(){
			@Override
			public Void execute(RequestInfo request, int counter) {
				provider.provideForRequest(request);

				Converters converters = currentContainer.instanceFor(Converters.class);

				final HashMap<Class<?>, Class<?>> EXPECTED_CONVERTERS = new HashMap<Class<?>, Class<?>>() {
					{
						put(int.class, PrimitiveIntConverter.class);
						put(long.class, PrimitiveLongConverter.class);
						put(short.class, PrimitiveShortConverter.class);
						put(byte.class, PrimitiveByteConverter.class);
						put(double.class, LocaleBasedPrimitiveDoubleConverter.class);
						put(float.class, LocaleBasedPrimitiveFloatConverter.class);
						put(boolean.class, PrimitiveBooleanConverter.class);
						put(Integer.class, IntegerConverter.class);
						put(Long.class, LongConverter.class);
						put(Short.class, ShortConverter.class);
						put(Byte.class, ByteConverter.class);
						put(Double.class, LocaleBasedDoubleConverter.class);
						put(Float.class, LocaleBasedFloatConverter.class);
						put(Boolean.class, BooleanConverter.class);
						put(Calendar.class, LocaleBasedCalendarConverter.class);
						put(Date.class, LocaleBasedDateConverter.class);
						put(Enum.class, EnumConverter.class);
					}
					private static final long serialVersionUID = 8559316558416038474L;
				};

				for (Entry<Class<?>, Class<?>> entry : EXPECTED_CONVERTERS.entrySet()) {
					Converter<?> converter = converters.to((Class<?>) entry.getKey());
					assertThat(converter, is(instanceOf(entry.getValue())));
				}
				return null;
			}
		});
	}

	@Test
	public void shoudRegisterInterceptorsInInterceptorRegistry() {
		InterceptorRegistry registry = getFromContainer(InterceptorRegistry.class);
		assertThat(registry.all(), hasOneCopyOf(InterceptorInTheClasspath.class));
	}

	protected <T> T instanceFor(final Class<T> component, Container container) {
		return container.instanceFor(component);
	}
}
