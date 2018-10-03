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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.naming.InitialContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.solacesystems.pubsub.sdkperf.config.RuntimeProperties;
import com.solacesystems.pubsub.sdkperf.core.AbstractClient;
import com.solacesystems.pubsub.sdkperf.core.ClientFactory;
import com.solacesystems.pubsub.sdkperf.core.Constants.GenericAuthenticationScheme;
import com.solacesystems.pubsub.sdkperf.jms.core.JmsClientTransactedSession;
import com.solacesystems.pubsub.sdkperf.jms.core.JmsClientXaSession;
import com.solacesystems.pubsub.sdkperf.jms.core.JmsSdkperfFactory;

public class AmqpJmsSdkperfFactory implements JmsSdkperfFactory {

	private static final Log Trace = LogFactory.getLog(AmqpJmsSdkperfFactory.class);
	public static final String INITIAL_CONTEXT_FACTORY_NAME = "org.apache.qpid.jms.jndi.JmsInitialContextFactory";
	private static final String CONNECTION_FACTORY_PREFIX = "connectionfactory";
	
	public InitialContext createInitialContext(RuntimeProperties rxProps, int clientIdInt) throws Exception {
		// Must build the provider URL string array.
		String hostList = rxProps.getStringProperty(RuntimeProperties.CLIENT_IP_ADDR);
		int reconnectAttempts;
		int reconnectDelay = 3000;
		List<?> extraPropsListTemp = null;
		List<String> extraPropsList = new ArrayList<String>();
		String clientVpn = null;
		try {
			reconnectAttempts = rxProps.getIntegerProperty(RuntimeProperties.RECONNECT_ATTEMPTS);
		} catch (NullPointerException e) {
			// If the value that user provides for reconnect attempts is less than -2, it is stored as 'null'
			throw new IllegalArgumentException("Reconnect attempts should have a value of -1 or above");
		}
		try {
			reconnectDelay = rxProps.getIntegerProperty(RuntimeProperties.RECONNECT_INTERVAL_MSEC);
		} catch(Exception e) {
			if (Trace.isDebugEnabled()) {
				Trace.debug("Max. Reconnect Delay for failover is set to a default value of " + reconnectDelay + " milliseconds");
			}
		}
		// Get extra properties passed from command line, if any
		try {
			extraPropsListTemp = (List<?>)rxProps.getProperty(RuntimeProperties.EXTRA_PROP_LIST);
		} catch(Exception e) {
			if (Trace.isDebugEnabled()) {
				Trace.debug("No extra properties supplied via command line");
			}
		}
		// Convert the list items into strings, so the list can be appended if needed.
		if (extraPropsListTemp != null && extraPropsListTemp.size() > 0) {
			for (int i = 0; i < extraPropsListTemp.size(); i++) {
				extraPropsList.add((String) extraPropsListTemp.get(i));
			}
			extraPropsListTemp = null;
		}
		// Get the message-vpn, if user has specified one.
		try {
			clientVpn = rxProps.getStringProperty(RuntimeProperties.CLIENT_VPN);
		} catch(Exception e) {
			if (Trace.isDebugEnabled()) {
				Trace.debug("User did not specify a message-vpn");
			}
		}
		// If there is a user-specified message-vpn, append it to the local extra-props list to be used during uri construction.
		if (clientVpn != null && !clientVpn.isEmpty()) {
			extraPropsList.add("amqp.vhost");
			extraPropsList.add(clientVpn);
		}
		// check host
		StringBuilder bldr = new StringBuilder();
		String[] hostListArray = hostList.split(",");
		
		// If reconnectAttempts is zero, do not build the failover url.
		if (reconnectAttempts < -1) {
			throw new IllegalArgumentException("Reconnect attempts should have a value of -1 or above");
		}
		if (reconnectAttempts == 0 && hostListArray.length > 1) {
			throw new IllegalArgumentException("Reconnect attempts with a value of zero is not supported with multiple hosts"); 
		}
		if (reconnectAttempts != 0 ) {
			bldr.append("failover:(");
		}
		for(int i = 0; i < hostListArray.length; i++) {
			hostListArray[i] = hostListArray[i].trim();
			if (i > 0){
				bldr.append(",");
			}
			if (hostListArray[i].indexOf("://") == -1) {
				bldr.append("amqp://");
			}
			bldr.append(hostListArray[i]);
		}
		if (reconnectAttempts != 0 ) {
			bldr.append(")?failover.maxReconnectAttempts=");
			bldr.append(reconnectAttempts);
			bldr.append("&failover.useReconnectBackOff=false&failover.reconnectDelay=");
			bldr.append(reconnectDelay);
		}
		// Append extra properties to the URI
		int extraPropsListSize = extraPropsList.size();
		if (extraPropsListSize > 0) {
			// Append only key-value pairs to URI, 
			// discard the last element in case the list doesn't have even number of elements
			if (extraPropsListSize % 2 != 0) {
				throw new  IOException("Extra properties list set incorrectly.");
			}
			for (int i = 0; i < extraPropsListSize; i++) {
				// Property key in even indices
				if (i % 2 == 0) {
					// Append '?' (if it is not present already) or '&'.
					if (i == 0 && bldr.toString().indexOf('?') == -1) {
						bldr.append("?");
					} else {
						bldr.append("&");
					}
					// Append failover.nested to "transport." and "amqp." options
					if (reconnectAttempts != 0 && (extraPropsList.get(i).startsWith("transport.") ||
							extraPropsList.get(i).startsWith("amqp."))) {
						bldr.append("failover.nested." + extraPropsList.get(i) + "=");
					} else {
						bldr.append(extraPropsList.get(i) + "=");
					}
				} else {
					// Property value in odd indices
					bldr.append(extraPropsList.get(i));
				}
			}
		}
		
		Trace.info("Constructed Connection URI: " + bldr.toString());
		
		Hashtable<String,Object> 	env = new Hashtable<String,Object>();
		env.put(InitialContext.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY_NAME);
		
		String s = CONNECTION_FACTORY_PREFIX + "." + AmqpJmsClient.CONNECTION_FACTORY_LOOKUP;
		env.put(s, bldr.toString());
		
		GenericAuthenticationScheme authScheme = (GenericAuthenticationScheme) rxProps.getProperty(RuntimeProperties.AUTHENTICATION_SCHEME);
		
		if (!rxProps.getStringProperty(RuntimeProperties.CLIENT_USERNAME).equals("") ||
				(rxProps.getStringProperty(RuntimeProperties.CLIENT_USERNAME).equals("") &&
				 !authScheme.equals(GenericAuthenticationScheme.CLIENT_CERTIFICATE) &&
				 !authScheme.equals(GenericAuthenticationScheme.GSS_KRB))) {
			String jmsUsername = ClientFactory.generateClientUsername(rxProps, clientIdInt);
			env.put(javax.naming.Context.SECURITY_PRINCIPAL, jmsUsername);
		}
		
		String jmsPassword = rxProps.getStringProperty(RuntimeProperties.CLIENT_PASSWORD);
		env.put(javax.naming.Context.SECURITY_CREDENTIALS, jmsPassword);
		
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_PROTOCOL).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_PROTOCOL, rxProps.getProperty(RuntimeProperties.SSL_PROTOCOL));
//		}
		
