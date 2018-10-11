/** 
 *  Copyright 2009-2018 Solace Corporation. All rights reserved
 *  
 *  http://www.solace.com
 *  
 *  This source is distributed WITHOUT ANY WARRANTY or support;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 *  A PARTICULAR PURPOSE.  All parts of this program are subject to
 *  change without notice including the program's CLI options.
 *
 *  Unlimited use and re-distribution of this unmodified source code is   
 *  authorized only with written permission.  Use of part or modified  
 *  source code must carry prominent notices stating that you modified it, 
 *  and give a relevant date.
 */
package com.solacesystems.pubsub.sdkperf.jms.amqp;

import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TransactionRolledBackException;
import javax.jms.XAConnectionFactory;
import javax.transaction.xa.XAException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.pubsub.sdkperf.config.EpConfigProperties;
import com.solacesystems.pubsub.sdkperf.config.RuntimeProperties;
import com.solacesystems.pubsub.sdkperf.core.BasicMsgRep;
import com.solacesystems.pubsub.sdkperf.core.ChannelState;
import com.solacesystems.pubsub.sdkperf.core.PubSubException;
import com.solacesystems.pubsub.sdkperf.core.TransactionRollbackException;
import com.solacesystems.pubsub.sdkperf.jms.core.AbstractJmsClient;
import com.solacesystems.pubsub.sdkperf.jms.core.BasicMessageListener;
import com.solacesystems.pubsub.sdkperf.jms.core.JmsSdkperfFactory;
import com.solacesystems.pubsub.sdkperf.jms.core.JmsSdkperfVersion;

/**
 * Class for managing all activities of a single client. Handles all JMS
 * interactions.
 */
public class AmqpJmsClient extends AbstractJmsClient {

	private static final Log Trace = LogFactory.getLog(AmqpJmsClient.class);
	public static final String CONNECTION_FACTORY_LOOKUP = "lookup";

	private AmqpJmsSdkperfFactory sdkperfFactory = null;

	public AmqpJmsClient() {
	}

	public AmqpJmsClient(ConnectionFactory cf, Map<String, Queue> queueMap, Map<String, Topic> topicMap) {
		super(cf, queueMap, topicMap);
	}

	@Override
	protected ConnectionFactory setupConnectionFactory() throws Exception {
		ConnectionFactory jmsCf = (ConnectionFactory) _initialContext.lookup(CONNECTION_FACTORY_LOOKUP);
		return jmsCf;
	}

	@Override
	public void connect() throws Exception {
		if (isConnected()) {
			return;
		}

		boolean isTransacted = false;

		if (Trace.isDebugEnabled()) {
			Trace.debug("AmqpJmsClient - connect() was called.");
		}
		
		try {
			String username = _rxProps.getStringProperty(RuntimeProperties.CLIENT_USERNAME);
			String password = _rxProps.getStringProperty(RuntimeProperties.CLIENT_PASSWORD);
			_jmsConnection = _cf.createConnection(username, password);
			
			if (!_rxProps.getStringProperty(RuntimeProperties.CLIENT_NAME_PREFIX).equals(""))
				_jmsConnection.setClientID(_clientIdStr);
			
			if (_wantOnExceptionListener)
				_jmsConnection.setExceptionListener(this);

			_jmsConnection.start();
			
			_jmsSession = _jmsConnection.createSession(isTransacted, Session.AUTO_ACKNOWLEDGE);

			for (int i = 0; i < _producers.length; ++i) {
				_producers[i] = _jmsSession.createProducer(null);
			}

			_defaultProducer = _producers[0];

			configureProducer(_defaultProducer, _rxProps);

			reconnectToFlows();

		} catch (JMSException ex) {
			updateLastErrorResponse(ex);
			throw ex;
		}

		if (_rxProps.getBooleanProperty(RuntimeProperties.WANT_REPLY_TEMPORARY_QUEUE) && (_tempQueueReplyTo == null)) {
			_tempQueueReplyTo = _jmsSession.createTemporaryQueue();
			_tempQueueMsgConsumer = _jmsSession.createConsumer(_tempQueueReplyTo);
			_tempQueueMsgConsumer.setMessageListener(new BasicMessageListener(this, _tempQueueReplyTo
					.getQueueName(), false, null, true, null, _tempQueueMsgConsumer));
		}

		_channelState = ChannelState.CLIENT_STATE_CONNECTED;

	}

	@Override
	protected XAConnectionFactory setupXAConnectionFactory() throws Exception {
		String jmsCF = _rxProps.getStringProperty(RuntimeProperties.JMS_CONNECTION_FACTORY);
		try {
			XAConnectionFactory jmsXaCf = (XAConnectionFactory) _initialContext.lookup(jmsCF);
			return jmsXaCf;
		} catch (Exception exception) {
			String msg = exception.getMessage();
			throw new PubSubException("XA is not enabled on Connection Factory. " + msg);
		}
	}

	@Override
	protected String getSessionName(Session session) {
		Trace.warn("getSessionName() not implemented.");
		return null;
	}

	@Override
	protected void modifyMsgForJmsExtendedProps(Message jmsMessage) throws JMSException {
		if (Trace.isDebugEnabled()) {
			Trace.debug("modifyMsgForJmsExtendedProps() not implemented.");
		}
	}

