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

import java.util.Map;

import javax.jms.CompletionListener;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.solacesystems.pubsub.sdkperf.jms.amqp.AmqpJmsClient;

public class AmqpJms_2_0_Client extends AmqpJmsClient implements CompletionListener {

	private static final Log Trace = LogFactory.getLog(AmqpJms_2_0_Client.class);

	public AmqpJms_2_0_Client() {
		super();
	}

	public AmqpJms_2_0_Client(ConnectionFactory cf, Map<String, Queue> queueMap, Map<String, Topic> topicMap) {
		super(cf, queueMap, topicMap);
	}

	@Override
	protected void publishMessage(MessageProducer prod, Destination dest, Message msg) throws JMSException {
		prod.send(dest, msg, (CompletionListener) this);
	}
	
	public void onCompletion(Message msg) {}

	public void onException(Message msg, Exception e) {
		Trace.warn("CLIENT " + _clientIdStr + " failed to publish message asynchronously. Message:" + msg.toString() + "\nCLIENT " + _clientIdStr + ": Exception listener error.", e);
		updateLastErrorResponse(e);
		_asyncExceptionOccured = true;
	}

}


