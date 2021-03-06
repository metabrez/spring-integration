/*
 * Copyright 2002-2018 the original author or authors.
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
 */

package org.springframework.integration.rmi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.rmi.RemoteException;

import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.gateway.RequestReplyExchanger;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.remoting.RemoteLookupFailureException;
import org.springframework.remoting.rmi.RmiServiceExporter;
import org.springframework.util.SocketUtils;

/**
 * @author Mark Fisher
 * @author Gary Russell
 * @author Artem Bilan
 */
public class RmiOutboundGatewayTests {

	private static final int port = SocketUtils.findAvailableTcpPort();

	private final RmiOutboundGateway gateway =
			new RmiOutboundGateway("rmi://localhost:" + port + "/testRemoteHandler");

	private final QueueChannel output = new QueueChannel(1);

	@Before
	public void initializeGateway() {
		this.gateway.setOutputChannel(this.output);
	}

	@Before
	public void createExporter() throws RemoteException {
		RmiServiceExporter exporter = new RmiServiceExporter();
		exporter.setService(new TestExchanger());
		exporter.setServiceInterface(RequestReplyExchanger.class);
		exporter.setServiceName("testRemoteHandler");
		exporter.setRegistryPort(port);
		exporter.afterPropertiesSet();
	}


	@Test
	public void serializablePayload() {
		gateway.handleMessage(new GenericMessage<>("test"));
		Message<?> replyMessage = output.receive(0);
		assertNotNull(replyMessage);
		assertEquals("TEST", replyMessage.getPayload());
	}

	@Test
	public void failedMessage() {
		GenericMessage<String> message = new GenericMessage<>("fail");
		try {
			gateway.handleMessage(message);
			fail("Exception expected");
		}
		catch (MessagingException e) {
			assertSame(message, e.getFailedMessage());
			assertEquals("bar", ((MessagingException) e.getCause()).getFailedMessage().getPayload());
		}
	}

	@Test
	public void serializableAttribute() {
		Message<String> requestMessage = MessageBuilder.withPayload("test")
				.setHeader("testAttribute", "foo").build();
		gateway.handleMessage(requestMessage);
		Message<?> replyMessage = output.receive(0);
		assertNotNull(replyMessage);
		assertEquals("foo", replyMessage.getHeaders().get("testAttribute"));
	}

	@Test(expected = MessageHandlingException.class)
	public void nonSerializablePayload() {
		NonSerializableTestObject payload = new NonSerializableTestObject();
		Message<?> requestMessage = new GenericMessage<>(payload);
		gateway.handleMessage(requestMessage);
	}

	@Test
	public void nonSerializableAttribute() {
		Message<String> requestMessage = MessageBuilder.withPayload("test")
				.setHeader("testAttribute", new NonSerializableTestObject()).build();
		gateway.handleMessage(requestMessage);
		Message<?> reply = output.receive(0);
		assertNotNull(requestMessage.getHeaders().get("testAttribute"));
		assertNotNull(reply.getHeaders().get("testAttribute"));
	}

	@Test
	public void invalidServiceName() {
		RmiOutboundGateway gateway = new RmiOutboundGateway("rmi://localhost:1099/noSuchService");
		boolean exceptionThrown = false;
		try {
			gateway.handleMessage(new GenericMessage<>("test"));
		}
		catch (MessageHandlingException e) {
			assertEquals(RemoteLookupFailureException.class, e.getCause().getClass());
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
	}

	@Test
	public void invalidHost() {
		RmiOutboundGateway gateway = new RmiOutboundGateway("rmi://noSuchHost:1099/testRemoteHandler");
		boolean exceptionThrown = false;
		try {
			gateway.handleMessage(new GenericMessage<>("test"));
		}
		catch (MessageHandlingException e) {
			assertEquals(RemoteLookupFailureException.class, e.getCause().getClass());
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
	}

	@Test
	public void invalidUrl() {
		RmiOutboundGateway gateway = new RmiOutboundGateway("invalid");
		boolean exceptionThrown = false;
		try {
			gateway.handleMessage(new GenericMessage<>("test"));
		}
		catch (MessageHandlingException e) {
			assertEquals(RemoteLookupFailureException.class, e.getCause().getClass());
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
	}


	private static class TestExchanger implements RequestReplyExchanger {

		TestExchanger() {
			super();
		}

		@Override
		public Message<?> exchange(Message<?> message) {
			if (message.getPayload().equals("fail")) {
				new AbstractReplyProducingMessageHandler() {

					@Override
					protected Object handleRequestMessage(Message<?> requestMessage) {
						throw new RuntimeException("foo");
					}
				}.handleMessage(new GenericMessage<>("bar"));
			}
			return new GenericMessage<>(message.getPayload().toString().toUpperCase(), message.getHeaders());
		}
	}


	private static class NonSerializableTestObject {

		NonSerializableTestObject() {
			super();
		}

	}

}
