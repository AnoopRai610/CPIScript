package com.acn

import java.text.SimpleDateFormat

import javax.mail.util.ByteArrayDataSource
import javax.xml.namespace.NamespaceContext
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException

import org.apache.camel.Exchange
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.component.ahc.AhcOperationFailedException
import org.apache.cxf.binding.soap.SoapFault
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

import com.sap.gateway.core.ip.component.odata.exception.OsciException
import com.sap.gateway.ip.core.customdev.util.AttachmentWrapper
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.msglog.MessageLog

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

//Write to Message Monitoring Custom Header
//AddCustomHeader
// Name1~Expression1|Name2~Expression2|...
// XPATH, Simple Expression
// XPATH-> xpath:
// Simple Expression -> simple:
def Message writeToCustomHeader(Message message) {
	String customheaderProperty = message.getProperty("AddCustomHeader");
	List<ExpressionData> expdata = parseExpressions(message.exchange, message.getBody(String.class), customheaderProperty);

	MessageLog messageLog = messageLogFactory.getMessageLog(message);
	if(messageLog!=null)
		expdata.each { k->
			messageLog.addCustomHeaderProperty(k.getName(), k.getExpressionValue())
		}
	return message;
}

//Write to trace log
def Message logMessage(Message message) {
	if(!message.getProperty("enableLog").equals("true"))
		return message
	MessageLog messageLog = messageLogFactory.getMessageLog(message);
	if(messageLog == null)
		return message
	messageLog.addAttachmentAsString("FullLog", generateLog(message), "text/plain")
	return message;
}

def Message logExceptionMessage(Message message) {
	MessageLog messageLog = messageLogFactory.getMessageLog(message);
	if(messageLog == null)
		return message
	messageLog.addAttachmentAsString("ExceptionFullLog", generateLog(message), "text/plain")
	return message;
}


//Alert Creation
//MainMessagePayload
//Identifiers
//Attachments -> log.txt~fullLog|mainpayload.txt~simple:${property.MainMessagePayload}|errorResponse.json~error:body
//OtherDetails
//Subject
def Message formatAlertMail(Message message) {
	Exchange ex = message.exchange;
	def prop = message.getProperties();

	ErrorLog errorLog = parseException(message);
	if(errorLog.getProperty("HttpError")!=null && !errorLog.getProperty("HttpError").toString().equals("true"))
		parseHTTPExceptions(errorLog);

	String identifiers = prop.get("Identifiers")
	if(identifiers==null||identifiers.isEmpty())
		throw new Exception("Please maintain \"Identifiers\" property with expression detail.")

	List<ExpressionData> expdataIdentifier = parseExpressions(message.exchange, prop.get("MainMessagePayload"), identifiers);
	List<ExpressionData> expdataOtherDetails = (prop.get("OtherDetails")==null||prop.get("OtherDetails").toString().isEmpty())?null:parseExpressions(message.exchange, prop.get("MainMessagePayload"), prop.get("OtherDetails"));

	String formatedHTTPBody = formatHTTPBody(message, errorLog, expdataIdentifier, expdataOtherDetails);

	List<ExpressionData> expdataAttachment = (prop.get("Attachments")==null||prop.get("Attachments").toString().isEmpty())?null:parseExpressions(message.exchange, prop.get("MainMessagePayload"), prop.get("Attachments"));

	String flow = getSimple('${camelId}',ex);
	String subject = (prop.get("Subject")==null)?"[Alert] Alert raised by $flow":getSimple(prop.get("Subject"), ex)

	//Put objects in message...
	if(expdataAttachment!=null)
		expdataAttachment.each {
			if(it.getExpressionValue().equalsIgnoreCase("fullLog")) {
				message.addAttachmentObject(it.getName(), new AttachmentWrapper(new ByteArrayDataSource(generateLog(message).getBytes(),"text/plain")))
			} else if(it.getExpressionValue().startsWith("error:")){
				String description="";
				if((errorLog.getErrorDescription().startsWith("{") && errorLog.getErrorDescription().endsWith("}"))||(errorLog.getErrorDescription().startsWith("[") && errorLog.getErrorDescription().endsWith("]"))) {
					description = JsonOutput.prettyPrint(errorLog.getErrorDescription())
				} else if(errorLog.getErrorDescription().startsWith("<?xml") && errorLog.getErrorDescription().endsWith(">")) {
					description = XmlUtil.serialize(errorLog.getErrorDescription())
				} else
					description = errorLog.getErrorDescription()
				message.addAttachmentObject(it.getName(), new AttachmentWrapper(new ByteArrayDataSource(description.getBytes(),"text/plain")))
			} else
				message.addAttachmentObject(it.getName(), new AttachmentWrapper(new ByteArrayDataSource(it.getExpressionValue().getBytes(),"text/plain")))
		}
	message.setBody(formatedHTTPBody)
	message.setProperty("subject", subject);
	return message;
}

