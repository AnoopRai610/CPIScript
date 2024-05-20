package com.acn

import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.AccessTokenAndUser
import com.sap.it.api.securestore.SecureStoreService

def Message processData(Message message) {
	String OAuthCredentialName = message.getProperty("OAuthCredentialName");
	String HeaderPrefix = message.getProperty("HeaderPrefix");

	if(OAuthCredentialName==null || OAuthCredentialName.isEmpty())
		throw new Exception("Please maintain OAuthCredentialName as parameter with a proper credential name")

	SecureStoreService secureService = ITApiFactory.getService(SecureStoreService.class, null)
	AccessTokenAndUser accesssTokenAndUser = secureService.getAccesTokenForOauth2AuthorizationCodeCredential(OAuthCredentialName)
	if(accesssTokenAndUser==null)
		throw new Exception("Please maintain OAuth 2.0 Credential in security material with name as ${OAuthCredentialName}");

	message.setHeader("Authorization", (HeaderPrefix==null || HeaderPrefix.isEmpty())?"Bearer " + accesssTokenAndUser.getAccessToken(): HeaderPrefix + " " +accesssTokenAndUser.getAccessToken())
	return message;
}