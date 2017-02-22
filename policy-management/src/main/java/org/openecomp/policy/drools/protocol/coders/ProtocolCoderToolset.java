package org.openecomp.policy.drools.protocol.coders;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomCoder;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Protocol Coding/Decoding Toolset
 */
public abstract class ProtocolCoderToolset {
	
	/**
	 * topic
	 */
	protected final String topic;
	
	/**
	 * controller id
	 */
	protected final String controllerId;
	
	/**
	 * group id
	 */
	protected final String groupId;
	
	/**
	 * artifact id
	 */
	protected final String artifactId;
	
	/**
	 * Protocols and associated Filters
	 */
	protected final List<CoderFilters> coders = new ArrayList<CoderFilters>();
	
	/**
	 * Tree model (instead of class model) generic parsing to be able to inspect elements
	 */
	protected JsonParser filteringParser = new JsonParser();

	/**
	 * custom coder
	 */
	protected CustomCoder customCoder;
	
	/**
	 * Constructor
	 * 
	 * @param topic the topic
	 * @param controllerId the controller id
	 * @param codedClass the decoded class
	 * @param filters list of filters that apply to the
	 * selection of this decodedClass in case of multiplicity
	 * @throws IllegalArgumentException if invalid data has been passed in
	 */
	public ProtocolCoderToolset(String topic, 
								String controllerId, 
			                    String groupId,
			                    String artifactId,
			                    String codedClass, 
			                    JsonProtocolFilter filters, 
						        CustomCoder customCoder,
						        int modelClassLoaderHash)
		throws IllegalArgumentException {
		
		if (topic == null || controllerId == null || 
			groupId == null || artifactId == null ||
			codedClass == null || filters == null ||
			topic.isEmpty() || controllerId.isEmpty()) {
			// TODO
			throw new IllegalArgumentException("Invalid input");
		}
		
		this.topic = topic;
		this.controllerId = controllerId;
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.coders.add(new CoderFilters(codedClass, filters, modelClassLoaderHash));
		this.customCoder = customCoder;
	}
	
	/**
	 * gets the coder + filters associated with this class name
	 * 
	 * @param classname class name
	 * @return the decoder filters or null if not found
	 */
	public CoderFilters getCoder(String classname) {
		for (CoderFilters decoder: this.coders) {
			if (decoder.factClass.equals(classname)) {
				return decoder;
			}
		}
		return null;
	}

	/**
	 * get all coder filters in use
	 * 
	 * @return coder filters
	 */
	public List<CoderFilters> getCoders() {
		return this.coders;
	}

	/**
	 * add coder or replace it exists
	 * 
	 * @param eventClass decoder
	 * @param filter filter
	 */
	public void addCoder(String eventClass, JsonProtocolFilter filter, int modelClassLoaderHash) {
		synchronized(this) {
			for (CoderFilters coder: this.coders) {
				if (coder.factClass.equals(eventClass)) {
					// this is a better check than checking pointers, just
					// in case classloader is different and this is just an update
					coder.factClass = eventClass;
					coder.filter = filter;
					coder.modelClassLoaderHash = modelClassLoaderHash;
					return;
				}
			}
		}
		
		this.coders.add(new CoderFilters(eventClass, filter, modelClassLoaderHash));
	}
	
	/**
	 * remove coder
	 * 
	 * @param eventClass decoder
	 * @param filter filter
	 */
	public void removeCoders(String eventClass) {
		synchronized(this) {
			Iterator<CoderFilters> codersIt = this.coders.iterator();
			while (codersIt.hasNext()) {
				CoderFilters coder = codersIt.next();
				if (coder.factClass.equals(eventClass)) {
					codersIt.remove();
				}
			}
		}
	}

	/**
	 * gets the topic
	 * 
	 * @return the topic
	 */
	public String getTopic() {return topic;}
	
	/**
	 * gets the controller id
	 * 
	 * @return the controller id
	 */
	public String getControllerId() {return controllerId;}

	/**
	 * @return the groupId
	 */
	public String getGroupId() {
		return groupId;
	}

	/**
	 * @return the artifactId
	 */
	public String getArtifactId() {
		return artifactId;
	}

	/**
	 * @return the customCoder
	 */
	public CustomCoder getCustomCoder() {
		return customCoder;
	}