def Message formatCentralAlertRequest(Message message) {
	Exchange ex = message.exchange;
	def prop = message.getProperties();

	ErrorLog errorLog = parseException(message);
	if(errorLog.getProperty("HttpError")!=null && !errorLog.getProperty("HttpError").toString().equals("true"))
		parseHTTPExceptions(errorLog);

	String identifiers = prop.get("Identifiers")
	if(identifiers==null||identifiers.isEmpty())
		throw new Exception("Please maintain \"Identifiers\" property with expression detail.")

	List<ExpressionData> expdataIdentifier = parseExpressions(message.exchange, prop.get("MainMessagePayload"), identifiers);
	List<ExpressionData> expdataOtherDetails = (prop.get("OtherDetails")==null||prop.get("OtherDetails").toString().isEmpty())?null:parseExpressions(message.exchange, prop.get("MainMessagePayload"), prop.get("OtherDetails"));

	String formatedHTTPBody = formatHTTPBody(message, errorLog, expdataIdentifier, expdataOtherDetails);

	List<ExpressionData> expdataAttachment = (prop.get("Attachments")==null||prop.get("Attachments").toString().isEmpty())?null:parseExpressions(message.exchange, prop.get("MainMessagePayload"), prop.get("Attachments"));

	String flow = getSimple('${camelId}',ex);
	String subject = (prop.get("Subject")==null)?"[Alert] Alert raised by $flow":getSimple(prop.get("Subject"), ex)

	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmssSSSS")
	XMLUtility xml = new XMLUtility("<Requests><Request/></Requests>");
	Element Request = xml.getDocument().getElementsByTagName("Request").item(0);
	xml.createNode("id", prop.get('SAP_MessageProcessingLogID'), Request)
	xml.createNode("eventType", "Email", Request)
	xml.createNode("eventTimestamp", (prop.get('CamelCreatedTimestamp')!=null)?sdf.format(prop.get('CamelCreatedTimestamp')):sdf.format(new Date()), Request)
	xml.createNode("severity", (prop.get('severity'))?prop.get('severity'):"LOW", Request)
	xml.createNode("category", (prop.get('category'))?prop.get('category'):"SAPCPI", Request)
	xml.createNode("flow", flow, Request)
	xml.createNode("subject", subject, Request)
	xml.createNode("priority", (prop.get('priority'))?prop.get('priority'):"0", Request)
	
	Element properties = xml.createNode("Properties", "", Request);
	
	prop.each { 
		Element property = xml.createNode("Property", "", properties);
		xml.createNode("Name", it.getKey(), property);
		xml.createNode("Value", Base64.getEncoder().encodeToString(it.getValue().toString().getBytes()), property);
	}
	
	Element headers = xml.createNode("Headers", "", Request);
	
	message.getHeaders().each {
		Element header = xml.createNode("Header", "", headers);
		xml.createNode("Name", it.getKey(), header);
		xml.createNode("Value", Base64.getEncoder().encodeToString(it.getValue().toString().getBytes()), header);
	}
	
	Element payload = xml.createNode("Payload", "", Request);
	
	xml.createNode("body", Base64.getEncoder().encodeToString(formatedHTTPBody.getBytes()), payload)
	
	Element attachments = xml.createNode("Attachments", "", payload);
	
	//Put objects in message...
	if(expdataAttachment!=null)
		expdataAttachment.each {
			Element attachment = xml.createNode("Attachment", "", attachments);
			if(it.getExpressionValue().equalsIgnoreCase("fullLog")) {
				xml.createNode("Name", it.getName(), attachment);
				xml.createNode("Value", Base64.getEncoder().encodeToString(generateLog(message).getBytes()), attachment);
				xml.createNode("Type", "text/plain", attachment);
			} else if(it.getExpressionValue().startsWith("error:")){
				String description="";
				if((errorLog.getErrorDescription().startsWith("{") && errorLog.getErrorDescription().endsWith("}"))||(errorLog.getErrorDescription().startsWith("[") && errorLog.getErrorDescription().endsWith("]"))) {
					description = JsonOutput.prettyPrint(errorLog.getErrorDescription())
				} else if(errorLog.getErrorDescription().startsWith("<?xml") && errorLog.getErrorDescription().endsWith(">")) {
					description = XmlUtil.serialize(errorLog.getErrorDescription())
				} else
					description = errorLog.getErrorDescription()
				xml.createNode("Name", it.getName(), attachment);
				xml.createNode("Value", Base64.getEncoder().encodeToString(description.getBytes()), attachment);
				xml.createNode("Type", "text/plain", attachment);
			} else {
				xml.createNode("Name", it.getName(), attachment);
				xml.createNode("Value", Base64.getEncoder().encodeToString(it.getExpressionValue().getBytes()), attachment);
				xml.createNode("Type", "text/plain", attachment);
			}
		}	
			
	message.setBody(xml.getDocumentString())
	return message;
}

