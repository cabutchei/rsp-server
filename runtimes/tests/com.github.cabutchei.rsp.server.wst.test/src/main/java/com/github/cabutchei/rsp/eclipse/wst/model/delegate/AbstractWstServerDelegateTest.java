package com.github.cabutchei.rsp.eclipse.wst.model.delegate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.eclipse.debug.core.DebugException;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.eclipse.debug.core.IStreamListener;
import com.github.cabutchei.rsp.eclipse.debug.core.model.IProcess;
import com.github.cabutchei.rsp.eclipse.debug.core.model.IStreamMonitor;
import com.github.cabutchei.rsp.eclipse.debug.core.model.IStreamsProxy;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstPublishListener;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerControl;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.ServerEvent;

@SuppressWarnings("restriction")
public class AbstractWstServerDelegateTest {

	@Test
	public void testConstructorRegistersListenersAndSeedsInitialRunState() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STARTING);

		verify(fixture.wstServerControl).addServerListener(fixture.serverListenerCaptor.capture());
		verify(fixture.wstServerControl).addPublishListener(fixture.publishListenerCaptor.capture());
		verifyZeroInteractions(fixture.serverModel);
		assertEquals(IServerDelegate.STATE_STARTING, fixture.delegate.getServerRunState());
	}

	@Test
	public void testStateListenerIgnoresEventsWithoutBothRequiredKindBits() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STARTING);
		verify(fixture.wstServerControl).addServerListener(fixture.serverListenerCaptor.capture());

		fixture.serverListenerCaptor.getValue().serverChanged(new ServerEvent(ServerEvent.SERVER_CHANGE,
				fixture.wstServerControl, IServerDelegate.STATE_STARTED, IServerDelegate.PUBLISH_STATE_NONE, false));
		fixture.serverListenerCaptor.getValue().serverChanged(new ServerEvent(ServerEvent.STATE_CHANGE,
				fixture.wstServerControl, IServerDelegate.STATE_STARTED, IServerDelegate.PUBLISH_STATE_NONE, false));
		fixture.serverListenerCaptor.getValue().serverChanged(new ServerEvent(ServerEvent.MODULE_CHANGE,
				fixture.wstServerControl, IServerDelegate.STATE_STARTED, IServerDelegate.PUBLISH_STATE_NONE, false));
		fixture.serverListenerCaptor.getValue().serverChanged(null);

		assertEquals(IServerDelegate.STATE_STARTING, fixture.delegate.getServerRunState());
		verifyZeroInteractions(fixture.serverModel);
	}

	@Test
	public void testStateListenerSyncsRunStateAndFiresServerModelNotification() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STARTING);
		verify(fixture.wstServerControl).addServerListener(fixture.serverListenerCaptor.capture());
		when(fixture.wstServerControl.getServerRunState()).thenReturn(IServerDelegate.STATE_STARTED);

		fixture.serverListenerCaptor.getValue().serverChanged(new ServerEvent(
				ServerEvent.STATE_CHANGE | ServerEvent.SERVER_CHANGE, fixture.wstServerControl,
				IServerDelegate.STATE_STARTED, IServerDelegate.PUBLISH_STATE_NONE, false));

		ArgumentCaptor<ServerState> stateCaptor = ArgumentCaptor.forClass(ServerState.class);
		verify(fixture.serverModel).fireServerStateChanged(eq(fixture.wstServerControl), stateCaptor.capture());
		assertEquals(IServerDelegate.STATE_STARTED, fixture.delegate.getServerRunState());
		assertEquals(IServerDelegate.STATE_STARTED, stateCaptor.getValue().getState());
		assertEquals(fixture.wstServerControl.getId(), stateCaptor.getValue().getServer().getId());
	}

	@Test
	public void testPublishListenerForwardsLifecycleEventsToServerModel() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STOPPED);
		verify(fixture.wstServerControl).addPublishListener(fixture.publishListenerCaptor.capture());

		IWstPublishListener publishListener = fixture.publishListenerCaptor.getValue();
		publishListener.publishStarted();
		publishListener.publishFinished();

		verify(fixture.serverModel).fireServerPublishStarted(fixture.wstServerControl);
		verify(fixture.serverModel).fireServerPublishFinished(fixture.wstServerControl);
	}

	@Test
	public void testHandleLaunchReadyTagsProcessesAndForwardsBothStreams() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STOPPED);
		TestProcess process = new TestProcess();
		TestLaunch launch = new TestLaunch(process);

		fixture.delegate.handleLaunchReadyForTest(launch);

		String processId = fixture.delegate.processId(process);
		assertNotNull(processId);
		assertEquals(1, process.getOutputMonitor().listenerCount());
		assertEquals(1, process.getErrorMonitor().listenerCount());

		process.getOutputMonitor().emit("stdout line");
		process.getErrorMonitor().emit("stderr line");

		verify(fixture.serverModel).fireServerStreamAppended(eq(fixture.wstServerControl), eq(processId),
				eq(ServerManagementAPIConstants.STREAM_TYPE_SYSOUT), eq("stdout line"));
		verify(fixture.serverModel).fireServerStreamAppended(eq(fixture.wstServerControl), eq(processId),
				eq(ServerManagementAPIConstants.STREAM_TYPE_SYSERR), eq("stderr line"));
	}

	@Test
	public void testAttachLaunchStreamListenersFromMonitorSkipsAlreadyTaggedProcesses() {
		Fixture fixture = new Fixture(IServerDelegate.STATE_STOPPED);
		TestProcess process = new TestProcess();
		TestLaunch launch = new TestLaunch(process);

		fixture.delegate.handleLaunchReadyForTest(launch);
		String processId = fixture.delegate.processId(process);
		assertNotNull(processId);

		fixture.delegate.attachLaunchStreamListenersFromMonitor(launch);

		assertEquals(1, process.getOutputMonitor().listenerCount());
		assertEquals(1, process.getErrorMonitor().listenerCount());

		process.getOutputMonitor().emit("stdout after monitor attach");
		process.getErrorMonitor().emit("stderr after monitor attach");

		verify(fixture.serverModel, times(1)).fireServerStreamAppended(eq(fixture.wstServerControl), eq(processId),
				eq(ServerManagementAPIConstants.STREAM_TYPE_SYSOUT), eq("stdout after monitor attach"));
		verify(fixture.serverModel, times(1)).fireServerStreamAppended(eq(fixture.wstServerControl), eq(processId),
				eq(ServerManagementAPIConstants.STREAM_TYPE_SYSERR), eq("stderr after monitor attach"));
	}

	private static final class Fixture {
		private final IWstServerControl wstServerControl = mock(IWstServerControl.class);
		private final IServerManagementModel managementModel = mock(IServerManagementModel.class);
		private final IServerModel serverModel = mock(IServerModel.class);
		private final IServerType serverType = mock(IServerType.class);
		private final ArgumentCaptor<com.github.cabutchei.rsp.server.spi.servertype.IServerListener> serverListenerCaptor =
				ArgumentCaptor.forClass(com.github.cabutchei.rsp.server.spi.servertype.IServerListener.class);
		private final ArgumentCaptor<IWstPublishListener> publishListenerCaptor =
				ArgumentCaptor.forClass(IWstPublishListener.class);
		private final TestWstServerDelegate delegate;

		private Fixture(int initialRunState) {
			when(wstServerControl.getId()).thenReturn("test-server");
			when(wstServerControl.getServerManagementModel()).thenReturn(managementModel);
			when(wstServerControl.getServerModel()).thenReturn(serverModel);
			when(wstServerControl.getServerType()).thenReturn(serverType);
			when(wstServerControl.getServerRunState()).thenReturn(initialRunState);
			when(serverType.getId()).thenReturn("test.type");
			when(serverType.getName()).thenReturn("Test Type");
			when(serverType.getDescription()).thenReturn("Test description");
			when(managementModel.getServerModel()).thenReturn(serverModel);
			delegate = new TestWstServerDelegate(wstServerControl);
		}
	}

	private static final class TestWstServerDelegate extends AbstractWstServerDelegate {
		private TestWstServerDelegate(IServer server) {
			super(server);
		}

		@Override
		protected boolean registerAsProcessListener() {
			return false;
		}

		private void handleLaunchReadyForTest(ILaunch launch) {
			handleLaunchReady(launch);
		}

		private String processId(IProcess process) {
			return getProcessId(process);
		}
	}

	private static final class TestLaunch implements ILaunch {
		private final List<IProcess> processes = new ArrayList<>();
		private final Map<String, String> attributes = new HashMap<>();

		private TestLaunch(IProcess... initialProcesses) {
			for (IProcess process : initialProcesses) {
				addProcess(process);
			}
		}

		@Override
		public Object[] getChildren() {
			return getProcesses();
		}

		@Override
		public IProcess[] getProcesses() {
			return processes.toArray(new IProcess[processes.size()]);
		}

		@Override
		public void addProcess(IProcess process) {
			processes.add(process);
		}

		@Override
		public void removeProcess(IProcess process) {
			processes.remove(process);
		}

		@Override
		public String getLaunchMode() {
			return "run";
		}

		@Override
		public void setAttribute(String key, String value) {
			attributes.put(key, value);
		}

		@Override
		public String getAttribute(String key) {
			return attributes.get(key);
		}

		@Override
		public boolean hasChildren() {
			return !processes.isEmpty();
		}

		@Override
		public boolean canTerminate() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public void terminate() throws DebugException {
		}
	}

	private static final class TestProcess implements IProcess {
		private final Map<String, String> attributes = new HashMap<>();
		private final TestStreamMonitor outputMonitor = new TestStreamMonitor();
		private final TestStreamMonitor errorMonitor = new TestStreamMonitor();
		private final IStreamsProxy streamsProxy = new TestStreamsProxy(outputMonitor, errorMonitor);

		@Override
		public String getLabel() {
			return "test-process";
		}

		@Override
		public ILaunch getLaunch() {
			return null;
		}

		@Override
		public IStreamsProxy getStreamsProxy() {
			return streamsProxy;
		}

		@Override
		public void setAttribute(String key, String value) {
			attributes.put(key, value);
		}

		@Override
		public String getAttribute(String key) {
			return attributes.get(key);
		}

		@Override
		public int getExitValue() throws DebugException {
			return 0;
		}

		@Override
		public boolean canTerminate() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public void terminate() throws DebugException {
		}

		private TestStreamMonitor getOutputMonitor() {
			return outputMonitor;
		}

		private TestStreamMonitor getErrorMonitor() {
			return errorMonitor;
		}
	}

	private static final class TestStreamsProxy implements IStreamsProxy {
		private final IStreamMonitor outputMonitor;
		private final IStreamMonitor errorMonitor;

		private TestStreamsProxy(IStreamMonitor outputMonitor, IStreamMonitor errorMonitor) {
			this.outputMonitor = outputMonitor;
			this.errorMonitor = errorMonitor;
		}

		@Override
		public IStreamMonitor getErrorStreamMonitor() {
			return errorMonitor;
		}

		@Override
		public IStreamMonitor getOutputStreamMonitor() {
			return outputMonitor;
		}

		@Override
		public void write(String input) throws IOException {
		}
	}

	private static final class TestStreamMonitor implements IStreamMonitor {
		private final List<IStreamListener> listeners = new ArrayList<>();
		private final StringBuilder contents = new StringBuilder();

		@Override
		public void addListener(IStreamListener listener) {
			listeners.add(listener);
		}

		@Override
		public String getContents() {
			return contents.toString();
		}

		@Override
		public void removeListener(IStreamListener listener) {
			listeners.remove(listener);
		}

		private void emit(String text) {
			contents.append(text);
			for (IStreamListener listener : new ArrayList<>(listeners)) {
				listener.streamAppended(text, this);
			}
		}

		private int listenerCount() {
			return listeners.size();
		}
	}
}
