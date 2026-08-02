package com.github.cabutchei.rsp.eclipse.debug.core.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.cabutchei.rsp.eclipse.debug.core.DebugEvent;
import com.github.cabutchei.rsp.eclipse.debug.core.IDebugEventSetListener;
import com.github.cabutchei.rsp.eclipse.debug.core.Launch;
import com.github.cabutchei.rsp.launching.RuntimeProcessEventManager;

public class RuntimeProcessTest {

	@Test
	public void testRuntimeProcessFiresCreateAndTerminateEvents() throws Exception {
		RuntimeProcessEventManager eventManager = RuntimeProcessEventManager.getDefault();
		RecordingDebugEventListener listener = new RecordingDebugEventListener();
		eventManager.addListener(listener);
		try {
			Launch launch = new Launch(null, "run", null);
			ControllableProcess process = new ControllableProcess();

			RuntimeProcess runtimeProcess = new RuntimeProcess(launch, process, "test-process", null);

			DebugEvent createEvent = listener.awaitEvent(DebugEvent.CREATE);
			assertSame(runtimeProcess, createEvent.getSource());
			assertEquals(1, launch.getProcesses().length);
			assertSame(runtimeProcess, launch.getProcesses()[0]);

			process.terminate(23);

			DebugEvent terminateEvent = listener.awaitEvent(DebugEvent.TERMINATE);
			assertSame(runtimeProcess, terminateEvent.getSource());
			assertTrue(runtimeProcess.isTerminated());
			assertEquals(23, runtimeProcess.getExitValue());
		} finally {
			eventManager.removeListener(listener);
		}
	}

	private static final class RecordingDebugEventListener implements IDebugEventSetListener {
		private final List<DebugEvent> events = new ArrayList<>();
		private final CountDownLatch createLatch = new CountDownLatch(1);
		private final CountDownLatch terminateLatch = new CountDownLatch(1);

		@Override
		public synchronized void handleDebugEvents(DebugEvent[] debugEvents) {
			if (debugEvents == null) {
				return;
			}
			for (DebugEvent event : debugEvents) {
				events.add(event);
				if (event.getKind() == DebugEvent.CREATE) {
					createLatch.countDown();
				}
				if (event.getKind() == DebugEvent.TERMINATE) {
					terminateLatch.countDown();
				}
			}
		}

		private DebugEvent awaitEvent(int kind) throws InterruptedException {
			CountDownLatch latch = kind == DebugEvent.CREATE ? createLatch : terminateLatch;
			assertTrue("Timed out waiting for debug event kind " + kind, latch.await(5, TimeUnit.SECONDS));
			synchronized (this) {
				for (DebugEvent event : events) {
					if (event.getKind() == kind) {
						return event;
					}
				}
			}
			throw new AssertionError("Missing debug event kind " + kind);
		}
	}

	private static final class ControllableProcess extends Process {
		private final CountDownLatch terminationLatch = new CountDownLatch(1);
		private final InputStream inputStream = new ByteArrayInputStream(new byte[0]);
		private final InputStream errorStream = new ByteArrayInputStream(new byte[0]);
		private final OutputStream outputStream = new ByteArrayOutputStream();
		private volatile boolean terminated;
		private volatile int exitCode;

		@Override
		public OutputStream getOutputStream() {
			return outputStream;
		}

		@Override
		public InputStream getInputStream() {
			return inputStream;
		}

		@Override
		public InputStream getErrorStream() {
			return errorStream;
		}

		@Override
		public int waitFor() throws InterruptedException {
			terminationLatch.await();
			return exitCode;
		}

		@Override
		public int exitValue() {
			if (!terminated) {
				throw new IllegalThreadStateException("Process still running");
			}
			return exitCode;
		}

		@Override
		public void destroy() {
			terminate(exitCode);
		}

		private void terminate(int code) {
			exitCode = code;
			terminated = true;
			terminationLatch.countDown();
		}
	}
}