def ErrorLog parseException(Message message) {
	Exception exception = message.getProperty(Exchange.EXCEPTION_CAUGHT)
	ErrorLog errorLog = new ErrorLog();
	errorLog.setProperty("HttpError", "false")
	errorLog.setProperty("ErrorType", "Simple")
	if(exception instanceof AhcOperationFailedException){
		AhcOperationFailedException ahcException = (AhcOperationFailedException) exception
		errorLog.setErrorDescription(ahcException.getResponseBody())
		errorLog.setProperty("URL", ahcException.getUrl())
		errorLog.setProperty("RedirectLocation", ahcException.getRedirectLocation())
		errorLog.setProperty("StatusCode", ahcException.getStatusCode()) //Can use Header CamelHttpResponseCode
		errorLog.setProperty("StatusText", ahcException.getStatusText()) //Can use Header CamelHttpResponseText
		errorLog.setProperty("ResponseHeaders", ahcException.getResponseHeaders())
		errorLog.setProperty("HttpError", "true")
	} else if(exception instanceof OsciException && message.getBodySize()>0){
		errorLog.setErrorDescription(message.getBody(String))
		errorLog.setProperty("HttpError", "true")
		Map header = ["content-type":"application/xml"]
		errorLog.setProperty("RedirectLocation", header)
	}else if(exception instanceof SoapFault) {
		SoapFault soapFault = (SoapFault) exception
		errorLog.setErrorDescription(soapFault.getRole() + soapFault.getReason())
	} else if(exception instanceof Exception) {
		errorLog.setErrorDescription("Error message: ${exception.getMessage()}.")
	}
	else {
		errorLog.setErrorDescription("Error message: ${exception}.")
	}
	return errorLog;
}

