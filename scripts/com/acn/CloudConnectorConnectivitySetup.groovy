package com.acn

import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.securestore.SecureStoreService
import com.sap.it.api.securestore.UserCredential
import com.sap.it.api.securestore.exception.SecureStoreException

/**
 * @see <a href="https://github.com/AnoopRai610/sapcpilookup">SAP CPI Lookup function</a>
 */ 
def Message processData(Message message) {
	String body = message.getBody(String.class);
	String credName = message.getProperty("CredentialName");
	SecureStoreService secService= ITApiFactory.getApi(SecureStoreService.class, null)
	String user = "";
	String password = "";
	try{
		UserCredential userCredential =  secService.getUserCredential(credName);
		user = userCredential.getUsername();
		password = new String(userCredential.getPassword());
	} catch(Exception e){
		throw new SecureStoreException("Please maintain secure parameter $credName");
	}
	
	ODataLookup odataLookup = new ODataLookup(user, password, true);
	message.setProperty("odataLookup", odataLookup);
	
	return message;
}