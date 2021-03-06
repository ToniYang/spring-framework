/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.sockjs.transport.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.AbstractHttpRequestTests;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.sockjs.frame.SockJsFrame;
import org.springframework.web.socket.sockjs.frame.SockJsFrameFormat;
import org.springframework.web.socket.sockjs.transport.session.AbstractSockJsSession;
import org.springframework.web.socket.sockjs.transport.session.PollingSockJsSession;
import org.springframework.web.socket.sockjs.transport.session.StreamingSockJsSession;
import org.springframework.web.socket.sockjs.transport.session.StubSockJsServiceConfig;

/**
 * Test fixture for {@link AbstractHttpSendingTransportHandler} and sub-classes.
 *
 * @author Rossen Stoyanchev
 */
public class HttpSendingTransportHandlerTests  extends AbstractHttpRequestTests {

	private WebSocketHandler webSocketHandler;

	private StubSockJsServiceConfig sockJsConfig;

	private TaskScheduler taskScheduler;


	@Override
	@Before
	public void setUp() {
		super.setUp();

		this.webSocketHandler = mock(WebSocketHandler.class);
		this.taskScheduler = mock(TaskScheduler.class);

		this.sockJsConfig = new StubSockJsServiceConfig();
		this.sockJsConfig.setTaskScheduler(this.taskScheduler);
	}

	@Test
	public void handleRequestXhr() throws Exception {

		XhrPollingTransportHandler transportHandler = new XhrPollingTransportHandler();
		transportHandler.initialize(this.sockJsConfig);

		AbstractSockJsSession session = transportHandler.createSession("1", this.webSocketHandler, null);
		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals("application/javascript;charset=UTF-8", this.response.getHeaders().getContentType().toString());
		assertEquals("o\n", this.servletResponse.getContentAsString());
		assertFalse("Polling request should complete after open frame", this.servletRequest.isAsyncStarted());
		verify(this.webSocketHandler).afterConnectionEstablished(session);

		resetRequestAndResponse();
		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertTrue("Polling request should remain open", this.servletRequest.isAsyncStarted());
		verify(this.taskScheduler).schedule(any(Runnable.class), any(Date.class));

		resetRequestAndResponse();
		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertFalse("Request should have been rejected", this.servletRequest.isAsyncStarted());
		assertEquals("c[2010,\"Another connection still open\"]\n", this.servletResponse.getContentAsString());
	}

	@Test
	public void jsonpTransport() throws Exception {

		JsonpPollingTransportHandler transportHandler = new JsonpPollingTransportHandler();
		transportHandler.initialize(this.sockJsConfig);
		PollingSockJsSession session = transportHandler.createSession("1", this.webSocketHandler, null);

		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals(500, this.servletResponse.getStatus());
		assertEquals("\"callback\" parameter required", this.servletResponse.getContentAsString());

		resetRequestAndResponse();
		this.servletRequest.setQueryString("c=callback");
		this.servletRequest.addParameter("c", "callback");
		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals("application/javascript;charset=UTF-8", this.response.getHeaders().getContentType().toString());
		assertFalse("Polling request should complete after open frame", this.servletRequest.isAsyncStarted());
		verify(this.webSocketHandler).afterConnectionEstablished(session);
	}

	@Test
	public void handleRequestXhrStreaming() throws Exception {

		XhrStreamingTransportHandler transportHandler = new XhrStreamingTransportHandler();
		transportHandler.initialize(this.sockJsConfig);
		AbstractSockJsSession session = transportHandler.createSession("1", this.webSocketHandler, null);

		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals("application/javascript;charset=UTF-8", this.response.getHeaders().getContentType().toString());
		assertTrue("Streaming request not started", this.servletRequest.isAsyncStarted());
		verify(this.webSocketHandler).afterConnectionEstablished(session);
	}

	@Test
	public void htmlFileTransport() throws Exception {

		HtmlFileTransportHandler transportHandler = new HtmlFileTransportHandler();
		transportHandler.initialize(this.sockJsConfig);
		StreamingSockJsSession session = transportHandler.createSession("1", this.webSocketHandler, null);

		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals(500, this.servletResponse.getStatus());
		assertEquals("\"callback\" parameter required", this.servletResponse.getContentAsString());

		resetRequestAndResponse();
		this.servletRequest.setQueryString("c=callback");
		this.servletRequest.addParameter("c", "callback");
		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals("text/html;charset=UTF-8", this.response.getHeaders().getContentType().toString());
		assertTrue("Streaming request not started", this.servletRequest.isAsyncStarted());
		verify(this.webSocketHandler).afterConnectionEstablished(session);
	}

	@Test
	public void eventSourceTransport() throws Exception {

		EventSourceTransportHandler transportHandler = new EventSourceTransportHandler();
		transportHandler.initialize(this.sockJsConfig);
		StreamingSockJsSession session = transportHandler.createSession("1", this.webSocketHandler, null);

		transportHandler.handleRequest(this.request, this.response, this.webSocketHandler, session);

		assertEquals("text/event-stream;charset=UTF-8", this.response.getHeaders().getContentType().toString());
		assertTrue("Streaming request not started", this.servletRequest.isAsyncStarted());
		verify(this.webSocketHandler).afterConnectionEstablished(session);
	}

	@Test
	public void frameFormats() throws Exception {

		this.servletRequest.setQueryString("c=callback");
		this.servletRequest.addParameter("c", "callback");

		SockJsFrame frame = SockJsFrame.openFrame();

		SockJsFrameFormat format = new XhrPollingTransportHandler().getFrameFormat(this.request);
		SockJsFrame formatted = format.format(frame);
		assertEquals(frame.getContent() + "\n", formatted.getContent());

		format = new XhrStreamingTransportHandler().getFrameFormat(this.request);
		formatted = format.format(frame);
		assertEquals(frame.getContent() + "\n", formatted.getContent());

		format = new HtmlFileTransportHandler().getFrameFormat(this.request);
		formatted = format.format(frame);
		assertEquals("<script>\np(\"" + frame.getContent() + "\");\n</script>\r\n", formatted.getContent());

		format = new EventSourceTransportHandler().getFrameFormat(this.request);
		formatted = format.format(frame);
		assertEquals("data: " + frame.getContent() + "\r\n\r\n", formatted.getContent());

		format = new JsonpPollingTransportHandler().getFrameFormat(this.request);
		formatted = format.format(frame);
		assertEquals("callback(\"" + frame.getContent() + "\");\r\n", formatted.getContent());
	}

}