	/**
	 * @param customCoder the customCoder to set
	 */
	public void setCustomCoder(CustomCoder customCoder) {
		this.customCoder = customCoder;
	}

	/**
	 * performs filtering on a json string
	 * 
	 * @param json json string
	 * @return the decoder that passes the filter, otherwise null
	 * @throws UnsupportedOperationException can't filter
	 * @throws IllegalArgumentException invalid input
	 */
	protected CoderFilters filter(String json) 
			throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
		

		// 1. Get list of decoding classes for this controller Id and topic
		// 2. If there are no classes, return error
		// 3. Otherwise, from the available classes for decoding, pick the first one that
		//    passes the filters	
		
		// Don't parse if it is not necessary
		
		if (this.coders.isEmpty()) {
			// TODO this is an error
			throw new IllegalStateException("No coders available");
		}
		
		if (this.coders.size() == 1) {
			JsonProtocolFilter filter = this.coders.get(0).getFilter();
			if (!filter.isRules()) {
				return this.coders.get(0);
			}
		}
		
		JsonElement event;
		try {
			event = this.filteringParser.parse(json);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new UnsupportedOperationException(e);
		}
		
		for (CoderFilters decoder: this.coders) {
			try {
				boolean accepted = decoder.getFilter().accept(event);
				if (accepted) {
					return decoder;
				} 
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		
		return null;
	}

	/**
	 * Decode json into a POJO object
	 * @param json json string
	 * 
	 * @return a POJO object for the json string
	 * @throws IllegalArgumentException if an invalid parameter has been received
	 * @throws UnsupportedOperationException if parsing into POJO is not possible
	 */
	public abstract Object decode(String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException;
	
	/**
	 * Encodes a POJO object into a JSON String
	 * 
	 * @param event JSON POJO event to be converted to String
	 * @return JSON string version of POJO object
	 * @throws IllegalArgumentException if an invalid parameter has been received
	 * @throws UnsupportedOperationException if parsing into POJO is not possible
	 */
	public abstract String encode(Object event) 
			throws IllegalArgumentException, UnsupportedOperationException;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ProtocolCoderToolset [topic=").append(topic).append(", controllerId=").append(controllerId)
				.append(", groupId=").append(groupId).append(", artifactId=").append(artifactId).append(", coders=")
				.append(coders).append(", filteringParser=").append(filteringParser).append(", customCoder=")
				.append(customCoder).append("]");
		return builder.toString();
	}
}

/**
 * Tools used for encoding/decoding using Jackson
 */
class JacksonProtocolCoderToolset extends ProtocolCoderToolset {
	private static Logger  logger = FlexLogger.getLogger(JacksonProtocolCoderToolset.class);	
	/**
	 * decoder
	 */
	@JsonIgnore
	protected final ObjectMapper decoder = new ObjectMapper();
	
	/**
	 * encoder
	 */
	@JsonIgnore
	protected final ObjectMapper encoder = new ObjectMapper();
	
	/**
	 * Toolset to encode/decode tools associated with a topic
	 * 
	 * @param topic topic
	 * @param decodedClass decoded class of an event
	 * @param filter 
	 */
	public JacksonProtocolCoderToolset(String topic, String controllerId, 
									   String groupId, String artifactId,
			                           String decodedClass, 
			                           JsonProtocolFilter filter,
			                           CustomJacksonCoder customJacksonCoder,
			                           int modelClassLoaderHash) {
		super(topic, controllerId, groupId, artifactId, decodedClass, filter, customJacksonCoder, modelClassLoaderHash);
		decoder.registerModule(new JavaTimeModule());
		decoder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
				          false);
	}
	
	/**
	 * gets the Jackson decoder
	 * 
	 * @return the Jackson decoder
	 */
	@JsonIgnore
	protected ObjectMapper getDecoder() {return decoder;}
	
	/**
	 * gets the Jackson encoder
	 * 
	 * @return the Jackson encoder
	 */
	@JsonIgnore
	protected ObjectMapper getEncoder() {return encoder;}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException {

		// 0. Use custom coder if available
		
		if (this.customCoder != null) {
			throw new UnsupportedOperationException
					("Jackon Custom Decoder is not supported at this time");
		}
		
		DroolsController droolsController = 
				DroolsController.factory.get(groupId, artifactId, "");
		if (droolsController == null) {
			String error = "NO-DROOLS-CONTROLLER for: " + json + " IN " + this;
			logger.warn(error);
			throw new IllegalStateException(error);
		}
		
		CoderFilters decoderFilter = filter(json);
		if (decoderFilter == null) {
			String error = "NO-DECODER for: " + json + " IN " + this;
			logger.warn(error);
			throw new UnsupportedOperationException(error);
		}
		
		Class<?> decoderClass;
		try {
			decoderClass = 
					droolsController.fetchModelClass(decoderFilter.getCodedClass());
			if (decoderClass ==  null) {
				String error = "DECODE-ERROR FETCHING MODEL CLASS: " + ":" + json + ":" + this;
				logger.error(error);
				throw new IllegalStateException(error);				
			}
		} catch (Exception e) {
			String error = "DECODE-ERROR FETCHING MODEL CLASS: "+ e.getMessage() + ":" + json + ":" + this;
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, error);
			throw new UnsupportedOperationException(error, e);
		}
		

		try {
			Object fact = this.decoder.readValue(json, decoderClass);
			return fact;
		} catch (Exception e) {
			String error = "DECODE-ERROR FROM PDP-D FRAMEWORK: "+ json + ":" + e.getMessage() + ":" + this;
			logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
			throw new UnsupportedOperationException(error, e);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(Object event) 
			throws IllegalArgumentException, UnsupportedOperationException {
		
		// 0. Use custom coder if available
		
		if (this.customCoder != null) {
			throw new UnsupportedOperationException
					("Jackon Custom Encoder is not supported at this time");
		}
		
		try {
			String encodedEvent = this.encoder.writeValueAsString(event);
			return encodedEvent;			
		} catch (JsonProcessingException e) {
			String error = "ENCODE-ERROR: "+ event + " IN " + this;
			logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
			throw new UnsupportedOperationException(error, e);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JacksonProtocolCoderToolset [toString()=").append(super.toString()).append("]");
		return builder.toString();
	}

}

/**
 * Tools used for encoding/decoding using Jackson
 */
class GsonProtocolCoderToolset extends ProtocolCoderToolset {
	
	private static Logger  logger = FlexLogger.getLogger(GsonProtocolCoderToolset.class);
	/**
	 * Formatter for JSON encoding/decoding
	 */
	@JsonIgnore
	public static DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx");
	
	@JsonIgnore
	public static DateTimeFormatter zuluFormat = DateTimeFormatter.ISO_INSTANT;
	
	/**
	 * Adapter for ZonedDateTime
	 */

	public static class GsonUTCAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {

		public ZonedDateTime deserialize(JsonElement element, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			try {
				return ZonedDateTime.parse(element.getAsString(), format);
			} catch (Exception e) {
				System.err.println(e);
			}
			return null;
		}

		public JsonElement serialize(ZonedDateTime datetime, Type type, JsonSerializationContext context) {
			return new JsonPrimitive(datetime.format(format));
		}	
	}
	
	public static class GsonInstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

		@Override
		public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return Instant.ofEpochMilli(json.getAsLong());
		}

		@Override
		public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(src.toEpochMilli());
		}
		
	}

	
	/**
	 * decoder
	 */
	@JsonIgnore
	protected final Gson decoder = new GsonBuilder().disableHtmlEscaping().
													 registerTypeAdapter(ZonedDateTime.class, new GsonUTCAdapter()).
													 create();
	
