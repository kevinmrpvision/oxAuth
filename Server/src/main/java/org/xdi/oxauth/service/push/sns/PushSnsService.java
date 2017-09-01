package org.xdi.oxauth.service.push.sns;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.service.EncryptionService;
import org.xdi.oxauth.util.ServerUtil;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

/**
 * Provides operations to send AWS SNS push messages
 *
 * @author Yuriy Movchan Date: 08/31/2017
 */
@Stateless
@Named
public class PushSnsService {

	@Inject
    private EncryptionService encryptionService;

	@Inject
	private AppConfiguration appConfiguration;

	@Inject
    private LdapEntryManager ldapEntryManager;

	public AmazonSNS createSnsClient(String accessKey, String secretKey, String region) {
		String decryptedAccessKey = encryptionService.decrypt(accessKey, true);
		String decryptedSecretKey = encryptionService.decrypt(secretKey, true);

		BasicAWSCredentials credentials = new BasicAWSCredentials(decryptedAccessKey, decryptedSecretKey);
	    AmazonSNS snsClient = AmazonSNSClientBuilder.standard().withRegion(Regions.fromName(region)).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	    
	    return snsClient;
	}

	public String createPlatformArn(AmazonSNS snsClient, String platformApplicationArn, String token, User user) {
		CreatePlatformEndpointRequest platformEndpointRequest = new CreatePlatformEndpointRequest();
		platformEndpointRequest.setPlatformApplicationArn(platformApplicationArn);
		platformEndpointRequest.setToken(token);
		
		String customUserData = String.format("Issuer: %s, user: %s, date: %s", appConfiguration.getIssuer(), user.getUserId(),
				ldapEntryManager.encodeGeneralizedTime(new Date()));
		platformEndpointRequest.setCustomUserData(customUserData);
		
		CreatePlatformEndpointResult platformEndpointResult = snsClient.createPlatformEndpoint(platformEndpointRequest);

		return platformEndpointResult.getEndpointArn();
	}

	public PublishResult sendPushMessage(AmazonSNS snsClient, PushPlatform platform, String targetArn, Map<String, Object> customAppMessageMap, Map<String, MessageAttributeValue> messageAttributes) throws IOException {
		Map<String, Object> appMessageMap = new HashMap<String, Object>();

		if (platform == PushPlatform.GCM) {
			appMessageMap.put("collapse_key", "single");
			appMessageMap.put("delay_while_idle", true);
			appMessageMap.put("time_to_live", 30);
			appMessageMap.put("dry_run", false);
		}

		if (customAppMessageMap != null) {
			appMessageMap.putAll(customAppMessageMap);
		}

		String message = ServerUtil.asJson(appMessageMap);

		return sendPushMessage(snsClient, platform, targetArn, message, messageAttributes);
	}

	public PublishResult sendPushMessage(AmazonSNS snsClient, PushPlatform platform, String targetArn, String message,
			Map<String, MessageAttributeValue> messageAttributes) throws IOException {
		Map<String, String> messageMap = new HashMap<String, String>();
		messageMap.put(platform.name(), message);
		message = ServerUtil.asJson(messageMap);

	    PublishRequest publishRequest = new PublishRequest();
		publishRequest.setMessageStructure("json");
		
		if (messageAttributes != null) {
			publishRequest.setMessageAttributes(messageAttributes);
		}

		publishRequest.setTargetArn(targetArn);
		publishRequest.setMessage(message);

		PublishResult publishResult = snsClient.publish(publishRequest);

		return publishResult;
	}
	private static Map<String, String> getData() throws JsonGenerationException, JsonMappingException, IOException {
		Map<String, String> pushRequest = new HashMap<String, String>();
		pushRequest.put("app", "https://ce-release.gluu.org/identity/authentication/authcode");
		pushRequest.put("method", "authenticate");
		pushRequest.put("req_ip", "130.180.209.30");
		pushRequest.put("created", "2017-08-28T09:57:40.665000");
		pushRequest.put("issuer", "https://ce-release.gluu.org");
		pushRequest.put("req_loc", "Ukraine%2C%20Odessa%2C%20Odesa%20%28Prymors%5C%27kyi%20district%29");
		pushRequest.put("state", "bbf58b34-dba2-4a5a-b3b8-464fc56e8649");

		ObjectMapper om = new ObjectMapper();
		String pushRequestString = om.writeValueAsString(pushRequest);

		Map<String, String> payload = new HashMap<String, String>();
		payload.put("message", pushRequestString);
		payload.put("title", "Super-Gluu");

		return payload;
	}

	public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException {
		Map<String, Object> appMessageMap = new HashMap<String, Object>();

		appMessageMap.put("collapse_key", "single");
		appMessageMap.put("delay_while_idle", true);
		appMessageMap.put("time_to_live", 30);
		appMessageMap.put("dry_run", false);
		appMessageMap.put("data", getData());

		String message = ServerUtil.asJson(appMessageMap);
		System.out.println(message);

	}
}
