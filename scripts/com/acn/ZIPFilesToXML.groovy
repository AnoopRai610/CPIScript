package com.acn

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlUtil
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.xpath.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

import javax.xml.XMLConstants
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import groovy.xml.StreamingMarkupBuilder


def Message processData(Message message) {
	String body = message.getBody(String.class);

	XMLUtility xml = new XMLUtility(body)
	ByteArrayOutputStream zipOS = new ByteArrayOutputStream()
	ZipOutputStream zipStream = new ZipOutputStream(zipOS)

	NodeList nlSuccess = xml.executeXpath("//Documents[./Status='Success']", "NODESET")

	nlSuccess.each {
		String fileName = xml.retrieveNodeOfParentNode("./PDFName",it).getTextContent()
        //Read only PDF file, manupulate it based on your use case
		fileName = (fileName.contains("."))?fileName:fileName+".pdf"
		String content = xml.retrieveNodeOfParentNode("./PDFContent",it).getTextContent()

		zipStream.putNextEntry(new ZipEntry(fileName))
		zipStream.write(Base64.getMimeDecoder().decode(content.getBytes()))
		zipStream.closeEntry();
	}
	zipStream.close();
	message.setBody(new String(Base64.getEncoder().encode(zipOS.toByteArray())))

	boolean isSuccess = (nlSuccess.getLength()>0)
	boolean isError = xml.executeXpath("boolean(//Documents[./Status='Error'])", "BOOLEAN")
	if(isSuccess && !isError) {
		message.setProperty("FinalStatus","Success")
	} else if(isSuccess && isError) {
		message.setProperty("FinalStatus","Partially Completed")
	} else
		message.setProperty("FinalStatus","Error")
	return message;
}

def Message processMaterialResponse(Message message) {

	XMLUtility xmlSAPMat = new XMLUtility(message.getBody(String.class))
	XMLUtility xmlSFDC = new XMLUtility(message.getProperty("mainRequest"))

	NodeList nlMaterial = xmlSAPMat.executeXpath("//materials", "NODESET")

	Map<String, String> materialBatch = new HashMap()
	nlMaterial.each {
		String mat = xmlSAPMat.retrieveStringOfParentNode("./material", it)
		mat = (mat!=null) ? mat.replaceAll("^0*",""):""
		if(!mat.isEmpty() && !materialBatch.containsKey(mat)) {
			String batch = xmlSAPMat.retrieveStringOfParentNode("./batch", it)
			materialBatch.put(mat, (batch!=null)?batch:"")
		}
	}

	NodeList nl = xmlSFDC.executeXpath("/root/requests[./format/text() != 'SAP' and ./subformat/text() != 'COA']", "NODESET");

	nl.each {
		String customer = xmlSFDC.retrieveStringOfParentNode("./customer/text()", it)
		String format = xmlSFDC.retrieveStringOfParentNode("./format/text()", it)
		String subformat = xmlSFDC.retrieveStringOfParentNode("./subformat/text()", it)
		String language = xmlSFDC.retrieveStringOfParentNode("./language/text()", it)
		String documentType = xmlSFDC.retrieveStringOfParentNode("./documentType/text()", it)
		Node root = it.parentNode() 
		materialBatch.forEach { t, u ->
			Node requests = xmlSFDC.createNode("requests", "", root);
			xmlSFDC.createNode("product", t, requests);
			xmlSFDC.createNode("batch", u, requests);
			xmlSFDC.createNode("customer", customer, requests);
			xmlSFDC.createNode("format", format, requests);
			xmlSFDC.createNode("subformat", subformat, requests);
			xmlSFDC.createNode("language", language, requests);
			xmlSFDC.createNode("documentType", documentType, requests);
		}
		xmlSFDC.remove(it)
	}
	
	message.setBody(xmlSFDC.getDocumentAsString())
	
	return message;
}

class XMLUtility{
	private Document xmlData
	private XPath xpath
	XMLUtility(String xmlPreviousString) {
		//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		xpath = XPathFactory.newInstance().newXPath()
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true)

		DocumentBuilder builder =dbf.newDocumentBuilder()
		this.xmlData = builder.parse(new InputSource(new StringReader(xmlPreviousString)))
	}

	public Object executeXpath(String xpathEquation, String type) {
		def xpathResult = this.xpath.evaluate(xpathEquation, xmlData.documentElement, new QName("http://www.w3.org/1999/XSL/Transform", type))
		return xpathResult
	}

	public Node retrieveNodeOfParentNode(String path, Node parentNode) throws XPathExpressionException {
		return (Node) this.xpath.evaluate(path, parentNode, XPathConstants.NODE);
	}

	public String retrieveStringOfParentNode(String path, Node parentNode) throws XPathExpressionException {
		return (String) this.xpath.evaluate(path, parentNode, XPathConstants.STRING);
	}

	public Node createNode(String name, String value, Node newParentNode) {
		Node newNode = xmlData.createElement(name);
		newNode.setTextContent(value);
		newParentNode.appendChild(newNode);
		return newNode;
	}

	public Node createNode(String name, Node baseNode, Node newParentNode) {
		if (baseNode != null) {
			return createNode(name, baseNode.getNodeName(), newParentNode);
		} else {
			return null;
		}
	}
	
	public void remove(Object it) {
		Element n = it
		n.parentNode().removeChild(n)
	}

	public Document getDocument() {
		return xmlData
	}

	public String getDocumentAsString() {
		StringWriter stringWriter = new StringWriter()

		javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
				new DOMSource(getDocument()),
				new StreamResult(stringWriter)
				)

		return stringWriter.toString()
	}
}