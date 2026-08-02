package com.github.cabutchei.rsp.server.eap.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerControl;
import com.github.cabutchei.rsp.server.eap.adapter.IJBossRuntimeAdapter;
import com.github.cabutchei.rsp.server.eap.servertype.IEapServerAttributes;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;

@SuppressWarnings("restriction")
public class EapServerDelegateUpdateServerTest {

	@Test
	public void testUpdateServerUsesDefaultRestartPatternAndWiresRuntimeWorkingCopy() throws Exception {
		Fixture fixture = new Fixture();
		IServerWorkingCopy workingCopy = mock(IServerWorkingCopy.class);
		IRuntime runtime = mock(IRuntime.class);
		IRuntimeWorkingCopy runtimeWc = mock(IRuntimeWorkingCopy.class);
		IJBossRuntimeAdapter jbossRuntime = mock(IJBossRuntimeAdapter.class);
		UpdateServerResponse response = new UpdateServerResponse();

		when(workingCopy.getAttribute(IEapServerAttributes.RESTART_FILE_PATTERN, (String) null)).thenReturn(null);
		when(workingCopy.getAttribute(IEapServerAttributes.VM_INSTALL_PATH,
				IEapServerAttributes.VM_INSTALL_PATH_DEFAULT)).thenReturn(IEapServerAttributes.VM_INSTALL_PATH_DEFAULT);
		when(workingCopy.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null)).thenReturn("/tmp/eap-home");
		when(workingCopy.getAttribute(IEapServerAttributes.CONFIG_FILE,
				IEapServerAttributes.CONFIG_FILE_DEFAULT)).thenReturn("standalone-full.xml");
		when(workingCopy.getRuntime()).thenReturn(runtime);
		when(runtime.isWorkingCopy()).thenReturn(false);
		when(runtime.createWorkingCopy()).thenReturn(runtimeWc);
		when(runtimeWc.loadAdapter(IJBossRuntimeAdapter.class)).thenReturn(jbossRuntime);

		fixture.delegate.updateServer(workingCopy, response);

