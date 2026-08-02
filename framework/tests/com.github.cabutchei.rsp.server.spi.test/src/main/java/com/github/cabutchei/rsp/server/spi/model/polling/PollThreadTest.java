/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.spi.model.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.server.spi.model.polling.IServerStatePoller.CANCELATION_CAUSE;
import com.github.cabutchei.rsp.server.spi.model.polling.IServerStatePoller.SERVER_STATE;
import com.github.cabutchei.rsp.server.spi.model.polling.IServerStatePoller.TIMEOUT_BEHAVIOR;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class PollThreadTest {

	private static final int TIMEOUT = 20000;
	private static final int NO_POLLER_SLEEP = -1;
	private static final long POLLER_SLEEP = 10000;
	private static final long RESULT_TIMEOUT = 4000;

	private IServerDelegate delegate;
	private IServerStatePoller poller;
	private VerifiablePollResultListener resultListener;
	private PollThread pollThread;
	private IServer server;

	@Before
	public void before() throws Exception {
		this.delegate = mockServerDelegate();
		this.server = mockServer(delegate);
		this.poller = mockPoller(SERVER_STATE.UP);
		this.resultListener = new VerifiablePollResultListener();
		this.pollThread = new PollThread(SERVER_STATE.UP, poller, resultListener, server, TIMEOUT);

		doReturn(pollThread).when(delegate).getSharedData(PollThreadUtils.PROP_POLL_THREAD_KEY);
	}

	@After
	public void after() {
		this.pollThread.cancel();
	}
	
	@Test
	public void canGetPollThread() {
		PollThreadUtils.getPollThread(delegate);

		verify(delegate, times(1)).getSharedData(eq(PollThreadUtils.PROP_POLL_THREAD_KEY));
	}
	
	@Test
	public void canSetPollThread() {
		PollThreadUtils.savePollThread(pollThread, delegate);

		verify(delegate, times(1)).putSharedData(eq(PollThreadUtils.PROP_POLL_THREAD_KEY), eq(pollThread));
	}

	@Test
	public void stopPollingDoesNotCancelThreadThatsNotRunning() {
		PollThread pollThreadSpy = spy(pollThread);

		PollThreadUtils.stopPolling(pollThreadSpy);
		verify(pollThreadSpy, never()).cancel();
	}

	@Test
	public void stopPollingCancelsRunningThread() throws Exception {
		TestPollThread pollThreadSpy = runningPollThread();

		PollThreadUtils.stopPolling(pollThreadSpy);
		pollThreadSpy.join(RESULT_TIMEOUT);

		assertThat(pollThreadSpy.wasCancelled()).isTrue();
	}

	@Test
	public void pollServerSavesNewThread() {
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);

		verify(delegate).putSharedData(eq(PollThreadUtils.PROP_POLL_THREAD_KEY), eq(pollThread));

		pollThread.cancel();
	}

	@Test
	public void pollServerCancelsCurrentThread() throws Exception {
		TestPollThread pollThreadSpy = runningPollThread();

		PollThread newPollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, pollThreadSpy, resultListener, TIMEOUT);
		pollThreadSpy.join(RESULT_TIMEOUT);

		assertThat(pollThreadSpy.wasCancelled()).isTrue();

		newPollThread.cancel();
	}

	@Test
	public void pollServerCreatesNewThread() throws Exception {
		TestPollThread pollThreadSpy = runningPollThread();

		PollThread newPollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, pollThreadSpy, resultListener, TIMEOUT);
		pollThreadSpy.join(RESULT_TIMEOUT);

		assertThat(pollThreadSpy).isNotEqualTo(newPollThread);

		newPollThread.cancel();
	}

	@Test
	public void notifiesOppositeStateIfNoPoller() throws InterruptedException {
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, null, resultListener, TIMEOUT);

		AssertedState state = resultListener.getNextNotifiedState();

		assertThat(state.isAsserted()).isFalse();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.DOWN);

		pollThread.cancel();
	}

	@Test
	public void notifiesOppositeStateIfCancelled() throws Exception {
		// given
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);
		pollThread.cancel();
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isFalse();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.DOWN);
		
		// cleanup
		pollThread.cancel();
	}

	@Test
	public void cancelsPollerIfServerStopped() throws Exception {
		// given
		IServerDelegate delegate = mockServerDelegate();
		doReturn(IServerDelegate.STATE_STOPPED).when(delegate).getServerRunState();
		IServer server = mockServer(delegate);
		IServerStatePoller poller = mockPoller(SERVER_STATE.UP);

		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);
		pollThread.join();

		// then
		verify(poller).cancel(eq(CANCELATION_CAUSE.FAILED));
		
		// cleanup
		pollThread.cancel();
	}

	@Test
	public void cancelsPollerIfPollerException() throws Exception {
		// given
		IServerStatePoller poller = mockPoller(SERVER_STATE.UP);
		when(poller.isComplete()).thenThrow(new PollingException(""));
		
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);
		pollThread.join();

		// then
		verify(poller).cancel(eq(CANCELATION_CAUSE.FAILED));

		// cleanup
		pollThread.cancel();
	}

	@Test
	public void notifiesAssertedStateIfExpectedState() throws Exception {
		// given
		IServerStatePoller poller = mockPoller(SERVER_STATE.UP);
		
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isTrue();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.UP);
		
		// cleanup
		pollThread.cancel();
	}

	@Test
	public void notifiesNonAssertedStateIfUnexpectedState() throws Exception {
		// given
		IServerStatePoller poller = mockPoller(SERVER_STATE.DOWN);
		
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, TIMEOUT);
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isFalse();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.DOWN);
		
		// cleanup
		pollThread.cancel();
	}

	@Test
	public void notifiesFailureUponTimeout() throws Exception {
		// given
		IServerStatePoller poller = spy(new DelayableFixedResponsePoller(SERVER_STATE.UNKNOWN, POLLER_SLEEP));
		doReturn(TIMEOUT_BEHAVIOR.FAIL).when(poller).getTimeoutBehavior();
		
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, 1);
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isFalse();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.DOWN);
		
		// cleanup
		pollThread.cancel();
	}


	@Test
	public void notifiesFailureUponCustomTimeout() throws Exception {
		notifiesFailureUponCustomTimeoutImpl(200);
		notifiesFailureUponCustomTimeoutImpl(600);
		notifiesFailureUponCustomTimeoutImpl(1000);
	}
	
	
	public void notifiesFailureUponCustomTimeoutImpl(int serverTimeoutVal) throws Exception {
		doReturn(serverTimeoutVal).when(server).getAttribute(DefaultServerAttributes.SERVER_TIMEOUT_STARTUP, 1);
		// given
		IServerStatePoller poller = spy(new DelayableFixedResponsePoller(SERVER_STATE.UNKNOWN, POLLER_SLEEP));
		doReturn(TIMEOUT_BEHAVIOR.FAIL).when(poller).getTimeoutBehavior();
		
		long start = System.currentTimeMillis();
		// when
		PollThread pollThread = PollThreadUtils.pollServer(1, server, SERVER_STATE.UP, poller, resultListener);
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isFalse();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.DOWN);
		// cleanup
		pollThread.cancel();
		
		long end = System.currentTimeMillis();
		long diff = end - start;
		assertThat(diff > serverTimeoutVal);
	}

	@Test
	public void notifiesSuccessUponTimeout() throws Exception {
		// given
		IServerStatePoller poller = spy(new DelayableFixedResponsePoller(SERVER_STATE.UNKNOWN, POLLER_SLEEP));
		doReturn(TIMEOUT_BEHAVIOR.SUCCEED).when(poller).getTimeoutBehavior();
		
		// when
		PollThread pollThread = PollThreadUtils.pollServer(server, SERVER_STATE.UP, poller, resultListener, 1);
		pollThread.join();

		// then
		AssertedState state = resultListener.getNextNotifiedState();
		assertThat(state.isAsserted()).isTrue();
		assertThat(state.getServerState()).isEqualTo(SERVER_STATE.UP);
		
		// cleanup
		pollThread.cancel();
	}

	private IServerDelegate mockServerDelegate() {
		return mock(IServerDelegate.class);
	}

	private IServer mockServer(IServerDelegate delegate) {
		IServer server = mock(IServer.class);
		doReturn(delegate).when(server).getDelegate();
		return server;
	}	

	private IServerStatePoller mockPoller(SERVER_STATE pollResult) throws PollingException, RequiresInfoException {
		return mockPoller(pollResult, NO_POLLER_SLEEP);
	}

	private IServerStatePoller mockPoller(SERVER_STATE pollResult, long delay) throws PollingException, RequiresInfoException {
		IServerStatePoller poller = mock(IServerStatePoller.class);
		doAnswer(new Answer<SERVER_STATE>() {

			@Override
			public SERVER_STATE answer(InvocationOnMock invocation) throws Throwable {
				if (delay > NO_POLLER_SLEEP) {
					Thread.sleep(delay);
				}
				return pollResult;
			}
		})
		.when(poller).getState();
		doReturn(true).when(poller).isComplete();
		return poller;
	}

	private TestPollThread runningPollThread() throws Exception {
		IServerStatePoller runningPoller = mock(IServerStatePoller.class);
		doReturn(false).when(runningPoller).isComplete();
		doReturn(SERVER_STATE.UNKNOWN).when(runningPoller).getState();
		TestPollThread thread = new TestPollThread(runningPoller);
		thread.start();
		Thread.sleep(150);
		return thread;
	}

	private class TestPollThread extends PollThread {
		private volatile boolean cancelled;

		private TestPollThread(IServerStatePoller poller) {
			super(SERVER_STATE.UP, poller, resultListener, server, TIMEOUT);
		}

		@Override
		public void cancel() {
			cancelled = true;
			super.cancel();
		}

		private boolean wasCancelled() {
			return cancelled;
		}
	}

	private class AssertedState {

		private boolean isAsserted;
		private SERVER_STATE state;

		private AssertedState(boolean isAsserted, SERVER_STATE state) {
			this.isAsserted = isAsserted;
			this.state = state;
		}

		public boolean isAsserted() {
			return isAsserted;
		}

		public SERVER_STATE getServerState() {
			return state;
		}
	}

	private class VerifiablePollResultListener implements IPollResultListener {

		private BlockingQueue<AssertedState> notifiedStates = new ArrayBlockingQueue<>(20);

		@Override
		public void stateAsserted(SERVER_STATE state, SERVER_STATE currentState) {
			notifiedStates.add(new AssertedState(true, currentState));
		}

		@Override
		public void stateNotAsserted(SERVER_STATE state, SERVER_STATE currentState) {
			notifiedStates.add(new AssertedState(false, currentState));
		}

		public AssertedState getNextNotifiedState() throws InterruptedException {
			return notifiedStates.poll(RESULT_TIMEOUT, TimeUnit.MILLISECONDS);
		}
	}

}