//		if (rxProps.getProperty(RuntimeProperties.SSL_CONNECTION_DOWNGRADE_TO) != null
//				&& !rxProps.getProperty(RuntimeProperties.SSL_CONNECTION_DOWNGRADE_TO).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_CONNECTION_DOWNGRADE_TO,
//					rxProps.getProperty(RuntimeProperties.SSL_CONNECTION_DOWNGRADE_TO));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_EXLCUDED_PROTOCOLS).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_EXCLUDED_PROTOCOLS, rxProps
//					.getProperty(RuntimeProperties.SSL_EXLCUDED_PROTOCOLS));
//		}
		
//		if (!rxProps.getBooleanProperty(RuntimeProperties.SSL_VALIDATE_CERTIFICATE)) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE, rxProps
//					.getBooleanProperty(RuntimeProperties.SSL_VALIDATE_CERTIFICATE));
//		}
		
//		if (!rxProps.getBooleanProperty(RuntimeProperties.SSL_VALIDATE_CERTIFICATE_DATE)) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE_DATE, rxProps
//					.getBooleanProperty(RuntimeProperties.SSL_VALIDATE_CERTIFICATE_DATE));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_CIPHER_SUITES).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_CIPHER_SUITES, rxProps
//					.getProperty(RuntimeProperties.SSL_CIPHER_SUITES));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_TRUST_STORE).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE, rxProps
//					.getProperty(RuntimeProperties.SSL_TRUST_STORE));
//		}
		
		// Check for WANT_NON_BLOCKING_CONNECT
		Boolean value = rxProps.getBooleanProperty(RuntimeProperties.WANT_NON_BLOCKING_CONNECT);
		if (value != null && value) {
			Trace.warn("Cannot connect in a non-blocking way when using the JmsClient.");
		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_TRUST_STORE_PASSWORD).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE_PASSWORD, rxProps