		verify(workingCopy).setAttribute(IEapServerAttributes.USE_DEFAULT_RESTART_FILE_PATTERN, true);
		verify(workingCopy).setAttribute(IEapServerAttributes.RESTART_FILE_PATTERN,
				IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT);
		verify(runtime).createWorkingCopy();
		verify(runtimeWc).setLocation(eq(new Path("/tmp/eap-home")));
		verify(jbossRuntime).setConfigurationFile("standalone-full.xml");
		verify(jbossRuntime).setVM(null);
		verify(workingCopy).setRuntime(runtimeWc);
		assertEquals(null, response.getValidation().getStatus());
	}

	@Test
	public void testUpdateServerPreservesCustomRestartPattern() throws Exception {
		Fixture fixture = new Fixture();
		IServerWorkingCopy workingCopy = mock(IServerWorkingCopy.class);
		IRuntimeWorkingCopy runtimeWc = mock(IRuntimeWorkingCopy.class);
		IJBossRuntimeAdapter jbossRuntime = mock(IJBossRuntimeAdapter.class);
		UpdateServerResponse response = new UpdateServerResponse();

		when(workingCopy.getAttribute(IEapServerAttributes.RESTART_FILE_PATTERN, (String) null))
				.thenReturn(".*\\.dodeploy$");
		when(workingCopy.getAttribute(IEapServerAttributes.VM_INSTALL_PATH,
				IEapServerAttributes.VM_INSTALL_PATH_DEFAULT)).thenReturn(IEapServerAttributes.VM_INSTALL_PATH_DEFAULT);
		when(workingCopy.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null)).thenReturn(null);
		when(workingCopy.getAttribute(IEapServerAttributes.CONFIG_FILE,
				IEapServerAttributes.CONFIG_FILE_DEFAULT)).thenReturn(IEapServerAttributes.CONFIG_FILE_DEFAULT);
		when(workingCopy.getRuntime()).thenReturn(runtimeWc);
		when(runtimeWc.isWorkingCopy()).thenReturn(true);
		when(runtimeWc.loadAdapter(IJBossRuntimeAdapter.class)).thenReturn(jbossRuntime);

		fixture.delegate.updateServer(workingCopy, response);

		verify(workingCopy).setAttribute(IEapServerAttributes.USE_DEFAULT_RESTART_FILE_PATTERN, false);
		verify(workingCopy, never()).setAttribute(eq(IEapServerAttributes.RESTART_FILE_PATTERN), any(String.class));
		verify(runtimeWc, never()).createWorkingCopy();
		verify(runtimeWc, never()).setLocation(any(Path.class));
		verify(jbossRuntime).setConfigurationFile(IEapServerAttributes.CONFIG_FILE_DEFAULT);
		verify(jbossRuntime).setVM(null);
		verify(workingCopy).setRuntime(runtimeWc);
		assertEquals(null, response.getValidation().getStatus());
	}

	@Test
	public void testUpdateServerSetsValidationErrorWhenRuntimeCannotAdapt() {
		Fixture fixture = new Fixture();
		IServerWorkingCopy workingCopy = mock(IServerWorkingCopy.class);
		IRuntimeWorkingCopy runtimeWc = mock(IRuntimeWorkingCopy.class);
		UpdateServerResponse response = new UpdateServerResponse();

		when(workingCopy.getAttribute(IEapServerAttributes.RESTART_FILE_PATTERN, (String) null)).thenReturn(null);
		when(workingCopy.getAttribute(IEapServerAttributes.VM_INSTALL_PATH,
				IEapServerAttributes.VM_INSTALL_PATH_DEFAULT)).thenReturn(IEapServerAttributes.VM_INSTALL_PATH_DEFAULT);
		when(workingCopy.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null)).thenReturn(null);
		when(workingCopy.getAttribute(IEapServerAttributes.CONFIG_FILE,
				IEapServerAttributes.CONFIG_FILE_DEFAULT)).thenReturn(IEapServerAttributes.CONFIG_FILE_DEFAULT);
		when(workingCopy.getRuntime()).thenReturn(runtimeWc);
		when(runtimeWc.isWorkingCopy()).thenReturn(true);
		when(runtimeWc.loadAdapter(IJBossRuntimeAdapter.class)).thenReturn(null);

		fixture.delegate.updateServer(workingCopy, response);

		assertNotNull(response.getValidation().getStatus());
		assertEquals(com.github.cabutchei.rsp.api.dao.Status.ERROR,
				response.getValidation().getStatus().getSeverity());
		assertNotNull(response.getValidation().getStatus().getMessage());
		verify(workingCopy, never()).setRuntime(runtimeWc);
	}

	private static final class Fixture {
		private final IWstServerControl wstServerControl = mock(IWstServerControl.class);
		private final IServerManagementModel managementModel = mock(IServerManagementModel.class);
		private final IServerModel serverModel = mock(IServerModel.class);
		private final IServerType serverType = mock(IServerType.class);
		private final TestEapServerDelegate delegate;

		private Fixture() {
			when(wstServerControl.getId()).thenReturn("test-server");
			when(wstServerControl.getServerManagementModel()).thenReturn(managementModel);
			when(wstServerControl.getServerModel()).thenReturn(serverModel);
			when(wstServerControl.getServerType()).thenReturn(serverType);
			when(wstServerControl.getServerRunState()).thenReturn(IServerDelegate.STATE_STOPPED);
			when(serverType.getId()).thenReturn(IEapServerAttributes.EAP_70_SERVER_TYPE);
			when(serverType.getName()).thenReturn("JBoss EAP 7.0");
			when(serverType.getDescription()).thenReturn("JBoss EAP");
			when(managementModel.getServerModel()).thenReturn(serverModel);
			delegate = new TestEapServerDelegate(wstServerControl);
		}
	}

	private static final class TestEapServerDelegate extends EapServerDelegate {
		private TestEapServerDelegate(IWstServerControl server) {
			super(server);
		}

		@Override
		protected boolean registerAsProcessListener() {
			return false;
		}
	}
}
