package com.github.cabutchei.rsp.server.eap.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;

public class JBossRuntimeAdapterFactoryTest {

	@Test
	public void testGetAdapterUsesLoadAdapterFirst() {
		JBossRuntimeAdapterFactory factory = new JBossRuntimeAdapterFactory();
		IRuntime runtime = mock(IRuntime.class);
		org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime nativeRuntime =
				mock(org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime.class);

		when(runtime.loadAdapter(org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime.class)).thenReturn(nativeRuntime);

		IJBossRuntimeAdapter adapter = factory.getAdapter(runtime, IJBossRuntimeAdapter.class);

		assertNotNull(adapter);
	}

	@Test
	public void testGetAdapterFallsBackToGetAdapterWhenLoadAdapterReturnsNull() {
		JBossRuntimeAdapterFactory factory = new JBossRuntimeAdapterFactory();
		IRuntime runtime = mock(IRuntime.class);
		org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime nativeRuntime =
				mock(org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime.class);

		when(runtime.loadAdapter(org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime.class)).thenReturn(null);
		when(runtime.getAdapter(org.jboss.ide.eclipse.as.core.server.IJBossServerRuntime.class)).thenReturn(nativeRuntime);

		IJBossRuntimeAdapter adapter = factory.getAdapter(runtime, IJBossRuntimeAdapter.class);

		assertNotNull(adapter);
	}

	@Test
	public void testGetAdapterReturnsNullForUnsupportedInputs() {
		JBossRuntimeAdapterFactory factory = new JBossRuntimeAdapterFactory();
		IRuntime runtime = mock(IRuntime.class);

		assertNull(factory.getAdapter(null, IJBossRuntimeAdapter.class));
		assertNull(factory.getAdapter("not-a-runtime", IJBossRuntimeAdapter.class));
		assertNull(factory.getAdapter(runtime, String.class));
		assertNull(factory.getAdapter(runtime, IJBossRuntimeAdapter.class));
		assertEquals(1, factory.getAdapterList().length);
		assertEquals(IJBossRuntimeAdapter.class, factory.getAdapterList()[0]);
	}
}