	@Override
	protected void publishBinarySmfMsg(BasicMsgRep msgRep, int pubSessionIndex) throws Exception {

		Trace.error("publishBinarySmfMsg() not supported in AmqpJmsClient.");

		if (msgRep.getSmfBytes() == null) {
			throw new PubSubException("CLIENT " + _clientIdStr + ": Error trying to publish binary message (null).");
		}

		Message jmsMessage = SolJmsUtility.fromByteArray(msgRep.getSmfBytes());

		Destination dest = jmsMessage.getJMSDestination();

		if (_txProps.getBooleanProperty(RuntimeProperties.WANT_REPLY_TOPIC)
				|| _txProps.getBooleanProperty(RuntimeProperties.WANT_REPLY_TEMPORARY_QUEUE)) {
			updateReplyToTopic(msgRep, jmsMessage);
		}

		try {
			if(msgRep.getPriority() != null) {
				_producers[pubSessionIndex].setPriority(msgRep.getPriority());
			}
			_producers[pubSessionIndex].send(dest, jmsMessage);
		} catch (Exception e) {
			updateLastErrorResponse(e);
			throw e;
		}
	}

	@Override
	protected String getMessageConsumerName(MessageConsumer msgConsumer) {
		Trace.warn("getMessageConsumerName() not implemented.");
		return null;
	}

	@Override
	protected void updateLastErrorResponse(JMSException exception) {
		_lastErrorResponse = exception;
	}

	@Override
	public void commitTransactionOnCurrPub(int producerIndex, boolean wantRollback) throws Exception {
		try {
			if (wantRollback) {
				_currTransactedSession.rollback();
			} else {
				_currTransactedSession.commit();
			}
		} catch (TransactionRolledBackException e) {
			updateLastErrorResponse(e);
			throw new TransactionRollbackException(-1, e);
		} catch (JMSException e) {
			updateLastErrorResponse(e);
			throw e;
		}
	}

	@Override
	public void commitXaSessionOnCurrPub(int producerIndex, boolean wantRollback, boolean onePhase, boolean noStart)
			throws Exception {
		try {
			super.commitXaSessionOnCurrPub(producerIndex, wantRollback, onePhase, noStart);
		} catch (Exception e) {
			if (e instanceof TransactionRolledBackException) {
				throw new TransactionRollbackException(-1, e);
			} else if (e instanceof XAException) {
				throw new TransactionRollbackException(-1, e);
			}
		}
	}

	@Override
	public void updateLastErrorResponse(Exception exception) {
		if (exception instanceof JMSException) {
			updateLastErrorResponse((JMSException) exception);
		} else {
			_lastErrorResponse = exception;
		}
	}

	@Override
	protected BasicMessageListener createMessageListener(MessageConsumer msgConsumer, EpConfigProperties epProps,
			String destName, boolean isTopic) throws JMSException, PubSubException {

		BasicMessageListener msgListener = new BasicMessageListener(this, destName, isTopic, null, false, null,
				msgConsumer);
		msgListener.setWantPriorityOrderChecking(epProps.getWantMessagePriorityOrderChecking());
		msgListener.startMessageListener();
		return msgListener;
	}

	@Override
	public String getFormattedVersionInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append(JmsSdkperfVersion.getSdkPerfJmsVersion() + System.getProperty("line.separator"));
		// Get version for qpid-jms-client and proton-j
		String qpidApiVersion = getQpidApiVersion();
		if (!qpidApiVersion.isEmpty()) {
			sb.append(qpidApiVersion + System.getProperty("line.separator"));
		}
		return sb.toString();
	}

	@Override
	protected JmsSdkperfFactory getJmsFactory() {
		if (sdkperfFactory == null) {
			sdkperfFactory = new AmqpJmsSdkperfFactory();
		}
		return sdkperfFactory;
	}

	public static String getQpidApiVersion() {
		
		String versionString = "";
		StringBuilder sb = new StringBuilder("");
		Class<?> sampleClass;
		//Get qpid-jms-client version
		try {
			sampleClass = Class.forName("org.apache.qpid.jms.JmsSession");
			versionString = getVersionString(sampleClass);
			if (!versionString.isEmpty()) {
				sb.append("qpid-jms-client version: " + versionString);
				sb.append(System.getProperty("line.separator"));			
			}
		} catch(ClassNotFoundException e) {
			if (Trace.isDebugEnabled()) {
				Trace.debug("qpid-jms-client version cannot be found");
			}
		}

		//Get proton-j version
		try {
			sampleClass = Class.forName("org.apache.qpid.proton.Proton");
			versionString = getVersionString(sampleClass);
			if (!versionString.isEmpty()) {
				sb.append("proton-j version: " + versionString);
			}		
		} catch(ClassNotFoundException e) {
			if (Trace.isDebugEnabled()) {
				Trace.debug("proton-j version cannot be found");
			}
		}

		return sb.toString();		
	}
	
	private static String getVersionString(Class<?> sampleClass) {
		
		String versionString = "";
		URL location = sampleClass.getResource('/' + sampleClass.getName().replace('.', '/') + ".class");
		Pattern pattern = Pattern.compile("-\\d+.\\d+.\\d+.jar!");
		Matcher matcher = pattern.matcher(location.toString());
		if (matcher.find()) {
			versionString = matcher.group(0);
			versionString = versionString.substring(1, versionString.length()-5);
		}
		return versionString;
	}
	
}
