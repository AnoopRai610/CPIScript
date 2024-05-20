package com.acn

import com.sap.gateway.ip.core.customdev.util.AttachmentWrapper;
import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;
import javax.activation.DataHandler;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

def Message processData(Message message) {
	Map<String, AttachmentWrapper> attachments = message.getAttachmentWrapperObjects()
	
	Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
	Element elAttachments = doc.createElement("Attachments")
	doc.appendChild(elAttachments);
	
	//Attachments Summary
	elAttachments.appendChild(createTextNode(doc,"TotalAttachmentSize",Long.toString(message.getAttachmentsSize())));
	elAttachments.appendChild(createTextNode(doc,"NumberOfAttachments",Integer.toString(attachments.size())));

	attachments.each { K,V->
			
		Element elAttachment = doc.createElement("Attachment");
		elAttachment.appendChild(createTextNode(doc,"Name",K));
		
		//Write all attachment headers
		Element elHeaders = doc.createElement("Headers");
		elAttachment.appendChild(elHeaders);
		V.getHeaderNames().each { H->
			elHeaders.appendChild(createTextNode(doc, H, V.getHeader(H)))
		}
		
		//Write Data content
		Element elData = doc.createElement("Data");
		elAttachment.appendChild(elData);
	
		DataHandler dh = V.getDataHandler();
		byte[] dataBuffer = readContent(dh);
		int sizeDataBuffer = dataBuffer.length
		
		byte[] base64DataBuffer = Base64.getEncoder().encode(dataBuffer);
		int sizeBase64DataBuffer = base64DataBuffer.length;
		
		String base64Content = new String(base64DataBuffer);
		
		elData.appendChild(createTextNode(doc, "Base64Content", base64Content));
		elData.appendChild(createTextNode(doc, "RawSizeBytes", Integer.toString(sizeDataBuffer)));
		elData.appendChild(createTextNode(doc, "Base64SizeBytes", Integer.toString(sizeBase64DataBuffer)));
		
		
		elAttachments.appendChild(elAttachment);
		
	}
	
	message.setBody(new String(getDocumentByte(doc)));

	return message;
}

def Element createTextNode(Document doc, String name, String value) {
	Element el = doc.createElement(name);
	el.appendChild(doc.createTextNode(value));
	return el;
}

def byte[] readContent(DataHandler dh) {
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	dh.writeTo(byteArrayOutputStream);
	return byteArrayOutputStream.toByteArray();
}

def byte[] readContent(InputStream io) {
	byte[] buffer = new byte["?".length()];
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	int nRead;
	while ((nRead = io.read(buffer, 0, buffer.length)) != -1) {
		byteArrayOutputStream.write(buffer, 0, nRead);
	}
	return byteArrayOutputStream.toByteArray();
}

public byte[] getDocumentByte(Document doc) {
	ByteArrayOutputStream bos=new ByteArrayOutputStream()
	javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
			new DOMSource(doc),
			new StreamResult(bos)
			);
	return bos.toByteArray()
}

