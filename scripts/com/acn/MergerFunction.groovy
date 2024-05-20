package com.acn

import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import groovy.xml.XmlUtil
import javax.xml.xpath.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import groovy.xml.StreamingMarkupBuilder


def Message processData(Message message) {
	def properties = message.getProperties()
	def xmlPrevious = properties.get("mainBody")
	def xmlCurrent = message.getBody(String.class)
	message.setBody(processMerge(xmlPrevious, properties.get("OrignalPathToNode"),properties.get("OrignalKeyElement"),xmlCurrent, properties.get("LookupPathToNode"), properties.get("LookupKeyElement")))
	return message
}

def String processMerge(String xmlPreviousString, String xpathSource, String sourceNodeName, String xmlCurrentString, String xpathTarget, String targetNodeName) {
	MergeXMLUtility xmlPreviousUtil = new MergeXMLUtility(xmlPreviousString)
	MergeXMLUtility xmlCurrentUtil = new MergeXMLUtility(xmlCurrentString)

	def nodeListPrevious = xmlPreviousUtil.executeXpath(xpathSource+"/" + sourceNodeName, "NODESET")
	Map<String,Node> gotNode = new HashMap<>();
	nodeListPrevious.each{
		def nodeCurrent = null;
		if(gotNode.containsKey(it.getTextContent()))
			nodeCurrent = gotNode.get(it.getTextContent());
		else {
			nodeCurrent = xmlCurrentUtil.executeXpath("${xpathTarget}[./${targetNodeName} = '${it.getTextContent()}']", "NODE")
			gotNode.put(it.getTextContent(), nodeCurrent)
		}
		if(nodeCurrent!=null)
			it.getParentNode().appendChild(xmlPreviousUtil.getDocument().importNode(nodeCurrent,true))
	}

	StringWriter stringWriter = new StringWriter()

	javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
			new DOMSource(xmlPreviousUtil.getDocument()),
			new StreamResult(stringWriter)
			)

	return stringWriter.toString()
}

class MergeXMLUtility{
	private Document xmlData
	private XPath xpath
	MergeXMLUtility(String xmlPreviousString) {
		//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		xpath = XPathFactory.newInstance().newXPath()
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
		this.xmlData = builder.parse(new InputSource(new StringReader(xmlPreviousString)))
	}

	public Object executeXpath(String xpathEquation, String type) {
		def xpathResult = this.xpath.evaluate(xpathEquation, xmlData.documentElement, new QName("http://www.w3.org/1999/XSL/Transform", type))
		return xpathResult
	}

	public Node retrieveNodeOfParentNode(String path, Node parentNode) throws XPathExpressionException {
		return (Node) this.xpath.evaluate(path, parentNode, XPathConstants.NODE);
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

	public Document getDocument() {
		return xmlData
	}
}