	/**
	 * encoder
	 */
	@JsonIgnore
	protected final Gson encoder = new GsonBuilder().disableHtmlEscaping().
													 registerTypeAdapter(ZonedDateTime.class, new GsonUTCAdapter()).
													 create();
	
	/**
	 * Toolset to encode/decode tools associated with a topic
	 * 
	 * @param topic topic
	 * @param decodedClass decoded class of an event
	 * @param filter 
	 */
	public GsonProtocolCoderToolset(String topic, String controllerId, 
					   				String groupId, String artifactId,
					   				String decodedClass, 
					   				JsonProtocolFilter filter,
							        CustomGsonCoder customGsonCoder,
							        int modelClassLoaderHash) {
		super(topic, controllerId, groupId, artifactId, decodedClass, filter, customGsonCoder, modelClassLoaderHash);
	}
	
	/**
	 * gets the Gson decoder
	 * 
	 * @return the Gson decoder
	 */
	@JsonIgnore
	protected Gson getDecoder() {return decoder;}
	
	/**
	 * gets the Gson encoder
	 * 
	 * @return the Gson encoder
	 */
	@JsonIgnore
	protected Gson getEncoder() {return encoder;}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(String json) 
			throws IllegalArgumentException, UnsupportedOperationException, IllegalStateException {
	
		DroolsController droolsController = 
				DroolsController.factory.get(groupId, artifactId, "");
		if (droolsController == null) {
			String error = "NO-DROOLS-CONTROLLER for: " + json + " IN " + this;
			logger.warn(error);
			throw new IllegalStateException(error);
		}
		
		CoderFilters decoderFilter = filter(json);
		if (decoderFilter == null) {
			String error = "NO-DECODER for: " + json + " IN " + this;
			logger.warn(error);
			throw new UnsupportedOperationException(error);
		}
		
		Class<?> decoderClass;
		try {
			decoderClass = 
					droolsController.fetchModelClass(decoderFilter.getCodedClass());
			if (decoderClass ==  null) {
				String error = "DECODE-ERROR FETCHING MODEL CLASS: " + ":" + json + ":" + this;
				logger.error(error);
				throw new IllegalStateException(error);				
			}
		} catch (Exception e) {
			String error = "DECODE-ERROR FETCHING MODEL CLASS: "+ e.getMessage() + ":" + json + ":" + this;
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, error);
			throw new UnsupportedOperationException(error, e);
		}
		
