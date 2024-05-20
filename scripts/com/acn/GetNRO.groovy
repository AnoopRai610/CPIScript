package com.acn

import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory;
import com.sap.it.api.nrc.NumberRangeConfigurationService


def Message processRequest(Message message) {
	String norName = message.getProperty("NROName");
	String counter = getNRO(norName);
	message.setProperty("count", counter);
	return message;
}

private String getNRO(String NROName) {
	def service = ITApiFactory.getService(NumberRangeConfigurationService.class, null);
	if(service!=null) {
		String returnValue = service.getNextValuefromNumberRange(NROName,null)
	}
	else
		throw new Exception("API Factory class is not retrieving NumberRangeConfigurationService.")
}