def void parseHTTPExceptions(ErrorLog errorLog) {
	String httpError = errorLog.getProperty("HttpError")
	Map<String, String> nameValueComb = new LinkedHashMap<>()
	int indexDulicate = 1
	if(httpError.equals("true")) {
		Map<String, String> responseHeader = errorLog.getProperty("ResponseHeaders")
		if((responseHeader.get("content-type")!=null && responseHeader.get("content-type").matches("application.*json|application.*xml"))) {
			String body = errorLog.getErrorDescription();
			if(body.trim().startsWith("<")) {
				def xmlMsg = new XmlParser().parseText(body)
				xmlMsg.'*'.each{ N->
					if(nameValueComb.containsKey(N.name())){
						nameValueComb.put(N.name() +""+ indexDulicate, N)
						indexDulicate += 1
					}
					else
						nameValueComb.put(N.name(), N)
				}
			} else {
				Map msg = new JsonSlurper().parseText(body)
				msg.each { K,V->
					nameValueComb.put(K, V)
				}
			}
		}
		if(nameValueComb.size()>0) {
			errorLog.setProperty("HTTPParsedMap",nameValueComb)
			errorLog.setProperty("ErrorType", "Complex")
			errorLog.setErrorDescription("")
		}
	}
}

def String formatHTTPBody(Message message, ErrorLog errorLog, List<ExpressionData> expdataIdentifier, List<ExpressionData> expdataOtherDetails) {

	Map properties = message.getProperties();

	StringBuilder identifier = null;
	int size = expdataIdentifier.size()-1;
	expdataIdentifier.eachWithIndex {v,i->
		if(identifier==null) {
			identifier = new StringBuilder(v.getString(" "))
		} else {
			(size==i)?identifier.append(" and " + v.getString(" ")):identifier.append(", " + v.getString(" "))
		}
	}

	def cpiPropertiesCalc = { String name, String desc->
		return (properties.get(name)==null||properties.get(name).toString().isEmpty())?null:desc + ": " + properties.get(name);
	}

	Map map = errorLog.getErrorProperties();

	def styleCSS='''
		table { font-family: arial, sans-serif; border-collapse: collapse; width: 100%; } 
		td, th { border: 1px solid #000000; text-align: left; padding: 8px; } 
		th { background-color: #dddddd; }		
		''';
	def builder  = new StreamingMarkupBuilder().bind{
		mkp.yieldUnescaped '<!DOCTYPE html>'
		html(lang:'en') {
			header {
				if(map.get("ErrorType").equals("Complex"))
					style(styleCSS)
			}
			body {
				p('Dear User,')
				p('An alert email has been raised by integration flow ' << getSimple('${camelId}',message.exchange) << ' at ' << properties.get("CamelCreatedTimestamp") << '. ')
				p{b("$identifier processing failed")}

				if(map.get("ErrorType").equals("Simple") && !errorLog.getErrorDescription().isEmpty()) {
					p{
						div([("style"):"color:red"],"Error description:")
						div(errorLog.getErrorDescription())
					}
				}

				if(map.get("ErrorType").equals("Complex")) {
					p([("style"):"color:red"],'Below is the response for HTTP request: ')
					table {
						tr{
							th('Name')
							th('Value')
						}
						nonBase64.each{K,V->
							tr{
								td(K)
								td(V)
							}
						}
					}
				}
				if(expdataOtherDetails!=null) {
					p('Other details related to message:')
					ol{
						expdataOtherDetails.each {
							li(it.getString(": "))
						}
					}
				}
				div{
					p('Details for tracking in SAP CPI:')
					ol{
						[
							"CamelCorrelationId~Camel Correlation Id",
							"CamelCreatedTimestamp~Camel Created Timestamp",
							"SAP_MplCorrelationId~SAP MPL Correlation Id",
							"SAP_MessageProcessingLogID~SAP Message Processing Log ID",
							"CamelToEndpoint~Camel To Endpoint"
						].each {
							String[] val = it.split("~")
							String cpiDes = cpiPropertiesCalc(val[0],val[1])
							if(cpiDes!=null)
								li(cpiDes)
						}
					}
				}
				div{
					p('Important links associated to message:')

					String messageID = message.getProperty('SAP_MessageProcessingLogID');
					String run_id = message.getProperty("SAP_RunId");

					String messageReq = "{\"identifier\":\"${messageID}\"}";
					String MessageProcessingRunReq = "{\"parentContext\":{\"MessageMonitor\":{\"artifactName\":\"\",\"identifier\":\"${messageID}\"}},\"messageProcessingLog\":\"${messageID}\",\"RunId\":\"${run_id}\"}"
					String MessageDetailsReq = "{\"parentContext\":{\"identifier\":\"${messageID}\",\"idType\":\"MPL\",\"namePattern\":\"\",\"artifactKey\":\"__ALL__MESSAGE_PROVIDER\",\"artifactName\":\"\",\"packageId\":null,\"packageName\":\"\",\"status\":\"ALL\",\"time\":\"PASTHOUR\",\"from\":\"\",\"to\":\"\",\"useAdvancedFields\":false,\"sender\":null,\"receiver\":null,\"customStatus\":null,\"messageType\":null,\"messageCustomHeaderExpression\":null,\"artifactDisplayText\":\"All Artifacts\"},\"messageGuid\":\"${messageID}\"}"
					Map envMap = System.getenv();
					String CPIHOST_URL = "https://${envMap.get('TENANT_NAME')}.${envMap.get('IT_TENANT_ISTUDIO_UX_DOMAIN')}/shell/monitoring/"
					String URLMessages = CPIHOST_URL + "Messages/"+URLEncoder.encode(messageReq,"UTF8")
					String URLMessageProcessingRun = CPIHOST_URL + "MessageProcessingRun/"+URLEncoder.encode(MessageProcessingRunReq,"UTF8")
					String URLMessageDetails = CPIHOST_URL + "MessageDetails/"+URLEncoder.encode(MessageDetailsReq,"UTF8")
					ul{
						li{mkp.yieldUnescaped "<a href=\"${URLMessages}\">Message</a>"}
						li{mkp.yieldUnescaped "<a href=\"${URLMessageProcessingRun}\">Message Processing Steps</a>"}
						li{mkp.yieldUnescaped "<a href=\"${URLMessageDetails}\">Message Details (Log)</a>"}
					}
				}
				div{
					mkp.yieldUnescaped '<p>Regards,<br/>SAP CPI</p>'
				}
			}
		}
	}

	return XmlUtil.serialize(builder).replaceAll(/<.xml.*?>/,"");
}