//					.getProperty(RuntimeProperties.SSL_TRUST_STORE_PASSWORD));
//		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_TRUST_STORE_FORMAT).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_TRUST_STORE_FORMAT, rxProps
//					.getProperty(RuntimeProperties.SSL_TRUST_STORE_FORMAT));
//		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_TRUSTED_COMMON_NAME_LIST).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_TRUSTED_COMMON_NAME_LIST, rxProps
//					.getProperty(RuntimeProperties.SSL_TRUSTED_COMMON_NAME_LIST));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_KEY_STORE).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE, rxProps
//					.getProperty(RuntimeProperties.SSL_KEY_STORE));
//		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_KEY_STORE_PASSWORD).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE_PASSWORD, rxProps
//					.getProperty(RuntimeProperties.SSL_KEY_STORE_PASSWORD));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_KEY_STORE_FORMAT).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_KEY_STORE_FORMAT, rxProps
//					.getProperty(RuntimeProperties.SSL_KEY_STORE_FORMAT));
//		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_PRIVATE_KEY_ALIAS).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_PRIVATE_KEY_ALIAS, rxProps
//					.getProperty(RuntimeProperties.SSL_PRIVATE_KEY_ALIAS));
//		}
//		
//		if (!rxProps.getProperty(RuntimeProperties.SSL_PRIVATE_KEY_PASSWORD).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_SSL_PRIVATE_KEY_PASSWORD, rxProps
//					.getProperty(RuntimeProperties.SSL_PRIVATE_KEY_PASSWORD));
//		}
		
//		if (!rxProps.getProperty(RuntimeProperties.AUTHENTICATION_SCHEME).equals("")) {
//			env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, rxProps
//					.getProperty(RuntimeProperties.AUTHENTICATION_SCHEME));
//			
//			if (authScheme.equals(GenericAuthenticationScheme.BASIC)) {
//				env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_BASIC);
//			} else if (authScheme.equals(GenericAuthenticationScheme.CLIENT_CERTIFICATE)) {
//				env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);
//			} else if (authScheme.equals(GenericAuthenticationScheme.GSS_KRB)) {
//				env.put(SupportedProperty.SOLACE_JMS_AUTHENTICATION_SCHEME, SupportedProperty.AUTHENTICATION_SCHEME_GSS_KRB);
//			} else {
//				throw new IllegalArgumentException("Unknown authentication type \"" + authScheme + "\".");
//			}
//		}
		
		if (rxProps.getProperty(RuntimeProperties.EXTRA_PROP_LIST) != null) {
			@SuppressWarnings("unchecked")
			List<String> extraProp = (List<String>) rxProps.getProperty(RuntimeProperties.EXTRA_PROP_LIST);
			
			for(int i = 0; i < (extraProp.size() - 1); i +=2) {
				env.put(extraProp.get(i), extraProp.get(i+1));
			}
		}
		
		if (rxProps.getIntegerProperty(RuntimeProperties.RECONNECT_INTERVAL_MSEC) != null) {
		}
		if (rxProps.getIntegerProperty(RuntimeProperties.KEEPALIVE_INTERVAL_MSEC) != null) {
		}
		if (rxProps.getIntegerProperty(RuntimeProperties.CLIENT_COMPRESSION_LEVEL) != null) {
		}
		
		return new InitialContext(env);
	}
	
	public JmsClientTransactedSession createClientTransactedSession(AbstractClient client, Connection connection, Session transactedSession, RuntimeProperties rprops) {
		return new JmsClientTransactedSession(client, transactedSession, rprops);
	}

	public JmsClientXaSession createXaTransactedSession(AbstractClient client,
			XAConnection xaConnection, XASession xaSession, RuntimeProperties rprops) {
		return new JmsClientXaSession(client, xaSession, rprops);
	}
}