		if (this.customCoder != null) {
			try {
				Class<?> gsonClassContainer = 
						droolsController.fetchModelClass(this.customCoder.getClassContainer());
				Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
				Object gsonObject = gsonField.get(null);
				Method fromJsonMethod = gsonObject.getClass().
						                     getDeclaredMethod
						                     	("fromJson", new Class[]{String.class, Class.class});
				Object fact = fromJsonMethod.invoke(gsonObject, json, decoderClass);
				return fact;
			} catch (NoSuchFieldException | SecurityException | IllegalAccessException | 
					 NoSuchMethodException | InvocationTargetException e) {
				String error = "DECODE-ERROR-FROM-CUSTOM-CODER: " + e.getMessage() + ":" + json + ":" + this;
				logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
				throw new UnsupportedOperationException(error, e);
			}			
		} else {
			try {
				Object fact = this.decoder.fromJson(json, decoderClass);
				return fact;
			} catch (Exception e) {
				String error = "DECODE-ERROR FROM PDP-D FRAMEWORK: "+ json + ":" + e.getMessage() + ":" + this;
				logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
				throw new UnsupportedOperationException(error, e);
			}			
		}
	}


	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(Object event) 
			throws IllegalArgumentException, UnsupportedOperationException {	
		
		DroolsController droolsController = 
				DroolsController.factory.get(groupId, artifactId, "");
		if (droolsController == null) {
			String error = "NO-DROOLS-CONTROLLER for: " + event + " IN " + this;
			logger.warn(error);
			throw new IllegalStateException(error);
		}
		
		if (this.customCoder != null) {
			try {
				Class<?> gsonClassContainer = 
						droolsController.fetchModelClass(this.customCoder.getClassContainer());
				Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
				Object gsonObject = gsonField.get(null);
				Method toJsonMethod = gsonObject.getClass().
						                     getDeclaredMethod
						                     	("toJson", new Class[]{Object.class});
				String encodedJson = (String) toJsonMethod.invoke(gsonObject, event);
				return encodedJson;
			} catch (NoSuchFieldException | SecurityException | IllegalAccessException | 
					 NoSuchMethodException | InvocationTargetException e) {
				String error = "DECODE-ERROR-FROM-CUSTOM-CODER: " + e.getMessage() + ":" + event + ":" + this;
				logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
				throw new UnsupportedOperationException(error, e);
			}			
		} else {
			try {
				String encodedEvent = this.encoder.toJson(event);
				return encodedEvent;			
			} catch (Exception e) {
				String error = "ENCODE-ERROR: "+ event + " IN " + this;
				logger.error(MessageCodes.EXCEPTION_ERROR, e, error);
				throw new UnsupportedOperationException(error, e);
			}		
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("GsonProtocolCoderToolset [toString()=").append(super.toString()).append("]");
		return builder.toString();
	}
}