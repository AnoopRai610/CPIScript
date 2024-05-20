package com.acn;

import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.*
import org.codehaus.jettison.json.JSONArray
import org.codehaus.jettison.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

import com.sap.gateway.ip.core.customdev.util.Message

import groovy.transform.Field
@Field
		XMLUtility xml;
@Field
		String ns = "http://www.w3.org/2001/XMLSchema";
@Field
		String sequence = "xsd:sequence";

def Message processData(Message message) {
	JSONObject jsonObject = new JSONObject(message.getBody(String.class));
	Map<String,String> namespaces = [xsd:ns];
	xml = new XMLUtility("<xsd:schema attributeFormDefault=\"unqualified\" elementFormDefault=\"qualified\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"/>",namespaces);

	Element elTemp = xml.createElement("xsd:element", ["name" : "rootNode"],xml.getDocument().getDocumentElement());
	Element elComplex = xml.createElement("xsd:complexType", elTemp);
	Element elSequence = xml.createElement(sequence, elComplex);
	processJsonObject(jsonObject,elSequence);
	message.setBody(xml.getDocumentString());
	return message;
}

public void processJsonObject(JSONObject jsonObject, Element parent) {
	for (String key : jsonObject.keys()) {
		String newKey = key.replaceAll("/", "_-_");
		Object value = jsonObject.get(key);

		if (value instanceof JSONObject) {
			Element elSequence;
			Element existingElement = existingElement(parent, newKey)
			if(existingElement==null) {
				Element elTemp = xml.createElement("xsd:element", ["name" : newKey, minOccurs:"0"],parent);
				Element elComplex = xml.createElement("xsd:complexType", elTemp);
				elSequence = xml.createElement(sequence, elComplex);
			} else {
				if(existingElement.hasChildNodes())
					elSequence = existingElement.getFirstChild().getFirstChild();
				else
					elSequence = existingElement;
			}
			processJsonObject(value,elSequence);
		}
		else if (value instanceof JSONArray) {
			Element elSequence;
			Element existingElement = existingElement(parent, newKey)
			if(existingElement==null) {
				Element elTemp = xml.createElement("xsd:element", ["name" : newKey, minOccurs:"0", maxOccurs:"unbounded"],parent);
				Element elComplex = xml.createElement("xsd:complexType", elTemp);
				elSequence = xml.createElement(sequence, elComplex);
			} else {
				if(existingElement.hasChildNodes())
					elSequence = existingElement.getFirstChild().getFirstChild();
				else
					elSequence = existingElement;
			}
			processJsonArray(value,elSequence);
		}
		else {
			if(existingElement(parent, newKey)==null)
				xml.createElement("xsd:element", ["name" : newKey, type: "xsd:string", minOccurs:"0"],parent);
		}
	}
}

public void processJsonArray(JSONArray jsonArray, Element parent) {
	if(jsonArray.length()==0 && parent.getNodeName().equals(sequence)) {
		Element pTemp = parent.getParentNode().getParentNode();
		xml.remove(parent.getParentNode());
		pTemp.setAttribute("type", "xsd:string")
		if(false)
			xml.createElement("xsd:element", ["name" : "item", type: "xsd:string", minOccurs:"0", maxOccurs:"unbounded"],parent);
	}
	for (int i = 0; i < jsonArray.length(); i++) {
		Object value = jsonArray.get(i);

		if (value instanceof JSONObject) {
			processJsonObject(value,parent);
		}
		else if (value instanceof JSONArray) {
			processJsonArray(value,parent)
		}
		else {
			if(i==0 && parent.getNodeName().equals(sequence)) {
				Element pTemp = parent.getParentNode().getParentNode();
				xml.remove(parent.getParentNode());
				pTemp.setAttribute("type", "xsd:string")
				if(false)
					xml.createElement("xsd:element", ["name" : "item", type: "xsd:string", minOccurs:"0", maxOccurs:"unbounded"],parent);
			}
		}
	}
}

def Element existingElement(Element parent,String name) {
	return parent.getChildNodes().find { N->
		(N.getAttribute("name").equals(name))
	}
}

class XMLUtility{
	private Document xmlData
	private XPath xpath

	XMLUtility(String xml) {
		try{
			//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
			xpath = XPathFactory.newInstance().newXPath()
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
			this.xmlData = builder.parse(new InputSource(new StringReader(xml)))
		} catch(Exception e) {
			throw new Exception(xml);
		}
	}

	XMLUtility(InputStream xml) {
		//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		xpath = XPathFactory.newInstance().newXPath()
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
		this.xmlData = builder.parse(xml)
	}

	XMLUtility(InputStream xml, Map<String,String> namespaces) {
		//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		xpath = XPathFactory.newInstance().newXPath()
		xpath.setNamespaceContext(new NamespaceContextImp(namespaces))
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance()
		docBuilderFactory.setNamespaceAware(true)
		this.xmlData = docBuilderFactory.newDocumentBuilder().parse(xml)
	}

	XMLUtility(String xml, Map<String,String> namespaces) {
		//xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		xpath = XPathFactory.newInstance().newXPath()
		xpath.setNamespaceContext(new NamespaceContextImp(namespaces))
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance()
		docBuilderFactory.setNamespaceAware(true)
		this.xmlData = docBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
	}

	public Object executeXpath(String xpathEquation, String type) {
		def xpathResult = this.xpath.evaluate(xpathEquation, xmlData.documentElement, new QName("http://www.w3.org/1999/XSL/Transform", type))
		return xpathResult
	}

	public Object executeXpath(String xpathEquation, String type, Element parent) {
		def xpathResult = this.xpath.evaluate(xpathEquation, parent, new QName("http://www.w3.org/1999/XSL/Transform", type))
		return xpathResult
	}

	public Document getDocument() {
		return xmlData
	}

	public void remove(Object it) {
		Element n = it
		n.getParentNode().removeChild(n)
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

	public Element createElement(String name, Node parentNode) {
		Element newNode = xmlData.createElement(name);
		parentNode.appendChild(newNode);
		return newNode;
	}

	public Element createElement(String name, Map<String,String> attributes, Node parentNode) {
		Element newNode = xmlData.createElement(name);
		attributes.each {
			newNode.setAttribute(it.key, it.value);
		}
		parentNode.appendChild(newNode);
		return newNode;
	}

	public Element createElement(String name, String ns, Map<String,String> attributes, Node parentNode) {
		Element newNode = xmlData.createElementNS(ns,name);
		attributes.each {
			newNode.setAttribute(it.key, it.value);
		}
		parentNode.appendChild(newNode);
		return newNode;
	}

	public Element createElement(String name, Map<String,String> attributes) {
		Element newNode = xmlData.createElement(name);
		attributes.each {
			newNode.setAttribute(it.key, it.value);
		}
		return newNode;
	}

	public Element createElement(String name, String ns, Map<String,String> attributes) {
		Element newNode = xmlData.createElementNS(ns,name);
		attributes.each {
			newNode.setAttribute(it.key, it.value);
		}
		return newNode;
	}

	public byte[] getDocumentByte() {
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
				new DOMSource(this.getDocument()),
				new StreamResult(bos)
				);
		return bos.toByteArray()
	}

	public String getDocumentString() {
		return new String(this.getDocumentByte());
	}
}

class NamespaceContextImp implements NamespaceContext {

	private Map<String,String> namespaces

	public NamespaceContextImp(Map<String, String> namespaces) {
		this.namespaces = namespaces
	}

	@Override
	public String getNamespaceURI(String prefix) {
		return namespaces.get(prefix)
	}

	@Override
	public String getPrefix(String namespaceURI) {
		namespaces.each {
			if(it.value.equals(namespaces))
				return it.key
		}
		return ""
	}

	@Override
	public Iterator<String> getPrefixes(String namespaceURI) {
		List<String> itr = new HashSet<>()
		namespaces.each {
			if(it.value.equals(namespaces))
				itr.add(it.key)
		}
		return itr.iterator()
	}
}