class ErrorLog {
	String errorDescription;
	Map<String,Object> errorProperties;

	public ErrorLog() {
		errorProperties = new HashMap();
	}
	public String getErrorDescription() {
		return (errorDescription==null)?"" : errorDescription;
	}
	public void setErrorDescription(String errorDescription) {
		this.errorDescription = errorDescription;
	}
	public Map<String, String> getErrorProperties() {
		return errorProperties;
	}
	public void setErrorProperties(Map<String, String> errorProperties) {
		this.errorProperties = errorProperties;
	}
	public Object getProperty(String name) {
		return (errorProperties.containsKey(name))?errorProperties.get(name):"";
	}
	public void setProperty(String name, Object value) {
		errorProperties.put(name, value);
	}
}


List<ExpressionData> parseExpressions(Exchange ex, String body, String expressions){
	String[] expression = expressions.split("\\|")
	List<ExpressionData> listExpData = new ArrayList();
	XMLUtility xmlUtil = null;

	def expressionCalc = { String exp->
		String value=null;
		if(exp.startsWith("simple:")) {
			value = getSimple(exp.replaceFirst("simple:", "").trim(),ex)
		} else if(exp.startsWith("xpath:")) {
			String xpath = exp.replaceFirst("xpath:", "").trim()
			if(xmlUtil==null)
				xmlUtil = new XMLUtility(body)
			value = xmlUtil.executeXpath(xpath, "STRING")
		} else {
			value = exp
		}
		println value;
		return value;
	}

	expression.each {
		if(it.contains("~")) {
			String[] exp = it.split("~")
			ExpressionData expData = new ExpressionData(expressionCalc(exp[0]),exp[1]);
			expData.setExpressionValue(expressionCalc(exp[1]));
			listExpData.add(expData);
		}
	}
	return listExpData;
}


class ExpressionData {
	private String name;
	private String expression;
	private String expressionValue;
	public ExpressionData(String name, String expression) {
		this.name = name;
		this.expression = expression;
	}
	public String getName() {
		return name;
	}
	public String getExpressionValue() {
		return expressionValue;
	}
	public void setExpressionValue(String expressionValue) {
		this.expressionValue = expressionValue;
	}
	public String getExpression() {
		return expression;
	}
	public String getString(String sep) {
		return name + sep + expressionValue;
	}
}

