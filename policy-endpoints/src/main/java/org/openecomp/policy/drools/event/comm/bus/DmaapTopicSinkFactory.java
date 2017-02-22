/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.openecomp.policy.drools.event.comm.bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.openecomp.policy.drools.event.comm.bus.internal.InlineDmaapTopicSink;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.properties.PolicyProperties;

/**
 * DMAAP Topic Sink Factory
 */
public interface DmaapTopicSinkFactory {
	
	/**
	 * Instantiates a new DMAAP Topic Sink
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param userName AAF user name
	 * @param password AAF password
	 * @param partitionKey Consumer Group
	 * @param managed is this sink endpoint managed?
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								boolean managed)
			throws IllegalArgumentException;
	
	/**
	 * Creates an DMAAP Topic Sink based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<DmaapTopicSink> build(Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new DMAAP Topic Sink
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink build(List<String> servers, String topic)
			throws IllegalArgumentException;
	
	/**
	 * Destroys an DMAAP Topic Sink based on a topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);

	/**
	 * gets an DMAAP Topic Sink based on topic name
	 * @param topic the topic name
	 * 
	 * @return an DMAAP Topic Sink with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the DMAAP Topic Reader is 
	 * an incorrect state
	 */
	public DmaapTopicSink get(String topic)
			   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the DMAAP Topic Sinks
	 * @return a list of the DMAAP Topic Sinks
	 */
	public List<DmaapTopicSink> inventory();

	/**
	 * Destroys all DMAAP Topic Sinks
	 */
	public void destroy();
}

/* ------------- implementation ----------------- */

/**
 * Factory of DMAAP Reader Topics indexed by topic name
 */
class IndexedDmaapTopicSinkFactory implements DmaapTopicSinkFactory {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(IndexedDmaapTopicSinkFactory.class);	
	
	/**
	 * DMAAP Topic Name Index
	 */
	protected HashMap<String, DmaapTopicSink> dmaapTopicWriters =
			new HashMap<String, DmaapTopicSink>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								boolean managed) 
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized (this) {
			if (dmaapTopicWriters.containsKey(topic)) {
				return dmaapTopicWriters.get(topic);
			}
			
			DmaapTopicSink dmaapTopicSink = 
					new InlineDmaapTopicSink(servers, topic, 
										     apiKey, apiSecret,
										     userName, password,
										     partitionKey);
			
			if (managed)
				dmaapTopicWriters.put(topic, dmaapTopicSink);
			return dmaapTopicSink;
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink build(List<String> servers, String topic) throws IllegalArgumentException {
		return this.build(servers, topic, null, null, null, null, null, true);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<DmaapTopicSink> build(Properties properties) throws IllegalArgumentException {
		
		String writeTopics = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS);
		if (writeTopics == null || writeTopics.isEmpty()) {
			logger.warn("No topic for DMAAP Sink " + properties);
			return new ArrayList<DmaapTopicSink>();
		}
		List<String> writeTopicList = new ArrayList<String>(Arrays.asList(writeTopics.split("\\s*,\\s*")));
		
		synchronized(this) {
			List<DmaapTopicSink> dmaapTopicWriters = new ArrayList<DmaapTopicSink>();
			for (String topic: writeTopicList) {
				
				String servers = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + 
				                                        topic + 
				                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				if (servers == null || servers.isEmpty()) {
					logger.error("No DMAAP servers provided in " + properties);
					continue;
				}
				
				List<String> serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				
				String apiKey = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
						                               "." + topic + 
						                               PolicyProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);		 
				String apiSecret = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                          "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);
				
				String aafMechId = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                          "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX);
				String aafPassword = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
         				                                    "." + topic + 
         				                                    PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX);
				
				String partitionKey = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                             "." + topic + 
                                                             PolicyProperties.PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX);
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic +
						                                      PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}
				
				DmaapTopicSink dmaapTopicSink = this.build(serverList, topic, 
						   						           apiKey, apiSecret, aafMechId, aafPassword,
						   						           partitionKey, managed);
				dmaapTopicWriters.add(dmaapTopicSink);
			}
			return dmaapTopicWriters;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy(String topic) 
		   throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		DmaapTopicSink dmaapTopicWriter;
		synchronized(this) {
			if (!dmaapTopicWriters.containsKey(topic)) {
				return;
			}
			
			dmaapTopicWriter = dmaapTopicWriters.remove(topic);
		}
		
		dmaapTopicWriter.shutdown();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy() {
		List<DmaapTopicSink> writers = this.inventory();
		for (DmaapTopicSink writer: writers) {
			writer.shutdown();
		}
		
		synchronized(this) {
			this.dmaapTopicWriters.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink get(String topic) 
			throws IllegalArgumentException, IllegalStateException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (dmaapTopicWriters.containsKey(topic)) {
				return dmaapTopicWriters.get(topic);
			} else {
				throw new IllegalStateException("DmaapTopicSink for " + topic + " not found");
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<DmaapTopicSink> inventory() {
		 List<DmaapTopicSink> writers = 
				 new ArrayList<DmaapTopicSink>(this.dmaapTopicWriters.values());
		 return writers;
	}
	
}