def String getSimple(String text, Exchange exchange) {
	return SimpleBuilder.simple(text).evaluate(exchange, String);
}

def String generateLog(Message message) {
	def NL = System.lineSeparator();
	def Payload = message.getBody(java.lang.String) as String;
	def LS = NL << repeateAString("-",150) << NL;
	StringBuilder sbMessage = new StringBuilder();
	sbMessage << "Message headers " << LS;
	iterateMap(message.getHeaders(),sbMessage);
	sbMessage << LS << "Message Properties " << LS;
	iterateMap(message.getProperties(),sbMessage);
	sbMessage << LS << "Message Body " << LS;
	sbMessage << Payload

	if(message.getAttachmentsSize()>0) {
		sbMessage << LS << "Attachment " << LS;
		Map<String, AttachmentWrapper> attachWrap = message.getAttachmentWrapperObjects();
		attachWrap.each { k,v -> sbMessage << "Attachment Name " << k << ":\n" << v.getDataHandler().getContent() as String}
	}

	sbMessage << NL << NL << repeateAString("*",150) << NL;

	Exchange exchange = message.exchange;
	sbMessage << NL << getSimple('${messageHistory}',exchange) + NL

	return sbMessage.toString();
}

def void iterateMap(Map<String,Object> map, StringBuilder sb) {
	map.each {K,V-> sb << ((K.matches("Auth.*"))? "$K = '***' \n": "$K = $V \n")};
}

def String repeateAString(String value, int repeatBy) {
	StringBuilder sb = new StringBuilder();
	while (repeatBy>0) {
		sb.append(value);
		repeatBy--;
	}
	return sb.toString();
}

class XMLUtility{
	private Document xmlData
	private XPath xpath

	XMLUtility(String xml) {
		xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		//xpath = XPathFactory.newInstance().newXPath()
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
		this.xmlData = builder.parse(new InputSource(new StringReader(xml)))
	}

	XMLUtility(InputStream xml) {
		xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		//xpath = XPathFactory.newInstance().newXPath()
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
		this.xmlData = builder.parse(xml)
	}

	XMLUtility(InputStream xml, Map<String,String> namespaces) {
		xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		//xpath = XPathFactory.newInstance().newXPath()
		xpath.setNamespaceContext(new NamespaceContextImp(namespaces))
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance()
		docBuilderFactory.setNamespaceAware(true)
		this.xmlData = docBuilderFactory.newDocumentBuilder().parse(xml)
	}

	XMLUtility(String xml, Map<String,String> namespaces) {
		xpath = new net.sf.saxon.xpath.XPathFactoryImpl().newXPath()
		//xpath = XPathFactory.newInstance().newXPath()
		xpath.setNamespaceContext(new NamespaceContextImp(namespaces))
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance()
		docBuilderFactory.setNamespaceAware(true)
		this.xmlData = docBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
	}

	public Object executeXpath(String xpathEquation, String type) {
		def xpathResult = this.xpath.evaluate(xpathEquation, xmlData.documentElement, new QName("http://www.w3.org/1999/XSL/Transform", type))
		return xpathResult
	}

	public Node retrieveNodeOfParentNode(String path, Node parentNode) throws XPathExpressionException {
		return (Node) this.xpath.evaluate(path, parentNode, XPathConstants.NODE);
	}

	public NodeList retrieveNodeListOfParentNode(String path, Node parentNode) throws XPathExpressionException {
		return (NodeList) this.xpath.evaluate(path, parentNode, XPathConstants.NODESET);
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

	public String getDocumentString(){
		StringWriter stringWriter = new StringWriter()
		javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
				new DOMSource(xmlData),
				new StreamResult(stringWriter)
				)

		return stringWriter.toString()
	}

	public void remove(Object it) {
		Element n = it
		n.parentNode().removeChild(n)
	}

	public Document getDocument() {
		return xmlData
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